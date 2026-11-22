package com.yangstagram.seed;

import com.yangstagram.seed.SeedPhotos.Photo;
import com.yangstagram.seed.SeedService.FakeFollow;
import com.yangstagram.seed.SeedService.FakeUser;
import com.yangstagram.seed.SeedService.Persona;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M5-② 가짜 데이터: 비즈니스 계정의 제품(사진 이름 = 제품 이름)·게시물 제품 태그·위시리스트·주문,
 * 인플루언서 구독 가격·구독자·구독자 전용 게시물, 릴스 선물, 리믹스, 지난 라이브 기록.
 * 결제는 모두 가짜.
 */
final class SeedCommerce {

    record Result(int products, int productTags, int orders, int subscriptions, int exclusivePosts, int gifts,
                  int remixes, int pastLives) {
    }

    /** 주제별 가격대 (원) */
    private static final Map<Topic, int[]> PRICES = Map.ofEntries(
            Map.entry(Topic.CAFE, new int[] {4_500, 7_500, 100}),
            Map.entry(Topic.FOOD, new int[] {9_000, 28_000, 1_000}),
            Map.entry(Topic.COOKING, new int[] {3_500, 9_500, 500}),
            Map.entry(Topic.FASHION, new int[] {29_000, 139_000, 1_000}),
            Map.entry(Topic.BEAUTY, new int[] {12_000, 48_000, 1_000}),
            Map.entry(Topic.FITNESS, new int[] {19_000, 89_000, 1_000}),
            Map.entry(Topic.INTERIOR, new int[] {15_000, 189_000, 1_000}),
            Map.entry(Topic.PETS, new int[] {5_900, 39_000, 100}),
            Map.entry(Topic.BOOKS, new int[] {12_000, 22_000, 500}),
            Map.entry(Topic.PHOTO, new int[] {30_000, 150_000, 5_000}));

    private static final Map<Topic, List<String>> PREFIX = Map.of(
            Topic.CAFE, List.of("시그니처 ", "", "수제 ", "오늘의 "),
            Topic.FASHION, List.of("오버핏 ", "베이직 ", "데일리 ", "", "시즌 "),
            Topic.BEAUTY, List.of("비건 ", "데일리 ", "", "리뉴얼 "),
            Topic.COOKING, List.of("", "오늘 구운 ", "수제 "),
            Topic.INTERIOR, List.of("", "북유럽 ", "원목 "),
            Topic.PETS, List.of("", "수제 ", "유기농 "));

    private static final List<String> DESCRIPTIONS = List.of(
            "{shop}에서 직접 고르고 준비했어요.\n주문 후 2~3일 안에 보내 드려요.",
            "가장 많이 찾으시는 {name}! 재고가 많지 않아요.\n교환·환불은 DM으로 문의해 주세요.",
            "후기 이벤트 진행 중 ✨ 사진 후기 남기시면 다음 주문 10% 할인.",
            "{name} · 매장과 같은 가격이에요.\n선물 포장 가능 (요청 사항에 적어 주세요).",
            "");

    private static final int[] SUB_PRICES = {2_900, 4_900, 6_900, 9_900};
    private static final List<String> LIVE_TITLES = List.of("같이 수다 떨어요 ☕", "Q&A 받아요!", "오늘 하루 마무리 라이브",
            "언박싱 라이브 📦", "작업하면서 라이브", "주말 라이브 ✨");

    private final JdbcTemplate jdbc;
    private final Random rnd;
    private final Instant now;

    SeedCommerce(JdbcTemplate jdbc, Random rnd, Instant now) {
        this.jdbc = jdbc;
        this.rnd = rnd;
        this.now = now;
    }

    Result generate(List<FakeUser> users, List<FakeFollow> follows, List<Photo> photos) {
        Map<Long, List<Long>> followers = new HashMap<>();
        for (FakeFollow f : follows) {
            if ("ACCEPTED".equals(f.status())) {
                followers.computeIfAbsent(f.following().id, k -> new ArrayList<>()).add(f.follower().id);
            }
        }
        Map<Topic, List<Long>> productsByTopic = new HashMap<>();
        int products = products(users, photos, productsByTopic);
        int tags = productTags(users, productsByTopic);
        int orders = wishAndOrders(users);
        int[] subs = subscriptions(users, followers);
        int gifts = gifts(users, followers);
        int remixes = remixes();
        int lives = pastLives(users, followers);
        return new Result(products, tags, orders, subs[0], subs[1], gifts, remixes, lives);
    }

    // ------------------------------------------------------------ 제품

    private int products(List<FakeUser> users, List<Photo> photos, Map<Topic, List<Long>> byTopic) {
        Map<String, List<Photo>> pool = new HashMap<>();
        for (Photo p : photos) {
            if (p.topic() != null && p.label() != null) pool.computeIfAbsent(p.topic(), k -> new ArrayList<>()).add(p);
        }
        int n = 0;
        for (FakeUser u : users) {
            if (u.persona != Persona.BUSINESS) continue;
            Topic topic = u.interests.getFirst();
            List<Photo> list = new ArrayList<>(pool.getOrDefault(topic.name(), List.of()));
            if (list.isEmpty()) continue;
            Collections.shuffle(list, rnd);
            int[] range = PRICES.getOrDefault(topic, new int[] {10_000, 50_000, 1_000});
            List<String> prefixes = PREFIX.getOrDefault(topic, List.of(""));
            Set<String> names = new HashSet<>();
            int want = 4 + rnd.nextInt(5);
            for (Photo p : list) {
                if (names.size() >= want) break;
                String name = prefixes.get(rnd.nextInt(prefixes.size())) + p.label();
                if (!names.add(name)) continue;
                int steps = (range[1] - range[0]) / range[2];
                int price = range[0] + rnd.nextInt(steps + 1) * range[2];
                String desc = DESCRIPTIONS.get(rnd.nextInt(DESCRIPTIONS.size()))
                        .replace("{shop}", u.fullName).replace("{name}", name);
                Instant created = now.minus(Duration.ofDays(5 + rnd.nextInt(200)));
                Long id = jdbc.queryForObject("""
                        insert into products (seller_id, name, price, description, image_url, thumb_url, created_at)
                        values (?, ?, ?, ?, ?, ?, ?) returning id
                        """, Long.class, u.id, name, price, desc.isEmpty() ? null : desc, p.url(), p.thumbUrl(),
                        Timestamp.from(created));
                byTopic.computeIfAbsent(topic, k -> new ArrayList<>()).add(id);
                n++;
            }
        }
        return n;
    }

    /** 비즈니스 게시물 45%에 자기 제품 1~2개, 같은 주제 인플루언서 게시물 8%에 그 업종 제품 하나 */
    private int productTags(List<FakeUser> users, Map<Topic, List<Long>> byTopic) {
        int n = 0;
        Map<Long, List<Long>> bySeller = new HashMap<>();
        jdbc.query("select id, seller_id from products", rs -> {
            bySeller.computeIfAbsent(rs.getLong(2), k -> new ArrayList<>()).add(rs.getLong(1));
        });
        for (FakeUser u : users) {
            boolean business = u.persona == Persona.BUSINESS;
            if (!business && u.persona != Persona.INFLUENCER) continue;
            List<Map<String, Object>> posts = jdbc.queryForList("""
                    select id, topic from posts where author_id = ? and deleted_at is null and type <> 'REEL'
                    """, u.id);
            for (Map<String, Object> post : posts) {
                List<Long> candidates;
                if (business) {
                    if (rnd.nextDouble() >= 0.45) continue;
                    candidates = bySeller.getOrDefault(u.id, List.of());
                } else {
                    if (rnd.nextDouble() >= 0.08 || post.get("topic") == null) continue;
                    Topic t;
                    try {
                        t = Topic.valueOf((String) post.get("topic"));
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    candidates = byTopic.getOrDefault(t, List.of());
                }
                if (candidates.isEmpty()) continue;
                int count = business && rnd.nextDouble() < 0.35 ? 2 : 1;
                for (int i = 0; i < count; i++) {
                    long product = candidates.get(rnd.nextInt(candidates.size()));
                    n += jdbc.update("""
                            insert into post_products (post_id, product_id, media_index, x, y) values (?, ?, 0, ?, ?)
                            on conflict do nothing
                            """, post.get("id"), product, 0.2 + rnd.nextDouble() * 0.6, 0.25 + rnd.nextDouble() * 0.55);
                }
            }
        }
        return n;
    }

    private int wishAndOrders(List<FakeUser> users) {
        List<Map<String, Object>> products = jdbc.queryForList("select id, seller_id, price from products");
        if (products.isEmpty()) return 0;
        int orders = 0;
        for (FakeUser u : users) {
            if (u.persona == Persona.BUSINESS) continue;
            if (rnd.nextDouble() < 0.3) {
                int k = 1 + rnd.nextInt(6);
                for (int i = 0; i < k; i++) {
                    Map<String, Object> p = products.get(rnd.nextInt(products.size()));
                    if (((Number) p.get("seller_id")).longValue() == u.id) continue;
                    jdbc.update("""
                            insert into product_wishlist (user_id, product_id, created_at) values (?, ?, ?)
                            on conflict do nothing
                            """, u.id, p.get("id"), Timestamp.from(now.minus(Duration.ofHours(1 + rnd.nextInt(24 * 60)))));
                }
            }
            if (rnd.nextDouble() < 0.15) {
                int k = 1 + rnd.nextInt(3);
                for (int i = 0; i < k; i++) {
                    Map<String, Object> p = products.get(rnd.nextInt(products.size()));
                    if (((Number) p.get("seller_id")).longValue() == u.id) continue;
                    int q = rnd.nextDouble() < 0.8 ? 1 : 2 + rnd.nextInt(2);
                    jdbc.update("""
                            insert into orders (buyer_id, product_id, quantity, amount, created_at) values (?, ?, ?, ?, ?)
                            """, u.id, p.get("id"), q, ((Number) p.get("price")).intValue() * q,
                            Timestamp.from(now.minus(Duration.ofHours(1 + rnd.nextInt(24 * 90)))));
                    orders++;
                }
            }
        }
        return orders;
    }

    // ------------------------------------------------------------ 구독·선물

    /** @return {구독 수, 구독자 전용 게시물 수} */
    private int[] subscriptions(List<FakeUser> users, Map<Long, List<Long>> followers) {
        int subs = 0;
        int exclusive = 0;
        for (FakeUser u : users) {
            if (u.persona != Persona.INFLUENCER || rnd.nextDouble() >= 0.7) continue;
            int price = SUB_PRICES[rnd.nextInt(SUB_PRICES.length)];
            jdbc.update("update users set subscription_price = ? where id = ?", price, u.id);
            double rate = 0.02 + rnd.nextDouble() * 0.04;
            for (Long f : followers.getOrDefault(u.id, List.of())) {
                if (rnd.nextDouble() >= rate) continue;
                Instant started = now.minus(Duration.ofDays(1 + rnd.nextInt(120)));
                Instant renews = started;
                while (!renews.isAfter(now)) renews = renews.plus(Duration.ofDays(30));
                subs += jdbc.update("""
                        insert into subscriptions (subscriber_id, creator_id, price, status, started_at, renews_at)
                        values (?, ?, ?, 'ACTIVE', ?, ?) on conflict do nothing
                        """, f, u.id, price, Timestamp.from(started), Timestamp.from(renews));
            }
            exclusive += jdbc.update("""
                    update posts set subscribers_only = true
                    where author_id = ? and deleted_at is null and not trial and (hashtext(shortcode) % 100 + 100) % 100 < 6
                    """, u.id);
        }
        return new int[] {subs, exclusive};
    }

    /** 인플루언서 릴스 25%에 팔로워가 보낸 별 선물 1~8번 */
    private int gifts(List<FakeUser> users, Map<Long, List<Long>> followers) {
        int[] options = {10, 10, 10, 50, 50, 100, 500};
        int n = 0;
        for (FakeUser u : users) {
            if (u.persona != Persona.INFLUENCER) continue;
            List<Long> fans = followers.getOrDefault(u.id, List.of());
            if (fans.isEmpty()) continue;
            List<Map<String, Object>> reels = jdbc.queryForList("""
                    select id, created_at from posts where author_id = ? and type = 'REEL' and deleted_at is null
                    """, u.id);
            for (Map<String, Object> r : reels) {
                if (rnd.nextDouble() >= 0.25) continue;
                Instant created = ((Timestamp) r.get("created_at")).toInstant();
                int k = 1 + rnd.nextInt(8);
                for (int i = 0; i < k; i++) {
                    int stars = options[rnd.nextInt(options.length)];
                    long seconds = Math.max(60, Duration.between(created, now).toSeconds());
                    jdbc.update("""
                            insert into gifts (sender_id, recipient_id, post_id, kind, stars, amount, created_at)
                            values (?, ?, ?, 'GIFT', ?, ?, ?)
                            """, fans.get(rnd.nextInt(fans.size())), u.id, r.get("id"), stars, stars * 110,
                            Timestamp.from(created.plusSeconds((long) (rnd.nextDouble() * seconds))));
                    n++;
                }
            }
        }
        return n;
    }

    /** 릴스 약 15%를 다른 사람의 (더 먼저 올라온) 릴스를 리믹스한 것으로 */
    private int remixes() {
        List<Map<String, Object>> reels = jdbc.queryForList("""
                select id, author_id, created_at from posts where type = 'REEL' and deleted_at is null and not trial
                order by created_at
                """);
        int n = 0;
        for (int i = 10; i < reels.size(); i++) {
            if (rnd.nextDouble() >= 0.15) continue;
            Map<String, Object> remix = reels.get(i);
            for (int tries = 0; tries < 5; tries++) {
                Map<String, Object> original = reels.get(rnd.nextInt(i));
                if (original.get("author_id").equals(remix.get("author_id"))) continue;
                n += jdbc.update("update posts set remix_of_id = ? where id = ?", original.get("id"), remix.get("id"));
                break;
            }
        }
        return n;
    }

    /** 인플루언서마다 지난 30일 라이브 0~3번 (기록·통계용) */
    private int pastLives(List<FakeUser> users, Map<Long, List<Long>> followers) {
        int n = 0;
        for (FakeUser u : users) {
            if (u.persona != Persona.INFLUENCER) continue;
            int k = rnd.nextInt(4);
            int fans = followers.getOrDefault(u.id, List.of()).size();
            for (int i = 0; i < k; i++) {
                Instant start = now.minus(Duration.ofHours(6 + rnd.nextInt(24 * 30)));
                Instant end = start.plus(Duration.ofMinutes(10 + rnd.nextInt(60)));
                int total = 5 + (int) (fans * (0.05 + rnd.nextDouble() * 0.15));
                int peak = Math.max(1, (int) (total * (0.3 + rnd.nextDouble() * 0.4)));
                jdbc.update("""
                        insert into lives (host_id, title, kind, status, started_at, ended_at, peak_viewers, total_viewers,
                                           heart_count, comment_count, badge_count)
                        values (?, ?, 'CAMERA', 'ENDED', ?, ?, ?, ?, ?, ?, ?)
                        """, u.id, LIVE_TITLES.get(rnd.nextInt(LIVE_TITLES.size())), Timestamp.from(start),
                        Timestamp.from(end), peak, total, total * (3 + rnd.nextInt(20)), total / 2 + rnd.nextInt(30),
                        rnd.nextInt(Math.max(1, total / 10)));
                n++;
            }
        }
        return n;
    }
}
