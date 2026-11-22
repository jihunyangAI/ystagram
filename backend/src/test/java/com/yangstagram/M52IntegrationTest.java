package com.yangstagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.lab.LabSettings;
import com.yangstagram.seed.SeedService;
import com.yangstagram.seed.SeedSize;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** M5-②: 라이브, 통화, 리믹스, 쇼핑, 구독·선물 */
@SpringBootTest(properties = {"yangstagram.media.storage-dir=build/test-storage", "yangstagram.seed.size=small"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class M52IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    SeedService seedService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LabClock clock;

    @Autowired
    LabSettings labSettings;

    @BeforeEach
    void resetData() {
        clock.setOffset(Duration.ZERO);
        seedService.generate(SeedSize.SMALL, 42);
        jdbc.update("delete from lab_settings");
        labSettings.clearCache();
        jdbc.update("delete from blocks");
        jdbc.update("update lives set status = 'ENDED', ended_at = now() where status = 'LIVE'");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from lab_settings");
        labSettings.clearCache();
    }

    private MockHttpSession loginAs(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/lab/impersonate/" + username).session(session).with(csrf())).andExpect(status().isOk());
        return session;
    }

    private static String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    private ResultActions postJson(String url, MockHttpSession s, String json) throws Exception {
        return mvc.perform(post(url).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putJson(String url, MockHttpSession s, String json) throws Exception {
        return mvc.perform(put(url).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** 공개 성인 계정 n명 (서로 관계 없음) */
    private List<String> adults(int n) {
        return jdbc.queryForList("""
                select username from users where not is_private and birth_date < current_date - interval '18 years'
                  and account_type = 'PERSONAL' order by id limit ?
                """, String.class, n);
    }

    private long id(String username) {
        return jdbc.queryForObject("select id from users where username = ?", Long.class, username);
    }

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.PINK);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static MockMultipartFile json(String name, String value) throws Exception {
        return new MockMultipartFile(name, "", MediaType.APPLICATION_JSON_VALUE, value.getBytes("UTF-8"));
    }

    // ------------------------------------------------------------ 라이브

    @Test
    void 라이브_시작_입장_댓글_하트_배지_신호_종료() throws Exception {
        List<String> u = adults(3);
        MockHttpSession host = loginAs(u.get(0));
        MockHttpSession viewer = loginAs(u.get(1));
        MockHttpSession other = loginAs(u.get(2));

        String live = body(postJson("/api/lives", host, "{\"title\":\"첫 라이브\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("CAMERA")).andExpect(jsonPath("$.status").value("LIVE")));
        int liveId = JsonPath.read(live, "$.id");
        postJson("/api/lives", host, "{}").andExpect(jsonPath("$.code").value("ALREADY_LIVE"));

        mvc.perform(get("/api/lives").session(viewer)).andExpect(jsonPath("$[?(@.id == " + liveId + ")]").exists());
        mvc.perform(post("/api/lives/" + liveId + "/join").session(viewer).with(csrf()))
                .andExpect(jsonPath("$.live.viewers").value(1))
                .andExpect(jsonPath("$.comments[-1].kind").value("JOIN"));
        postJson("/api/lives/" + liveId + "/comments", viewer, "{\"body\":\"안녕하세요!\"}")
                .andExpect(jsonPath("$.body").value("안녕하세요!"));
        postJson("/api/lives/" + liveId + "/hearts", viewer, "{\"count\":50}").andExpect(jsonPath("$.heartCount").value(10));
        postJson("/api/lives/" + liveId + "/badges", viewer, "{\"tier\":3}").andExpect(jsonPath("$.kind").value("BADGE"));
        postJson("/api/lives/" + liveId + "/badges", host, "{\"tier\":1}").andExpect(jsonPath("$.code").value("OWN_LIVE"));
        assertThat(jdbc.queryForObject("select amount from gifts where live_id = ?", Integer.class, liveId)).isEqualTo(6600);

        // 신호: 진행자 ↔ 보고 있는 시청자만
        String signal = "{\"scope\":\"live\",\"id\":%d,\"to\":%d,\"data\":{\"type\":\"offer\",\"sdp\":\"x\"}}";
        postJson("/api/rtc/signal", host, signal.formatted(liveId, id(u.get(1)))).andExpect(status().isNoContent());
        postJson("/api/rtc/signal", viewer, signal.formatted(liveId, id(u.get(0)))).andExpect(status().isNoContent());
        postJson("/api/rtc/signal", other, signal.formatted(liveId, id(u.get(0)))).andExpect(status().isForbidden());

        mvc.perform(post("/api/lives/" + liveId + "/end").session(viewer).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/lives/" + liveId + "/end").session(host).with(csrf()))
                .andExpect(jsonPath("$.status").value("ENDED")).andExpect(jsonPath("$.totalViewers").value(1))
                .andExpect(jsonPath("$.commentCount").value(2)).andExpect(jsonPath("$.badgeCount").value(1));
        mvc.perform(post("/api/lives/" + liveId + "/join").session(other).with(csrf()))
                .andExpect(jsonPath("$.code").value("LIVE_ENDED"));
        mvc.perform(get("/api/lives/mine").session(host)).andExpect(jsonPath("$[0].id").value(liveId));
    }

    // ------------------------------------------------------------ 통화

    @Test
    void 통화는_울림_받기_끊기_순서로_가고_대화방에_기록이_남는다() throws Exception {
        List<String> u = adults(3);
        MockHttpSession a = loginAs(u.get(0));
        MockHttpSession b = loginAs(u.get(1));
        MockHttpSession c = loginAs(u.get(2));
        String conv = body(postJson("/api/direct/conversations", a, "{\"usernames\":[\"" + u.get(1) + "\"]}"));
        int convId = JsonPath.read(conv, "$.id");

        String call = body(postJson("/api/direct/conversations/" + convId + "/calls", a, "{\"video\":true}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("RINGING"))
                .andExpect(jsonPath("$.video").value(true)));
        int callId = JsonPath.read(call, "$.id");
        postJson("/api/direct/conversations/" + convId + "/calls", a, "{}").andExpect(jsonPath("$.code").value("ALREADY_IN_CALL"));
        mvc.perform(get("/api/calls/" + callId).session(c)).andExpect(status().isNotFound());
        mvc.perform(post("/api/calls/" + callId + "/accept").session(a).with(csrf())).andExpect(status().isForbidden());

        mvc.perform(post("/api/calls/" + callId + "/accept").session(b).with(csrf()))
                .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.incoming").value(true));
        String signal = "{\"scope\":\"call\",\"id\":%d,\"to\":%d,\"data\":{\"type\":\"answer\"}}";
        postJson("/api/rtc/signal", b, signal.formatted(callId, id(u.get(0)))).andExpect(status().isNoContent());
        postJson("/api/rtc/signal", c, signal.formatted(callId, id(u.get(0)))).andExpect(status().isForbidden());

        mvc.perform(post("/api/calls/" + callId + "/end").session(a).with(csrf())).andExpect(jsonPath("$.status").value("ENDED"));
        postJson("/api/rtc/signal", b, signal.formatted(callId, id(u.get(0)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/direct/conversations/" + convId + "/messages").session(b))
                .andExpect(jsonPath("$.items[0].kind").value("CALL"))
                .andExpect(jsonPath("$.items[0].call.status").value("ENDED"))
                .andExpect(jsonPath("$.items[0].call.video").value(true));

        // 거절, 건 사람의 취소
        int second = JsonPath.read(body(postJson("/api/direct/conversations/" + convId + "/calls", b, "{\"video\":false}")), "$.id");
        mvc.perform(post("/api/calls/" + second + "/decline").session(a).with(csrf())).andExpect(jsonPath("$.status").value("DECLINED"));
        int third = JsonPath.read(body(postJson("/api/direct/conversations/" + convId + "/calls", a, "{}")), "$.id");
        mvc.perform(post("/api/calls/" + third + "/end").session(a).with(csrf())).andExpect(jsonPath("$.status").value("CANCELED"));
        assertThat(jdbc.queryForObject("select count(*) from messages where conversation_id = ? and kind = 'CALL'",
                Integer.class, convId)).isEqualTo(3);
    }

    // ------------------------------------------------------------ 리믹스

    @Test
    void 리믹스는_원본과_이어지고_리믹스를_끄면_만들_수_없다() throws Exception {
        Map<String, Object> original = jdbc.queryForMap("""
                select p.shortcode, u.username from posts p join users u on u.id = p.author_id
                where p.type = 'REEL' and not u.is_private and p.deleted_at is null and not p.trial and not p.subscribers_only
                order by p.id limit 1
                """);
        String code = (String) original.get("shortcode");
        // 시드가 만든 리믹스는 떼어 내고 센다
        jdbc.update("update posts set remix_of_id = null where remix_of_id = (select id from posts where shortcode = ?)", code);
        String me = jdbc.queryForObject("""
                select username from users where not is_private and username <> ? and birth_date < current_date - interval '18 years'
                order by id limit 1
                """, String.class, original.get("username"));
        MockHttpSession s = loginAs(me);
        MockMultipartFile video = new MockMultipartFile("video", "v.mp4", "video/mp4", new byte[] {0, 0, 0, 24, 102, 116});
        MockMultipartFile poster = new MockMultipartFile("poster", "p.png", "image/png", png(360, 640));

        String res = body(mvc.perform(multipart("/api/reels").file(video).file(poster)
                        .file(json("data", "{\"caption\":\"리믹스!\",\"durationMs\":5000,\"remixOf\":\"" + code + "\"}"))
                        .session(s).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extras.remixOf.shortcode").value(code))
                .andExpect(jsonPath("$.extras.remixOf.available").value(true)));
        String remix = JsonPath.read(res, "$.shortcode");
        mvc.perform(get("/api/posts/" + remix + "/remixes").session(s)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/posts/" + code + "/remixes").session(s)).andExpect(jsonPath("$[0].shortcode").value(remix));
        mvc.perform(get("/api/posts/" + code).session(s)).andExpect(jsonPath("$.extras.remixCount").value(1));
        assertThat(jdbc.queryForObject("""
                select count(*) from notifications n join posts p on p.id = n.post_id where n.type = 'REMIX' and p.shortcode = ?
                """, Integer.class, remix)).isEqualTo(1);

        MockHttpSession owner = loginAs((String) original.get("username"));
        putJson("/api/posts/" + code + "/remix-setting", s, "{\"allow\":false}").andExpect(status().isForbidden());
        putJson("/api/posts/" + code + "/remix-setting", owner, "{\"allow\":false}").andExpect(status().isNoContent());
        mvc.perform(multipart("/api/reels").file(video).file(poster)
                        .file(json("data", "{\"durationMs\":5000,\"remixOf\":\"" + code + "\"}")).session(s).with(csrf()))
                .andExpect(jsonPath("$.code").value("REMIX_NOT_ALLOWED"));
    }

    // ------------------------------------------------------------ 쇼핑

    @Test
    void 제품_태그_위시리스트_가짜_주문() throws Exception {
        Map<String, Object> product = jdbc.queryForMap("""
                select pr.id, pr.price, u.username from products pr join users u on u.id = pr.seller_id
                where not u.is_private order by pr.id limit 1
                """);
        long productId = ((Number) product.get("id")).longValue();
        int price = ((Number) product.get("price")).intValue();
        String seller = (String) product.get("username");
        String buyer = adults(1).getFirst();
        MockHttpSession b = loginAs(buyer);
        MockHttpSession s = loginAs(seller);

        mvc.perform(get("/api/users/" + seller + "/products").session(b)).andExpect(jsonPath("$[0].seller.username").value(seller));
        mvc.perform(get("/api/products/" + productId).session(b)).andExpect(jsonPath("$.product.id").value(productId));

        mvc.perform(put("/api/products/" + productId + "/wishlist").session(b).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/wishlist").session(b)).andExpect(jsonPath("$[0].id").value(productId))
                .andExpect(jsonPath("$[0].wishlisted").value(true));
        mvc.perform(delete("/api/products/" + productId + "/wishlist").session(b).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/wishlist").session(b)).andExpect(jsonPath("$[?(@.id == " + productId + ")]").isEmpty());

        postJson("/api/products/" + productId + "/orders", b, "{\"quantity\":2}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(price * 2));
        mvc.perform(get("/api/me/orders").session(b)).andExpect(jsonPath("$[0].product.id").value(productId));
        postJson("/api/products/" + productId + "/orders", s, "{\"quantity\":1}").andExpect(jsonPath("$.code").value("OWN_PRODUCT"));
        postJson("/api/products/" + productId + "/orders", b, "{\"quantity\":11}").andExpect(jsonPath("$.code").value("BAD_QUANTITY"));

        // 판매자 게시물에 제품 태그
        String post = jdbc.queryForObject("""
                select p.shortcode from posts p join users u on u.id = p.author_id
                where u.username = ? and p.type <> 'REEL' and p.deleted_at is null and not p.subscribers_only order by p.id limit 1
                """, String.class, seller);
        String tags = "{\"products\":[{\"productId\":%d,\"x\":0.3,\"y\":0.6}]}".formatted(productId);
        putJson("/api/posts/" + post + "/products", b, tags).andExpect(status().isForbidden());
        putJson("/api/posts/" + post + "/products", s, tags).andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + post).session(b))
                .andExpect(jsonPath("$.extras.products.length()").value(1))
                .andExpect(jsonPath("$.extras.products[0].id").value(productId))
                .andExpect(jsonPath("$.extras.products[0].x").value(0.3));

        // 개인 계정은 제품을 등록할 수 없다
        MockMultipartFile image = new MockMultipartFile("image", "p.png", "image/png", png(400, 400));
        mvc.perform(multipart("/api/products").file(image).file(json("data", "{\"name\":\"컵\",\"price\":9000}"))
                .session(b).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/products").file(image).file(json("data", "{\"name\":\"새 머그컵\",\"price\":9000}"))
                .session(s).with(csrf())).andExpect(status().isCreated()).andExpect(jsonPath("$.mine").value(true));
    }

    // ------------------------------------------------------------ 구독·선물

    @Test
    void 구독하면_구독자_전용_게시물이_열리고_댓글에_구독자_배지가_붙는다() throws Exception {
        Map<String, Object> creator = jdbc.queryForMap("""
                select id, username from users where subscription_price is not null and not is_private order by id limit 1
                """);
        String name = (String) creator.get("username");
        String exclusive = jdbc.queryForObject("""
                select shortcode from posts where author_id = ? and deleted_at is null and type <> 'REEL' order by id limit 1
                """, String.class, creator.get("id"));
        jdbc.update("update posts set subscribers_only = true where shortcode = ?", exclusive);
        String fan = jdbc.queryForObject("""
                select username from users u where not is_private and birth_date < current_date - interval '18 years'
                  and id <> ? and not exists (select 1 from subscriptions s where s.subscriber_id = u.id and s.creator_id = ?)
                order by id limit 1
                """, String.class, creator.get("id"), creator.get("id"));
        MockHttpSession f = loginAs(fan);

        mvc.perform(get("/api/posts/" + exclusive).session(f)).andExpect(jsonPath("$.code").value("SUBSCRIBERS_ONLY"));
        mvc.perform(get("/api/users/" + name + "/exclusive").session(f)).andExpect(jsonPath("$[0].locked").value(true))
                .andExpect(jsonPath("$[0].shortcode").doesNotExist());
        mvc.perform(get("/api/users/" + name + "/subscription").session(f)).andExpect(jsonPath("$.subscribed").value(false));

        mvc.perform(post("/api/users/" + name + "/subscription").session(f).with(csrf()))
                .andExpect(jsonPath("$.subscribed").value(true));
        mvc.perform(post("/api/users/" + name + "/subscription").session(f).with(csrf()))
                .andExpect(jsonPath("$.code").value("ALREADY_SUBSCRIBED"));
        mvc.perform(get("/api/posts/" + exclusive).session(f)).andExpect(jsonPath("$.extras.subscribersOnly").value(true));
        mvc.perform(get("/api/users/" + name + "/exclusive").session(f)).andExpect(jsonPath("$[0].locked").value(false));
        postJson("/api/posts/" + exclusive + "/comments", f, "{\"body\":\"구독했어요!\"}")
                .andExpect(jsonPath("$.subscriber").value(true));

        MockHttpSession owner = loginAs(name);
        mvc.perform(get("/api/users/" + name + "/subscription").session(owner)).andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.subscriberCount").isNumber());
        mvc.perform(get("/api/me/subscribers").session(owner)).andExpect(jsonPath("$[0].username").value(fan));

        mvc.perform(delete("/api/users/" + name + "/subscription").session(f).with(csrf()))
                .andExpect(jsonPath("$.subscribed").value(false));
        mvc.perform(get("/api/posts/" + exclusive).session(f)).andExpect(status().isForbidden());

        // 개인 계정은 구독 가격을 정할 수 없다
        putJson("/api/me/subscription-price", f, "{\"price\":4900}").andExpect(status().isForbidden());
    }

    @Test
    void 릴스에_별을_선물하면_작성자에게_보인다() throws Exception {
        Map<String, Object> reel = jdbc.queryForMap("""
                select p.shortcode, u.username from posts p join users u on u.id = p.author_id
                where p.type = 'REEL' and not u.is_private and not p.trial and not p.subscribers_only and p.deleted_at is null
                order by p.id limit 1
                """);
        String code = (String) reel.get("shortcode");
        String fan = jdbc.queryForObject("""
                select username from users where not is_private and username <> ? and birth_date < current_date - interval '18 years'
                order by id limit 1
                """, String.class, reel.get("username"));
        MockHttpSession f = loginAs(fan);
        MockHttpSession owner = loginAs((String) reel.get("username"));
        long before = jdbc.queryForObject("select coalesce(sum(g.stars), 0) from gifts g join posts p on p.id = g.post_id where p.shortcode = ?",
                Long.class, code);

        postJson("/api/posts/" + code + "/gifts", f, "{\"stars\":50}").andExpect(jsonPath("$.amount").value(5500))
                .andExpect(jsonPath("$.totalStars").value(before + 50));
        postJson("/api/posts/" + code + "/gifts", f, "{\"stars\":7}").andExpect(jsonPath("$.code").value("BAD_STARS"));
        postJson("/api/posts/" + code + "/gifts", owner, "{\"stars\":10}").andExpect(jsonPath("$.code").value("OWN_POST"));
        mvc.perform(get("/api/posts/" + code).session(owner)).andExpect(jsonPath("$.extras.giftStars").value(before + 50));
        mvc.perform(get("/api/posts/" + code).session(f)).andExpect(jsonPath("$.extras.giftStars").doesNotExist());
    }
}
