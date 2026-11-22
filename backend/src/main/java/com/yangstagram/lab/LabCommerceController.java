package com.yangstagram.lab;

import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lab → 가짜 수익 (M5-②): 쇼핑 주문·구독·선물·라이브 배지를 한곳에서 본다.
 * 모두 가짜 결제라 "매출"은 연구용 숫자일 뿐이다. 지금 켜져 있는 라이브·통화 수도 함께.
 */
@RestController
@RequestMapping("/api/lab/commerce")
@ConditionalOnProperty(prefix = "yangstagram.lab", name = "enabled", havingValue = "true")
public class LabCommerceController {

    private final NamedParameterJdbcTemplate jdbc;

    public LabCommerceController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @param amount 원 (가짜) */
    public record Total(String kind, long count, long amount) {
    }

    public record Earner(UserSummary user, long orders, long subscribers, long stars, long amount) {
    }

    public record Payment(String kind, UserSummary from, UserSummary to, String label, long amount, Instant at) {
    }

    public record Now(long lives, long fakeLives, long liveViewers, long ringingCalls, long activeCalls) {
    }

    public record Overview(List<Total> totals, List<Earner> top, List<Payment> recent, Now now) {
    }

    @GetMapping
    public Overview overview() {
        List<Total> totals = jdbc.query("""
                select 'ORDER' as kind, count(*) as n, coalesce(sum(amount), 0) as amount from orders
                union all
                select 'SUBSCRIPTION', count(*), coalesce(sum(price), 0) from subscriptions where status = 'ACTIVE'
                union all
                select 'GIFT', count(*), coalesce(sum(amount), 0) from gifts where kind = 'GIFT'
                union all
                select 'BADGE', count(*), coalesce(sum(amount), 0) from gifts where kind = 'BADGE'
                """, Map.of(), (rs, i) -> new Total(rs.getString(1), rs.getLong(2), rs.getLong(3)));

        List<Earner> top = jdbc.query("""
                with e as (
                  select pr.seller_id as uid, count(*) as orders, 0 as subs, 0 as stars, sum(o.amount) as amount
                  from orders o join products pr on pr.id = o.product_id group by pr.seller_id
                  union all
                  select creator_id, 0, count(*), 0, sum(price) from subscriptions where status = 'ACTIVE' group by creator_id
                  union all
                  select recipient_id, 0, 0, sum(stars), sum(amount) from gifts group by recipient_id
                )
                select %s, sum(e.orders) as orders, sum(e.subs) as subs, sum(e.stars) as stars, sum(e.amount) as amount
                from e join users u on u.id = e.uid
                group by u.id order by amount desc limit 15
                """.formatted(UserRows.cols("u")), Map.of(), (rs, i) -> new Earner(UserRows.read(rs, "u"),
                rs.getLong("orders"), rs.getLong("subs"), rs.getLong("stars"), rs.getLong("amount")));

        List<Payment> recent = jdbc.query("""
                select * from (
                  select 'ORDER' as kind, o.buyer_id as a, pr.seller_id as b, pr.name || ' × ' || o.quantity as label,
                         o.amount, o.created_at as at
                  from orders o join products pr on pr.id = o.product_id
                  union all
                  select 'SUBSCRIPTION', subscriber_id, creator_id, '월 구독', price, started_at
                  from subscriptions where status = 'ACTIVE'
                  union all
                  select kind, sender_id, recipient_id,
                         case when kind = 'GIFT' then '별 ' || stars || '개' else '배지 ' || stars || '단계' end, amount, created_at
                  from gifts
                ) t order by at desc limit 30
                """, Map.of(), (rs, i) -> {
            Timestamp at = rs.getTimestamp("at");
            return new Payment(rs.getString("kind"), user(rs.getLong("a")), user(rs.getLong("b")),
                    rs.getString("label"), rs.getLong("amount"), at.toInstant());
        });

        Now now = jdbc.queryForObject("""
                select (select count(*) from lives where status = 'LIVE') as lives,
                       (select count(*) from lives where status = 'LIVE' and kind = 'VIDEO') as fake_lives,
                       (select count(*) from live_viewers v join lives l on l.id = v.live_id where l.status = 'LIVE') as viewers,
                       (select count(*) from calls where status = 'RINGING') as ringing,
                       (select count(*) from calls where status = 'ACTIVE') as active
                """, Map.of(), (rs, i) -> new Now(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)));
        return new Overview(totals, top, recent, now);
    }

    private UserSummary user(long id) {
        return jdbc.queryForObject("select %s from users u where u.id = :id".formatted(UserRows.cols("u")),
                Map.of("id", id), (rs, i) -> UserRows.read(rs, "u"));
    }
}
