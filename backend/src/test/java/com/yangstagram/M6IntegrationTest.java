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
}
