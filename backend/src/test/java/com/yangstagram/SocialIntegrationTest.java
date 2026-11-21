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
import com.yangstagram.common.time.LabClock;
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

/** M2: 팔로우 요청, 알림 묶음, 댓글 답글·수정·고정, 저장·컬렉션, 리포스트, 검색, DM, 스토리, 시간 가속 */
@SpringBootTest(properties = {"yangstagram.media.storage-dir=build/test-storage", "yangstagram.seed.size=small"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SocialIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    SeedService seedService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LabClock clock;

    @BeforeEach
    void resetData() {
        clock.setOffset(Duration.ZERO);
        seedService.generate(SeedSize.SMALL, 42);
    }

    @AfterEach
    void resetClock() {
        clock.setOffset(Duration.ZERO);
    }

    private MockHttpSession loginAs(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/lab/impersonate/" + username).session(session).with(csrf()))
                .andExpect(status().isOk());
        return session;
    }

    private void advance(long minutes) throws Exception {
        mvc.perform(post("/api/lab/clock").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"addMinutes\":" + minutes + "}")).andExpect(status().isOk());
    }

    /** 서로 팔로우하지 않는 공개 계정 두 명 */
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

    private String publicPostOf(String exceptAuthor) {
        return jdbc.queryForObject("""
                select p.shortcode from posts p join users u on u.id = p.author_id
                where not u.is_private and not p.comments_disabled and u.username <> ?
                order by p.id limit 1
                """, String.class, exceptAuthor);
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

    // ------------------------------------------------------------ 시드

    @Test
    void 시드에_스토리_DM_알림_저장_리포스트가_만들어진다() {
        assertThat(jdbc.queryForObject("select count(*) from stories where expires_at > now()", Integer.class))
                .isGreaterThan(10);
        assertThat(jdbc.queryForObject("select count(*) from stories where expires_at <= now()", Integer.class))
                .isGreaterThan(10);
        assertThat(jdbc.queryForObject("select count(*) from conversations", Integer.class)).isGreaterThan(30);
        assertThat(jdbc.queryForObject("select count(*) from messages", Integer.class)).isGreaterThan(300);
        assertThat(jdbc.queryForObject("select count(*) from notifications", Integer.class)).isGreaterThan(1000);
        assertThat(jdbc.queryForObject("select count(*) from saved_posts", Integer.class)).isGreaterThan(50);
        assertThat(jdbc.queryForObject("select count(*) from reposts", Integer.class)).isGreaterThan(5);
        // 비공개 계정 게시물은 리포스트되지 않는다
        assertThat(jdbc.queryForObject("""
                select count(*) from reposts r join posts p on p.id = r.post_id join users a on a.id = p.author_id
                where a.is_private""", Integer.class)).isZero();
        // 카운터 일치
        assertThat(jdbc.queryForObject("""
                select count(*) from posts p
                where p.save_count <> (select count(*) from saved_posts s where s.post_id = p.id)
                   or p.repost_count <> (select count(*) from reposts r where r.post_id = p.id)
                """, Integer.class)).isZero();
    }

    // ------------------------------------------------------------ 팔로우 요청 (데모 시나리오 4)

    @Test
    void 비공개_계정은_승인해야_게시물이_보이고_요청자는_알림을_받는다() throws Exception {
        String target = jdbc.queryForObject("""
                select u.username from users u where u.is_private and u.posts_count > 0 order by u.id limit 1""",
                String.class);
        String requester = jdbc.queryForObject("""
                select u.username from users u where u.username <> ? and not exists (
                  select 1 from follows f join users t on t.id = f.following_id
                  where f.follower_id = u.id and t.username = ?) order by u.id limit 1""",
                String.class, target, target);
        MockHttpSession a = loginAs(requester);
        mvc.perform(put("/api/users/" + target + "/follow").session(a).with(csrf()))
                .andExpect(jsonPath("$.relationship").value("REQUESTED"));
        mvc.perform(get("/api/users/" + target + "/posts").session(a)).andExpect(status().isForbidden());

        MockHttpSession c = loginAs(target);
        String list = mvc.perform(get("/api/me/follow-requests").session(c)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(list).contains("\"username\":\"" + requester + "\"");
        mvc.perform(get("/api/notifications").session(c))
                .andExpect(jsonPath("$.requests.count").isNumber());
        mvc.perform(put("/api/me/follow-requests/" + requester).session(c).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/users/" + target + "/posts").session(a)).andExpect(status().isOk());
        String activity = mvc.perform(get("/api/notifications").session(a))
                .andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(activity, "$.items[*].type");
        assertThat(types).contains("FOLLOW_ACCEPTED");
    }

    @Test
    void 공개로_바꾸면_대기_중인_요청이_모두_승인된다() throws Exception {
        String target = jdbc.queryForObject("""
                select u.username from users u where u.is_private and exists (
                  select 1 from follows f where f.following_id = u.id and f.status = 'PENDING') order by u.id limit 1""",
                String.class);
        MockHttpSession c = loginAs(target);
        mvc.perform(put("/api/me/privacy").session(c).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"isPrivate\":false}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                select count(*) from follows f join users u on u.id = f.following_id
                where u.username = ? and f.status = 'PENDING'""", Integer.class, target)).isZero();
        assertThat(jdbc.queryForObject("select is_private from users where username = ?", Boolean.class, target))
                .isFalse();
    }

    // ------------------------------------------------------------ 알림 묶음 (데모 시나리오 3)

    @Test
    void 같은_게시물_좋아요는_한_줄로_묶이고_취소하면_빠진다() throws Exception {
        String[] pair = strangers();
        String author = pair[0];
        String code = jdbc.queryForObject("""
                select p.shortcode from posts p join users u on u.id = p.author_id where u.username = ?
                order by p.id limit 1""", String.class, author);
        jdbc.update("delete from likes where post_id = (select id from posts where shortcode = ?)", code);
        jdbc.update("delete from notifications where post_id = (select id from posts where shortcode = ?)", code);
        jdbc.update("update posts set like_count = 0 where shortcode = ?", code);

        List<String> likers = jdbc.queryForList("""
                select username from users where not is_private and username <> ? order by id limit 3""",
                String.class, author);
        for (String liker : likers) {
            mvc.perform(put("/api/posts/" + code + "/like").session(loginAs(liker)).with(csrf()))
                    .andExpect(status().isOk());
        }
        MockHttpSession me = loginAs(author);
        mvc.perform(get("/api/notifications/unread").session(me)).andExpect(jsonPath("$.count").isNumber());
        String activity = mvc.perform(get("/api/notifications").session(me))
                .andReturn().getResponse().getContentAsString();
        List<Integer> counts = JsonPath.read(activity, "$.items[?(@.type == 'LIKE' && @.shortcode == '" + code
                + "')].actorCount");
        assertThat(counts).containsExactly(3);
        List<String> firstActors = JsonPath.read(activity, "$.items[?(@.type == 'LIKE' && @.shortcode == '" + code
                + "')].actors[0].username");
        assertThat(firstActors).containsExactly(likers.get(2)); // 가장 최근 사람이 먼저

        mvc.perform(delete("/api/posts/" + code + "/like").session(loginAs(likers.get(2))).with(csrf()));
        activity = mvc.perform(get("/api/notifications").session(me)).andReturn().getResponse().getContentAsString();
        counts = JsonPath.read(activity, "$.items[?(@.type == 'LIKE' && @.shortcode == '" + code + "')].actorCount");
        assertThat(counts).containsExactly(2);

        mvc.perform(post("/api/notifications/read").session(me).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread").session(me)).andExpect(jsonPath("$.count").value(0));
    }

    // ------------------------------------------------------------ 댓글

    @Test
    void 댓글은_15분_안에만_수정되고_시간_가속으로_확인할_수_있다() throws Exception {
        String[] pair = strangers();
        String code = publicPostOf(pair[0]);
        MockHttpSession me = loginAs(pair[0]);
        String res = mvc.perform(post("/api/posts/" + code + "/comments").session(me).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"처음 댓글\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.canEdit").value(true))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(res, "$.id");

        mvc.perform(patch("/api/comments/" + id).session(me).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"고친 댓글\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.edited").value(true))
                .andExpect(jsonPath("$.body").value("고친 댓글"));

        advance(16);
        mvc.perform(patch("/api/comments/" + id).session(me).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"또 고침\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EDIT_WINDOW_EXPIRED"));
    }

    @Test
    void 답글은_따로_열리고_고정은_게시물_주인만_3개까지() throws Exception {
        String author = jdbc.queryForObject("""
                select u.username from users u join posts p on p.author_id = u.id
                where not u.is_private and not p.comments_disabled
                group by u.username, u.id order by u.id limit 1""", String.class);
        String code = jdbc.queryForObject("""
                select p.shortcode from posts p join users u on u.id = p.author_id
                where u.username = ? and not p.comments_disabled order by p.id limit 1""", String.class, author);
        jdbc.update("update comments set pinned_at = null where post_id = (select id from posts where shortcode = ?)",
                code);
        String other = jdbc.queryForObject("select username from users where username <> ? and not is_private "
                + "order by id limit 1", String.class, author);
        MockHttpSession guest = loginAs(other);
        int[] ids = new int[4];
        for (int i = 0; i < 4; i++) {
            String res = mvc.perform(post("/api/posts/" + code + "/comments").session(guest).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"댓글 " + i + "\"}"))
                    .andReturn().getResponse().getContentAsString();
            ids[i] = JsonPath.read(res, "$.id");
        }
        mvc.perform(post("/api/posts/" + code + "/comments").session(guest).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"답글이에요\",\"parentId\":" + ids[0] + "}")).andExpect(status().isCreated());
        mvc.perform(get("/api/comments/" + ids[0] + "/replies").session(guest))
                .andExpect(jsonPath("$[0].body").value("답글이에요"));

        mvc.perform(put("/api/comments/" + ids[0] + "/pin").session(guest).with(csrf()))
                .andExpect(status().isForbidden());
        MockHttpSession owner = loginAs(author);
        for (int i = 0; i < 3; i++) {
            mvc.perform(put("/api/comments/" + ids[i] + "/pin").session(owner).with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.pinned").value(true));
        }
        mvc.perform(put("/api/comments/" + ids[3] + "/pin").session(owner).with(csrf()))
                .andExpect(jsonPath("$.code").value("TOO_MANY_PINNED"));
        mvc.perform(get("/api/posts/" + code + "/comments").session(owner))
                .andExpect(jsonPath("$.items[0].id").value(ids[0]))
                .andExpect(jsonPath("$.items[0].replyCount").value(1));

        mvc.perform(put("/api/comments/" + ids[3] + "/like").session(owner).with(csrf()))
                .andExpect(jsonPath("$.likeCount").value(1));
    }

    // ------------------------------------------------------------ 저장·리포스트

    @Test
    void 저장하고_컬렉션에_넣고_저장을_취소하면_컬렉션에서도_빠진다() throws Exception {
        String[] pair = strangers();
        String code = publicPostOf(pair[0]);
        MockHttpSession me = loginAs(pair[0]);
        String res = mvc.perform(post("/api/me/collections").session(me).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"가고 싶은 곳\",\"shortcode\":\"" + code + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.count").value(1))
                .andReturn().getResponse().getContentAsString();
        int cid = JsonPath.read(res, "$.id");
        mvc.perform(get("/api/posts/" + code).session(me)).andExpect(jsonPath("$.savedByMe").value(true));

        mvc.perform(delete("/api/posts/" + code + "/save").session(me).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/me/collections/" + cid).session(me)).andExpect(jsonPath("$.count").value(0));

        // 남의 컬렉션은 볼 수 없다
        mvc.perform(get("/api/me/collections/" + cid).session(loginAs(pair[1]))).andExpect(status().isNotFound());
    }

    @Test
    void 리포스트는_팔로워_피드에_리포스트한_사람과_함께_보인다() throws Exception {
        // 팔로워가 있는 공개 계정 R, R의 팔로워 F, F가 팔로우하지 않는 공개 계정의 게시물
        Map<String, Object> row = jdbc.queryForMap("""
                select r.username as r, f.username as f, p.shortcode as code
                from follows fr
                join users r on r.id = fr.following_id
                join users f on f.id = fr.follower_id
                join posts p on p.author_id <> r.id and p.author_id <> f.id and p.deleted_at is null
                join users a on a.id = p.author_id and not a.is_private
                where fr.status = 'ACCEPTED' and not exists (
                  select 1 from follows x where x.follower_id = f.id and x.following_id = a.id)
                order by p.created_at desc limit 1
                """);
        String code = (String) row.get("code");
        // 다른 사람의 리포스트가 이미 있을 수 있으니 이 게시물의 리포스트를 비운다
        jdbc.update("delete from reposts where post_id = (select id from posts where shortcode = ?)", code);

        MockHttpSession reposter = loginAs((String) row.get("r"));
        mvc.perform(put("/api/posts/" + code + "/repost").session(reposter).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"이거 꼭 봐\"}"))
                .andExpect(jsonPath("$.reposted").value(true)).andExpect(jsonPath("$.repostCount").value(1));

        MockHttpSession follower = loginAs((String) row.get("f"));
        String feed = mvc.perform(get("/api/feed").session(follower)).andReturn().getResponse().getContentAsString();
        List<String> reposters = JsonPath.read(feed, "$.items[?(@.shortcode == '" + code + "')].repost.by.username");
        assertThat(reposters).containsExactly((String) row.get("r"));

        List<String> own = jdbc.queryForList("""
                select p.shortcode from posts p join users u on u.id = p.author_id where u.username = ? limit 1""",
                String.class, row.get("r"));
        if (!own.isEmpty()) {
            mvc.perform(put("/api/posts/" + own.getFirst() + "/repost").session(reposter).with(csrf()))
                    .andExpect(jsonPath("$.code").value("CANNOT_REPOST_OWN"));
        }
        mvc.perform(get("/api/users/" + row.get("r") + "/reposts").session(follower))
                .andExpect(jsonPath("$.items[0].shortcode").value(code));
    }

    // ------------------------------------------------------------ 검색

    @Test
    void 계정_태그_장소를_검색하고_최근_검색에_남긴다() throws Exception {
        String[] pair = strangers();
        MockHttpSession me = loginAs(pair[0]);
        String prefix = pair[1].substring(0, Math.min(4, pair[1].length()));
        String res = mvc.perform(get("/api/search").param("q", prefix).session(me))
                .andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(res, "$.users[*].user.username");
        assertThat(names).contains(pair[1]);

        String tag = jdbc.queryForObject("select name from hashtags order by post_count desc limit 1", String.class);
        mvc.perform(get("/api/search").param("q", "#" + tag).session(me))
                .andExpect(jsonPath("$.tags[0].name").value(tag));
        mvc.perform(get("/api/tags/" + tag).session(me)).andExpect(jsonPath("$.top").isArray());
        mvc.perform(get("/api/tags/" + tag + "/posts").session(me)).andExpect(jsonPath("$.items").isArray());

        mvc.perform(post("/api/search/recent").session(me).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"USER\",\"term\":\"" + pair[1] + "\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/search/recent").session(me)).andExpect(jsonPath("$[0].user.user.username").value(pair[1]));
        mvc.perform(delete("/api/search/recent").session(me).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/search/recent").session(me)).andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------ DM (데모 시나리오 5)

    @Test
    void 팔로우하지_않는_사람의_DM은_요청함으로_가고_읽음_수정_전송취소가_된다() throws Exception {
        String[] pair = strangers();
        MockHttpSession a = loginAs(pair[0]);
        MockHttpSession b = loginAs(pair[1]);
        String conv = mvc.perform(post("/api/direct/conversations").session(a).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usernames\":[\"" + pair[1] + "\"]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int cid = JsonPath.read(conv, "$.id");
        String msg = mvc.perform(post("/api/direct/conversations/" + cid + "/messages").session(a).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"안녕하세요\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.canEdit").value(true))
                .andReturn().getResponse().getContentAsString();
        int mid = JsonPath.read(msg, "$.id");

        // 같은 사람에게 다시 열면 같은 방
        mvc.perform(post("/api/direct/conversations").session(a).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usernames\":[\"" + pair[1] + "\"]}"))
                .andExpect(jsonPath("$.id").value(cid));

        String inbox = mvc.perform(get("/api/direct/inbox").param("folder", "REQUEST").session(b))
                .andReturn().getResponse().getContentAsString();
        List<Boolean> unread = JsonPath.read(inbox, "$[?(@.id == " + cid + ")].unread");
        assertThat(unread).containsExactly(true);
        mvc.perform(get("/api/direct/unread").session(b)).andExpect(jsonPath("$.requests").value(1));

        mvc.perform(put("/api/direct/conversations/" + cid + "/read").session(b).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"messageId\":" + mid + "}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/direct/conversations/" + cid).session(a))
                .andExpect(jsonPath("$.members[0].lastReadMessageId").value(mid));

        // 답장하면 기본 메시지함으로
        mvc.perform(post("/api/direct/conversations/" + cid + "/messages").session(b).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"반가워요\",\"replyToId\":" + mid + "}"))
                .andExpect(jsonPath("$.replyTo.text").value("안녕하세요"));
        mvc.perform(get("/api/direct/conversations/" + cid).session(b)).andExpect(jsonPath("$.folder").value("PRIMARY"));

        mvc.perform(patch("/api/direct/messages/" + mid).session(a).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"안녕하세요!\"}"))
                .andExpect(jsonPath("$.edited").value(true));
        mvc.perform(put("/api/direct/messages/" + mid + "/reaction").session(b).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"❤️\"}"))
                .andExpect(jsonPath("$.reactions[0].emoji").value("❤️"));

        advance(16);
        mvc.perform(patch("/api/direct/messages/" + mid).session(a).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"늦은 수정\"}"))
                .andExpect(jsonPath("$.code").value("EDIT_WINDOW_EXPIRED"));
        mvc.perform(patch("/api/direct/messages/" + mid).session(b).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"남의 것\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/direct/messages/" + mid).session(a).with(csrf())).andExpect(status().isNoContent());
        String page = mvc.perform(get("/api/direct/conversations/" + cid + "/messages").session(b))
                .andReturn().getResponse().getContentAsString();
        List<Boolean> unsent = JsonPath.read(page, "$.items[?(@.id == " + mid + ")].unsent");
        assertThat(unsent).containsExactly(true);
        assertThat(page).doesNotContain("안녕하세요!");

        // 대화방 참여자가 아니면 볼 수 없다
        String third = jdbc.queryForObject("select username from users where username not in (?, ?) limit 1",
                String.class, pair[0], pair[1]);
        mvc.perform(get("/api/direct/conversations/" + cid + "/messages").session(loginAs(third)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 게시물을_DM으로_공유하면_미리보기가_붙는다() throws Exception {
        String[] pair = strangers();
        String code = publicPostOf(pair[0]);
        MockHttpSession a = loginAs(pair[0]);
        String res = mvc.perform(post("/api/direct/share").session(a).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shortcode\":\"" + code + "\",\"usernames\":[\"" + pair[1] + "\"],\"body\":\"이거 봐\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> conversations = JsonPath.read(res, "$.conversations");
        mvc.perform(get("/api/direct/conversations/" + conversations.getFirst() + "/messages").session(loginAs(pair[1])))
                .andExpect(jsonPath("$.items[0].body").value("이거 봐"))
                .andExpect(jsonPath("$.items[1].kind").value("POST"))
                .andExpect(jsonPath("$.items[1].post.shortcode").value(code));
    }

    // ------------------------------------------------------------ 스토리 (데모 시나리오 6)

    @Test
    void 스토리는_24시간_뒤_트레이에서_사라지고_아카이브에_남는다() throws Exception {
        Map<String, Object> row = jdbc.queryForMap("""
                select a.username as author, f.username as follower from follows x
                join users a on a.id = x.following_id join users f on f.id = x.follower_id
                where x.status = 'ACCEPTED' order by x.id limit 1""");
        MockHttpSession author = loginAs((String) row.get("author"));
        String res = mvc.perform(multipart("/api/stories").file(new MockMultipartFile("file", "s.png", "image/png",
                        png(540, 960))).session(author).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int storyId = JsonPath.read(res, "$.id");

        MockHttpSession follower = loginAs((String) row.get("follower"));
        String tray = mvc.perform(get("/api/stories/tray").session(follower)).andReturn().getResponse()
                .getContentAsString();
        List<String> rings = JsonPath.read(tray, "$[?(@.user.username == '" + row.get("author") + "')].ring");
        assertThat(rings).containsExactly("UNSEEN");

        String mine = mvc.perform(get("/api/stories/users/" + row.get("author")).session(follower))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(mine, "$[*].id");
        for (Integer id : ids) {
            mvc.perform(post("/api/stories/" + id + "/view").session(follower).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        tray = mvc.perform(get("/api/stories/tray").session(follower)).andReturn().getResponse().getContentAsString();
        rings = JsonPath.read(tray, "$[?(@.user.username == '" + row.get("author") + "')].ring");
        assertThat(rings).containsExactly("SEEN");

        mvc.perform(get("/api/stories/" + storyId + "/viewers").session(author))
                .andExpect(jsonPath("$[0].user.username").value(row.get("follower")));
        mvc.perform(post("/api/stories/" + storyId + "/reply").session(follower).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"멋져요\"}"))
                .andExpect(jsonPath("$.kind").value("STORY_REPLY"));

        advance(25 * 60);
        tray = mvc.perform(get("/api/stories/tray").session(follower)).andReturn().getResponse().getContentAsString();
        List<String> authors = JsonPath.read(tray, "$[*].user.username");
        assertThat(authors).doesNotContain((String) row.get("author"));
        mvc.perform(post("/api/stories/" + storyId + "/view").session(follower).with(csrf()))
                .andExpect(status().isNotFound());

        String archive = mvc.perform(get("/api/stories/archive").session(author)).andReturn().getResponse()
                .getContentAsString();
        List<Integer> archived = JsonPath.read(archive, "$.items[*].id");
        assertThat(archived).contains(storyId);
        List<Boolean> expired = JsonPath.read(archive, "$.items[?(@.id == " + storyId + ")].expired");
        assertThat(expired).containsExactly(true);
    }
}
