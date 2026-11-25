package com.yangstagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yangstagram.auth.Totp;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.lab.LabSettings;
import com.yangstagram.seed.SeedService;
import com.yangstagram.seed.SeedSize;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

/** M5-①: 신고·모더레이션·정지, 2단계 인증, 지도, 브로드캐스트 채널, Blend */
@SpringBootTest(properties = {"yangstagram.media.storage-dir=build/test-storage", "yangstagram.seed.size=small"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class M5IntegrationTest {

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

    private ResultActions postJson(String url, MockHttpSession s, String json) throws Exception {
        return mvc.perform(post(url).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** 서로 팔로우하는 공개 계정 두 명 */
    private String[] mutuals() {
        Map<String, Object> row = jdbc.queryForMap("""
                select a.username as a, b.username as b from follows f1
                join follows f2 on f2.follower_id = f1.following_id and f2.following_id = f1.follower_id
                join users a on a.id = f1.follower_id join users b on b.id = f1.following_id
                where f1.status = 'ACCEPTED' and f2.status = 'ACCEPTED' and a.id < b.id
                  and not a.is_private and not b.is_private
                  and a.birth_date < current_date - interval '18 years' and b.birth_date < current_date - interval '18 years'
                order by a.id, b.id limit 1
                """);
        return new String[] {(String) row.get("a"), (String) row.get("b")};
    }

    // ------------------------------------------------------------ 신고·모더레이션

    @Test
    void 신고가_큐에_모이고_삭제하면_경고가_쌓여_3회째에_정지된다() throws Exception {
        jdbc.update("delete from reports");
        Map<String, Object> target = jdbc.queryForMap("""
                select u.username, u.id from users u where not u.is_private and u.posts_count >= 3
                  and u.birth_date < current_date - interval '18 years' order by u.id limit 1
                """);
        String owner = (String) target.get("username");
        List<String> codes = jdbc.queryForList(
                "select shortcode from posts where author_id = ? and deleted_at is null and type <> 'REEL' order by id limit 3",
                String.class, target.get("id"));
        String[] reporters = jdbc.queryForList("select username from users where username <> ? order by id limit 2",
                String.class, owner).toArray(String[]::new);

        for (String code : codes) {
            for (String r : reporters) {
                postJson("/api/reports", loginAs(r), "{\"targetType\":\"POST\",\"target\":\"" + code + "\",\"reason\":\"SPAM\"}")
                        .andExpect(status().isNoContent());
            }
        }
        // 같은 사람이 같은 글을 두 번 신고해도 한 건
        postJson("/api/reports", loginAs(reporters[0]), "{\"targetType\":\"POST\",\"target\":\"" + codes.getFirst()
                + "\",\"reason\":\"SCAM\"}").andExpect(status().isNoContent());
        // 내 글은 신고 못 함
        postJson("/api/reports", loginAs(owner), "{\"targetType\":\"POST\",\"target\":\"" + codes.getFirst()
                + "\",\"reason\":\"SPAM\"}").andExpect(status().isBadRequest());

        String queue = body(mvc.perform(get("/api/lab/moderation")));
        List<Integer> counts = JsonPath.read(queue, "$[?(@.targetType == 'POST')].reportCount");
        assertThat(counts).hasSize(3).allMatch(c -> c == 2);

        long postId = jdbc.queryForObject("select id from posts where shortcode = ?", Long.class, codes.getFirst());
        for (String code : codes) {
            long id = jdbc.queryForObject("select id from posts where shortcode = ?", Long.class, code);
            postJson("/api/lab/moderation/POST/" + id, new MockHttpSession(), "{\"action\":\"REMOVE\"}")
                    .andExpect(status().isNoContent());
        }
        assertThat(jdbc.queryForObject("select deleted_at is not null from posts where id = ?", Boolean.class, postId)).isTrue();
        assertThat(jdbc.queryForObject("select strike_count from users where username = ?", Integer.class, owner)).isEqualTo(3);

        // 경고 3회 → 정지: 로그인 불가, 프로필은 다른 사람에게 404
        postJson("/api/auth/login", new MockHttpSession(), "{\"login\":\"" + owner + "\",\"password\":\"yang1234\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        mvc.perform(get("/api/users/" + owner).session(loginAs(reporters[1]))).andExpect(status().isNotFound());

        // 신고한 사람은 처리 결과를 본다
        String mine = body(mvc.perform(get("/api/me/reports").session(loginAs(reporters[1]))));
        assertThat((List<String>) JsonPath.read(mine, "$[*].status")).contains("REMOVED");

        postJson("/api/lab/moderation/POST/" + postId, new MockHttpSession(), "{\"action\":\"UNSUSPEND\"}")
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/" + owner).session(loginAs(reporters[1]))).andExpect(status().isOk());

        // 신고가 없는 대상을 처리하려 하면 500이 아니라 404 (max()가 NULL 한 줄을 돌려주던 NPE)
        postJson("/api/lab/moderation/POST/999999999", new MockHttpSession(), "{\"action\":\"DISMISS\"}")
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ 2단계 인증

    @Test
    void 이단계_인증을_켜면_비밀번호_다음에_코드를_묻고_백업_코드는_한_번만_쓴다() throws Exception {
        MockHttpSession s = new MockHttpSession();
        postJson("/api/auth/signup", s, SignupCodes.with(mvc, jdbc, """
                {"email":"tfa@example.com","fullName":"이단계","username":"tfa.user","password":"secret1","birthDate":"1990-01-01"}
                """)).andExpect(status().isCreated());
        // 설정 전(비밀값 NULL)에 Lab 코드를 물으면 500이 아니라 400 (E2E에서 찾은 NPE)
        mvc.perform(get("/api/lab/2fa/tfa.user/code")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_SECRET"));
        String setup = body(postJson("/api/me/2fa/setup", s, "{}").andExpect(status().isOk()));
        String secret = JsonPath.read(setup, "$.secret");
        assertThat((String) JsonPath.read(setup, "$.otpauthUri")).startsWith("otpauth://totp/Yangstagram:tfa.user");

        postJson("/api/me/2fa/enable", s, "{\"code\":\"000000\"}").andExpect(status().isBadRequest());
        String enabled = body(postJson("/api/me/2fa/enable", s,
                "{\"code\":\"" + Totp.code(secret, Instant.now().getEpochSecond()) + "\"}").andExpect(status().isOk()));
        List<String> backups = JsonPath.read(enabled, "$.backupCodes");
        assertThat(backups).hasSize(10);

        // 비밀번호만으로는 로그인되지 않는다
        MockHttpSession login = new MockHttpSession();
        postJson("/api/auth/login", login, "{\"login\":\"tfa.user\",\"password\":\"secret1\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.twoFactorRequired").value(true));
        mvc.perform(get("/api/me/2fa").session(login)).andExpect(status().isUnauthorized());
        postJson("/api/auth/login/2fa", login, "{\"code\":\"123456\"}").andExpect(status().isBadRequest());
        postJson("/api/auth/login/2fa", login, "{\"code\":\"" + backups.getFirst() + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value("tfa.user"));
        mvc.perform(get("/api/me/2fa").session(login)).andExpect(jsonPath("$.backupCodesLeft").value(9));

        // 쓴 백업 코드는 다시 안 된다
        MockHttpSession again = new MockHttpSession();
        postJson("/api/auth/login", again, "{\"login\":\"tfa.user\",\"password\":\"secret1\"}");
        postJson("/api/auth/login/2fa", again, "{\"code\":\"" + backups.getFirst() + "\"}").andExpect(status().isBadRequest());
        String lab = body(mvc.perform(get("/api/lab/2fa/tfa.user/code")));
        postJson("/api/auth/login/2fa", again, "{\"code\":\"" + JsonPath.read(lab, "$.code") + "\"}").andExpect(status().isOk());
    }

    // ------------------------------------------------------------ 지도

    @Test
    void 지도는_서로_팔로우하는_친구의_위치와_장소를_보여준다() throws Exception {
        String[] pair = mutuals();
        jdbc.update("delete from user_locations");
        MockHttpSession a = loginAs(pair[0]);
        mvc.perform(put("/api/map/me").session(a).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lat\":37.5444,\"lng\":127.0374,\"placeName\":\"서울숲\",\"sharing\":\"MUTUAL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sharing").value("MUTUAL"));

        String map = body(mvc.perform(get("/api/map").session(loginAs(pair[1]))));
        assertThat((List<String>) JsonPath.read(map, "$.friends[*].user.username")).contains(pair[0]);
        assertThat((List<Object>) JsonPath.read(map, "$.places[*].name")).isNotEmpty();

        String stranger = jdbc.queryForObject("""
                select u.username from users u, users o where o.username = ? and u.id <> o.id
                  and not exists (select 1 from follows f where f.follower_id = u.id and f.following_id = o.id)
                order by u.id limit 1""", String.class, pair[0]);
        String other = body(mvc.perform(get("/api/map").session(loginAs(stranger))));
        assertThat((List<String>) JsonPath.read(other, "$.friends[*].user.username")).doesNotContain(pair[0]);

        // 3일 지나면 사라진다
        clock.setOffset(Duration.ofDays(4));
        String later = body(mvc.perform(get("/api/map").session(loginAs(pair[1]))));
        assertThat((List<String>) JsonPath.read(later, "$.friends[*].user.username")).doesNotContain(pair[0]);
    }

    // ------------------------------------------------------------ 채널

    @Test
    void 채널은_주인만_글을_쓰고_참여자는_반응과_투표만_한다() throws Exception {
        String[] pair = mutuals();
        jdbc.update("delete from channels where owner_id = (select id from users where username = ?)", pair[0]);
        MockHttpSession owner = loginAs(pair[0]);
        String created = body(postJson("/api/channels", owner, "{\"name\":\"맛집 공유방\"}").andExpect(status().isCreated()));
        int id = JsonPath.read(created, "$.id");
        postJson("/api/channels", owner, "{\"name\":\"두 번째\"}").andExpect(status().isConflict());

        MockHttpSession member = loginAs(pair[1]);
        mvc.perform(get("/api/users/" + pair[0] + "/channel").session(member))
                .andExpect(jsonPath("$.joined").value(false));
        mvc.perform(put("/api/channels/" + id + "/members/me").session(member).with(csrf()))
                .andExpect(jsonPath("$.joined").value(true)).andExpect(jsonPath("$.memberCount").value(2));
        postJson("/api/channels/" + id + "/messages", member, "{\"body\":\"참여자는 못 씀\"}").andExpect(status().isForbidden());

        String poll = body(postJson("/api/channels/" + id + "/messages", owner,
                "{\"body\":\"다음 주 어디?\",\"pollOptions\":[\"성수\",\"연남\"]}").andExpect(status().isCreated()));
        int messageId = JsonPath.read(poll, "$.id");
        postJson("/api/channels/" + id + "/messages", owner, "{\"body\":\"보기 하나\",\"pollOptions\":[\"하나\"]}")
                .andExpect(status().isBadRequest());

        // 투표 전에는 결과가 안 보이고, 투표하면 보인다. 한 번만
        String view = body(mvc.perform(get("/api/channels/" + id).session(member)));
        assertThat((List<Object>) JsonPath.read(view, "$.messages[0].poll.votes[*]")).containsOnlyNulls();
        mvc.perform(put("/api/channels/messages/" + messageId + "/vote").session(member).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"option\":1}"))
                .andExpect(jsonPath("$.poll.votes[1]").value(1)).andExpect(jsonPath("$.poll.myVote").value(1));
        mvc.perform(put("/api/channels/messages/" + messageId + "/vote").session(member).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"option\":0}"))
                .andExpect(jsonPath("$.poll.votes[0]").value(0));
        mvc.perform(put("/api/channels/messages/" + messageId + "/reaction").session(member).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"🔥\"}"))
                .andExpect(jsonPath("$.reactions[0].emoji").value("🔥"));

        String inbox = body(mvc.perform(get("/api/channels").session(member)));
        assertThat((List<String>) JsonPath.read(inbox, "$[*].name")).contains("맛집 공유방");
    }

    // ------------------------------------------------------------ Blend

    @Test
    void Blend는_초대하고_수락하면_두_사람을_위한_릴스가_나온다() throws Exception {
        String[] pair = mutuals();
        MockHttpSession a = loginAs(pair[0]);
        String conv = body(postJson("/api/direct/conversations", a, "{\"usernames\":[\"" + pair[1] + "\"]}"));
        int convId = JsonPath.read(conv, "$.id");
        String blend = body(postJson("/api/direct/conversations/" + convId + "/blend", a, "{}").andExpect(status().isOk()));
        int blendId = JsonPath.read(blend, "$.id");
        assertThat((String) JsonPath.read(blend, "$.status")).isEqualTo("PENDING");

        // 수락 전에는 릴스를 볼 수 없고, 초대한 사람은 수락할 수 없다
        mvc.perform(get("/api/blends/" + blendId + "/reels").session(a)).andExpect(status().isBadRequest());
        postJson("/api/blends/" + blendId + "/accept", a, "{}").andExpect(status().isNotFound());

        MockHttpSession b = loginAs(pair[1]);
        String msgs = body(mvc.perform(get("/api/direct/conversations/" + convId + "/messages").session(b)));
        assertThat((List<String>) JsonPath.read(msgs, "$.items[*].kind")).contains("BLEND_INVITE");
        postJson("/api/blends/" + blendId + "/accept", b, "{}").andExpect(jsonPath("$.status").value("ACTIVE"));

        String reels = body(mvc.perform(get("/api/blends/" + blendId + "/reels").session(a)).andExpect(status().isOk()));
        List<String> types = JsonPath.read(reels, "$.items[*].post.type");
        assertThat(types).isNotEmpty().containsOnly("REEL");
        List<String> reasons = JsonPath.read(reels, "$.items[*].reason");
        assertThat(reasons).allMatch(r -> !r.isBlank());
    }
}
