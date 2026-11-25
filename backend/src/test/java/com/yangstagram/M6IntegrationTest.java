package com.yangstagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yangstagram.seed.SeedService;
import com.yangstagram.seed.SeedSize;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import com.yangstagram.account.AccountService;
import org.springframework.test.web.servlet.MockMvc;

/** M6: 기획서 §4에 있었지만 M0~M5에 빠졌던 기능 (보관·고정·최근 삭제됨 …) */
@SpringBootTest(properties = {"yangstagram.media.storage-dir=build/test-storage", "yangstagram.seed.size=small"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class M6IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    SeedService seedService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AccountService accountService;

    @Autowired
    com.yangstagram.common.time.LabClock clock;

    @BeforeEach
    void resetData() {
        seedService.generate(SeedSize.SMALL, 42);
    }

    private MockHttpSession loginAs(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/lab/impersonate/" + username).session(session).with(csrf()))
                .andExpect(status().isOk());
        return session;
    }

    /** 게시물이 4개 이상인 공개 계정 */
    private String publicAuthor() {
        return jdbc.queryForObject("""
                select u.username from users u join posts p on p.author_id = u.id
                where not u.is_private and p.deleted_at is null and not p.trial
                group by u.username having count(*) >= 4 order by u.username limit 1
                """, String.class);
    }

    private List<String> postsOf(String username) {
        return jdbc.queryForList("""
                select p.shortcode from posts p join users u on u.id = p.author_id
                where u.username = ? and p.deleted_at is null and not p.trial order by p.created_at
                """, String.class, username);
    }

    private List<String> grid(MockHttpSession session, String username) throws Exception {
        String body = mvc.perform(get("/api/users/" + username + "/posts").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[*].shortcode");
    }

    private int postsCount(String username) {
        return jdbc.queryForObject("select posts_count from users where username = ?", Integer.class, username);
    }

    @Test
    void 보관한_게시물은_나만_보고_프로필에서_빠지며_되돌릴_수_있다() throws Exception {
        String owner = publicAuthor();
        String code = postsOf(owner).getLast(); // 최신 글 (그리드 첫 페이지)
        String other = jdbc.queryForObject("select username from users where username <> ? and not is_private order by id limit 1",
                String.class, owner);
        MockHttpSession me = loginAs(owner);
        MockHttpSession them = loginAs(other);
        int count = postsCount(owner);

        mvc.perform(put("/api/posts/" + code + "/archive").session(them).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/posts/" + code + "/archive").session(me).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extras.archived").value(true));

        assertThat(grid(me, owner)).doesNotContain(code);
        assertThat(grid(them, owner)).doesNotContain(code);
        assertThat(postsCount(owner)).isEqualTo(count - 1);
        mvc.perform(get("/api/posts/" + code).session(them)).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + code).session(me)).andExpect(status().isOk());
        mvc.perform(get("/api/me/archive/posts").session(me))
                .andExpect(jsonPath("$.items[0].shortcode").value(code));

        mvc.perform(delete("/api/posts/" + code + "/archive").session(me).with(csrf()))
                .andExpect(jsonPath("$.extras.archived").value(false));
        assertThat(grid(them, owner)).contains(code);
        assertThat(postsCount(owner)).isEqualTo(count);
    }

    @Test
    void 프로필_고정은_최대_3개이고_그리드_맨_앞에_온다() throws Exception {
        String owner = publicAuthor();
        List<String> codes = postsOf(owner); // 오래된 순
        MockHttpSession me = loginAs(owner);
        for (int i = 0; i < 3; i++) {
            mvc.perform(put("/api/posts/" + codes.get(i) + "/pin").session(me).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.extras.pinned").value(true));
        }
        mvc.perform(put("/api/posts/" + codes.get(3) + "/pin").session(me).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PIN_LIMIT"));

        String body = mvc.perform(get("/api/users/" + owner + "/posts").session(me))
                .andReturn().getResponse().getContentAsString();
        List<String> first = JsonPath.read(body, "$.items[0:3].shortcode");
        List<Boolean> pinned = JsonPath.read(body, "$.items[0:4].pinned");
        assertThat(first).containsExactly(codes.get(2), codes.get(1), codes.get(0)); // 최근에 고정한 것부터
        assertThat(pinned).containsExactly(true, true, true, false);
        List<String> all = JsonPath.read(body, "$.items[*].shortcode");
        assertThat(all).doesNotHaveDuplicates();

        mvc.perform(delete("/api/posts/" + codes.get(0) + "/pin").session(me).with(csrf())).andExpect(status().isOk());
        mvc.perform(put("/api/posts/" + codes.get(3) + "/pin").session(me).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void 지운_게시물은_30일_안에_최근_삭제됨에서_되살린다() throws Exception {
        String owner = publicAuthor();
        String code = postsOf(owner).getLast();
        MockHttpSession me = loginAs(owner);
        int count = postsCount(owner);

        mvc.perform(delete("/api/posts/" + code).session(me).with(csrf())).andExpect(status().isNoContent());
        assertThat(postsCount(owner)).isEqualTo(count - 1);
        mvc.perform(get("/api/me/recently-deleted").session(me))
                .andExpect(jsonPath("$.items[0].shortcode").value(code));
        String other = jdbc.queryForObject("select username from users where username <> ? order by id limit 1",
                String.class, owner);
        mvc.perform(post("/api/posts/" + code + "/restore").session(loginAs(other)).with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/posts/" + code + "/restore").session(me).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortcode").value(code));
        assertThat(postsCount(owner)).isEqualTo(count);
        assertThat(grid(me, owner)).contains(code);

        // 30일이 지나면 목록에서 빠지고 되살릴 수 없다
        mvc.perform(delete("/api/posts/" + code).session(me).with(csrf())).andExpect(status().isNoContent());
        jdbc.update("update posts set deleted_at = now() - interval '31 days' where shortcode = ?", code);
        mvc.perform(get("/api/me/recently-deleted").session(me))
                .andExpect(jsonPath("$.items[?(@.shortcode == '" + code + "')]").isEmpty());
        mvc.perform(post("/api/posts/" + code + "/restore").session(me).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESTORE_EXPIRED"));
    }

    // ------------------------------------------------------------ ② DM

    private String[] strangers() {
        Map<String, Object> row = jdbc.queryForMap("""
                select a.username as a, b.username as b from users a, users b
                where a.id < b.id and not a.is_private and not b.is_private
                  and a.birth_date < current_date - interval '18 years' and b.birth_date < current_date - interval '18 years'
                  and not exists (select 1 from follows f where f.follower_id = a.id and f.following_id = b.id)
                  and not exists (select 1 from follows f where f.follower_id = b.id and f.following_id = a.id)
                order by a.id, b.id limit 1
                """);
        return new String[] {(String) row.get("a"), (String) row.get("b")};
    }

    private int conversation(MockHttpSession from, String... to) throws Exception {
        String body = mvc.perform(post("/api/direct/conversations").session(from).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernames\":[\"" + String.join("\",\"", to) + "\"]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private int send(MockHttpSession s, int cid, String text) throws Exception {
        String body = mvc.perform(post("/api/direct/conversations/" + cid + "/messages").session(s).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + text + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void 일회_보기_사진은_받는_사람이_한_번만_열고_보낸_사람은_열어봄만_본다() throws Exception {
        String[] pair = strangers();
        MockHttpSession a = loginAs(pair[0]);
        MockHttpSession b = loginAs(pair[1]);
        int cid = conversation(a, pair[1]);
        String sent = mvc.perform(multipart("/api/direct/conversations/" + cid + "/images").param("viewOnce", "true")
                        .file(new MockMultipartFile("file", "p.png", "image/png", png())).session(a).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.media").isEmpty())
                .andExpect(jsonPath("$.viewOnce.state").value("SENT"))
                .andReturn().getResponse().getContentAsString();
        int mid = JsonPath.read(sent, "$.id");

        mvc.perform(get("/api/direct/conversations/" + cid + "/messages").session(b))
                .andExpect(jsonPath("$.items[0].viewOnce.state").value("UNOPENED"))
                .andExpect(jsonPath("$.items[0].media").isEmpty());
        mvc.perform(post("/api/direct/messages/" + mid + "/open").session(a).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/direct/messages/" + mid + "/open").session(b).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media.url").isNotEmpty());
        mvc.perform(post("/api/direct/messages/" + mid + "/open").session(b).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALREADY_OPENED"));
        mvc.perform(get("/api/direct/conversations/" + cid + "/messages").session(b))
                .andExpect(jsonPath("$.items[0].viewOnce.state").value("OPENED"))
                .andExpect(jsonPath("$.items[0].media").isEmpty());
        mvc.perform(get("/api/direct/conversations/" + cid + "/messages").session(a))
                .andExpect(jsonPath("$.items[0].viewOnce.state").value("OPENED"));
    }

    @Test
    void 메시지_고정은_대화방마다_3개_채팅방_고정은_받은_메시지함_맨_위() throws Exception {
        String[] pair = strangers();
        MockHttpSession a = loginAs(pair[0]);
        int cid = conversation(a, pair[1]);
        int[] ids = new int[4];
        for (int i = 0; i < 4; i++) ids[i] = send(a, cid, "고정 " + i);
        for (int i = 0; i < 3; i++) {
            mvc.perform(put("/api/direct/messages/" + ids[i] + "/pin").session(a).with(csrf()))
                    .andExpect(jsonPath("$.pinned").value(true));
        }
        mvc.perform(put("/api/direct/messages/" + ids[3] + "/pin").session(a).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PIN_LIMIT"));
        mvc.perform(get("/api/direct/conversations/" + cid).session(loginAs(pair[1])))
                .andExpect(jsonPath("$.pins.length()").value(3))
                .andExpect(jsonPath("$.pins[0].id").value(ids[2]));
        mvc.perform(delete("/api/direct/messages/" + ids[0] + "/pin").session(a).with(csrf()))
                .andExpect(jsonPath("$.pinned").value(false));

        // 채팅방 고정: 가장 오래된 대화도 맨 위로
        String oldest = jdbc.queryForObject("""
                select u.username from users u where u.username not in (?, ?) and not u.is_private order by u.id desc limit 1
                """, String.class, pair[0], pair[1]);
        int old = conversation(a, oldest);
        send(a, old, "오래된 대화");
        send(a, cid, "새 메시지");
        mvc.perform(put("/api/direct/conversations/" + old + "/pin").session(a).with(csrf())).andExpect(status().isNoContent());
        String folder = jdbc.queryForObject("""
                select folder from conversation_members cm join users u on u.id = cm.user_id
                where cm.conversation_id = ? and u.username = ?""", String.class, old, pair[0]);
        mvc.perform(get("/api/direct/inbox").param("folder", folder).session(a))
                .andExpect(jsonPath("$[0].id").value(old))
                .andExpect(jsonPath("$[0].pinned").value(true));
    }

    @Test
    void 사라지는_메시지는_1대1에서만_켜고_끄면_그동안_보낸_메시지가_지워진다() throws Exception {
        String[] pair = strangers();
        MockHttpSession a = loginAs(pair[0]);
        MockHttpSession b = loginAs(pair[1]);
        int cid = conversation(a, pair[1]);
        int kept = send(a, cid, "남는 메시지");
        mvc.perform(put("/api/direct/conversations/" + cid + "/vanish").session(a).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"on\":true}"))
                .andExpect(jsonPath("$.vanishMode").value(true));
        int gone = send(b, cid, "사라질 메시지");
        mvc.perform(get("/api/direct/conversations/" + cid + "/messages").session(a))
                .andExpect(jsonPath("$.items[0].vanish").value(true));
        mvc.perform(put("/api/direct/conversations/" + cid + "/vanish").session(b).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"on\":false}"))
                .andExpect(jsonPath("$.vanishMode").value(false));
        List<Integer> ids = JsonPath.read(mvc.perform(get("/api/direct/conversations/" + cid + "/messages").session(a))
                .andReturn().getResponse().getContentAsString(), "$.items[*].id");
        assertThat(ids).contains(kept).doesNotContain(gone);

        String third = jdbc.queryForObject("select username from users where username not in (?, ?) order by id limit 1",
                String.class, pair[0], pair[1]);
        int group = conversation(a, pair[1], third);
        mvc.perform(put("/api/direct/conversations/" + group + "/vanish").session(a).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"on\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 음성_메시지는_1분까지_올리고_번역은_사전으로_보여준다() throws Exception {
        String[] pair = strangers();
        MockHttpSession a = loginAs(pair[0]);
        int cid = conversation(a, pair[1]);
        byte[] fake = new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 1, 2, 3, 4};
        mvc.perform(multipart("/api/direct/conversations/" + cid + "/audio").param("durationMs", "3200")
                        .file(new MockMultipartFile("file", "v.webm", "audio/webm;codecs=opus", fake)).session(a).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("AUDIO"))
                .andExpect(jsonPath("$.durationMs").value(3200))
                .andExpect(jsonPath("$.media.url").isNotEmpty());
        mvc.perform(multipart("/api/direct/conversations/" + cid + "/audio").param("durationMs", "75000")
                        .file(new MockMultipartFile("file", "v.webm", "audio/webm", fake)).session(a).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/direct/conversations/" + cid + "/audio").param("durationMs", "1000")
                        .file(new MockMultipartFile("file", "v.txt", "text/plain", fake)).session(a).with(csrf()))
                .andExpect(status().isBadRequest());

        int mid = send(a, cid, "안녕하세요 고마워요");
        mvc.perform(post("/api/direct/messages/" + mid + "/translate").session(loginAs(pair[1])).with(csrf()))
                .andExpect(jsonPath("$.text").value("hello thank you"))
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.to").value("en"));
    }

    // ------------------------------------------------------------ ③ 음악·검색

    private static MockMultipartFile json(String name, String value) throws Exception {
        return new MockMultipartFile(name, "", MediaType.APPLICATION_JSON_VALUE, value.getBytes("UTF-8"));
    }

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private String adult() {
        return jdbc.queryForObject("""
                select username from users where not is_private and birth_date < current_date - interval '18 years'
                order by id limit 1""", String.class);
    }

    private String reel(MockHttpSession s, String data) throws Exception {
        MockMultipartFile video = new MockMultipartFile("video", "v.mp4", "video/mp4", new byte[] {0, 0, 0, 24, 102, 116});
        MockMultipartFile poster = new MockMultipartFile("poster", "p.png", "image/png", png(360, 640));
        return mvc.perform(multipart("/api/reels").file(video).file(poster).file(json("data", data)).session(s).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void 시드에는_음원_목록과_원본_오디오가_있고_리믹스는_원본의_소리를_쓴다() {
        assertThat(jdbc.queryForObject("select count(*) from audio_tracks where origin_post_id is null", Integer.class))
                .isGreaterThanOrEqualTo(30);
        assertThat(jdbc.queryForObject("select count(*) from posts where type = 'REEL' and audio_id is null",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from posts r join posts o on o.id = r.remix_of_id where r.audio_id is distinct from o.audio_id
                """, Integer.class)).isZero();
    }

    @Test
    void 음악_없이_올린_릴스는_원본_오디오가_되고_다른_사람이_이_오디오를_쓸_수_있다() throws Exception {
        String me = adult();
        MockHttpSession s = loginAs(me);
        String first = reel(s, "{\"caption\":\"내 소리\",\"durationMs\":8000}");
        assertThat((Boolean) JsonPath.read(first, "$.extras.audio.original")).isTrue();
        assertThat((String) JsonPath.read(first, "$.extras.audio.artist")).isEqualTo(me);
        int audioId = JsonPath.read(first, "$.extras.audio.id");

        String second = reel(s, "{\"durationMs\":5000,\"audioId\":" + audioId + "}");
        assertThat((Integer) JsonPath.read(second, "$.extras.audio.id")).isEqualTo(audioId);
        mvc.perform(get("/api/audio/" + audioId).session(s))
                .andExpect(jsonPath("$.audio.title").value("원본 오디오"))
                .andExpect(jsonPath("$.originShortcode").value((String) JsonPath.read(first, "$.shortcode")))
                .andExpect(jsonPath("$.reelCount").value(2));
        mvc.perform(get("/api/audio/" + audioId + "/reels").session(s))
                .andExpect(jsonPath("$.items.length()").value(2));

        mvc.perform(multipart("/api/reels").file(new MockMultipartFile("video", "v.mp4", "video/mp4", new byte[] {1, 2}))
                        .file(new MockMultipartFile("poster", "p.png", "image/png", png(360, 640)))
                        .file(json("data", "{\"durationMs\":5000,\"audioId\":999999}")).session(s).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_AUDIO"));
    }

    @Test
    void 음원_검색_게시물과_스토리에_음악_붙이기() throws Exception {
        MockHttpSession s = loginAs(adult());
        String list = mvc.perform(get("/api/audio").param("q", "한강").session(s))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int track = JsonPath.read(list, "$[0].id");
        assertThat((String) JsonPath.read(list, "$[0].title")).isEqualTo("한강 산책");
        mvc.perform(get("/api/search").param("q", "한강 산책").session(s))
                .andExpect(jsonPath("$.audios[0].id").value(track));

        MockMultipartFile photo = new MockMultipartFile("files", "a.png", "image/png", png(400, 500));
        mvc.perform(multipart("/api/posts").file(photo).file(json("data", "{\"caption\":\"음악 있는 사진\",\"audioId\":" + track + "}"))
                        .session(s).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extras.audio.title").value("한강 산책"))
                .andExpect(jsonPath("$.extras.audio.original").value(false));
        mvc.perform(multipart("/api/stories").file(new MockMultipartFile("file", "s.png", "image/png", png(540, 960)))
                        .file(json("data", "{\"audioId\":" + track + "}")).session(s).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.audio.artist").value("새벽세시"));
    }

    @Test
    void 키워드_검색은_캡션에_검색어가_들어간_볼_수_있는_게시물만() throws Exception {
        String me = adult();
        MockHttpSession s = loginAs(me);
        MockMultipartFile photo = new MockMultipartFile("files", "a.png", "image/png", png(400, 500));
        String code = JsonPath.read(mvc.perform(multipart("/api/posts").file(photo)
                        .file(json("data", "{\"caption\":\"오늘은 zxqvortex 실험 중\"}")).session(s).with(csrf()))
                .andReturn().getResponse().getContentAsString(), "$.shortcode");
        jdbc.update("update posts set caption = 'zxqvortex 비공개 글' where id = (select p.id from posts p join users u on u.id = p.author_id where u.is_private and u.username <> ? order by p.id limit 1)", me);
        String other = jdbc.queryForObject("""
                select username from users u where not is_private and username <> ?
                  and not exists (select 1 from follows f join users pu on pu.id = f.following_id
                                  where f.follower_id = u.id and pu.is_private)
                order by id limit 1""", String.class, me);
        List<String> hits = JsonPath.read(mvc.perform(get("/api/search/posts").param("q", "ZXQVORTEX").session(loginAs(other)))
                .andReturn().getResponse().getContentAsString(), "$.items[*].shortcode");
        assertThat(hits).containsExactly(code);
        mvc.perform(get("/api/search/posts").param("q", "z").session(s)).andExpect(jsonPath("$.items").isEmpty());
    }

    // ------------------------------------------------------------ ④-a 계정 센터

    private MockHttpSession passwordLogin(String login, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());
        return session;
    }

    private String plainUser() {
        return jdbc.queryForObject("""
                select username from users where is_fake and not totp_enabled and deleted_at is null
                  and birth_date < current_date - interval '18 years' and not is_private order by id limit 1""",
                String.class);
    }

    @Test
    void 가입은_이메일로_받은_인증_코드가_있어야_하고_코드는_Lab에_보인다() throws Exception {
        String data = "{\"email\":\"Code.Test@example.com\",\"fullName\":\"코드\",\"username\":\"code.test\","
                + "\"password\":\"secret1\",\"birthDate\":\"1990-01-01\"";
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(data + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_REQUIRED"));
        mvc.perform(post("/api/auth/signup/code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Code.Test@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sentTo").value("code.test@example.com"));
        String code = JsonPath.read(mvc.perform(get("/api/lab/codes")).andReturn().getResponse().getContentAsString(),
                "$[0].code");
        String wrong = code.equals("000000") ? "111111" : "000000";
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(data + ",\"code\":\"" + wrong + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_CODE"));
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(data + ",\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated());
        // 한 번 쓴 코드는 다시 못 쓰고, 이미 가입한 이메일은 코드를 받지 않는다
        mvc.perform(post("/api/auth/signup/code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"code.test@example.com\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void 비밀번호를_재설정하면_다른_곳의_로그인이_끊기고_새_비밀번호로만_들어간다() throws Exception {
        String u = plainUser();
        MockHttpSession elsewhere = passwordLogin(u, "yang1234");
        mvc.perform(get("/api/me/profile").session(elsewhere)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/password/code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + u + "\"}")).andExpect(status().isOk());
        // 없는 계정도 같은 응답 (가입 여부를 흘리지 않음)
        mvc.perform(post("/api/auth/password/code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"no.such.user.zz\"}")).andExpect(status().isOk());
        String code = jdbc.queryForObject("""
                select c.code from auth_codes c join users x on x.id::text = c.target
                where c.purpose = 'RESET' and x.username = ? order by c.id desc limit 1""", String.class, u);
        mvc.perform(post("/api/auth/password/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + u + "\",\"code\":\"" + code + "\",\"password\":\"newpass9\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/me/profile").session(elsewhere)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + u + "\",\"password\":\"yang1234\"}"))
                .andExpect(status().isUnauthorized());
        passwordLogin(u, "newpass9");
    }

    @Test
    void 로그인_활동에서_다른_기기를_로그아웃시킨다() throws Exception {
        String u = plainUser();
        MockHttpSession laptop = passwordLogin(u, "yang1234");
        MockHttpSession phone = passwordLogin(u, "yang1234");
        String list = mvc.perform(get("/api/me/sessions").session(laptop)).andReturn().getResponse().getContentAsString();
        List<Boolean> current = JsonPath.read(list, "$[*].current");
        assertThat(current).containsOnlyOnce(true);
        List<Integer> others = JsonPath.read(list, "$[?(@.current == false)].id");
        assertThat(others).isNotEmpty();

        mvc.perform(delete("/api/me/sessions/" + others.getFirst()).session(laptop).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/profile").session(phone)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/profile").session(laptop)).andExpect(status().isOk());

        MockHttpSession tablet = passwordLogin(u, "yang1234");
        mvc.perform(delete("/api/me/sessions").session(laptop).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/me/profile").session(tablet)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/profile").session(laptop)).andExpect(status().isOk());
        // 로그아웃하면 기록에서도 사라진다
        mvc.perform(post("/api/auth/logout").session(laptop).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("""
                select count(*) from login_sessions s join users x on x.id = s.user_id where x.username = ? and s.revoked_at is null
                """, Integer.class, u)).isZero();
    }

    @Test
    void 계정을_추가하면_비밀번호_없이_오간다() throws Exception {
        List<String> two = jdbc.queryForList("""
                select username from users where is_fake and not totp_enabled and deleted_at is null
                  and birth_date < current_date - interval '18 years' order by id limit 2""", String.class);
        MockHttpSession s = passwordLogin(two.get(0), "yang1234");
        mvc.perform(post("/api/auth/accounts").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + two.get(1) + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/accounts").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + two.get(1) + "\",\"password\":\"yang1234\"}"))
                .andExpect(jsonPath("$.user.username").value(two.get(1)));
        String linked = mvc.perform(get("/api/auth/accounts").session(s)).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(linked, "$.accounts[*].username")).containsExactly(two.get(0), two.get(1));
        int firstId = JsonPath.read(linked, "$.accounts[0].id");

        mvc.perform(post("/api/auth/accounts/" + firstId + "/switch").session(s).with(csrf()))
                .andExpect(jsonPath("$.user.username").value(two.get(0)));
        mvc.perform(get("/api/auth/me").session(s)).andExpect(jsonPath("$.user.username").value(two.get(0)));
        long stranger = jdbc.queryForObject("select id from users where username not in (?, ?) order by id limit 1",
                Long.class, two.get(0), two.get(1));
        mvc.perform(post("/api/auth/accounts/" + stranger + "/switch").session(s).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_LINKED"));
    }

    @Test
    void 비활성화는_다시_로그인하면_복구되고_삭제는_30일이_지나면_영구_삭제된다() throws Exception {
        String u = plainUser();
        MockHttpSession me = passwordLogin(u, "yang1234");
        MockHttpSession other = loginAs(jdbc.queryForObject(
                "select username from users where username <> ? and deleted_at is null order by id limit 1", String.class, u));
        mvc.perform(post("/api/me/deactivate").session(me).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"wrong\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/deactivate").session(me).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"yang1234\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/profile").session(me)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/" + u).session(other)).andExpect(status().isNotFound());

        me = passwordLogin(u, "yang1234"); // 다시 로그인 → 복구
        mvc.perform(get("/api/users/" + u).session(other)).andExpect(status().isOk());

        mvc.perform(post("/api/me/delete").session(me).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"yang1234\"}")).andExpect(jsonPath("$.deleteAt").isNotEmpty());
        mvc.perform(get("/api/users/" + u).session(other)).andExpect(status().isNotFound());
        accountService.purgeExpired();
        assertThat(jdbc.queryForObject("select count(*) from users where username = ?", Integer.class, u)).isOne();

        jdbc.update("update users set delete_requested_at = now() - interval '31 days' where username = ?", u);
        assertThat(accountService.purgeExpired()).isOne();
        assertThat(jdbc.queryForObject("select count(*) from users where username = ?", Integer.class, u)).isZero();
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + u + "\",\"password\":\"yang1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ ④-b 안전·알림 설정

    private void settings(MockHttpSession s, String json) throws Exception {
        mvc.perform(patch("/api/me/settings").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private String comment(MockHttpSession s, String code, String text, int expected) throws Exception {
        return mvc.perform(post("/api/posts/" + code + "/comments").session(s).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + text + "\"}"))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    /** 게시물 주인, 그를 팔로우하지 않고 그도 팔로우하지 않는 공개 성인 계정 */
    private String[] ownerAndStranger() {
        Map<String, Object> row = jdbc.queryForMap("""
                select o.username as o, x.username as x from users o, users x
                where o.id <> x.id and not o.is_private and not x.is_private and not o.totp_enabled
                  and x.birth_date < current_date - interval '18 years'
                  and exists (select 1 from posts p where p.author_id = o.id and p.deleted_at is null and not p.trial
                              and not p.comments_disabled and not p.subscribers_only)
                  and not exists (select 1 from follows f where f.follower_id = o.id and f.following_id = x.id)
                  and not exists (select 1 from follows f where f.follower_id = x.id and f.following_id = o.id)
                  and not exists (select 1 from blocks b where (b.blocker_id = o.id and b.blocked_id = x.id)
                                  or (b.blocker_id = x.id and b.blocked_id = o.id))
                  and not exists (select 1 from restricts r where r.user_id = o.id and r.restricted_id = x.id)
                order by o.id, x.id limit 1""");
        return new String[] {(String) row.get("o"), (String) row.get("x")};
    }

    private String postOf(String owner) {
        return jdbc.queryForObject("""
                select p.shortcode from posts p join users u on u.id = p.author_id where u.username = ?
                  and p.deleted_at is null and not p.trial and not p.comments_disabled and not p.subscribers_only
                order by p.id desc limit 1""", String.class, owner);
    }

    @Test
    void 숨겨진_단어에_걸린_댓글은_쓴_사람만_보고_주인은_숨겨진_댓글에서_본다() throws Exception {
        String[] ox = ownerAndStranger();
        MockHttpSession owner = loginAs(ox[0]);
        MockHttpSession writer = loginAs(ox[1]);
        String code = postOf(ox[0]);
        settings(owner, "{\"hideOffensive\":true,\"hiddenWords\":[\"스포일러\"]}");

        int bad = JsonPath.read(comment(writer, code, "진짜 바보 같네", 201), "$.id");
        int spoiler = JsonPath.read(comment(writer, code, "결말 스포일러 있음", 201), "$.id");
        int fine = JsonPath.read(comment(writer, code, "사진 예뻐요", 201), "$.id");
        String third = jdbc.queryForObject("select username from users where username not in (?, ?) and deleted_at is null order by id limit 1",
                String.class, ox[0], ox[1]);

        List<Integer> seenByOthers = JsonPath.read(mvc.perform(get("/api/posts/" + code + "/comments").session(loginAs(third)))
                .andReturn().getResponse().getContentAsString(), "$.items[*].id");
        assertThat(seenByOthers).contains(fine).doesNotContain(bad, spoiler);
        List<Integer> seenByOwner = JsonPath.read(mvc.perform(get("/api/posts/" + code + "/comments").session(owner))
                .andReturn().getResponse().getContentAsString(), "$.items[*].id");
        assertThat(seenByOwner).doesNotContain(bad, spoiler);
        List<Integer> seenByWriter = JsonPath.read(mvc.perform(get("/api/posts/" + code + "/comments").session(writer))
                .andReturn().getResponse().getContentAsString(), "$.items[*].id");
        assertThat(seenByWriter).contains(bad, spoiler, fine);
        List<Integer> hidden = JsonPath.read(mvc.perform(get("/api/posts/" + code + "/comments/hidden").session(owner))
                .andReturn().getResponse().getContentAsString(), "$[*].id");
        assertThat(hidden).containsExactlyInAnyOrder(bad, spoiler);
        mvc.perform(get("/api/posts/" + code + "/comments/hidden").session(writer)).andExpect(status().isForbidden());
        // 숨긴 댓글은 알림도 가지 않는다
        assertThat(jdbc.queryForObject("select count(*) from notifications where comment_id in (?, ?)", Integer.class, bad, spoiler)).isZero();
    }

    @Test
    void 댓글_허용_범위를_줄이면_허용되지_않은_사람은_댓글을_달_수_없다() throws Exception {
        String[] ox = ownerAndStranger();
        MockHttpSession owner = loginAs(ox[0]);
        MockHttpSession stranger = loginAs(ox[1]);
        String code = postOf(ox[0]);
        settings(owner, "{\"commentAudience\":\"FOLLOWERS\"}");
        mvc.perform(get("/api/posts/" + code).session(stranger)).andExpect(jsonPath("$.extras.canComment").value(false));
        comment(stranger, code, "안녕하세요", 403);

        mvc.perform(put("/api/users/" + ox[0] + "/follow").session(stranger).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + code).session(stranger)).andExpect(jsonPath("$.extras.canComment").value(true));
        comment(stranger, code, "팔로우했어요", 201);

        settings(owner, "{\"commentAudience\":\"OFF\"}");
        comment(stranger, code, "또 왔어요", 403);
        comment(owner, code, "주인은 늘 가능", 201);
        mvc.perform(patch("/api/me/settings").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"commentAudience\":\"NOBODY\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void 알림_종류를_끄면_남지_않고_일시_중지하면_기록만_남는다() throws Exception {
        String[] ox = ownerAndStranger();
        MockHttpSession owner = loginAs(ox[0]);
        MockHttpSession fan = loginAs(ox[1]);
        String code = postOf(ox[0]);
        long ownerId = jdbc.queryForObject("select id from users where username = ?", Long.class, ox[0]);
        settings(owner, "{\"notifOff\":[\"LIKES\"]}");
        mvc.perform(put("/api/posts/" + code + "/like").session(fan).with(csrf())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from notifications n join users a on a.id = n.actor_id "
                + "where n.user_id = ? and a.username = ? and n.type = 'LIKE'", Integer.class, ownerId, ox[1])).isZero();

        settings(owner, "{\"notifOff\":[],\"pauseMinutes\":60}");
        mvc.perform(get("/api/me/settings").session(owner)).andExpect(jsonPath("$.notifPausedUntil").isNotEmpty());
        comment(fan, code, "일시 중지 중 댓글", 201);
        assertThat(jdbc.queryForObject("select count(*) from notifications n join users a on a.id = n.actor_id "
                + "where n.user_id = ? and a.username = ? and n.type = 'COMMENT'", Integer.class, ownerId, ox[1])).isOne();
        settings(owner, "{\"resumeNotifications\":true}");
        mvc.perform(get("/api/me/settings").session(owner)).andExpect(jsonPath("$.notifPausedUntil").isEmpty());
    }

    @Test
    void 민감한_콘텐츠는_덜_보기면_탐색에_안_나오고_10대는_바꿀_수_없다() throws Exception {
        String adult = jdbc.queryForObject("""
                select username from users where birth_date < current_date - interval '18 years' and deleted_at is null
                order by following_count desc limit 1""", String.class);
        MockHttpSession s = loginAs(adult);
        settings(s, "{\"sensitiveLevel\":\"MORE\"}");
        // 작은 시드에서는 탐색 첫 화면에 민감 표시 글이 없을 수 있어서, 첫 화면 글 몇 개에 직접 표시한다
        List<String> first = JsonPath.read(mvc.perform(get("/api/explore").param("page", "0").session(s))
                .andReturn().getResponse().getContentAsString(), "$.items[*].shortcode");
        assertThat(first).isNotEmpty();
        jdbc.update("update posts set sensitive = true where shortcode in ("
                + String.join(",", first.stream().limit(3).map(c -> "'" + c + "'").toList()) + ")");
        boolean sawSensitive = false;
        for (int page = 0; page < 6 && !sawSensitive; page++) {
            List<String> codes = JsonPath.read(mvc.perform(get("/api/explore").param("page", String.valueOf(page)).session(s))
                    .andReturn().getResponse().getContentAsString(), "$.items[*].shortcode");
            if (codes.isEmpty()) break;
            sawSensitive = jdbc.queryForObject("select count(*) from posts where sensitive and shortcode in ("
                    + String.join(",", codes.stream().map(c -> "'" + c + "'").toList()) + ")", Integer.class) > 0;
        }
        assertThat(sawSensitive).as("더 보기면 민감 표시 게시물도 탐색에 나온다").isTrue();
        settings(s, "{\"sensitiveLevel\":\"LESS\"}");
        for (int page = 0; page < 6; page++) {
            List<String> codes = JsonPath.read(mvc.perform(get("/api/explore").param("page", String.valueOf(page)).session(s))
                    .andReturn().getResponse().getContentAsString(), "$.items[*].shortcode");
            if (codes.isEmpty()) break;
            assertThat(jdbc.queryForObject("select count(*) from posts where sensitive and shortcode in ("
                    + String.join(",", codes.stream().map(c -> "'" + c + "'").toList()) + ")", Integer.class)).isZero();
        }
        String teen = jdbc.queryForObject("""
                select username from users where birth_date > current_date - interval '18 years' and deleted_at is null
                order by id limit 1""", String.class);
        MockHttpSession t = loginAs(teen);
        mvc.perform(get("/api/me/settings").session(t)).andExpect(jsonPath("$.sensitiveLevel").value("LESS"))
                .andExpect(jsonPath("$.sensitiveLocked").value(true));
        mvc.perform(patch("/api/me/settings").session(t).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sensitiveLevel\":\"MORE\"}")).andExpect(status().isForbidden());
    }

    @Test
    void 임시_저장한_게시물은_예약_목록과_따로_있고_지금_게시할_수_있다() throws Exception {
        MockHttpSession s = loginAs(adult());
        MockMultipartFile photo = new MockMultipartFile("files", "a.png", "image/png", png(400, 500));
        String draft = mvc.perform(multipart("/api/posts/drafts").file(photo)
                        .file(json("data", "{\"caption\":\"임시 저장 글\"}")).session(s).with(csrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.scheduledAt").isEmpty())
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(draft, "$.id");
        mvc.perform(get("/api/me/drafts").session(s)).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/me/scheduled").session(s)).andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
        mvc.perform(post("/api/me/scheduled/" + id + "/publish").session(s).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.caption").value("임시 저장 글"));
        mvc.perform(get("/api/me/drafts").session(s)).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void 수면_모드_시간에는_알림을_기록만_한다() throws Exception {
        String[] ox = ownerAndStranger();
        MockHttpSession owner = loginAs(ox[0]);
        int h = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul")).getHour();
        settings(owner, "{\"sleepOn\":true,\"sleepStart\":" + h + ",\"sleepEnd\":" + ((h + 2) % 24) + "}");
        mvc.perform(get("/api/me/settings").session(owner)).andExpect(jsonPath("$.sleepingNow").value(true));
        settings(owner, "{\"sleepStart\":" + ((h + 3) % 24) + ",\"sleepEnd\":" + ((h + 4) % 24) + "}");
        mvc.perform(get("/api/me/settings").session(owner)).andExpect(jsonPath("$.sleepingNow").value(false));
    }

    @Test
    void 홈_피드를_여러_쪽_넘겨도_같은_게시물이_두_번_나오지_않는다_광고_포함() throws Exception {
        // 전체 기능 점검에서 찾음: 앞쪽에 보통 글로 나온 게시물이 뒤쪽에서 광고로 또 나왔다
        String u = jdbc.queryForObject("select username from users where deleted_at is null order by following_count desc limit 1", String.class);
        MockHttpSession s = loginAs(u);
        java.util.Set<String> seen = new java.util.HashSet<>();
        String cursor = null;
        for (int page = 0; page < 8; page++) {
            var req = get("/api/feed").param("mode", "home").session(s);
            if (cursor != null) req = req.param("cursor", cursor);
            String body = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<String> codes = JsonPath.read(body, "$.items[*].shortcode");
            for (String c : codes) assertThat(seen.add(c)).as("중복: " + c + " (" + page + "쪽)").isTrue();
            cursor = JsonPath.read(body, "$.nextCursor");
            if (cursor == null) break;
        }
        assertThat(seen.size()).isGreaterThan(10);
    }

    @Test
    void 같은_시드값이면_하루_중_몇_시에_만들든_같은_데이터가_나온다() {
        // 게시물 시각을 "저녁 7~11시"처럼 하루 중 시각에 맞추던 때는, 만드는 시각에 따라 난수를 뽑는 수가 달라져
        // 같은 시드값이어도 데이터가 어긋났다 (여러 테스트가 시간대에 따라 흔들린 원인)
        List<String> first = jdbc.queryForList("select shortcode from posts order by id", String.class);
        Integer likes = jdbc.queryForObject("select count(*) from likes", Integer.class);
        Integer follows = jdbc.queryForObject("select count(*) from follows", Integer.class);
        Integer stories = jdbc.queryForObject("select count(*) from stories", Integer.class);
        try {
            clock.setOffset(java.time.Duration.ofHours(9).plusMinutes(37));
            seedService.generate(SeedSize.SMALL, 42);
            assertThat(jdbc.queryForList("select shortcode from posts order by id", String.class)).isEqualTo(first);
            assertThat(jdbc.queryForObject("select count(*) from likes", Integer.class)).isEqualTo(likes);
            assertThat(jdbc.queryForObject("select count(*) from follows", Integer.class)).isEqualTo(follows);
            assertThat(jdbc.queryForObject("select count(*) from stories", Integer.class)).isEqualTo(stories);
        } finally {
            clock.setOffset(java.time.Duration.ZERO);
        }
    }
}
