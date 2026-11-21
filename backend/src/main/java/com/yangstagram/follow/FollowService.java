package com.yangstagram.follow;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.event.EventService;
import com.yangstagram.notification.NotificationService;
import com.yangstagram.notification.NotificationType;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팔로우 관계와 "볼 수 있는가" 판정 (기획서 §6.1의 MVP 부분: 비공개 계정은 승인된 팔로워와 본인만).
 * 차단·친한 친구 조건은 M2~M3에서 이 클래스에 더한다.
 */
@Service
public class FollowService {

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;
    private final NotificationService notifications;
    private final EventService events;

    public FollowService(NamedParameterJdbcTemplate jdbc, LabClock clock, NotificationService notifications,
                         EventService events) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.notifications = notifications;
        this.events = events;
    }

    public record UserListItem(UserSummary user, Relationship relationship) {
    }

    public record SuggestedUser(UserSummary user, int mutualCount, boolean followsMe) {
    }

    // ------------------------------------------------------------ 판정

    public FollowStatus status(long followerId, long followingId) {
        List<String> rows = jdbc.queryForList(
                "select status from follows where follower_id = :a and following_id = :b",
                Map.of("a", followerId, "b", followingId), String.class);
        return rows.isEmpty() ? null : FollowStatus.valueOf(rows.getFirst());
    }

    public Relationship relationship(long viewerId, long targetId) {
        return viewerId == targetId ? Relationship.SELF : Relationship.of(status(viewerId, targetId));
    }

    /** 이 사람의 게시물·팔로워 목록을 볼 수 있는가 */
    public boolean canViewContent(long viewerId, long authorId, boolean authorPrivate) {
        if (viewerId == authorId) return true;
        if (blocked(viewerId, authorId)) return false;
        return !authorPrivate || status(viewerId, authorId) == FollowStatus.ACCEPTED;
    }

    /** 둘 사이에 차단이 있는가 (어느 쪽이든) */
    public boolean blocked(long a, long b) {
        Integer n = jdbc.queryForObject("""
                select count(*) from blocks where (blocker_id = :a and blocked_id = :b) or (blocker_id = :b and blocked_id = :a)
                """, Map.of("a", a, "b", b), Integer.class);
        return n != null && n > 0;
    }

    public void requireCanView(long viewerId, long authorId, boolean authorPrivate) {
        if (viewerId != authorId && blocked(viewerId, authorId)) {
            throw ApiException.notFound("게시물을 볼 수 없습니다.");
        }
        if (!canViewContent(viewerId, authorId, authorPrivate)) {
            throw ApiException.forbidden("PRIVATE_ACCOUNT", "비공개 계정입니다. 팔로우하면 사진과 동영상을 볼 수 있습니다.");
        }
    }

    /** 여러 사람에 대한 관계를 한 번에 */
    public Map<Long, Relationship> relationships(long viewerId, Collection<Long> targetIds) {
        Map<Long, Relationship> result = new HashMap<>();
        if (targetIds.isEmpty()) return result;
        targetIds.forEach(id -> result.put(id, id == viewerId ? Relationship.SELF : Relationship.NONE));
        jdbc.query("select following_id, status from follows where follower_id = :me and following_id in (:ids)",
                new MapSqlParameterSource("me", viewerId).addValue("ids", targetIds),
                rs -> {
                    result.put(rs.getLong(1), Relationship.of(FollowStatus.valueOf(rs.getString(2))));
                });
        return result;
    }

    // ------------------------------------------------------------ 변경

    /** 공개 계정은 바로 팔로우, 비공개 계정은 요청(승인 대기). */
    @Transactional
    public Relationship follow(long viewerId, long targetId, boolean targetPrivate) {
        if (viewerId == targetId) {
            throw ApiException.badRequest("CANNOT_FOLLOW_SELF", "자기 자신은 팔로우할 수 없습니다.");
        }
        if (blocked(viewerId, targetId)) {
            throw ApiException.forbidden("BLOCKED", "이 계정을 팔로우할 수 없습니다. 차단을 해제한 뒤 다시 시도하세요.");
        }
        FollowStatus status = targetPrivate ? FollowStatus.PENDING : FollowStatus.ACCEPTED;
        int inserted = jdbc.update("""
                insert into follows (follower_id, following_id, status, is_favorite, created_at)
                values (:a, :b, :status, false, :now)
                on conflict (follower_id, following_id) do nothing
                """, new MapSqlParameterSource("a", viewerId).addValue("b", targetId)
                .addValue("status", status.name()).addValue("now", Timestamp.from(Instant.now(clock))));
        if (inserted == 1) {
            if (status == FollowStatus.ACCEPTED) adjustCounts(viewerId, targetId, +1);
            events.record(viewerId, "FOLLOW", null, targetId, null);
            notifications.notify(targetId, viewerId,
                    status == FollowStatus.ACCEPTED ? NotificationType.FOLLOW : NotificationType.FOLLOW_REQUEST,
                    null, null, null, null);
        }
        return Relationship.of(status(viewerId, targetId));
    }

    /** 언팔로우 또는 보낸 요청 취소 */
    @Transactional
    public Relationship unfollow(long viewerId, long targetId) {
        List<String> deleted = jdbc.queryForList(
                "delete from follows where follower_id = :a and following_id = :b returning status",
                Map.of("a", viewerId, "b", targetId), String.class);
        if (!deleted.isEmpty() && FollowStatus.valueOf(deleted.getFirst()) == FollowStatus.ACCEPTED) {
            adjustCounts(viewerId, targetId, -1);
            events.record(viewerId, "UNFOLLOW", null, targetId, null);
        }
        notifications.retract(viewerId, NotificationType.FOLLOW, null, null, null, targetId);
        notifications.retract(viewerId, NotificationType.FOLLOW_REQUEST, null, null, null, targetId);
        return Relationship.NONE;
    }

    // ------------------------------------------------------------ 팔로우 요청 (비공개 계정)

    /** 나에게 온 승인 대기 요청 (최근 순) */
    public List<UserListItem> requests(long me, int page, int size) {
        List<UserSummary> users = jdbc.query("""
                select %s from follows f join users u on u.id = f.follower_id
                where f.following_id = :me and f.status = 'PENDING' and u.deleted_at is null
                order by f.created_at desc, f.id desc
                limit :limit offset :offset
                """.formatted(UserRows.cols("u")),
                new MapSqlParameterSource("me", me).addValue("limit", size).addValue("offset", page * size),
                (rs, i) -> UserRows.read(rs, "u"));
        Map<Long, Relationship> rel = relationships(me, users.stream().map(UserSummary::id).toList());
        return users.stream().map(u -> new UserListItem(u, rel.get(u.id()))).toList();
    }

    /** 요청 승인: 상대가 내 팔로워가 된다 */
    @Transactional
    public void accept(long me, long requesterId) {
        int updated = jdbc.update("""
                update follows set status = 'ACCEPTED', created_at = :now
                where follower_id = :a and following_id = :me and status = 'PENDING'
                """, new MapSqlParameterSource("a", requesterId).addValue("me", me)
                .addValue("now", Timestamp.from(Instant.now(clock))));
        if (updated == 0) {
            throw ApiException.notFound("팔로우 요청을 찾을 수 없습니다.");
        }
        adjustCounts(requesterId, me, +1);
        notifications.retract(requesterId, NotificationType.FOLLOW_REQUEST, null, null, null, me);
        notifications.notify(me, requesterId, NotificationType.FOLLOW, null, null, null, null);
        notifications.notify(requesterId, me, NotificationType.FOLLOW_ACCEPTED, null, null, null, null);
    }

    /** 요청 거절 (상대에게는 알리지 않는다) */
    @Transactional
    public void decline(long me, long requesterId) {
        jdbc.update("delete from follows where follower_id = :a and following_id = :me and status = 'PENDING'",
                Map.of("a", requesterId, "me", me));
        notifications.retract(requesterId, NotificationType.FOLLOW_REQUEST, null, null, null, me);
    }

    /** 내 팔로워에서 삭제 (상대에게 알리지 않음) */
    @Transactional
    public void removeFollower(long me, long followerId) {
        List<String> deleted = jdbc.queryForList(
                "delete from follows where follower_id = :a and following_id = :me returning status",
                Map.of("a", followerId, "me", me), String.class);
        if (!deleted.isEmpty() && FollowStatus.valueOf(deleted.getFirst()) == FollowStatus.ACCEPTED) {
            adjustCounts(followerId, me, -1);
        }
    }

    /** 계정 공개 범위 변경. 공개로 바꾸면 대기 중인 요청을 모두 승인한다. */
    @Transactional
    public void setPrivate(long me, boolean isPrivate) {
        jdbc.update("update users set is_private = :p, updated_at = :now where id = :me",
                new MapSqlParameterSource("p", isPrivate).addValue("me", me)
                        .addValue("now", Timestamp.from(Instant.now(clock))));
        if (!isPrivate) {
            List<Long> requesters = jdbc.queryForList(
                    "select follower_id from follows where following_id = :me and status = 'PENDING'",
                    Map.of("me", me), Long.class);
            for (Long r : requesters) {
                accept(me, r);
            }
        }
    }

    private void adjustCounts(long followerId, long followingId, int delta) {
        Map<String, Object> p = Map.of("a", followerId, "b", followingId, "d", delta);
        jdbc.update("update users set following_count = greatest(0, following_count + :d) where id = :a", p);
        jdbc.update("update users set followers_count = greatest(0, followers_count + :d) where id = :b", p);
    }

    // ------------------------------------------------------------ 목록

    /** 팔로워 목록 (최근 팔로우 순) */
    public List<UserListItem> followers(long viewerId, long userId, int page, int size) {
        return list("f.following_id = :uid", "f.follower_id", viewerId, userId, page, size);
    }

    /** 팔로잉 목록 (최근 팔로우 순) */
    public List<UserListItem> following(long viewerId, long userId, int page, int size) {
        return list("f.follower_id = :uid", "f.following_id", viewerId, userId, page, size);
    }

    private List<UserListItem> list(String where, String otherColumn, long viewerId, long userId, int page, int size) {
        List<UserSummary> users = jdbc.query("""
                select %s from follows f join users u on u.id = %s
                where %s and f.status = 'ACCEPTED' and u.deleted_at is null
                order by f.created_at desc, f.id desc
                limit :limit offset :offset
                """.formatted(UserRows.cols("u"), otherColumn, where),
                new MapSqlParameterSource("uid", userId).addValue("limit", size).addValue("offset", page * size),
                (rs, i) -> UserRows.read(rs, "u"));
        Map<Long, Relationship> rel = relationships(viewerId, users.stream().map(UserSummary::id).toList());
        return users.stream().map(u -> new UserListItem(u, rel.get(u.id()))).toList();
    }

    /** 추천 계정: 내가 팔로우하는 사람들이 많이 팔로우하는 계정 → 인기 계정 순 */
    public List<SuggestedUser> suggestions(long viewerId, int limit) {
        return jdbc.query("""
                select %s,
                       count(f2.follower_id) as mutual,
                       exists (select 1 from follows x where x.follower_id = u.id and x.following_id = :me
                               and x.status = 'ACCEPTED') as follows_me
                from users u
                left join follows f2 on f2.following_id = u.id and f2.status = 'ACCEPTED'
                     and f2.follower_id in (select following_id from follows
                                            where follower_id = :me and status = 'ACCEPTED')
                where u.id <> :me and u.deleted_at is null
                  and not exists (select 1 from follows f where f.follower_id = :me and f.following_id = u.id)
                  and not exists (select 1 from blocks b where (b.blocker_id = :me and b.blocked_id = u.id)
                                                         or (b.blocker_id = u.id and b.blocked_id = :me))
                group by u.id
                order by follows_me desc, mutual desc, u.followers_count desc, u.id
                limit :limit
                """.formatted(UserRows.cols("u")),
                new MapSqlParameterSource("me", viewerId).addValue("limit", limit),
                (rs, i) -> new SuggestedUser(UserRows.read(rs, "u"), rs.getInt("mutual"), rs.getBoolean("follows_me")));
    }
}
