package com.yangstagram.notification;

import com.yangstagram.common.time.LabClock;
import com.yangstagram.follow.FollowStatus;
import com.yangstagram.follow.Relationship;
import com.yangstagram.realtime.Realtime;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 알림 (분석 문서 §16, 기획서 §6.4).
 * 저장은 행동 하나당 한 줄, 묶음은 읽을 때 만든다:
 * 같은 대상·같은 종류(좋아요 등)는 24시간 안에서 하나로 묶고 최근 2명 + 나머지 수를 보여 준다.
 */
@Service
public class NotificationService {

    public static final Duration BUNDLE_WINDOW = Duration.ofHours(24);
    private static final Duration KEEP = Duration.ofDays(60);
    private static final int SCAN_LIMIT = 500;

    private final NamedParameterJdbcTemplate jdbc;
    private final Realtime realtime;
    private final LabClock clock;

    public NotificationService(NamedParameterJdbcTemplate jdbc, Realtime realtime, LabClock clock) {
        this.jdbc = jdbc;
        this.realtime = realtime;
        this.clock = clock;
    }

    /** 실시간 알림 배지·토스트용 */
    public record Ping(NotificationType type, UserSummary actor, String body, long unread) {
    }

    public record Unread(long count, long requests) {
    }

    /** 활동 화면 한 줄. 묶인 경우 actors는 최근 2명, actorCount는 전체 인원 */
    public record ActivityItem(long id, NotificationType type, List<UserSummary> actors, int actorCount,
                               String shortcode, String thumbUrl, Long commentId, String body,
                               Instant createdAt, boolean unread, Relationship relationship) {
    }

    /** 맨 위 "팔로우 요청" 줄 */
    public record Requests(long count, UserSummary latest) {
    }

    public record Activity(Requests requests, List<ActivityItem> items) {
    }

    // ------------------------------------------------------------ 만들기·지우기

    public void notify(long recipient, long actor, NotificationType type, Long postId, Long commentId,
                       Long storyId, String body) {
        if (recipient == actor) return;
        Integer excluded = jdbc.queryForObject("""
                select count(*) from blocks where (blocker_id = :a and blocked_id = :b) or (blocker_id = :b and blocked_id = :a)
                """, Map.of("a", recipient, "b", actor), Integer.class);
        Integer muted = jdbc.queryForObject("select count(*) from mutes where user_id = :a and muted_id = :b",
                Map.of("a", recipient, "b", actor), Integer.class);
        if ((excluded != null && excluded > 0) || (muted != null && muted > 0)) return;
        String text = body == null ? null : body.length() > 200 ? body.substring(0, 199) + "…" : body;
        jdbc.update("""
                insert into notifications (user_id, actor_id, type, post_id, comment_id, story_id, body, created_at)
                values (:to, :actor, :type, :post, :comment, :story, :body, :now)
                """, new MapSqlParameterSource("to", recipient).addValue("actor", actor).addValue("type", type.name())
                .addValue("post", postId).addValue("comment", commentId).addValue("story", storyId)
                .addValue("body", text).addValue("now", Timestamp.from(Instant.now(clock))));
        UserSummary actorSummary = jdbc.queryForObject(
                "select %s from users u where u.id = :id".formatted(UserRows.cols("u")),
                Map.of("id", actor), (rs, i) -> UserRows.read(rs, "u"));
        realtime.send(recipient, "notification:new", new Ping(type, actorSummary, text, unread(recipient).count()));
    }

    /** 좋아요 취소·언팔로우 등으로 행동이 없어지면 알림도 지운다 */
    public void retract(long actor, NotificationType type, Long postId, Long commentId, Long storyId, Long recipient) {
        MapSqlParameterSource p = new MapSqlParameterSource("actor", actor).addValue("type", type.name());
        StringBuilder where = new StringBuilder("actor_id = :actor and type = :type");
        if (postId != null) { where.append(" and post_id = :post"); p.addValue("post", postId); }
        if (commentId != null) { where.append(" and comment_id = :comment"); p.addValue("comment", commentId); }
        if (storyId != null) { where.append(" and story_id = :story"); p.addValue("story", storyId); }
        if (recipient != null) { where.append(" and user_id = :to"); p.addValue("to", recipient); }
        jdbc.update("delete from notifications where " + where, p);
    }

    // ------------------------------------------------------------ 읽기

    public Unread unread(long userId) {
        Map<String, Object> row = jdbc.queryForMap("""
                select count(*) filter (where read_at is null) as unread,
                       (select count(*) from follows where following_id = :me and status = 'PENDING') as requests
                from notifications where user_id = :me
                """, Map.of("me", userId));
        return new Unread(((Number) row.get("unread")).longValue(), ((Number) row.get("requests")).longValue());
    }

    @Transactional
    public void markAllRead(long userId) {
        jdbc.update("update notifications set read_at = :now where user_id = :me and read_at is null",
                Map.of("me", userId, "now", Timestamp.from(Instant.now(clock))));
    }

    public Activity activity(long userId) {
        Instant now = Instant.now(clock);
        List<Row> rows = jdbc.query("""
                select n.id, n.type, n.post_id, n.comment_id, n.story_id, n.body, n.created_at, n.read_at, %s,
                       p.shortcode,
                       (select m.thumb_url from post_media m where m.post_id = n.post_id and m.position = 0) as post_thumb,
                       s.media_url as story_thumb
                from notifications n
                join users u on u.id = n.actor_id
                left join posts p on p.id = n.post_id
                left join stories s on s.id = n.story_id
                where n.user_id = :me and n.type <> 'FOLLOW_REQUEST' and u.deleted_at is null
                  and (n.post_id is null or p.deleted_at is null and p.archived_at is null)
                  and n.created_at > :since and n.created_at <= :now
                order by n.created_at desc, n.id desc
                limit :limit
                """.formatted(UserRows.cols("u")),
                new MapSqlParameterSource("me", userId).addValue("since", Timestamp.from(now.minus(KEEP)))
                        .addValue("now", Timestamp.from(now)).addValue("limit", SCAN_LIMIT),
                (rs, i) -> new Row(rs.getLong("id"), NotificationType.valueOf(rs.getString("type")),
                        UserRows.read(rs, "u"), (Long) rs.getObject("post_id"), (Long) rs.getObject("comment_id"),
                        (Long) rs.getObject("story_id"), rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("read_at") == null,
                        rs.getString("shortcode"),
                        rs.getString("post_thumb") != null ? rs.getString("post_thumb") : rs.getString("story_thumb")));

        List<Group> groups = bundle(rows);

        Set<Long> followActors = new HashSet<>();
        groups.stream().filter(g -> g.first.type == NotificationType.FOLLOW)
                .forEach(g -> followActors.add(g.first.actor.id()));
        Map<Long, Relationship> rel = relationships(userId, followActors);

        List<ActivityItem> items = groups.stream().map(g -> new ActivityItem(g.first.id, g.first.type,
                g.actors.stream().limit(2).toList(), g.actors.size(), g.first.shortcode, g.first.thumb,
                g.first.commentId, g.first.body, g.first.createdAt, g.unread,
                g.first.type == NotificationType.FOLLOW ? rel.get(g.first.actor.id()) : null)).toList();

        List<UserSummary> latest = jdbc.query("""
                select %s from follows f join users u on u.id = f.follower_id
                where f.following_id = :me and f.status = 'PENDING'
                order by f.created_at desc limit 1
                """.formatted(UserRows.cols("u")), Map.of("me", userId), (rs, i) -> UserRows.read(rs, "u"));
        long requestCount = unread(userId).requests();
        return new Activity(new Requests(requestCount, latest.isEmpty() ? null : latest.getFirst()), items);
    }

    /** 맞팔로우 버튼용: 나 → 상대 관계 (FollowService를 쓰면 서로 의존하게 되어 직접 조회) */
    private Map<Long, Relationship> relationships(long me, Set<Long> ids) {
        Map<Long, Relationship> result = new java.util.HashMap<>();
        ids.forEach(id -> result.put(id, Relationship.NONE));
        if (ids.isEmpty()) return result;
        jdbc.query("select following_id, status from follows where follower_id = :me and following_id in (:ids)",
                new MapSqlParameterSource("me", me).addValue("ids", ids),
                rs -> { result.put(rs.getLong(1), Relationship.of(FollowStatus.valueOf(rs.getString(2)))); });
        return result;
    }

    record Row(long id, NotificationType type, UserSummary actor, Long postId, Long commentId, Long storyId,
               String body, Instant createdAt, boolean unread, String shortcode, String thumb) {

        /** 같은 묶음이 될 수 있는 대상 키 (묶지 않는 종류는 null) */
        String bundleKey() {
            return switch (type) {
                case LIKE, REPOST -> type + ":p" + postId;
                case COMMENT_LIKE -> type + ":c" + commentId;
                case STORY_LIKE -> type + ":s" + storyId;
                default -> null;
            };
        }
    }

    static final class Group {
        final Row first;           // 가장 최근 것
        final List<UserSummary> actors = new ArrayList<>();
        final Set<Long> actorIds = new HashSet<>();
        boolean unread;

        Group(Row first) {
            this.first = first;
            add(first);
        }

        void add(Row r) {
            if (actorIds.add(r.actor.id())) actors.add(r.actor);
            unread |= r.unread;
        }
    }

    /** 최신순 목록을 받아 묶는다. 묶음은 가장 최근 항목 기준 24시간 안의 같은 대상·종류 */
    static List<Group> bundle(List<Row> rows) {
        List<Group> result = new ArrayList<>();
        Map<String, Group> open = new LinkedHashMap<>();
        for (Row r : rows) {
            String key = r.bundleKey();
            if (key == null) {
                result.add(new Group(r));
                continue;
            }
            Group g = open.get(key);
            if (g != null && Duration.between(r.createdAt, g.first.createdAt).compareTo(BUNDLE_WINDOW) <= 0) {
                g.add(r);
            } else {
                g = new Group(r);
                open.put(key, g);
                result.add(g);
            }
        }
        return result;
    }
}
