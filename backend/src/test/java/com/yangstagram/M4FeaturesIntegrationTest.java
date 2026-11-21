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
import com.yangstagram.dm.ScheduledMessages;
import com.yangstagram.lab.LabSettings;
import com.yangstagram.post.ScheduledPosts;
import com.yangstagram.seed.SeedService;
import com.yangstagram.seed.SeedSize;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

/** M4 ②: 예약 게시·예약 메시지, 노트, 시험 릴스, 10대 계정, AI 라벨, 가짜 광고, 자동 응답 봇 */
@SpringBootTest(properties = {"yangstagram.media.storage-dir=build/test-storage", "yangstagram.seed.size=small"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class M4FeaturesIntegrationTest {

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

    @Autowired
    ScheduledPosts scheduledPosts;

    @Autowired
    ScheduledMessages scheduledMessages;

    @BeforeEach
    void resetData() {
        clock.setOffset(Duration.ZERO);
        seedService.generate(SeedSize.SMALL, 42);
        jdbc.update("delete from lab_settings");
        labSettings.clearCache();
        jdbc.update("delete from blocks");
        jdbc.update("delete from mutes");
    }

    @AfterEach
    void cleanup() {
        clock.setOffset(Duration.ZERO);
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

    private void advanceMinutes(long minutes) throws Exception {
        mvc.perform(post("/api/lab/clock").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"addMinutes\":" + minutes + "}")).andExpect(status().isOk());
    }

    private void flags(String json) throws Exception {
        String current = body(mvc.perform(get("/api/lab/flags")));
        Map<String, Object> merged = new java.util.LinkedHashMap<>(JsonPath.read(current, "$"));
        merged.putAll(JsonPath.read(json, "$"));
        mvc.perform(put("/api/lab/flags").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(new tools.jackson.databind.json.JsonMapper().writeValueAsString(merged)))
                .andExpect(status().isOk());
    }

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.CYAN);
        g.fillRect(0, 0, 300, 300);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** 서로 팔로우하는 성인 공개 계정 두 명 */
    private String[] mutuals() {
        Map<String, Object> row = jdbc.queryForMap("""
                select a.username as a, b.username as b from follows f1
                join follows f2 on f2.follower_id = f1.following_id and f2.following_id = f1.follower_id
                join users a on a.id = f1.follower_id join users b on b.id = f1.following_id
                where f1.status = 'ACCEPTED' and f2.status = 'ACCEPTED' and a.id < b.id
                  and a.birth_date < current_date - interval '18 years' and b.birth_date < current_date - interval '18 years'
                order by a.id, b.id limit 1
                """);
        return new String[] {(String) row.get("a"), (String) row.get("b")};
    }

    // ------------------------------------------------------------ 예약 게시

    @Test
    void 예약_게시물은_시각이_되면_게시되고_그전에는_보이지_않는다() throws Exception {
        String user = mutuals()[0];
        MockHttpSession s = loginAs(user);
        Instant at = Instant.now(clock).plus(Duration.ofHours(2));
        String data = "{\"caption\":\"예약한 글 #예약\",\"scheduledAt\":\"" + at + "\",\"aiLabel\":true}";

        // 너무 이른 시각은 거부
        String tooSoon = "{\"caption\":\"x\",\"scheduledAt\":\"" + Instant.now(clock).plusSeconds(60) + "\"}";
        mvc.perform(multipart("/api/posts/scheduled").file(new MockMultipartFile("files", "a.png", "image/png", png()))
                .file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, tooSoon.getBytes("UTF-8")))
                .session(s).with(csrf())).andExpect(status().isBadRequest());

        String created = body(mvc.perform(multipart("/api/posts/scheduled")
                .file(new MockMultipartFile("files", "a.png", "image/png", png()))
                .file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, data.getBytes("UTF-8")))
                .session(s).with(csrf())).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")));
        int id = JsonPath.read(created, "$.id");
        Integer before = jdbc.queryForObject("select count(*) from posts where caption = '예약한 글 #예약'", Integer.class);
        assertThat(before).isZero();

        scheduledPosts.publishDue();
        assertThat(jdbc.queryForObject("select count(*) from posts where caption = '예약한 글 #예약'", Integer.class)).isZero();

        advanceMinutes(150);
        scheduledPosts.publishDue();
        String list = body(mvc.perform(get("/api/me/scheduled").session(s)));
        assertThat((String) JsonPath.read(list, "$[?(@.id == " + id + ")].status").toString()).contains("PUBLISHED");
        String code = jdbc.queryForObject("select shortcode from posts where caption = '예약한 글 #예약'", String.class);
        mvc.perform(get("/api/posts/" + code).session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.aiLabeled").value(true));
        // 두 번 게시되지 않는다
        scheduledPosts.publishDue();
        assertThat(jdbc.queryForObject("select count(*) from posts where caption = '예약한 글 #예약'", Integer.class))
                .isEqualTo(1);
    }

    // ------------------------------------------------------------ 예약 메시지

    @Test
    void 예약_메시지는_보낸_사람에게만_보이고_시각이_되면_전송된다() throws Exception {
        String[] pair = mutuals();
        MockHttpSession a = loginAs(pair[0]);
        String conv = body(mvc.perform(post("/api/direct/conversations").session(a).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"usernames\":[\"" + pair[1] + "\"]}")));
        int convId = JsonPath.read(conv, "$.id");

        Instant at = Instant.now(clock).plus(Duration.ofDays(30));
        mvc.perform(post("/api/direct/conversations/" + convId + "/scheduled").session(a).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"너무 먼 예약\",\"scheduledAt\":\"" + at + "\"}")).andExpect(status().isBadRequest());

        at = Instant.now(clock).plus(Duration.ofHours(3));
        mvc.perform(post("/api/direct/conversations/" + convId + "/scheduled").session(a).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"생일 축하해!\",\"scheduledAt\":\"" + at + "\"}")).andExpect(status().isCreated());

        MockHttpSession b = loginAs(pair[1]);
        mvc.perform(get("/api/direct/conversations/" + convId + "/scheduled").session(b))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/direct/conversations/" + convId + "/scheduled").session(a))
                .andExpect(jsonPath("$[0].body").value("생일 축하해!"));

        advanceMinutes(181);
        scheduledMessages.sendDue();
        String msgs = body(mvc.perform(get("/api/direct/conversations/" + convId + "/messages").session(b)));
        List<String> bodies = JsonPath.read(msgs, "$.items[*].body");
        assertThat(bodies).contains("생일 축하해!");
        mvc.perform(get("/api/direct/conversations/" + convId + "/scheduled").session(a))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------ 노트

    @Test
    void 노트는_서로_팔로우하는_사람에게만_보이고_답장은_DM으로_간다() throws Exception {
        String[] pair = mutuals();
        jdbc.update("delete from notes");
        MockHttpSession a = loginAs(pair[0]);
        mvc.perform(put("/api/notes").session(a).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + "가".repeat(61) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/notes").session(a).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"주말에 한강 갈 사람?\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("주말에 한강 갈 사람?"));

        MockHttpSession b = loginAs(pair[1]);
        String notes = body(mvc.perform(get("/api/notes").session(b)));
        List<String> owners = JsonPath.read(notes, "$.others[*].user.username");
        assertThat(owners).contains(pair[0]);

        // 팔로우 관계가 없는 사람에게는 안 보인다
        String stranger = jdbc.queryForObject("""
                select u.username from users u, users o where o.username = ? and u.id <> o.id
                  and not exists (select 1 from follows f where f.follower_id = u.id and f.following_id = o.id)
                order by u.id limit 1
                """, String.class, pair[0]);
        String other = body(mvc.perform(get("/api/notes").session(loginAs(stranger))));
        assertThat((List<String>) JsonPath.read(other, "$.others[*].user.username")).doesNotContain(pair[0]);

        String reply = body(mvc.perform(post("/api/notes/" + pair[0] + "/reply").session(b).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"저요!\"}")).andExpect(status().isOk()));
        assertThat((String) JsonPath.read(reply, "$.kind")).isEqualTo("NOTE_REPLY");
        assertThat((String) JsonPath.read(reply, "$.quote")).isEqualTo("주말에 한강 갈 사람?");

        // 24시간 뒤 사라진다
        advanceMinutes(24 * 60 + 1);
        String later = body(mvc.perform(get("/api/notes").session(b)));
        assertThat((List<String>) JsonPath.read(later, "$.others[*].user.username")).doesNotContain(pair[0]);
    }

    // ------------------------------------------------------------ 시험 릴스

    @Test
    void 시험_릴스는_팔로워에게_안_보이고_공유하면_보인다() throws Exception {
        jdbc.update("update posts set trial = false");
        Map<String, Object> row = jdbc.queryForMap("""
                select p.id, p.shortcode, a.username author, f.username follower
                from posts p join users a on a.id = p.author_id
                join follows fo on fo.following_id = a.id and fo.status = 'ACCEPTED'
                join users f on f.id = fo.follower_id
                where p.type = 'REEL' and not a.is_private and p.deleted_at is null
                order by p.id limit 1
                """);
        String code = (String) row.get("shortcode");
        jdbc.update("update posts set trial = true where id = ?", row.get("id"));

        MockHttpSession follower = loginAs((String) row.get("follower"));
        String grid = body(mvc.perform(get("/api/users/" + row.get("author") + "/reels").session(follower)));
        assertThat((List<String>) JsonPath.read(grid, "$.items[*].shortcode")).doesNotContain(code);

        MockHttpSession author = loginAs((String) row.get("author"));
        String own = body(mvc.perform(get("/api/users/" + row.get("author") + "/reels").session(author)));
        assertThat((List<String>) JsonPath.read(own, "$.items[*].shortcode")).contains(code);
        mvc.perform(get("/api/posts/" + code).session(author)).andExpect(jsonPath("$.trial").value(true));
        mvc.perform(get("/api/posts/" + code + "/insights").session(author))
                .andExpect(jsonPath("$.trial.active").value(true));

        mvc.perform(post("/api/posts/" + code + "/trial/share").session(author).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.trial").value(false));
        String after = body(mvc.perform(get("/api/users/" + row.get("author") + "/reels").session(follower)));
        assertThat((List<String>) JsonPath.read(after, "$.items[*].shortcode")).contains(code);
    }

    // ------------------------------------------------------------ 10대 계정

    @Test
    void 십대_계정은_비공개로_시작하고_팔로우하지_않는_사람의_DM을_받지_않는다() throws Exception {
        MockHttpSession teen = new MockHttpSession();
        String birth = java.time.LocalDate.now().minusYears(15).toString();
        mvc.perform(post("/api/auth/signup").session(teen).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"teen@example.com\",\"fullName\":\"십대\",\"username\":\"teen.test\","
                        + "\"password\":\"secret1\",\"birthDate\":\"" + birth + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.teen").value(true))
                .andExpect(jsonPath("$.user.isPrivate").value(true));

        String adult = jdbc.queryForObject("""
                select username from users where not is_private and birth_date < current_date - interval '18 years'
                order by id limit 1""", String.class);
        MockHttpSession a = loginAs(adult);
        mvc.perform(post("/api/direct/conversations").session(a).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernames\":[\"teen.test\"]}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TEEN_PROTECTED"));

        // 10대가 먼저 팔로우하면 메시지를 받을 수 있다
        mvc.perform(put("/api/users/" + adult + "/follow").session(teen).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/direct/conversations").session(a).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernames\":[\"teen.test\"]}")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ AI 라벨·추천 제외

    @Test
    void AI_생성_프로필은_기본적으로_추천에서_빠진다() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from users where is_ai_profile", Integer.class)).isPositive();
        // 시험이 확실하도록 AI 프로필 게시물을 매우 인기 있게
        jdbc.update("update posts set like_count = like_count + 100000 where author_id in (select id from users where is_ai_profile)");
        String viewer = jdbc.queryForObject("""
                select u.username from users u where not u.is_ai_profile and not exists (
                  select 1 from follows f join users x on x.id = f.following_id
                  where f.follower_id = u.id and x.is_ai_profile) order by u.id limit 1
                """, String.class);
        MockHttpSession s = loginAs(viewer);
        String off = body(mvc.perform(get("/api/explore").session(s)));
        assertThat(aiAuthors(JsonPath.read(off, "$.items[*].shortcode"))).isZero();

        flags("{\"recommendAiProfiles\":true}");
        String on = body(mvc.perform(get("/api/explore").session(s)));
        assertThat(aiAuthors(JsonPath.read(on, "$.items[*].shortcode"))).isPositive();
    }

    private int aiAuthors(List<String> codes) {
        if (codes.isEmpty()) return 0;
        return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).queryForObject("""
                select count(*) from posts p join users u on u.id = p.author_id
                where p.shortcode in (:codes) and u.is_ai_profile
                """, Map.of("codes", codes), Integer.class);
    }

    // ------------------------------------------------------------ 가짜 광고

    @Test
    void 홈_피드에_광고가_끼고_숨기면_다시_나오지_않으며_끌_수_있다() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from ads", Integer.class)).isPositive();
        String user = mutuals()[0];
        MockHttpSession s = loginAs(user);
        String feed = body(mvc.perform(get("/api/feed").session(s)));
        List<Map<String, Object>> ads = JsonPath.read(feed, "$.items[?(@.ad != null)]");
        assertThat(ads).hasSize(1);
        String adCode = (String) ads.getFirst().get("shortcode");

        mvc.perform(post("/api/events").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":[{\"type\":\"AD_HIDE\",\"shortcode\":\"" + adCode + "\",\"surface\":\"AD\"}]}"))
                .andExpect(status().isOk());
        List<String> seenAds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String again = body(mvc.perform(get("/api/feed").session(s)));
            seenAds.addAll(JsonPath.read(again, "$.items[?(@.ad != null)].shortcode"));
        }
        assertThat(seenAds).doesNotContain(adCode);

        String stats = body(mvc.perform(get("/api/lab/ads")));
        assertThat((List<Object>) JsonPath.read(stats, "$[*].impressions")).isNotEmpty();

        flags("{\"adsPerPage\":0}");
        String none = body(mvc.perform(get("/api/feed").session(s)));
        assertThat((List<Object>) JsonPath.read(none, "$.items[?(@.ad != null)]")).isEmpty();
    }

    // ------------------------------------------------------------ 자동 응답 봇

    @Test
    void 가짜_사용자에게_DM을_보내면_몇_초_뒤_답장이_온다() throws Exception {
        MockHttpSession me = new MockHttpSession();
        mvc.perform(post("/api/auth/signup").session(me).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"bot.test@example.com\",\"fullName\":\"봇 시험\",\"username\":\"bot.tester\","
                        + "\"password\":\"secret1\",\"birthDate\":\"1990-01-01\"}")).andExpect(status().isCreated());
        String fake = jdbc.queryForObject("""
                select username from users where is_fake and birth_date < current_date - interval '18 years'
                order by id limit 1
                """, String.class);
        String conv = body(mvc.perform(post("/api/direct/conversations").session(me).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"usernames\":[\"" + fake + "\"]}")));
        int convId = JsonPath.read(conv, "$.id");
        mvc.perform(post("/api/direct/conversations/" + convId + "/messages").session(me).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"안녕하세요\"}"))
                .andExpect(status().isCreated());

        List<String> senders = List.of();
        for (int i = 0; i < 40 && !senders.contains(fake); i++) {
            Thread.sleep(300);
            String msgs = body(mvc.perform(get("/api/direct/conversations/" + convId + "/messages").session(me)));
            senders = JsonPath.read(msgs, "$.items[*].sender.username");
        }
        assertThat(senders).contains(fake);
    }
}
