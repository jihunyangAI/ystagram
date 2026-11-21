package com.yangstagram.relation;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.follow.FollowService;
import com.yangstagram.follow.FollowService.UserListItem;
import com.yangstagram.follow.FollowStatus;
import com.yangstagram.follow.Relationship;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관계 제어 (분석 문서 §13): 차단, 제한, 숨기기(뮤트), 친한 친구, 즐겨찾기.
 */
@Service
public class RelationService {

    public static final int MAX_FAVORITES = 50;

    private final NamedParameterJdbcTemplate jdbc;
    private final FollowService followService;
    private final LabClock clock;

    public RelationService(NamedParameterJdbcTemplate jdbc, FollowService followService, LabClock clock) {
        this.jdbc = jdbc;
        this.followService = followService;
        this.clock = clock;
    }

    /** 프로필 ⋯ 메뉴에 보여 줄 내 설정 */
    public record Flags(boolean blocked, boolean restricted, boolean mutedPosts, boolean mutedStories,
                        boolean closeFriend, boolean favorite) {
    }

    public record Candidate(UserSummary user, boolean selected) {
    }

    public Flags flags(long me, long target) {
        if (me == target) return new Flags(false, false, false, false, false, false);
        Map<String, Object> row = jdbc.queryForMap("""
                select exists (select 1 from blocks where blocker_id = :me and blocked_id = :t) as blocked,
                       exists (select 1 from restricts where user_id = :me and restricted_id = :t) as restricted,
                       coalesce((select posts from mutes where user_id = :me and muted_id = :t), false) as muted_posts,
                       coalesce((select stories from mutes where user_id = :me and muted_id = :t), false) as muted_stories,
                       exists (select 1 from close_friends where user_id = :me and friend_id = :t) as close_friend,
                       coalesce((select is_favorite from follows where follower_id = :me and following_id = :t), false)
                         as favorite
                """, Map.of("me", me, "t", target));
        return new Flags((Boolean) row.get("blocked"), (Boolean) row.get("restricted"),
                (Boolean) row.get("muted_posts"), (Boolean) row.get("muted_stories"),
                (Boolean) row.get("close_friend"), (Boolean) row.get("favorite"));
    }

    /** 상대가 나를 차단했는가 (프로필이 "찾을 수 없음"으로 보임) */
    public boolean blockedBy(long me, long target) {
        return exists("select count(*) from blocks where blocker_id = :t and blocked_id = :me", me, target);
    }

    public boolean blockedEitherWay(long a, long b) {
        return exists("""
                select count(*) from blocks where (blocker_id = :me and blocked_id = :t) or (blocker_id = :t and blocked_id = :me)
                """, a, b);
    }

    /** owner가 other를 제한했는가 */
    public boolean restricts(long owner, long other) {
        return exists("select count(*) from restricts where user_id = :me and restricted_id = :t", owner, other);
    }

    private boolean exists(String sql, long me, long target) {
        Integer n = jdbc.queryForObject(sql, Map.of("me", me, "t", target), Integer.class);
        return n != null && n > 0;
    }

    // ------------------------------------------------------------ 차단

    /** 차단하면 서로의 팔로우·친한 친구가 모두 풀린다 (다시 차단 해제해도 복구되지 않음) */
    @Transactional
    public void block(long me, long target) {
        if (me == target) throw ApiException.badRequest("CANNOT_BLOCK_SELF", "자기 자신은 차단할 수 없습니다.");
        followService.unfollow(me, target);
        followService.removeFollower(me, target);
        jdbc.update("""
                delete from close_friends where (user_id = :me and friend_id = :t) or (user_id = :t and friend_id = :me)
                """, Map.of("me", me, "t", target));
        jdbc.update("""
                insert into blocks (blocker_id, blocked_id, created_at) values (:me, :t, :now) on conflict do nothing
                """, params(me, target));
    }

    @Transactional
    public void unblock(long me, long target) {
        jdbc.update("delete from blocks where blocker_id = :me and blocked_id = :t", Map.of("me", me, "t", target));
    }

    // ------------------------------------------------------------ 제한·숨기기

    @Transactional
    public void restrict(long me, long target, boolean on) {
        if (me == target) throw ApiException.badRequest("CANNOT_RESTRICT_SELF", "자기 자신은 제한할 수 없습니다.");
        if (on) {
            jdbc.update("""
                    insert into restricts (user_id, restricted_id, created_at) values (:me, :t, :now) on conflict do nothing
                    """, params(me, target));
        } else {
            jdbc.update("delete from restricts where user_id = :me and restricted_id = :t", Map.of("me", me, "t", target));
        }
    }

    /** 게시물·스토리를 따로 숨긴다. 둘 다 false면 숨기기 해제 */
    @Transactional
    public void mute(long me, long target, boolean posts, boolean stories) {
        if (me == target) throw ApiException.badRequest("CANNOT_MUTE_SELF", "자기 자신은 숨길 수 없습니다.");
        if (!posts && !stories) {
            jdbc.update("delete from mutes where user_id = :me and muted_id = :t", Map.of("me", me, "t", target));
            return;
        }
        jdbc.update("""
                insert into mutes (user_id, muted_id, posts, stories, created_at) values (:me, :t, :p, :s, :now)
                on conflict (user_id, muted_id) do update set posts = excluded.posts, stories = excluded.stories
                """, params(me, target).addValue("p", posts).addValue("s", stories));
    }

    // ------------------------------------------------------------ 친한 친구·즐겨찾기

    @Transactional
    public void closeFriend(long me, long target, boolean on) {
        if (me == target) throw ApiException.badRequest("CANNOT_ADD_SELF", "자기 자신은 추가할 수 없습니다.");
        if (on) {
            if (blockedEitherWay(me, target)) throw ApiException.forbidden("BLOCKED", "차단한 계정은 추가할 수 없습니다.");
            jdbc.update("""
                    insert into close_friends (user_id, friend_id, created_at) values (:me, :t, :now) on conflict do nothing
                    """, params(me, target));
        } else {
            jdbc.update("delete from close_friends where user_id = :me and friend_id = :t", Map.of("me", me, "t", target));
        }
    }

    /** 즐겨찾기: 내가 팔로우하는 사람만, 최대 50명. 홈의 "즐겨찾기" 피드에 이 사람들의 글만 나온다 */
    @Transactional
    public void favorite(long me, long target, boolean on) {
        if (on) {
            if (followService.status(me, target) != FollowStatus.ACCEPTED) {
                throw ApiException.badRequest("NOT_FOLLOWING", "팔로우하는 계정만 즐겨찾기에 추가할 수 있습니다.");
            }
            Integer count = jdbc.queryForObject("select count(*) from follows where follower_id = :me and is_favorite",
                    Map.of("me", me), Integer.class);
            if (count != null && count >= MAX_FAVORITES) {
                throw ApiException.badRequest("TOO_MANY_FAVORITES", "즐겨찾기는 최대 " + MAX_FAVORITES + "명입니다.");
            }
        }
        jdbc.update("update follows set is_favorite = :on where follower_id = :me and following_id = :t",
                Map.of("on", on, "me", me, "t", target));
    }

    // ------------------------------------------------------------ 목록 (설정 화면)

    public List<UserListItem> list(long me, String kind) {
        String sql = switch (kind) {
            case "blocked" -> "select %s from blocks x join users u on u.id = x.blocked_id where x.blocker_id = :me order by x.created_at desc";
            case "restricted" -> "select %s from restricts x join users u on u.id = x.restricted_id where x.user_id = :me order by x.created_at desc";
            case "muted" -> "select %s from mutes x join users u on u.id = x.muted_id where x.user_id = :me order by x.created_at desc";
            case "close-friends" -> "select %s from close_friends x join users u on u.id = x.friend_id where x.user_id = :me order by u.username";
            case "favorites" -> "select %s from follows x join users u on u.id = x.following_id where x.follower_id = :me and x.is_favorite order by u.username";
            default -> throw ApiException.notFound("알 수 없는 목록입니다.");
        };
        List<UserSummary> users = jdbc.query(sql.formatted(UserRows.cols("u")), Map.of("me", me),
                (rs, i) -> UserRows.read(rs, "u"));
        Map<Long, Relationship> rel = followService.relationships(me, users.stream().map(UserSummary::id).toList());
        return users.stream().map(u -> new UserListItem(u, rel.get(u.id()))).toList();
    }

    /**
     * 친한 친구·즐겨찾기 고르는 화면의 후보.
     * 친한 친구: 나를 팔로우하는 사람 + 이미 고른 사람 / 즐겨찾기: 내가 팔로우하는 사람
     */
    public List<Candidate> candidates(long me, String kind) {
        String sql = switch (kind) {
            case "close-friends" -> """
                    select %s, exists (select 1 from close_friends c where c.user_id = :me and c.friend_id = u.id) as selected
                    from users u
                    where u.id <> :me and u.deleted_at is null and (
                      exists (select 1 from follows f where f.follower_id = u.id and f.following_id = :me and f.status = 'ACCEPTED')
                      or exists (select 1 from close_friends c where c.user_id = :me and c.friend_id = u.id))
                    order by selected desc, u.username
                    """;
            case "favorites" -> """
                    select %s, f.is_favorite as selected
                    from follows f join users u on u.id = f.following_id
                    where f.follower_id = :me and f.status = 'ACCEPTED' and u.deleted_at is null
                    order by selected desc, u.username
                    """;
            default -> throw ApiException.notFound("알 수 없는 목록입니다.");
        };
        return jdbc.query(sql.formatted(UserRows.cols("u")), Map.of("me", me),
                (rs, i) -> new Candidate(UserRows.read(rs, "u"), rs.getBoolean("selected")));
    }

    private MapSqlParameterSource params(long me, long target) {
        return new MapSqlParameterSource("me", me).addValue("t", target)
                .addValue("now", Timestamp.from(Instant.now(clock)));
    }
}
