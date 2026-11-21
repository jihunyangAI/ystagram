package com.yangstagram.seed;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 가짜 행동 이벤트 (기획서 §9.3): 인사이트·추천 실험에 쓸 과거 기록.
 * 건수가 많아서(medium 약 15만 건) DB 안에서 generate_series로 한 번에 만든다.
 *
 * <ul>
 *   <li>노출(IMPRESSION): 게시물마다 좋아요 수 × 2.5 + 5 (최대 400), 올린 뒤 평균 30시간 안에 몰림</li>
 *   <li>시청 시간(VIEW_TIME): 릴스 노출마다 하나, 릴스마다 끝까지 보는 정도가 다르다</li>
 *   <li>프로필 방문(PROFILE_VISIT): 좋아요 10건 중 1건꼴로 작성자 프로필 방문</li>
 *   <li>좋아요·댓글·저장·공유(LIKE, COMMENT, SAVE, SHARE): 이미 만든 기록을 그대로 옮김 (공유 = 리포스트)</li>
 * </ul>
 */
final class SeedEvents {

    private final JdbcTemplate jdbc;
    private final Instant now;
    private final long seed;

    SeedEvents(JdbcTemplate jdbc, Instant now, long seed) {
        this.jdbc = jdbc;
        this.now = now;
        this.seed = seed;
    }

    int generate() {
        // 같은 시드값이면 같은 이벤트가 나오도록 DB 난수도 고정
        jdbc.queryForList("select setseed(?)", (seed % 1000) / 1000.0);
        Timestamp ts = Timestamp.from(now);
        int users = jdbc.queryForObject("select count(*) from users", Integer.class);

        int impressions = jdbc.update("""
                insert into events (user_id, type, post_id, surface, created_at)
                select x.uid, 'IMPRESSION', x.id, x.surface, x.at
                from (
                  select p.id, p.author_id,
                         1 + floor(random() * ?)::bigint as uid,
                         case when p.type = 'REEL' then 'REELS'
                              else (array['HOME','HOME','HOME','EXPLORE','PROFILE'])[1 + floor(random() * 5)::int] end
                           as surface,
                         least(p.created_at + (-ln(1 - random() * 0.999) * 30) * interval '1 hour', ?) as at
                  from posts p
                  cross join lateral generate_series(1, least(400, (p.like_count * 2.5 + 5)::int)) g
                  where p.deleted_at is null and p.created_at <= ?
                ) x
                where x.uid <> x.author_id
                """, users, ts, ts);

        // 릴스 시청 시간: 릴스마다 고정된 "몰입도"(0.3~1.8)로 끝까지 보는 비율이 달라진다
        int viewTimes = jdbc.update("""
                insert into events (user_id, type, post_id, surface, value_ms, created_at)
                select e.user_id, 'VIEW_TIME', e.post_id, 'REELS',
                       (m.duration_ms * power(random(), 0.3 + ((hashtext(e.post_id::text) % 100 + 100) % 100) / 66.0))::int,
                       e.created_at
                from events e
                join post_media m on m.post_id = e.post_id and m.position = 0 and m.duration_ms is not null
                where e.type = 'IMPRESSION' and e.surface = 'REELS'
                """);

        int visits = jdbc.update("""
                insert into events (user_id, type, target_user_id, surface, created_at)
                select l.user_id, 'PROFILE_VISIT', p.author_id,
                       (array['HOME','EXPLORE','POST'])[1 + floor(random() * 3)::int], l.created_at
                from likes l join posts p on p.id = l.post_id
                where random() < 0.1
                """);

        int likes = jdbc.update("""
                insert into events (user_id, type, post_id, surface, created_at)
                select user_id, 'LIKE', post_id, 'HOME', created_at from likes
                """);
        int comments = jdbc.update("""
                insert into events (user_id, type, post_id, surface, created_at)
                select author_id, 'COMMENT', post_id, 'POST', created_at from comments
                """);
        int saves = jdbc.update("""
                insert into events (user_id, type, post_id, surface, created_at)
                select user_id, 'SAVE', post_id, 'HOME', created_at from saved_posts
                """);
        int shares = jdbc.update("""
                insert into events (user_id, type, post_id, surface, created_at)
                select user_id, 'SHARE', post_id, 'HOME', created_at from reposts
                """);
        return impressions + viewTimes + visits + likes + comments + saves + shares;
    }
}
