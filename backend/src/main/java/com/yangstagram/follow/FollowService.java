package com.yangstagram.follow;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
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

    public FollowService(NamedParameterJdbcTemplate jdbc, LabClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
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
        return viewerId == authorId || !authorPrivate || status(viewerId, authorId) == FollowStatus.ACCEPTED;
    }

    public void requireCanView(long viewerId, long authorId, boolean authorPrivate) {
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
        FollowStatus status = targetPrivate ? FollowStatus.PENDING : FollowStatus.ACCEPTED;
        int inserted = jdbc.update("""
                insert into follows (follower_id, following_id, status, is_favorite, created_at)
                values (:a, :b, :status, false, :now)
                on conflict (follower_id, following_id) do nothing
                """, new MapSqlParameterSource("a", viewerId).addValue("b", targetId)
                .addValue("status", status.name()).addValue("now", Timestamp.from(Instant.now(clock))));
        if (inserted == 1 && status == FollowStatus.ACCEPTED) {
            adjustCounts(viewerId, targetId, +1);
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
        }
        return Relationship.NONE;
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
                group by u.id
                order by follows_me desc, mutual desc, u.followers_count desc, u.id
                limit :limit
                """.formatted(UserRows.cols("u")),
                new MapSqlParameterSource("me", viewerId).addValue("limit", limit),
                (rs, i) -> new SuggestedUser(UserRows.read(rs, "u"), rs.getInt("mutual"), rs.getBoolean("follows_me")));
    }
}
