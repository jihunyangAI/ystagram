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

/** M3: 차단·제한·숨기기·친한 친구·즐겨찾기, 스토리 스티커·하이라이트, 사람 태그·공동 작업, 릴스·탐색 */
@SpringBootTest(properties = {"yangstagram.media.storage-dir=build/test-storage", "yangstagram.seed.size=small"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class M3IntegrationTest {

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
        // 시드가 만든 차단·숨기기·제한이 다른 검증을 흔들지 않도록 비운다
        jdbc.update("delete from blocks");
        jdbc.update("delete from mutes");
        jdbc.update("delete from restricts");
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

    private String body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    /** a가 b를 팔로우(승인)하는 공개 계정 쌍, b는 게시물이 있음 */
    private Map<String, Object> followPair() {
        return jdbc.queryForMap("""
                select f.username as follower, t.username as target from follows x
                join users f on f.id = x.follower_id join users t on t.id = x.following_id
                where x.status = 'ACCEPTED' and not f.is_private and not t.is_private and t.posts_count > 0
                order by x.id limit 1""");
    }

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.CYAN);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static MockMultipartFile json(String name, String value) throws Exception {
        return new MockMultipartFile(name, "", MediaType.APPLICATION_JSON_VALUE, value.getBytes("UTF-8"));
    }

    // ------------------------------------------------------------ 시드

    @Test
    void 시드에_친한_친구_하이라이트_사람_태그_스티커가_만들어진다() {
        assertThat(jdbc.queryForObject("select count(*) from close_friends", Integer.class)).isGreaterThan(20);
        assertThat(jdbc.queryForObject("select count(*) from highlights", Integer.class)).isGreaterThan(3);
        assertThat(jdbc.queryForObject("select count(*) from post_tags", Integer.class)).isGreaterThan(10);
        assertThat(jdbc.queryForObject("select count(*) from stories where stickers is not null", Integer.class))
                .isGreaterThan(10);
        assertThat(jdbc.queryForObject("select count(*) from stories where audience = 'CLOSE_FRIENDS'", Integer.class))
                .isGreaterThan(0);
        // 친한 친구 공개 스토리는 친한 친구만 봤다
        assertThat(jdbc.queryForObject("""
                select count(*) from story_views v join stories s on s.id = v.story_id
                where s.audience = 'CLOSE_FRIENDS' and not exists (
                  select 1 from close_friends c where c.user_id = s.author_id and c.friend_id = v.viewer_id)
                """, Integer.class)).isZero();
    }

    // ------------------------------------------------------------ 차단

    @Test
    void 차단하면_팔로우가_풀리고_서로의_프로필_게시물_DM이_막힌다() throws Exception {
        Map<String, Object> pair = followPair();
        String a = (String) pair.get("target"), b = (String) pair.get("follower");
        String code = jdbc.queryForObject("""
                select p.shortcode from posts p join users u on u.id = p.author_id where u.username = ? limit 1""",
                String.class, a);
        MockHttpSession sa = loginAs(a);
        mvc.perform(put("/api/users/" + b + "/block").session(sa).with(csrf()))
                .andExpect(jsonPath("$.blocked").value(true));
        assertThat(jdbc.queryForObject("""
                select count(*) from follows f join users x on x.id = f.follower_id join users y on y.id = f.following_id
                where (x.username = ? and y.username = ?) or (x.username = ? and y.username = ?)""",
                Integer.class, a, b, b, a)).isZero();

        MockHttpSession sb = loginAs(b);
        mvc.perform(get("/api/users/" + a).session(sb)).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + code).session(sb)).andExpect(status().isNotFound());
        mvc.perform(put("/api/users/" + a + "/follow").session(sb).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/direct/conversations").session(sb).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernames\":[\"" + a + "\"]}")).andExpect(status().isForbidden());
        String search = body(mvc.perform(get("/api/search").param("q", a).session(sb)));
        List<String> names = JsonPath.read(search, "$.users[*].user.username");
        assertThat(names).doesNotContain(a);

        // 차단한 쪽은 프로필이 보이되 내용은 잠김
        mvc.perform(get("/api/users/" + b).session(sa)).andExpect(jsonPath("$.canViewContent").value(false))
                .andExpect(jsonPath("$.flags.blocked").value(true));
        mvc.perform(get("/api/me/relations/blocked").session(sa)).andExpect(jsonPath("$[0].user.username").value(b));

        mvc.perform(delete("/api/users/" + b + "/block").session(sa).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + code).session(sb)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ 제한

    @Test
    void 제한한_사람의_댓글은_본인과_게시물_주인에게만_보이고_승인하면_공개된다() throws Exception {
        Map<String, Object> pair = followPair();
        String owner = (String) pair.get("target"), restricted = (String) pair.get("follower");
        String code = jdbc.queryForObject("""
                select p.shortcode from posts p join users u on u.id = p.author_id
                where u.username = ? and not p.comments_disabled limit 1""", String.class, owner);
        String third = jdbc.queryForObject("""
                select username from users where username not in (?, ?) and not is_private order by id limit 1""",
                String.class, owner, restricted);
        MockHttpSession so = loginAs(owner);
        mvc.perform(put("/api/users/" + restricted + "/restrict").session(so).with(csrf()))
                .andExpect(jsonPath("$.restricted").value(true));
        jdbc.update("delete from notifications");

        MockHttpSession sr = loginAs(restricted);
        String res = body(mvc.perform(post("/api/posts/" + code + "/comments").session(sr).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"제한된 댓글\"}")));
        int id = JsonPath.read(res, "$.id");
        assertThat(jdbc.queryForObject("select count(*) from notifications", Integer.class)).isZero();

        assertThat(body(mvc.perform(get("/api/posts/" + code + "/comments").session(sr)))).contains("제한된 댓글");
        assertThat(body(mvc.perform(get("/api/posts/" + code + "/comments").session(loginAs(third)))))
                .doesNotContain("제한된 댓글");
        String ownerView = body(mvc.perform(get("/api/posts/" + code + "/comments").session(so)));
        List<Boolean> flags = JsonPath.read(ownerView, "$.items[?(@.id == " + id + ")].restricted");
        assertThat(flags).containsExactly(true);

        mvc.perform(put("/api/comments/" + id + "/approve").session(so).with(csrf()))
                .andExpect(jsonPath("$.restricted").value(false));
        assertThat(body(mvc.perform(get("/api/posts/" + code + "/comments").session(loginAs(third)))))
                .contains("제한된 댓글");
    }

    // ------------------------------------------------------------ 숨기기·즐겨찾기

    @Test
    void 게시물을_숨기면_피드에서_빠지고_즐겨찾기_피드에는_즐겨찾기한_사람만_나온다() throws Exception {
        Map<String, Object> pair = followPair();
        String me = (String) pair.get("follower"), target = (String) pair.get("target");
        MockHttpSession s = loginAs(me);
        jdbc.update("update follows set is_favorite = false");
        mvc.perform(put("/api/users/" + target + "/favorite").session(s).with(csrf()))
                .andExpect(jsonPath("$.favorite").value(true));
        String fav = body(mvc.perform(get("/api/feed").param("mode", "favorites").session(s)));
        List<String> authors = JsonPath.read(fav, "$.items[*].author.username");
        assertThat(authors).isNotEmpty().contains(target);
        // 즐겨찾기한 사람의 글 또는 그 사람이 공동 작업자로 참여한 글만
        List<Map<String, Object>> items = JsonPath.read(fav, "$.items[*]");
        for (Map<String, Object> item : items) {
            String author = (String) ((Map<?, ?>) item.get("author")).get("username");
            List<String> collabs = ((List<?>) item.get("collaborators")).stream()
                    .map(c -> (String) ((Map<?, ?>) c).get("username")).toList();
            assertThat(author.equals(target) || collabs.contains(target)).as(author).isTrue();
        }

        mvc.perform(put("/api/users/" + target + "/mute").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"posts\":true,\"stories\":true}")).andExpect(jsonPath("$.mutedPosts").value(true));
        String feed = body(mvc.perform(get("/api/feed").session(s)));
        List<String> feedAuthors = JsonPath.read(feed, "$.items[*].author.username");
        assertThat(feedAuthors).doesNotContain(target);
        String tray = body(mvc.perform(get("/api/stories/tray").session(s)));
        List<String> trayUsers = JsonPath.read(tray, "$[*].user.username");
        assertThat(trayUsers).doesNotContain(target);
    }

    // ------------------------------------------------------------ 스토리: 친한 친구·스티커·하이라이트

    @Test
    void 친한_친구_공개_스토리는_친한_친구만_보고_스티커_응답이_집계된다() throws Exception {
        // 팔로워가 둘 이상인 공개 계정: 한 명은 친한 친구, 한 명은 아님
        Map<String, Object> row = jdbc.queryForMap("""
                select t.username as author, min(f.username) as friend, max(f.username) as other
                from follows x join users t on t.id = x.following_id join users f on f.id = x.follower_id
                where x.status = 'ACCEPTED' and not t.is_private
                group by t.username having count(*) >= 2 order by t.username limit 1""");
        String author = (String) row.get("author"), friend = (String) row.get("friend"), other = (String) row.get("other");
        jdbc.update("delete from close_friends where user_id = (select id from users where username = ?)", author);
        MockHttpSession sa = loginAs(author);
        mvc.perform(put("/api/users/" + friend + "/close-friend").session(sa).with(csrf()))
                .andExpect(jsonPath("$.closeFriend").value(true));

        String data = "{\"audience\":\"CLOSE_FRIENDS\",\"stickers\":[{\"type\":\"POLL\",\"x\":0.5,\"y\":0.6,"
                + "\"question\":\"커피 vs 차\",\"options\":[\"커피\",\"차\"]}]}";
        String res = body(mvc.perform(multipart("/api/stories")
                .file(new MockMultipartFile("file", "s.png", "image/png", png(540, 960)))
                .file(json("data", data)).session(sa).with(csrf())).andExpect(status().isCreated()));
        int storyId = JsonPath.read(res, "$.id");
        String sticker = JsonPath.read(res, "$.stickers[0].id");

        mvc.perform(get("/api/stories/users/" + author).session(loginAs(other)))
                .andExpect(jsonPath("$[?(@.id == " + storyId + ")]").isEmpty());
        MockHttpSession sf = loginAs(friend);
        String tray = body(mvc.perform(get("/api/stories/tray").session(sf)));
        List<String> rings = JsonPath.read(tray, "$[?(@.user.username == '" + author + "')].ring");
        assertThat(rings).containsAnyOf("CLOSE_FRIENDS", "SEEN");

        // 응답 전에는 결과 숫자가 안 보이고, 응답하면 보인다. 설문은 한 번만
        String before = body(mvc.perform(get("/api/stories/users/" + author).session(sf)));
        List<Object> hidden = JsonPath.read(before, "$[?(@.id == " + storyId + ")].results[0].counts");
        assertThat(hidden).containsOnlyNulls();
        mvc.perform(post("/api/stories/" + storyId + "/stickers/" + sticker).session(sf).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"1\"}"))
                .andExpect(jsonPath("$.results[0].counts[1]").value(1)).andExpect(jsonPath("$.results[0].myValue").value("1"));
        mvc.perform(post("/api/stories/" + storyId + "/stickers/" + sticker).session(sf).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"0\"}"))
                .andExpect(jsonPath("$.results[0].myValue").value("1"));

        // 잘못된 스티커는 거부
        mvc.perform(multipart("/api/stories").file(new MockMultipartFile("file", "s.png", "image/png", png(540, 960)))
                        .file(json("data", "{\"stickers\":[{\"type\":\"POLL\",\"options\":[\"하나뿐\"]}]}"))
                        .session(sa).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_STICKER"));
    }

    @Test
    void 하이라이트는_24시간이_지나도_프로필에서_볼_수_있다() throws Exception {
        Map<String, Object> pair = followPair();
        String author = (String) pair.get("target"), viewer = (String) pair.get("follower");
        MockHttpSession sa = loginAs(author);
        String res = body(mvc.perform(multipart("/api/stories")
                .file(new MockMultipartFile("file", "s.png", "image/png", png(540, 960)))
                .session(sa).with(csrf())));
        int storyId = JsonPath.read(res, "$.id");
        String hl = body(mvc.perform(post("/api/stories/highlights").session(sa).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"여행\",\"storyIds\":[" + storyId + "]}"))
                .andExpect(status().isCreated()));
        int hid = JsonPath.read(hl, "$.id");

        clock.setOffset(Duration.ofHours(25));
        MockHttpSession sv = loginAs(viewer);
        String list = body(mvc.perform(get("/api/stories/of/" + author + "/highlights").session(sv)));
        List<String> titles = JsonPath.read(list, "$[*].title");
        assertThat(titles).contains("여행");
        mvc.perform(get("/api/stories/highlights/" + hid).session(sv))
                .andExpect(jsonPath("$[0].id").value(storyId)).andExpect(jsonPath("$[0].expired").value(true));
    }

    // ------------------------------------------------------------ 사람 태그·공동 작업

    @Test
    void 사람을_태그하면_태그됨_탭에_보이고_공동_작업을_수락하면_양쪽_프로필에_보인다() throws Exception {
        Map<String, Object> pair = followPair();
        String author = (String) pair.get("follower"), friend = (String) pair.get("target");
        String data = "{\"caption\":\"같이 찍음\",\"tags\":[{\"username\":\"" + friend + "\",\"x\":0.4,\"y\":0.5}],"
                + "\"collaborators\":[\"" + friend + "\"],\"filter\":\"clarendon\"}";
        MockHttpSession sa = loginAs(author);
        String res = body(mvc.perform(multipart("/api/posts")
                .file(new MockMultipartFile("files", "p.png", "image/png", png(400, 500)))
                .file(json("data", data)).session(sa).with(csrf())).andExpect(status().isCreated()));
        String code = JsonPath.read(res, "$.shortcode");

        MockHttpSession sf = loginAs(friend);
        mvc.perform(get("/api/users/" + friend + "/tagged").session(sf)).andExpect(jsonPath("$.items[0].shortcode").value(code));
        mvc.perform(get("/api/posts/" + code).session(sf)).andExpect(jsonPath("$.collabInvite").value(true))
                .andExpect(jsonPath("$.tags[0].user.username").value(friend));
        String notes = body(mvc.perform(get("/api/notifications").session(sf)));
        List<String> types = JsonPath.read(notes, "$.items[*].type");
        assertThat(types).contains("TAG", "COLLAB_INVITE");

        mvc.perform(put("/api/posts/" + code + "/collab").session(sf).with(csrf()))
                .andExpect(jsonPath("$.collaborators[0].username").value(friend));
        mvc.perform(get("/api/users/" + friend + "/posts").session(sf)).andExpect(jsonPath("$.items[0].shortcode").value(code));

        mvc.perform(delete("/api/posts/" + code + "/tags/me").session(sf).with(csrf())).andExpect(jsonPath("$.tags").isEmpty());
    }

    // ------------------------------------------------------------ 릴스·탐색

    @Test
    void 릴스는_3분이_넘으면_추천에서_빠지고_20분이_넘으면_올릴_수_없다() throws Exception {
        String me = jdbc.queryForObject("select username from users where not is_private order by id limit 1", String.class);
        MockHttpSession s = loginAs(me);
        MockMultipartFile video = new MockMultipartFile("video", "v.mp4", "video/mp4", new byte[] {0, 0, 0, 24, 102, 116});
        MockMultipartFile poster = new MockMultipartFile("poster", "p.png", "image/png", png(360, 640));

        String res = body(mvc.perform(multipart("/api/reels").file(video).file(poster)
                .file(json("data", "{\"caption\":\"긴 영상\",\"durationMs\":200000}")).session(s).with(csrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("REEL"))
                .andExpect(jsonPath("$.media[0].type").value("VIDEO")));
        String code = JsonPath.read(res, "$.shortcode");
        assertThat(jdbc.queryForObject("select recommendable from posts where shortcode = ?", Boolean.class, code)).isFalse();

        mvc.perform(multipart("/api/reels").file(video).file(poster)
                        .file(json("data", "{\"durationMs\":1300000}")).session(s).with(csrf()))
                .andExpect(jsonPath("$.code").value("REEL_TOO_LONG"));
        mvc.perform(multipart("/api/reels").file(new MockMultipartFile("video", "v.gif", "image/gif", new byte[] {1}))
                        .file(poster).file(json("data", "{\"durationMs\":1000}")).session(s).with(csrf()))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_VIDEO"));

        // 다른 사람(팔로우 안 함)의 추천 릴스에는 안 나온다
        String stranger = jdbc.queryForObject("""
                select u.username from users u where u.username <> ? and not exists (
                  select 1 from follows f join users t on t.id = f.following_id where f.follower_id = u.id and t.username = ?)
                order by u.id limit 1""", String.class, me, me);
        MockHttpSession ss = loginAs(stranger);
        List<String> seen = new java.util.ArrayList<>();
        for (int page = 0; page < 40; page++) {
            String r = body(mvc.perform(get("/api/reels").param("page", String.valueOf(page)).session(ss)));
            seen.addAll(JsonPath.read(r, "$.items[*].shortcode"));
            if (JsonPath.read(r, "$.nextPage") == null) break;
        }
        assertThat(seen).doesNotContain(code);

        // 조회는 한 사람당 한 번
        mvc.perform(post("/api/posts/" + code + "/view").session(ss).with(csrf())).andExpect(jsonPath("$.viewCount").value(1));
        mvc.perform(post("/api/posts/" + code + "/view").session(ss).with(csrf())).andExpect(jsonPath("$.viewCount").value(1));
    }

    @Test
    void 탐색에는_팔로우하는_사람과_내_게시물이_나오지_않는다() throws Exception {
        String me = jdbc.queryForObject("""
                select username from users where following_count > 5 order by id limit 1""", String.class);
        MockHttpSession s = loginAs(me);
        String res = body(mvc.perform(get("/api/explore").session(s)).andExpect(status().isOk()));
        List<String> codes = JsonPath.read(res, "$.items[*].shortcode");
        assertThat(codes).isNotEmpty();
        for (String code : codes) {
            Integer bad = jdbc.queryForObject("""
                    select count(*) from posts p join users u on u.id = p.author_id
                    where p.shortcode = ? and (u.username = ? or u.is_private or exists (
                      select 1 from follows f join users me on me.id = f.follower_id
                      where me.username = ? and f.following_id = u.id and f.status = 'ACCEPTED'))
                    """, Integer.class, code, me, me);
            assertThat(bad).as(code).isZero();
        }
    }
}
