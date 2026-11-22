package com.yangstagram.moderation;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고·모더레이션 (기획서 §5 P2, Lab L10).
 * <ul>
 *   <li>게시물·댓글·계정을 사유와 함께 신고 (같은 대상은 한 번만)</li>
 *   <li>Lab 큐: 대상별로 신고를 묶어 보여 주고, 삭제·문제없음·정지로 한꺼번에 처리</li>
 *   <li>콘텐츠가 삭제되면 주인에게 경고 1회. 경고 3회마다 자동 7일 정지</li>
 *   <li>정지된 계정: 로그인 불가, 게시물·프로필이 다른 사람에게 안 보임</li>
 * </ul>
 */
@Service
public class ModerationService {

    public static final Map<String, String> REASONS = reasons();
    public static final int STRIKES_FOR_SUSPENSION = 3;

    private static Map<String, String> reasons() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("SPAM", "스팸");
        m.put("NUDITY", "나체 이미지 또는 성적 행위");
        m.put("HATE", "혐오 발언 또는 상징");
        m.put("VIOLENCE", "폭력 또는 위험한 단체");
        m.put("BULLYING", "따돌림 또는 괴롭힘");
        m.put("FALSE_INFO", "거짓 정보");
        m.put("SCAM", "사기 또는 거짓");
        m.put("IP", "지식재산권 침해");
        m.put("SELF_HARM", "자살 또는 자해");
        m.put("OTHER", "마음에 들지 않습니다");
        return m;
    }

    private static final Set<String> TYPES = Set.of("POST", "COMMENT", "USER");

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;

    public ModerationService(NamedParameterJdbcTemplate jdbc, LabClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------ 신고

    /**
     * @param target 게시물은 shortcode, 댓글은 id, 계정은 username
     */
    @Transactional
    public void report(long me, String type, String target, String reason, String detail) {
        if (type == null || !TYPES.contains(type)) throw ApiException.badRequest("BAD_TARGET", "신고 대상이 올바르지 않습니다.");
        if (reason == null || !REASONS.containsKey(reason)) throw ApiException.badRequest("BAD_REASON", "신고 사유를 골라 주세요.");
        String d = detail == null || detail.isBlank() ? null : detail.strip();
        if (d != null && d.length() > 300) d = d.substring(0, 300);
        Map<String, Object> t = resolve(type, target);
        long ownerId = ((Number) t.get("owner")).longValue();
        if (ownerId == me) throw ApiException.badRequest("SELF_REPORT", "내 콘텐츠는 신고할 수 없습니다.");
        jdbc.update("""
                insert into reports (reporter_id, target_type, target_id, target_user_id, reason, detail, created_at)
                values (:me, :type, :id, :owner, :reason, :detail, :now)
                on conflict (reporter_id, target_type, target_id) do nothing
                """, new MapSqlParameterSource("me", me).addValue("type", type).addValue("id", t.get("id"))
                .addValue("owner", ownerId).addValue("reason", reason).addValue("detail", d)
                .addValue("now", Timestamp.from(Instant.now(clock))));
    }

    private Map<String, Object> resolve(String type, String target) {
        String sql = switch (type) {
            case "POST" -> "select id, author_id as owner from posts where shortcode = :t and deleted_at is null";
            case "COMMENT" -> "select id, author_id as owner from comments where id = cast(:t as bigint)";
            default -> "select id, id as owner from users where username = :t and deleted_at is null";
        };
        try {
            return jdbc.queryForList(sql, Map.of("t", target == null ? "" : target)).stream().findFirst()
                    .orElseThrow(() -> ApiException.notFound("신고할 대상을 찾을 수 없습니다."));
        } catch (org.springframework.dao.DataAccessException e) {
            throw ApiException.notFound("신고할 대상을 찾을 수 없습니다.");
        }
    }

    /** 내 신고 내역 */
    public record MyReport(long id, String targetType, String targetLabel, String reason, String reasonLabel,
                           String status, Instant createdAt, Instant resolvedAt) {
    }

    public List<MyReport> mine(long me) {
        return jdbc.query("""
                select r.*, coalesce(p.shortcode, left(c.body, 40), u.username) as label
                from reports r
                left join posts p on r.target_type = 'POST' and p.id = r.target_id
                left join comments c on r.target_type = 'COMMENT' and c.id = r.target_id
                left join users u on r.target_type = 'USER' and u.id = r.target_id
                where r.reporter_id = :me order by r.created_at desc limit 50
                """, Map.of("me", me), (rs, i) -> new MyReport(rs.getLong("id"), rs.getString("target_type"),
                rs.getString("label"), rs.getString("reason"), REASONS.get(rs.getString("reason")),
                rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toInstant()));
    }

    // ------------------------------------------------------------ Lab 큐

    public record ReasonCount(String reason, String label, long count) {
    }

    /**
     * 신고 묶음 하나 (같은 대상).
     *
     * @param preview 게시물: 캡션 앞부분, 댓글: 본문, 계정: 소개
     */
    public record QueueItem(String targetType, long targetId, String shortcode, String thumbUrl, String preview,
                            UserSummary owner, int ownerStrikes, Instant ownerSuspendedUntil, long reportCount,
                            List<ReasonCount> reasons, Instant firstAt, Instant lastAt, String status, String action) {
    }

    public List<QueueItem> queue(String status) {
        boolean open = !"RESOLVED".equals(status);
        List<QueueItem> items = new ArrayList<>();
        jdbc.query("""
                select g.*, %s, o.strike_count, o.suspended_until,
                       p.shortcode, (select m.thumb_url from post_media m where m.post_id = p.id and m.position = 0) thumb,
                       coalesce(left(p.caption, 80), left(c.body, 200), left(u.bio, 80)) preview
                from (
                  select target_type, target_id, max(target_user_id) owner_id, count(*) n,
                         min(created_at) first_at, max(created_at) last_at,
                         max(status) status, max(action) action,
                         string_agg(reason, ',') reasons
                  from reports where (status = 'OPEN') = :open
                  group by target_type, target_id
                  order by count(*) desc, max(created_at) desc
                  limit 100
                ) g
                join users o on o.id = g.owner_id
                left join posts p on g.target_type = 'POST' and p.id = g.target_id
                left join comments c on g.target_type = 'COMMENT' and c.id = g.target_id
                left join users u on g.target_type = 'USER' and u.id = g.target_id
                order by g.n desc, g.last_at desc
                """.formatted(UserRows.cols("o")), Map.of("open", open), rs -> {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (String r : rs.getString("reasons").split(",")) counts.merge(r, 1L, Long::sum);
            List<ReasonCount> reasons = counts.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .map(e -> new ReasonCount(e.getKey(), REASONS.get(e.getKey()), e.getValue())).toList();
            Timestamp until = rs.getTimestamp("suspended_until");
            items.add(new QueueItem(rs.getString("target_type"), rs.getLong("target_id"), rs.getString("shortcode"),
                    rs.getString("thumb"), rs.getString("preview"), UserRows.read(rs, "o"), rs.getInt("strike_count"),
                    until == null ? null : until.toInstant(), rs.getLong("n"), reasons,
                    rs.getTimestamp("first_at").toInstant(), rs.getTimestamp("last_at").toInstant(),
                    rs.getString("status"), rs.getString("action")));
        });
        return items;
    }

    public record Stats(long open, long resolvedToday, long suspendedUsers) {
    }

    public Stats stats() {
        Timestamp now = Timestamp.from(Instant.now(clock));
        return new Stats(
                jdbc.queryForObject("select count(distinct (target_type, target_id)) from reports where status = 'OPEN'",
                        Map.of(), Long.class),
                jdbc.queryForObject("select count(*) from reports where resolved_at > cast(:now as timestamptz) - interval '1 day'",
                        Map.of("now", now), Long.class),
                jdbc.queryForObject("select count(*) from users where suspended_until > :now", Map.of("now", now), Long.class));
    }

    /**
     * 처리. REMOVE: 콘텐츠 삭제(계정이면 30일 정지) + 경고, DISMISS: 문제없음,
     * SUSPEND_7D·SUSPEND_30D·BAN: 콘텐츠 주인 정지, UNSUSPEND: 정지 풀기.
     */
    @Transactional
    public void act(String type, long targetId, String action) {
        Instant now = Instant.now(clock);
        Long owner = jdbc.queryForList("""
                select max(target_user_id) from reports where target_type = :t and target_id = :id
                """, Map.of("t", type, "id", targetId), Long.class).stream().findFirst().orElse(null);
        if (owner == null) throw ApiException.notFound("신고된 대상을 찾을 수 없습니다.");
        String status = switch (action) {
            case "REMOVE" -> {
                removeContent(type, targetId, owner, now);
                yield "REMOVED";
            }
            case "DISMISS" -> "DISMISSED";
            case "SUSPEND_7D" -> {
                suspend(owner, now.plus(Duration.ofDays(7)));
                yield "REMOVED";
            }
            case "SUSPEND_30D" -> {
                suspend(owner, now.plus(Duration.ofDays(30)));
                yield "REMOVED";
            }
            case "BAN" -> {
                suspend(owner, now.plus(Duration.ofDays(365 * 100)));
                yield "REMOVED";
            }
            case "UNSUSPEND" -> {
                jdbc.update("update users set suspended_until = null where id = :u", Map.of("u", owner));
                yield null;
            }
            default -> throw ApiException.badRequest("BAD_ACTION", "알 수 없는 처리입니다.");
        };
        if (status == null) return;
        jdbc.update("""
                update reports set status = :s, action = :a, resolved_at = :now
                where target_type = :t and target_id = :id and status = 'OPEN'
                """, new MapSqlParameterSource("s", status).addValue("a", action).addValue("t", type)
                .addValue("id", targetId).addValue("now", Timestamp.from(now)));
    }

    private void removeContent(String type, long id, long owner, Instant now) {
        Timestamp ts = Timestamp.from(now);
        switch (type) {
            case "POST" -> {
                int n = jdbc.update("update posts set deleted_at = :now where id = :id and deleted_at is null",
                        Map.of("now", ts, "id", id));
                if (n > 0) {
                    jdbc.update("update users set posts_count = greatest(0, posts_count - 1) where id = :u", Map.of("u", owner));
                }
            }
            case "COMMENT" -> {
                Long post = jdbc.queryForList("select post_id from comments where id = :id", Map.of("id", id), Long.class)
                        .stream().findFirst().orElse(null);
                int n = jdbc.update("delete from comments where id = :id or parent_id = :id", Map.of("id", id));
                if (post != null && n > 0) {
                    jdbc.update("update posts set comment_count = greatest(0, comment_count - :n) where id = :p",
                            Map.of("n", n, "p", post));
                }
            }
            default -> suspend(owner, now.plus(Duration.ofDays(30))); // 계정 신고의 "삭제" = 30일 정지
        }
        Integer strikes = jdbc.queryForObject(
                "update users set strike_count = strike_count + 1 where id = :u returning strike_count",
                Map.of("u", owner), Integer.class);
        if (strikes != null && strikes % STRIKES_FOR_SUSPENSION == 0) {
            suspend(owner, now.plus(Duration.ofDays(7)));
        }
    }

    private void suspend(long userId, Instant until) {
        jdbc.update("""
                update users set suspended_until = greatest(coalesce(suspended_until, :until), :until) where id = :u
                """, Map.of("u", userId, "until", Timestamp.from(until)));
    }

    /** 로그인할 때: 정지 중이면 언제까지인지 */
    public Instant suspendedUntil(long userId) {
        return jdbc.queryForList("select suspended_until from users where id = :u and suspended_until > :now",
                Map.of("u", userId, "now", Timestamp.from(Instant.now(clock))), Timestamp.class)
                .stream().findFirst().map(Timestamp::toInstant).orElse(null);
    }
}
