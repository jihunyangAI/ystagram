package com.yangstagram.shop;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.media.MediaStorage;
import com.yangstagram.media.StoredImage;
import com.yangstagram.post.PostQueries;
import com.yangstagram.post.dto.PostDtos.GridItem;
import com.yangstagram.relation.RelationSql;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 쇼핑 (기획서 §4.14, 🟡): 프로페셔널 계정의 제품, 게시물 제품 태그, 위시리스트, 가짜 주문.
 * 결제는 흉내만 낸다 (카드 정보를 받지 않고 버튼 한 번으로 "결제 완료").
 */
@Service
public class ProductService {

    public static final int MAX_PRODUCTS = 100;
    public static final int MAX_TAGS = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;
    private final MediaStorage storage;
    private final PostQueries posts;

    public ProductService(NamedParameterJdbcTemplate jdbc, LabClock clock, MediaStorage storage, PostQueries posts) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.storage = storage;
        this.posts = posts;
    }

    public record ProductView(long id, String name, int price, String description, String imageUrl, String thumbUrl,
                              UserSummary seller, boolean wishlisted, boolean mine, int taggedPosts, Instant createdAt) {
    }

    public record ProductDetail(ProductView product, List<GridItem> posts, List<ProductView> more) {
    }

    public record CreateProduct(String name, Integer price, String description) {
    }

    public record TagInput(long productId, Integer mediaIndex, Double x, Double y) {
    }

    public record OrderView(long id, ProductView product, int quantity, int amount, Instant createdAt) {
    }

    // ------------------------------------------------------------ 보기

    private static final String SELECT = """
            select pr.*, %s,
                   exists (select 1 from product_wishlist w where w.product_id = pr.id and w.user_id = :me) as wished,
                   (select count(*) from post_products pp join posts p on p.id = pp.post_id
                     where pp.product_id = pr.id and p.deleted_at is null) as tagged
            from products pr join users u on u.id = pr.seller_id
            """.formatted(UserRows.cols("u"));

    private static final String SHOWN = " pr.deleted_at is null and u.deleted_at is null and "
            + PostQueries.NOT_SUSPENDED.replace("a.", "u.") + " and " + RelationSql.notBlocked("u.id");

    private ProductView read(ResultSet rs, long me) throws SQLException {
        UserSummary seller = UserRows.read(rs, "u");
        return new ProductView(rs.getLong("id"), rs.getString("name"), rs.getInt("price"), rs.getString("description"),
                rs.getString("image_url"), rs.getString("thumb_url"), seller, rs.getBoolean("wished"),
                seller.id() == me, rs.getInt("tagged"), rs.getTimestamp("created_at").toInstant());
    }

    /** 프로필 "샵" 탭 */
    public List<ProductView> shop(String username, long me) {
        return jdbc.query(SELECT + " where lower(u.username) = lower(:u) and " + SHOWN + " order by pr.created_at desc, pr.id desc",
                new MapSqlParameterSource("u", username).addValue("me", me), (rs, i) -> read(rs, me));
    }

    /** 제품 태그 고르기: 이름으로 찾기 (빈 검색어면 내 제품) */
    public List<ProductView> search(String q, long me) {
        String term = q == null ? "" : q.strip();
        if (term.isEmpty()) {
            return jdbc.query(SELECT + " where pr.seller_id = :me and " + SHOWN + " order by pr.created_at desc",
                    new MapSqlParameterSource("me", me), (rs, i) -> read(rs, me));
        }
        return jdbc.query(SELECT + " where (pr.name ilike :q or u.username ilike :q) and " + SHOWN
                        + " order by (pr.seller_id = :me) desc, pr.created_at desc limit 30",
                new MapSqlParameterSource("q", "%" + term.replace("%", "") + "%").addValue("me", me),
                (rs, i) -> read(rs, me));
    }

    public ProductView get(long id, long me) {
        List<ProductView> list = jdbc.query(SELECT + " where pr.id = :id and " + SHOWN,
                new MapSqlParameterSource("id", id).addValue("me", me), (rs, i) -> read(rs, me));
        if (list.isEmpty()) throw ApiException.notFound("제품을 찾을 수 없습니다.");
        return list.getFirst();
    }

    /** 제품 페이지: 제품 + 이 제품이 태그된 게시물 + 같은 판매자의 다른 제품 */
    public ProductDetail detail(long id, long me) {
        ProductView product = get(id, me);
        List<GridItem> tagged = jdbc.query("""
                select p.id, p.shortcode, p.like_count, p.comment_count, p.hide_like_count, p.author_id, p.type,
                       p.view_count, m.thumb_url,
                       (select count(*) from post_media x where x.post_id = p.id) as media_count
                from post_products pp join posts p on p.id = pp.post_id join users a on a.id = p.author_id
                join post_media m on m.post_id = p.id and m.position = 0
                where pp.product_id = :id and p.deleted_at is null and a.deleted_at is null and %s
                order by p.created_at desc limit 12
                """.formatted(PostQueries.VISIBLE), new MapSqlParameterSource("id", id).addValue("me", me),
                (rs, i) -> PostQueries.gridItem(rs, me));
        List<ProductView> more = jdbc.query(SELECT + " where pr.seller_id = :seller and pr.id <> :id and " + SHOWN
                        + " order by pr.created_at desc limit 6",
                new MapSqlParameterSource("seller", product.seller().id()).addValue("id", id).addValue("me", me),
                (rs, i) -> read(rs, me));
        return new ProductDetail(product, tagged, more);
    }

    // ------------------------------------------------------------ 판매자

    @Transactional
    public ProductView create(long me, MultipartFile image, CreateProduct req) {
        String type = jdbc.queryForObject("select account_type from users where id = :me", Map.of("me", me), String.class);
        if ("PERSONAL".equals(type)) {
            throw ApiException.forbidden("NOT_PROFESSIONAL", "제품은 크리에이터·비즈니스 계정만 등록할 수 있습니다.");
        }
        String name = req.name() == null ? "" : req.name().strip();
        if (name.isEmpty()) throw ApiException.badRequest("EMPTY_NAME", "제품 이름을 입력해 주세요.");
        if (name.length() > 100) throw ApiException.badRequest("NAME_TOO_LONG", "제품 이름은 100자까지입니다.");
        int price = req.price() == null ? -1 : req.price();
        if (price < 100 || price > 10_000_000) {
            throw ApiException.badRequest("BAD_PRICE", "가격은 100원~1,000만 원 사이로 입력해 주세요.");
        }
        String description = req.description() == null ? null : req.description().strip();
        if (description != null && description.length() > 1000) {
            throw ApiException.badRequest("DESCRIPTION_TOO_LONG", "설명은 1,000자까지입니다.");
        }
        if (image == null || image.isEmpty()) throw ApiException.badRequest("MEDIA_REQUIRED", "제품 사진을 골라 주세요.");
        Integer count = jdbc.queryForObject("select count(*) from products where seller_id = :me and deleted_at is null",
                Map.of("me", me), Integer.class);
        if (count != null && count >= MAX_PRODUCTS) {
            throw ApiException.badRequest("TOO_MANY_PRODUCTS", "제품은 100개까지 등록할 수 있습니다.");
        }
        StoredImage img = storage.saveImage(image);
        Long id = jdbc.queryForObject("""
                insert into products (seller_id, name, price, description, image_url, thumb_url, created_at)
                values (:me, :name, :price, :d, :url, :thumb, :now) returning id
                """, new MapSqlParameterSource("me", me).addValue("name", name).addValue("price", price)
                .addValue("d", description == null || description.isEmpty() ? null : description)
                .addValue("url", img.url()).addValue("thumb", img.thumbUrl())
                .addValue("now", Timestamp.from(Instant.now(clock))), Long.class);
        return get(id, me);
    }

    @Transactional
    public void delete(long id, long me) {
        int n = jdbc.update("update products set deleted_at = :now where id = :id and seller_id = :me and deleted_at is null",
                new MapSqlParameterSource("now", Timestamp.from(Instant.now(clock))).addValue("id", id).addValue("me", me));
        if (n == 0) throw ApiException.notFound("제품을 찾을 수 없습니다.");
    }

    /** 게시물 제품 태그 바꾸기 (작성자만, 최대 5개). 다른 판매자의 제품도 태그할 수 있다 */
    @Transactional
    public void tagPost(String shortcode, long me, List<TagInput> tags) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, author_id, (select count(*) from post_media m where m.post_id = posts.id) as media
                from posts where shortcode = :s and deleted_at is null
                """, Map.of("s", shortcode));
        if (rows.isEmpty()) throw ApiException.notFound("게시물을 찾을 수 없습니다.");
        Map<String, Object> post = rows.getFirst();
        if (((Number) post.get("author_id")).longValue() != me) {
            throw ApiException.forbidden("NOT_AUTHOR", "내 게시물에만 제품을 태그할 수 있습니다.");
        }
        List<TagInput> list = tags == null ? List.of() : tags;
        if (list.size() > MAX_TAGS) throw ApiException.badRequest("TOO_MANY_TAGS", "제품은 5개까지 태그할 수 있습니다.");
        long postId = ((Number) post.get("id")).longValue();
        int media = ((Number) post.get("media")).intValue();
        jdbc.update("delete from post_products where post_id = :p", Map.of("p", postId));
        for (TagInput t : list) {
            get(t.productId(), me); // 있는 제품인지
            int index = t.mediaIndex() == null ? 0 : Math.clamp(t.mediaIndex(), 0, Math.max(0, media - 1));
            jdbc.update("""
                    insert into post_products (post_id, product_id, media_index, x, y) values (:p, :pr, :i, :x, :y)
                    on conflict do nothing
                    """, new MapSqlParameterSource("p", postId).addValue("pr", t.productId()).addValue("i", index)
                    .addValue("x", clamp(t.x())).addValue("y", clamp(t.y())));
        }
    }

    private static double clamp(Double v) {
        return v == null ? 0.5 : Math.clamp(v, 0.0, 1.0);
    }

    // ------------------------------------------------------------ 위시리스트·주문

    @Transactional
    public void wish(long id, long me, boolean on) {
        get(id, me);
        if (on) {
            jdbc.update("insert into product_wishlist (user_id, product_id, created_at) values (:me, :id, :now) on conflict do nothing",
                    new MapSqlParameterSource("me", me).addValue("id", id).addValue("now", Timestamp.from(Instant.now(clock))));
        } else {
            jdbc.update("delete from product_wishlist where user_id = :me and product_id = :id", Map.of("me", me, "id", id));
        }
    }

    public List<ProductView> wishlist(long me) {
        return jdbc.query(SELECT + " join product_wishlist wl on wl.product_id = pr.id and wl.user_id = :me where "
                + SHOWN + " order by wl.created_at desc", new MapSqlParameterSource("me", me), (rs, i) -> read(rs, me));
    }

    /** 가짜 주문: 카드 정보 없이 "결제 완료" 기록만 남긴다 */
    @Transactional
    public OrderView order(long id, long me, int quantity) {
        ProductView product = get(id, me);
        if (product.mine()) throw ApiException.badRequest("OWN_PRODUCT", "내 제품은 살 수 없습니다.");
        if (quantity < 1 || quantity > 10) throw ApiException.badRequest("BAD_QUANTITY", "수량은 1~10개입니다.");
        Instant now = Instant.now(clock);
        Long orderId = jdbc.queryForObject("""
                insert into orders (buyer_id, product_id, quantity, amount, created_at)
                values (:me, :p, :q, :amount, :now) returning id
                """, new MapSqlParameterSource("me", me).addValue("p", id).addValue("q", quantity)
                .addValue("amount", product.price() * quantity).addValue("now", Timestamp.from(now)), Long.class);
        return new OrderView(orderId, product, quantity, product.price() * quantity, now);
    }

    public List<OrderView> orders(long me) {
        return jdbc.query("""
                select o.id as o_id, o.quantity as o_quantity, o.amount as o_amount, o.created_at as o_created,
                       pr.*, %s, false as wished, 0 as tagged
                from orders o join products pr on pr.id = o.product_id join users u on u.id = pr.seller_id
                where o.buyer_id = :me order by o.created_at desc, o.id desc limit 100
                """.formatted(UserRows.cols("u")), new MapSqlParameterSource("me", me),
                (rs, i) -> new OrderView(rs.getLong("o_id"), read(rs, me), rs.getInt("o_quantity"), rs.getInt("o_amount"),
                        rs.getTimestamp("o_created").toInstant()));
    }
}
