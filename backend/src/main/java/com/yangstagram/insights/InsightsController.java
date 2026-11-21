package com.yangstagram.insights;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.post.PostQueries;
import com.yangstagram.post.dto.PostDtos.GridItem;
import com.yangstagram.seed.Topic;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인사이트 (분석 문서 §18): 이벤트 로그와 좋아요·댓글·팔로우 기록을 그대로 집계한다.
 * 도달 = 내 게시물이 노출된 서로 다른 사람 수, 노출 = 노출 이벤트 수.
 */
@RestController
@RequestMapping("/api")
public class InsightsController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;

    public InsightsController(NamedParameterJdbcTemplate jdbc, LabClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Totals(long reach, long impressions, long interactions, long likes, long comments, long saves,
                         long shares, long profileVisits, long newFollowers, long lostFollowers, long followers) {
    }

    public record Day(LocalDate date, long reach, long impressions, long interactions, long newFollowers) {
    }

    public record TopPost(GridItem post, long reach, long impressions, long interactions) {
    }

    public record Share(String key, String label, long count, double ratio) {
    }

    public record AccountInsights(int days, Instant from, Instant to, Totals totals, Totals previous,
                                  List<Day> daily, List<TopPost> topPosts, List<Share> surfaces,
                                  List<Share> audienceTopics) {
    }

    @GetMapping("/me/insights")
    public AccountInsights account(@RequestParam(defaultValue = "7") int days) {
        long me = CurrentUser.requireId();
        int d = days <= 7 ? 7 : days <= 30 ? 30 : 90;
        Instant to = Instant.now(clock);
        Instant from = to.minus(Duration.ofDays(d));
        Totals totals = totals(me, from, to);
        Totals previous = totals(me, from.minus(Duration.ofDays(d)), from);

        MapSqlParameterSource p = params(me, from, to);
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        LocalDate start = LocalDate.ofInstant(from, SEOUL);
        for (int i = 1; i <= d; i++) byDay.put(start.plusDays(i), new long[4]);
        jdbc.query("""
                select (e.created_at at time zone 'Asia/Seoul')::date as day, count(distinct e.user_id), count(*)
                from events e join posts p on p.id = e.post_id
                where p.author_id = :me and e.type = 'IMPRESSION' and e.created_at > :from and e.created_at <= :to
                group by 1
                """, p, rs -> {
            long[] row = byDay.get(rs.getDate(1).toLocalDate());
            if (row != null) {
                row[0] = rs.getLong(2);
                row[1] = rs.getLong(3);
            }
        });
        jdbc.query("""
                select (x.at at time zone 'Asia/Seoul')::date, count(*) from (
                  select l.created_at at from likes l join posts p on p.id = l.post_id
                  where p.author_id = :me and l.user_id <> :me and l.created_at > :from and l.created_at <= :to
                  union all
                  select c.created_at from comments c join posts p on p.id = c.post_id
                  where p.author_id = :me and c.author_id <> :me and c.created_at > :from and c.created_at <= :to
                  union all
                  select s.created_at from saved_posts s join posts p on p.id = s.post_id
                  where p.author_id = :me and s.created_at > :from and s.created_at <= :to
                ) x group by 1
                """, p, rs -> {
            long[] row = byDay.get(rs.getDate(1).toLocalDate());
            if (row != null) row[2] = rs.getLong(2);
        });
        jdbc.query("""
                select (created_at at time zone 'Asia/Seoul')::date, count(*) from follows
                where following_id = :me and status = 'ACCEPTED' and created_at > :from and created_at <= :to
                group by 1
                """, p, rs -> {
            long[] row = byDay.get(rs.getDate(1).toLocalDate());
            if (row != null) row[3] = rs.getLong(2);
        });
        List<Day> daily = new ArrayList<>();
        byDay.forEach((day, r) -> daily.add(new Day(day, r[0], r[1], r[2], r[3])));

        List<TopPost> top = jdbc.query("""
                select p.id, p.shortcode, p.like_count, p.comment_count, p.hide_like_count, p.author_id,
                       p.type, p.view_count, m.thumb_url,
                       (select count(*) from post_media x where x.post_id = p.id) as media_count,
                       count(distinct e.user_id) as reach, count(*) as impressions,
                       p.like_count + p.comment_count + p.save_count + p.repost_count as interactions
                from events e
                join posts p on p.id = e.post_id
                join post_media m on m.post_id = p.id and m.position = 0
                where p.author_id = :me and p.deleted_at is null and e.type = 'IMPRESSION'
                  and e.created_at > :from and e.created_at <= :to
                group by p.id, m.thumb_url
                order by reach desc, p.id desc
                limit 6
                """, p, (rs, i) -> new TopPost(PostQueries.gridItem(rs, me), rs.getLong("reach"),
                rs.getLong("impressions"), rs.getLong("interactions")));

        List<Share> surfaces = shares(jdbc.query("""
                select coalesce(e.surface, 'OTHER'), count(*) from events e join posts p on p.id = e.post_id
                where p.author_id = :me and e.type = 'IMPRESSION' and e.created_at > :from and e.created_at <= :to
                group by 1 order by 2 desc
                """, p, (rs, i) -> new Share(rs.getString(1), surfaceLabel(rs.getString(1)), rs.getLong(2), 0)));

        // 팔로워의 관심 주제 분포 (프로필 관심사 기준)
        List<Share> audience = shares(jdbc.query("""
                select t.topic, count(*) from follows f join users u on u.id = f.follower_id
                cross join lateral unnest(string_to_array(coalesce(u.interests, ''), ',')) as t(topic)
                where f.following_id = :me and f.status = 'ACCEPTED' and t.topic <> ''
                group by 1 order by 2 desc limit 6
                """, p, (rs, i) -> new Share(rs.getString(1), topicLabel(rs.getString(1)), rs.getLong(2), 0)));

        return new AccountInsights(d, from, to, totals, previous, daily, top, surfaces, audience);
    }

    public record PostInsights(String shortcode, long reach, long impressions, long likes, long comments, long saves,
                               long shares, long profileVisits, Long avgWatchMs, Double completion, Integer durationMs,
                               List<Share> surfaces, List<Day> daily) {
    }

    /** 게시물 인사이트 (작성자만) */
    @GetMapping("/posts/{shortcode}/insights")
    public PostInsights post(@PathVariable String shortcode) {
        long me = CurrentUser.requireId();
        Map<String, Object> row = jdbc.queryForList("""
                select p.id, p.author_id, p.like_count, p.comment_count, p.save_count, p.created_at,
                       m.duration_ms
                from posts p left join post_media m on m.post_id = p.id and m.position = 0
                where p.shortcode = :code and p.deleted_at is null
                """, Map.of("code", shortcode)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("게시물을 찾을 수 없습니다."));
        if (((Number) row.get("author_id")).longValue() != me) {
            throw ApiException.forbidden("NOT_AUTHOR", "인사이트는 작성자만 볼 수 있습니다.");
        }
        long id = ((Number) row.get("id")).longValue();
        MapSqlParameterSource p = new MapSqlParameterSource("id", id).addValue("me", me);
        Map<String, Object> agg = jdbc.queryForMap("""
                select count(distinct user_id) filter (where type = 'IMPRESSION') reach,
                       count(*) filter (where type = 'IMPRESSION') impressions,
                       count(*) filter (where type = 'SHARE') shares,
                       avg(value_ms) filter (where type = 'VIEW_TIME') avg_watch
                from events where post_id = :id
                """, p);
        Integer duration = (Integer) row.get("duration_ms");
        Double completion = duration == null ? null : jdbc.queryForObject("""
                select avg(least(value_ms::float / :d, 1)) from events where post_id = :id and type = 'VIEW_TIME'
                """, p.addValue("d", duration), Double.class);
        Instant created = ((Timestamp) row.get("created_at")).toInstant();
        Long visits = jdbc.queryForObject("""
                select count(*) from events where type = 'PROFILE_VISIT' and target_user_id = :me
                  and created_at between :from and :to
                """, p.addValue("from", Timestamp.from(created))
                .addValue("to", Timestamp.from(created.plus(Duration.ofDays(7)))), Long.class);

        List<Share> surfaces = shares(jdbc.query("""
                select coalesce(surface, 'OTHER'), count(*) from events where post_id = :id and type = 'IMPRESSION'
                group by 1 order by 2 desc
                """, p, (rs, i) -> new Share(rs.getString(1), surfaceLabel(rs.getString(1)), rs.getLong(2), 0)));

        // 올린 날부터 7일 동안의 노출 추이
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        LocalDate start = LocalDate.ofInstant(created, SEOUL);
        for (int i = 0; i < 7; i++) byDay.put(start.plusDays(i), new long[2]);
        jdbc.query("""
                select (created_at at time zone 'Asia/Seoul')::date, count(distinct user_id), count(*)
                from events where post_id = :id and type = 'IMPRESSION' group by 1
                """, p, rs -> {
            long[] r = byDay.get(rs.getDate(1).toLocalDate());
            if (r != null) {
                r[0] = rs.getLong(2);
                r[1] = rs.getLong(3);
            }
        });
        List<Day> daily = new ArrayList<>();
        byDay.forEach((day, r) -> daily.add(new Day(day, r[0], r[1], 0, 0)));

        Number avg = (Number) agg.get("avg_watch");
        return new PostInsights(shortcode, ((Number) agg.get("reach")).longValue(),
                ((Number) agg.get("impressions")).longValue(), ((Number) row.get("like_count")).longValue(),
                ((Number) row.get("comment_count")).longValue(), ((Number) row.get("save_count")).longValue(),
                ((Number) agg.get("shares")).longValue(), visits == null ? 0 : visits,
                avg == null ? null : avg.longValue(), completion == null ? null : Math.round(completion * 1000) / 1000.0,
                duration, surfaces, daily);
    }

    // ------------------------------------------------------------ 보조

    private Totals totals(long me, Instant from, Instant to) {
        MapSqlParameterSource p = params(me, from, to);
        Map<String, Object> r = jdbc.queryForMap("""
                select
                  (select count(distinct e.user_id) from events e join posts p on p.id = e.post_id
                   where p.author_id = :me and e.type = 'IMPRESSION' and e.created_at > :from and e.created_at <= :to) reach,
                  (select count(*) from events e join posts p on p.id = e.post_id
                   where p.author_id = :me and e.type = 'IMPRESSION' and e.created_at > :from and e.created_at <= :to) impressions,
                  (select count(*) from likes l join posts p on p.id = l.post_id
                   where p.author_id = :me and l.user_id <> :me and l.created_at > :from and l.created_at <= :to) likes,
                  (select count(*) from comments c join posts p on p.id = c.post_id
                   where p.author_id = :me and c.author_id <> :me and c.created_at > :from and c.created_at <= :to) comments,
                  (select count(*) from saved_posts s join posts p on p.id = s.post_id
                   where p.author_id = :me and s.created_at > :from and s.created_at <= :to) saves,
                  (select count(*) from events e join posts p on p.id = e.post_id
                   where p.author_id = :me and e.type = 'SHARE' and e.created_at > :from and e.created_at <= :to) shares,
                  (select count(*) from events where type = 'PROFILE_VISIT' and target_user_id = :me and user_id <> :me
                   and created_at > :from and created_at <= :to) visits,
                  (select count(*) from follows where following_id = :me and status = 'ACCEPTED'
                   and created_at > :from and created_at <= :to) new_followers,
                  (select count(*) from events where type = 'UNFOLLOW' and target_user_id = :me
                   and created_at > :from and created_at <= :to) lost_followers,
                  (select followers_count from users where id = :me) followers
                """, p);
        long likes = n(r, "likes");
        long comments = n(r, "comments");
        long saves = n(r, "saves");
        long shares = n(r, "shares");
        return new Totals(n(r, "reach"), n(r, "impressions"), likes + comments + saves + shares, likes, comments,
                saves, shares, n(r, "visits"), n(r, "new_followers"), n(r, "lost_followers"), n(r, "followers"));
    }

    private static long n(Map<String, Object> r, String key) {
        Object v = r.get(key);
        return v == null ? 0 : ((Number) v).longValue();
    }

    private static MapSqlParameterSource params(long me, Instant from, Instant to) {
        return new MapSqlParameterSource("me", me).addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
    }

    private static List<Share> shares(List<Share> raw) {
        long total = raw.stream().mapToLong(Share::count).sum();
        return raw.stream().map(s -> new Share(s.key(), s.label(), s.count(),
                total == 0 ? 0 : Math.round(s.count() * 1000.0 / total) / 1000.0)).toList();
    }

    private static String surfaceLabel(String key) {
        return switch (key) {
            case "HOME" -> "홈";
            case "EXPLORE" -> "탐색";
            case "REELS" -> "릴스";
            case "PROFILE" -> "프로필";
            case "POST" -> "게시물";
            case "SEARCH" -> "검색";
            case "SIM" -> "시뮬레이터";
            default -> "기타";
        };
    }

    private static String topicLabel(String key) {
        try {
            Topic t = Topic.valueOf(key);
            return t.emoji() + " " + t.label();
        } catch (IllegalArgumentException e) {
            return key;
        }
    }
}
