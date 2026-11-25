package com.yangstagram.ad;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홍보하기 (기획서 §4 ⚪ "가짜 광고 캠페인"): 프로페셔널 계정이 내 게시물을 광고로 돌린다.
 * 실제 결제는 없고, ads 표에 한 줄을 넣어 다른 사람 피드의 광고 자리에 나오게 한다. 성과는 실제 이벤트(노출·클릭)로 센다.
 */
@Service
public class PromotionService {

    /** 가짜 단가: 노출 1번에 5원 */
    public static final int COST_PER_IMPRESSION = 5;
    public static final Set<String> CTAS = Set.of("더 알아보기", "지금 구매하기", "예약하기", "쇼핑하기", "메시지 보내기",
            "프로필 방문하기");

    public record PromoteRequest(Integer dailyBudget, Integer days, String cta, String headline) {
    }

    public record Promotion(boolean active, String headline, String cta, String topic, int dailyBudget, Instant startedAt,
                            Instant endsAt, long impressions, long reach, long clicks, long spent, long budget) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;

    public PromotionService(NamedParameterJdbcTemplate jdbc, LabClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public Promotion promote(String shortcode, long me, PromoteRequest req) {
        Map<String, Object> post = own(shortcode, me);
        String type = jdbc.queryForObject("select account_type from users where id = :me", Map.of("me", me), String.class);
        if ("PERSONAL".equals(type)) {
            throw ApiException.forbidden("PRO_ONLY",
                    "홍보하기는 크리에이터·비즈니스 계정만 쓸 수 있습니다. 프로필 편집에서 계정 유형을 바꾸세요.");
        }
        int budget = req.dailyBudget() == null ? 10_000 : req.dailyBudget();
        int days = req.days() == null ? 3 : req.days();
        if (budget < 5_000 || budget > 1_000_000) {
            throw ApiException.badRequest("BAD_BUDGET", "하루 예산은 5천~100만 원(가짜)으로 정해 주세요.");
        }
        if (days < 1 || days > 30) throw ApiException.badRequest("BAD_DAYS", "기간은 1~30일로 정해 주세요.");
        String cta = req.cta() == null || !CTAS.contains(req.cta()) ? "더 알아보기" : req.cta();
        String headline = req.headline() == null || req.headline().isBlank()
                ? jdbc.queryForObject("select left(full_name || '님의 게시물', 60) from users where id = :me",
                Map.of("me", me), String.class)
                : req.headline().strip();
        if (headline.length() > 60) throw ApiException.badRequest("BAD_HEADLINE", "광고 문구는 60자까지 쓸 수 있습니다.");
        Instant now = Instant.now(clock);
        jdbc.update("""
                insert into ads (post_id, advertiser_id, headline, cta, topic, created_at, active, promoted, daily_budget,
                                 ends_at)
                values (:p, :me, :h, :cta, :topic, :now, true, true, :b, :end)
                on conflict (post_id) do update set active = true, promoted = true, headline = excluded.headline,
                    cta = excluded.cta, daily_budget = excluded.daily_budget, ends_at = excluded.ends_at,
                    created_at = excluded.created_at
                """, new MapSqlParameterSource("p", post.get("id")).addValue("me", me).addValue("h", headline)
                .addValue("cta", cta).addValue("topic", post.get("topic")).addValue("now", Timestamp.from(now))
                .addValue("b", budget).addValue("end", Timestamp.from(now.plus(Duration.ofDays(days)))));
        return stats(shortcode, me);
    }

    public Promotion stats(String shortcode, long me) {
        Map<String, Object> post = own(shortcode, me);
        List<Promotion> rows = jdbc.query("""
                select ad.active, ad.headline, ad.cta, ad.topic, coalesce(ad.daily_budget, 0) budget, ad.created_at,
                       ad.ends_at,
                       (select count(*) from events e where e.post_id = ad.post_id and e.type = 'IMPRESSION'
                          and e.surface = 'AD' and e.created_at >= ad.created_at) imp,
                       (select count(distinct e.user_id) from events e where e.post_id = ad.post_id
                          and e.type = 'IMPRESSION' and e.surface = 'AD' and e.created_at >= ad.created_at) reach,
                       (select count(*) from events e where e.post_id = ad.post_id and e.type = 'AD_CLICK'
                          and e.created_at >= ad.created_at) clicks
                from ads ad where ad.post_id = :p and ad.promoted
                """, Map.of("p", post.get("id")), (rs, i) -> {
            Instant start = rs.getTimestamp("created_at").toInstant();
            Instant end = rs.getTimestamp("ends_at") == null ? null : rs.getTimestamp("ends_at").toInstant();
            int daily = rs.getInt("budget");
            long days = end == null ? 1 : Math.max(1, Duration.between(start, end).toDays());
            long total = daily * days;
            long imp = rs.getLong("imp");
            boolean running = rs.getBoolean("active") && (end == null || end.isAfter(Instant.now(clock)));
            return new Promotion(running, rs.getString("headline"), rs.getString("cta"), rs.getString("topic"), daily,
                    start, end, imp, rs.getLong("reach"), rs.getLong("clicks"),
                    Math.min(total, imp * COST_PER_IMPRESSION), total);
        });
        if (rows.isEmpty()) throw ApiException.notFound("홍보한 적이 없는 게시물입니다.");
        return rows.getFirst();
    }

    @Transactional
    public Promotion stop(String shortcode, long me) {
        Map<String, Object> post = own(shortcode, me);
        jdbc.update("update ads set active = false where post_id = :p and promoted", Map.of("p", post.get("id")));
        return stats(shortcode, me);
    }

    private Map<String, Object> own(String shortcode, long me) {
        Map<String, Object> post = jdbc.queryForList("""
                select id, author_id, topic from posts where shortcode = :c and deleted_at is null and archived_at is null
                """, Map.of("c", shortcode)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("게시물을 찾을 수 없습니다."));
        if (((Number) post.get("author_id")).longValue() != me) {
            throw ApiException.forbidden("NOT_AUTHOR", "내 게시물만 홍보할 수 있습니다.");
        }
        return post;
    }
}
