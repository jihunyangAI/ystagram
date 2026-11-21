package com.yangstagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.lab.LabSettings;
import com.yangstagram.seed.SeedService;
import com.yangstagram.seed.SeedSize;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** M4 연구 도구 ①: 순위 피드·점수 내역, 알고리즘 실험실, 이벤트, 회원님의 알고리즘, 인사이트, 기능 플래그, 시뮬레이터 */
@SpringBootTest(properties = {"yangstagram.media.storage-dir=build/test-storage", "yangstagram.seed.size=small"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class M4IntegrationTest {

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
        jdbc.update("delete from mutes");
    }

    @AfterEach
    void cleanup() {
        clock.setOffset(Duration.ZERO);
        jdbc.update("delete from lab_settings");
        labSettings.clearCache(); // 다른 테스트 클래스가 같은 스프링 컨텍스트를 쓴다
    }

    private MockHttpSession loginAs(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/lab/impersonate/" + username).session(session).with(csrf())).andExpect(status().isOk());
        return session;
    }

    private static String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    /** 팔로우를 가장 많이 하는 공개 계정 */
    private String activeUser() {
        return jdbc.queryForObject("""
                select u.username from users u join follows f on f.follower_id = u.id and f.status = 'ACCEPTED'
                where not u.is_private group by u.id order by count(*) desc limit 1""", String.class);
    }

    @Test
    void 홈_피드는_점수순이고_추천이_섞이며_쪽을_넘겨도_중복이_없다() throws Exception {
        MockHttpSession s = loginAs(activeUser());
        List<String> seen = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 3; page++) {
            String feed = body(mvc.perform(get("/api/feed").param("cursor", cursor == null ? "" : cursor).session(s))
                    .andExpect(status().isOk()));
            seen.addAll(JsonPath.read(feed, "$.items[*].shortcode"));
            sources.addAll(JsonPath.read(feed, "$.items[*].ranking.source"));
            List<String> parts = JsonPath.read(feed, "$.items[0].ranking.parts[*].key");
            assertThat(parts).contains("AFFINITY", "RECENCY", "SEEN");
            cursor = JsonPath.read(feed, "$.nextCursor");
            if (cursor == null) break;
        }
        assertThat(seen).hasSizeGreaterThan(10).doesNotHaveDuplicates();
        assertThat(sources).contains("FOLLOWING", "SUGGESTED");
    }

    @Test
    void 인기도_가중치를_올리면_탐색_순서가_바뀌고_점수_내역이_따라온다() throws Exception {
        String user = activeUser();
        MockHttpSession s = loginAs(user);
        String before = body(mvc.perform(get("/api/explore").session(s)));
        List<String> a = JsonPath.read(before, "$.items[*].shortcode");
        List<Number> w = JsonPath.read(before, "$.items[0].ranking.parts[?(@.key == 'POPULARITY')].weight");
        assertThat(w.getFirst().doubleValue()).isEqualTo(1.0);

        // 미리보기(A/B)는 저장하지 않고 결과만
        String preview = body(mvc.perform(post("/api/lab/algorithm/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"surface\":\"EXPLORE\",\"username\":\"" + user + "\",\"a\":{},\"b\":{\"POPULARITY\":8,\"EXPLORATION\":0},\"limit\":20}"))
                .andExpect(status().isOk()));
        List<String> pa = JsonPath.read(preview, "$.a[*].shortcode");
        List<String> pb = JsonPath.read(preview, "$.b[*].shortcode");
        assertThat(pb).isNotEqualTo(pa);

        mvc.perform(put("/api/lab/algorithm/EXPLORE").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"POPULARITY\":8,\"EXPLORATION\":0}")).andExpect(status().isOk());
        String after = body(mvc.perform(get("/api/explore").session(s)));
        List<String> b = JsonPath.read(after, "$.items[*].shortcode");
        assertThat(b).isNotEqualTo(a);
        List<Number> weights = JsonPath.read(after, "$.items[0].ranking.parts[?(@.key == 'POPULARITY')].weight");
        assertThat(weights.getFirst().doubleValue()).isEqualTo(8.0);
        // 탐색 점수가 내림차순
        List<Number> scores = JsonPath.read(after, "$.items[*].ranking.score");
        for (int i = 1; i < scores.size(); i++) {
            assertThat(scores.get(i).doubleValue()).isLessThanOrEqualTo(scores.get(i - 1).doubleValue() + 1e-6);
        }

        mvc.perform(put("/api/lab/algorithm/EXPLORE").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"POPULARITY\":99}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/lab/algorithm/EXPLORE").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"AFFINITY\":1}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/lab/algorithm/EXPLORE").with(csrf()))
                .andExpect(jsonPath("$[?(@.key == 'EXPLORE')].components[?(@.key == 'POPULARITY')].weight").value(1.0));
    }

    @Test
    void 이벤트는_알려진_종류만_기록하고_관심_없음은_추천에서_빠진다() throws Exception {
        MockHttpSession s = loginAs(activeUser());
        String explore = body(mvc.perform(get("/api/explore").session(s)));
        String first = JsonPath.read(explore, "$.items[0].shortcode");
        long since = ((Number) JsonPath.read(explore, "$.since")).longValue();

        mvc.perform(post("/api/events").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"events":[{"type":"IMPRESSION","shortcode":"%s","surface":"EXPLORE"},
                           {"type":"NOT_INTERESTED","shortcode":"%s","surface":"EXPLORE"},
                           {"type":"LIKE","shortcode":"%s"},
                           {"type":"IMPRESSION","shortcode":"없는코드"}]}
                """.formatted(first, first, first))).andExpect(jsonPath("$.recorded").value(2));

        String again = body(mvc.perform(get("/api/explore").param("since", String.valueOf(since)).session(s)));
        List<String> codes = JsonPath.read(again, "$.items[*].shortcode");
        assertThat(codes).doesNotContain(first);
    }

    @Test
    void 관심_주제를_빼면_관심_일치_점수가_0이_된다() throws Exception {
        String user = activeUser();
        MockHttpSession s = loginAs(user);
        String view = body(mvc.perform(get("/api/me/algorithm").session(s)));
        List<String> topics = JsonPath.read(view, "$.topics[?(@.removed == false)].topic");
        assertThat(topics).isNotEmpty();

        for (String t : topics) {
            mvc.perform(put("/api/me/algorithm/" + t).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mode\":\"REMOVE\"}")).andExpect(status().isOk());
        }
        String explore = body(mvc.perform(get("/api/explore").session(s)));
        List<Number> interest = JsonPath.read(explore, "$.items[*].ranking.parts[?(@.key == 'INTEREST')].value");
        assertThat(interest).allMatch(v -> v.doubleValue() == 0);

        mvc.perform(put("/api/me/algorithm/" + topics.getFirst()).session(s).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"RESET\"}"))
                .andExpect(jsonPath("$.topics[?(@.topic == '" + topics.getFirst() + "')].removed").value(false));
        mvc.perform(put("/api/me/algorithm/NOPE").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"ADD\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void 인사이트는_이벤트를_집계하고_게시물_인사이트는_작성자만_본다() throws Exception {
        String author = jdbc.queryForObject("""
                select u.username from users u join posts p on p.author_id = u.id
                group by u.id order by sum(p.like_count) desc limit 1""", String.class);
        String code = jdbc.queryForObject("""
                select p.shortcode from posts p join users u on u.id = p.author_id where u.username = ?
                order by p.like_count desc limit 1""", String.class, author);
        MockHttpSession s = loginAs(author);
        String account = body(mvc.perform(get("/api/me/insights").param("days", "90").session(s)).andExpect(status().isOk()));
        assertThat(((Number) JsonPath.read(account, "$.totals.impressions")).longValue()).isPositive();
        assertThat(((List<?>) JsonPath.read(account, "$.daily")).size()).isEqualTo(90);

        String post = body(mvc.perform(get("/api/posts/" + code + "/insights").session(s)).andExpect(status().isOk()));
        long reach = ((Number) JsonPath.read(post, "$.reach")).longValue();
        long impressions = ((Number) JsonPath.read(post, "$.impressions")).longValue();
        assertThat(reach).isPositive().isLessThanOrEqualTo(impressions);

        String other = jdbc.queryForObject("select username from users where username <> ? limit 1", String.class, author);
        mvc.perform(get("/api/posts/" + code + "/insights").session(loginAs(other))).andExpect(status().isForbidden());
    }

    @Test
    void 해시태그_제한은_기능_플래그를_따른다() throws Exception {
        String user = activeUser();
        String code = jdbc.queryForObject("""
                select p.shortcode from posts p join users u on u.id = p.author_id where u.username = ? limit 1""",
                String.class, user);
        MockHttpSession s = loginAs(user);
        String caption = "{\"caption\":\"#a #b #c #d #e #f\"}";
        mvc.perform(patch("/api/posts/" + code).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(caption))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOO_MANY_HASHTAGS"));

        mvc.perform(put("/api/lab/flags").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"hashtagLimit\":30,\"navLayout\":\"CLASSIC\",\"hideLikeCounts\":true,\"feedSuggestedPerPage\":0}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/flags").session(s)).andExpect(jsonPath("$.navLayout").value("CLASSIC"));
        mvc.perform(patch("/api/posts/" + code).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(caption))
                .andExpect(status().isOk());

        // 좋아요 수 전체 숨김: 다른 사람 글은 null, 내 글은 보임
        String other = jdbc.queryForObject("""
                select p.shortcode from posts p join users u on u.id = p.author_id
                where u.username <> ? and not u.is_private limit 1""", String.class, user);
        mvc.perform(get("/api/posts/" + other).session(s)).andExpect(jsonPath("$.likeCount").doesNotExist());
        mvc.perform(get("/api/posts/" + code).session(s)).andExpect(jsonPath("$.likeCount").isNumber());

        mvc.perform(put("/api/lab/flags").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"hashtagLimit\":31,\"navLayout\":\"NEW\",\"hideLikeCounts\":false,\"feedSuggestedPerPage\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 시뮬레이터는_실제_서비스를_거쳐_행동하고_이벤트를_남긴다() throws Exception {
        Integer before = jdbc.queryForObject("select count(*) from events", Integer.class);
        String body = body(mvc.perform(post("/api/lab/sim/step").param("n", "20").with(csrf())).andExpect(status().isOk()));
        long total = ((Number) JsonPath.read(body, "$.totalActions")).longValue();
        assertThat(total).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from events", Integer.class)).isGreaterThan(before);

        mvc.perform(put("/api/lab/sim").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"running":false,"intervalSec":3,"actionsPerTick":3,"focusUsername":"없는사람",
                 "mix":{"LIKE":1}}""")).andExpect(status().isNotFound());
    }
}
