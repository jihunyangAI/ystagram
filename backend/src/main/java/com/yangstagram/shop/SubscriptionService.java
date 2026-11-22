package com.yangstagram.shop;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.notification.NotificationService;
import com.yangstagram.notification.NotificationType;
import com.yangstagram.post.PostService;
import com.yangstagram.post.PostQueries.PostRow;
import com.yangstagram.relation.RelationSql;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구독·선물 (기획서 §4.14, ⚪ 가짜 결제).
 * <ul>
 *   <li>구독: 크리에이터가 월 가격을 정하면 팔로워가 구독할 수 있다. 구독자는 댓글에 보라색 배지가 붙고,
 *       구독자 전용 게시물을 볼 수 있다 (다른 사람에게는 피드·프로필·검색에서 빠지고 "구독자 전용" 탭에 잠금으로 보인다)</li>
 *   <li>선물: 릴스에 별을 보낸다 (별 1개 = 110원)</li>
 * </ul>
 */
@Service
public class SubscriptionService {

    public static final int STAR_PRICE = 110;
    public static final List<Integer> GIFT_STARS = List.of(10, 50, 100, 500);

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;
    private final NotificationService notifications;
    private final PostService postService;

    public SubscriptionService(NamedParameterJdbcTemplate jdbc, LabClock clock, NotificationService notifications,
                               PostService postService) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.notifications = notifications;
        this.postService = postService;
    }

    /**
     * @param price           월 가격 (구독을 받지 않으면 null)
     * @param subscriberCount 내 프로필일 때만
     */
    public record SubscriptionInfo(UserSummary creator, Integer price, boolean subscribed, Instant since,
                                   Instant renewsAt, Long subscriberCount, int exclusiveCount, boolean mine) {
    }

    public record ExclusiveItem(String shortcode, String thumbUrl, String type, boolean locked, Instant createdAt) {
    }

    public record GiftResult(int stars, int amount, long totalStars) {
    }

    // ------------------------------------------------------------ 구독

    public SubscriptionInfo info(String username, long me) {
        Map<String, Object> u = user(username, me);
        long creator = ((Number) u.get("id")).longValue();
        Map<String, Object> sub = jdbc.queryForList("""
                select started_at, renews_at from subscriptions
                where subscriber_id = :me and creator_id = :c and status = 'ACTIVE'
                """, Map.of("me", me, "c", creator)).stream().findFirst().orElse(null);
        Long count = creator == me ? jdbc.queryForObject(
                "select count(*) from subscriptions where creator_id = :c and status = 'ACTIVE'",
                Map.of("c", creator), Long.class) : null;
        Integer exclusive = jdbc.queryForObject("""
                select count(*) from posts where author_id = :c and subscribers_only and deleted_at is null
                """, Map.of("c", creator), Integer.class);
        UserSummary summary = jdbc.queryForObject("select %s from users u where u.id = :id".formatted(UserRows.cols("u")),
                Map.of("id", creator), (rs, i) -> UserRows.read(rs, "u"));
        return new SubscriptionInfo(summary, (Integer) u.get("subscription_price"), sub != null,
                sub == null ? null : ((Timestamp) sub.get("started_at")).toInstant(),
                sub == null ? null : ((Timestamp) sub.get("renews_at")).toInstant(), count,
                exclusive == null ? 0 : exclusive, creator == me);
    }

    /** 가짜 결제로 구독 (한 달 뒤 갱신 예정) */
    @Transactional
    public SubscriptionInfo subscribe(String username, long me) {
        Map<String, Object> u = user(username, me);
        long creator = ((Number) u.get("id")).longValue();
        if (creator == me) throw ApiException.badRequest("SELF_SUBSCRIBE", "나를 구독할 수는 없습니다.");
        Integer price = (Integer) u.get("subscription_price");
        if (price == null) throw ApiException.badRequest("NO_SUBSCRIPTION", "이 계정은 구독을 받지 않습니다.");
        Instant now = Instant.now(clock);
        int n = jdbc.update("""
                insert into subscriptions (subscriber_id, creator_id, price, status, started_at, renews_at)
                values (:me, :c, :price, 'ACTIVE', :now, :renew)
                on conflict (subscriber_id, creator_id) do update
                  set status = 'ACTIVE', price = excluded.price, started_at = excluded.started_at,
                      renews_at = excluded.renews_at, canceled_at = null
                  where subscriptions.status <> 'ACTIVE'
                """, new MapSqlParameterSource("me", me).addValue("c", creator).addValue("price", price)
                .addValue("now", Timestamp.from(now)).addValue("renew", Timestamp.from(now.plus(Duration.ofDays(30)))));
        if (n == 0) throw ApiException.conflict("ALREADY_SUBSCRIBED", "이미 구독 중입니다.");
        notifications.notify(creator, me, NotificationType.SUBSCRIBE, null, null, null,
                "월 " + String.format("%,d", price) + "원");
        return info(username, me);
    }

    @Transactional
    public SubscriptionInfo cancel(String username, long me) {
        Map<String, Object> u = user(username, me);
        jdbc.update("""
                update subscriptions set status = 'CANCELED', canceled_at = :now
                where subscriber_id = :me and creator_id = :c and status = 'ACTIVE'
                """, new MapSqlParameterSource("me", me).addValue("c", u.get("id"))
                .addValue("now", Timestamp.from(Instant.now(clock))));
        return info(username, me);
    }

    /** 내 구독 가격 정하기 (null이면 구독 받지 않음). 크리에이터·비즈니스 계정만 */
    @Transactional
    public Integer setPrice(long me, Integer price) {
        String type = jdbc.queryForObject("select account_type from users where id = :me", Map.of("me", me), String.class);
        if ("PERSONAL".equals(type)) {
            throw ApiException.forbidden("NOT_PROFESSIONAL", "구독은 크리에이터·비즈니스 계정만 받을 수 있습니다.");
        }
        if (price != null && (price < 990 || price > 99_000)) {
            throw ApiException.badRequest("BAD_PRICE", "구독 가격은 990원~99,000원 사이입니다.");
        }
        jdbc.update("update users set subscription_price = :p where id = :me",
                new MapSqlParameterSource("p", price).addValue("me", me));
        return price;
    }

    /** 내가 구독 중인 크리에이터 */
    public List<SubscriptionInfo> mine(long me) {
        List<String> names = jdbc.queryForList("""
                select u.username from subscriptions s join users u on u.id = s.creator_id
                where s.subscriber_id = :me and s.status = 'ACTIVE' and u.deleted_at is null order by s.started_at desc
                """, Map.of("me", me), String.class);
        return names.stream().map(n -> info(n, me)).toList();
    }

    /** 내 구독자 (크리에이터용, 최근 순 100명) */
    public List<UserSummary> subscribers(long me) {
        return jdbc.query("""
                select %s from subscriptions s join users u on u.id = s.subscriber_id
                where s.creator_id = :me and s.status = 'ACTIVE' and u.deleted_at is null
                order by s.started_at desc limit 100
                """.formatted(UserRows.cols("u")), Map.of("me", me), (rs, i) -> UserRows.read(rs, "u"));
    }

    /** 프로필 "구독자 전용" 탭: 구독자가 아니면 잠금으로 */
    public List<ExclusiveItem> exclusive(String username, long me) {
        Map<String, Object> u = user(username, me);
        long creator = ((Number) u.get("id")).longValue();
        boolean open = creator == me || Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from subscriptions where subscriber_id = :me and creator_id = :c and status = 'ACTIVE')
                """, Map.of("me", me, "c", creator), Boolean.class));
        return jdbc.query("""
                select p.shortcode, p.type, p.created_at, m.thumb_url from posts p
                join post_media m on m.post_id = p.id and m.position = 0
                where p.author_id = :c and p.subscribers_only and p.deleted_at is null and p.archived_at is null
                order by p.created_at desc limit 60
                """, Map.of("c", creator), (rs, i) -> new ExclusiveItem(open ? rs.getString("shortcode") : null,
                rs.getString("thumb_url"), rs.getString("type"), !open, rs.getTimestamp("created_at").toInstant()));
    }

    /** 공개·팔로우 여부와 상관없이 프로필은 보이는 계정 (차단·정지·탈퇴 제외) */
    private Map<String, Object> user(String username, long me) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select a.id, a.subscription_price from users a
                where lower(a.username) = lower(:u) and a.deleted_at is null
                  and (a.id = :me or a.suspended_until is null or a.suspended_until <= current_timestamp) and %s
                """.formatted(RelationSql.notBlocked("a.id")), Map.of("u", username, "me", me));
        if (rows.isEmpty()) throw ApiException.notFound("사용자를 찾을 수 없습니다.");
        return rows.getFirst();
    }

    // ------------------------------------------------------------ 선물

    /** 릴스에 별 선물 (가짜 결제) */
    @Transactional
    public GiftResult gift(String shortcode, long me, int stars) {
        if (!GIFT_STARS.contains(stars)) throw ApiException.badRequest("BAD_STARS", "별은 10·50·100·500개 중에서 고를 수 있습니다.");
        PostRow post = postService.getVisible(shortcode, me);
        if (post.author().id() == me) throw ApiException.badRequest("OWN_POST", "내 게시물에는 선물할 수 없습니다.");
        if (!"REEL".equals(post.type())) throw ApiException.badRequest("NOT_REEL", "선물은 릴스에만 보낼 수 있습니다.");
        jdbc.update("""
                insert into gifts (sender_id, recipient_id, post_id, kind, stars, amount, created_at)
                values (:me, :to, :p, 'GIFT', :stars, :amount, :now)
                """, new MapSqlParameterSource("me", me).addValue("to", post.author().id()).addValue("p", post.id())
                .addValue("stars", stars).addValue("amount", stars * STAR_PRICE)
                .addValue("now", Timestamp.from(Instant.now(clock))));
        notifications.notify(post.author().id(), me, NotificationType.GIFT, post.id(), null, null, "별 " + stars + "개");
        Long total = jdbc.queryForObject("select coalesce(sum(stars), 0) from gifts where post_id = :p",
                Map.of("p", post.id()), Long.class);
        return new GiftResult(stars, stars * STAR_PRICE, total == null ? 0 : total);
    }
}
