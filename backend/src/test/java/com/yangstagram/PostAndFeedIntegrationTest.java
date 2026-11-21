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
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** M1: 피드·게시물 작성·좋아요·댓글·팔로우·프로필 편집 */
@SpringBootTest(properties = {"yangstagram.media.storage-dir=build/test-storage", "yangstagram.seed.size=small"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostAndFeedIntegrationTest {

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

    /** 팔로우를 꽤 한 일반 사용자 (피드가 충분히 차 있는 사람) */
    private String activeUser() {
        return jdbc.queryForObject("""
                select u.username from users u where u.account_type = 'PERSONAL'
                order by u.following_count desc, u.id limit 1
                """, String.class);
    }

    private static byte[] png(int w, int h, Color color) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private MockMultipartHttpServletRequestBuilder createPost(String dataJson, int photos) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/posts");
        for (int i = 0; i < photos; i++) {
            req.file(new MockMultipartFile("files", "p" + i + ".png", "image/png", png(400, 500, Color.ORANGE)));
        }
        req.file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, dataJson.getBytes("UTF-8")));
        return req;
    }

    // ------------------------------------------------------------ 시드

    @Test
    void 가짜_게시물_좋아요_댓글이_만들어지고_카운터가_맞는다() {
        Integer posts = jdbc.queryForObject("select count(*) from posts", Integer.class);
        Integer likes = jdbc.queryForObject("select count(*) from likes", Integer.class);
        Integer comments = jdbc.queryForObject("select count(*) from comments", Integer.class);
        assertThat(posts).isBetween(400, 900);
        assertThat(likes).isGreaterThan(posts * 5);
        assertThat(comments).isGreaterThan(posts / 2);

        assertThat(jdbc.queryForObject("""
                select count(*) from posts p where p.like_count <> (select count(*) from likes l where l.post_id = p.id)
                   or p.comment_count <> (select count(*) from comments c where c.post_id = p.id)
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from users u where u.posts_count <> (select count(*) from posts p where p.author_id = u.id)
                """, Integer.class)).isZero();
        // 해시태그는 게시물당 5개 이하, 캐러셀이 섞여 있다
        assertThat(jdbc.queryForObject(
                "select coalesce(max(c), 0) from (select count(*) c from post_hashtags group by post_id) t",
                Integer.class)).isLessThanOrEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from posts where type = 'CAROUSEL'", Integer.class)).isPositive();
        // 비공개 계정 글에는 승인된 팔로워(또는 본인)만 좋아요를 누른다
        assertThat(jdbc.queryForObject("""
                select count(*) from likes l join posts p on p.id = l.post_id join users a on a.id = p.author_id
                where a.is_private and l.user_id <> a.id and not exists (
                  select 1 from follows f where f.follower_id = l.user_id and f.following_id = a.id and f.status = 'ACCEPTED')
                """, Integer.class)).isZero();
        // 미래 시각이 없다
        assertThat(jdbc.queryForObject("select count(*) from posts where created_at > now() + interval '1 minute'",
                Integer.class)).isZero();
    }

    @Test
    void 같은_시드값이면_같은_게시물이_나온다() {
        List<String> first = jdbc.queryForList("select shortcode from posts order by id", String.class);
        Integer firstLikes = jdbc.queryForObject("select count(*) from likes", Integer.class);
        seedService.generate(SeedSize.SMALL, 42);
        assertThat(jdbc.queryForList("select shortcode from posts order by id", String.class)).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from likes", Integer.class)).isEqualTo(firstLikes);
    }

    // ------------------------------------------------------------ 피드

    @Test
    void 피드는_팔로우한_사람과_내_글만_최신순으로_이어서_보여준다() throws Exception {
        String username = activeUser();
        Long me = jdbc.queryForObject("select id from users where username = ?", Long.class, username);
        Set<Long> allowed = new HashSet<>(jdbc.queryForList(
                "select following_id from follows where follower_id = ? and status = 'ACCEPTED'", Long.class, me));
        allowed.add(me);
        MockHttpSession session = loginAs(username);

        List<String> seen = new ArrayList<>();
        Instant previous = Instant.MAX;
        String cursor = null;
        for (int page = 0; page < 4; page++) {
            String body = mvc.perform(get("/api/feed").param("mode", "following")
                            .param("cursor", cursor == null ? "" : cursor).session(session))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Number> authors = JsonPath.read(body, "$.items[*].author.id");
            List<String> codes = JsonPath.read(body, "$.items[*].shortcode");
            List<String> times = JsonPath.read(body, "$.items[*].createdAt");
            assertThat(authors).allMatch(a -> allowed.contains(a.longValue()));
            for (String t : times) {
                Instant at = Instant.parse(t);
                assertThat(at).isBeforeOrEqualTo(previous);
                previous = at;
            }
            seen.addAll(codes);
            cursor = JsonPath.read(body, "$.nextCursor");
            if (cursor == null) break;
        }
        assertThat(seen).hasSizeGreaterThan(10).doesNotHaveDuplicates();
    }

    // ------------------------------------------------------------ 게시물 작성

    @Test
    void 사진_여러_장으로_게시물을_올리면_내_피드_맨_위에_보인다() throws Exception {
        String username = activeUser();
        MockHttpSession session = loginAs(username);
        Integer before = jdbc.queryForObject("select posts_count from users where username = ?", Integer.class, username);

        String body = mvc.perform(createPost("""
                        {"caption":"테스트 게시물 #Cafe #cafe #여행 #test","location":"새로운 장소","altTexts":["주황색 사진"],
                         "hideLikeCount":false,"commentsDisabled":false}
                        """, 3).session(session).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.media.length()").value(3))
                .andExpect(jsonPath("$.media[0].altText").value("주황색 사진"))
                .andExpect(jsonPath("$.media[0].width").value(400))
                .andExpect(jsonPath("$.location.name").value("새로운 장소"))
                .andExpect(jsonPath("$.mine").value(true))
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.shortcode");

        mvc.perform(get("/api/feed").param("mode", "following").session(session))
                .andExpect(jsonPath("$.items[0].shortcode").value(code));
        assertThat(jdbc.queryForObject("select posts_count from users where username = ?", Integer.class, username))
                .isEqualTo(before + 1);
        // #Cafe #cafe 는 대소문자만 다른 같은 태그 → 3개
        assertThat(jdbc.queryForObject("""
                select count(*) from post_hashtags ph join posts p on p.id = ph.post_id where p.shortcode = ?
                """, Integer.class, code)).isEqualTo(3);
    }

    @Test
    void 해시태그_6개_사진_21장_빈_사진은_거부된다() throws Exception {
        MockHttpSession session = loginAs(activeUser());
        mvc.perform(createPost("{\"caption\":\"#a #b #c #d #e #f\"}", 1).session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_HASHTAGS"));
        mvc.perform(createPost("{\"caption\":\"많다\"}", 21).session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_MEDIA"));
        mvc.perform(createPost("{\"caption\":\"없다\"}", 0).session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_REQUIRED"));
        mvc.perform(createPost("{\"caption\":\"" + "가".repeat(2201) + "\"}", 1).session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTION_TOO_LONG"));
    }

    @Test
    void 본인_게시물만_수정_삭제할_수_있다() throws Exception {
        String code = jdbc.queryForObject("""
                select p.shortcode from posts p join users u on u.id = p.author_id where not u.is_private
                order by p.id limit 1""", String.class);
        String owner = jdbc.queryForObject(
                "select u.username from posts p join users u on u.id = p.author_id where p.shortcode = ?", String.class, code);
        String other = jdbc.queryForObject("select username from users where username <> ? order by id limit 1",
                String.class, owner);

        mvc.perform(delete("/api/posts/" + code).session(loginAs(other)).with(csrf()))
                .andExpect(status().isForbidden());

        MockHttpSession session = loginAs(owner);
        mvc.perform(patch("/api/posts/" + code).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caption\":\"고친 캡션 #수정\",\"hideLikeCount\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption").value("고친 캡션 #수정"))
                .andExpect(jsonPath("$.hideLikeCount").value(true));
        mvc.perform(delete("/api/posts/" + code).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + code).session(session)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ 좋아요·댓글

    @Test
    void 좋아요는_한_번만_세고_취소할_수_있다() throws Exception {
        String username = activeUser();
        Long me = jdbc.queryForObject("select id from users where username = ?", Long.class, username);
        String code = jdbc.queryForObject("""
                select p.shortcode from posts p join users u on u.id = p.author_id
                where not u.is_private and p.comments_disabled = false and p.hide_like_count = false
                  and not exists (select 1 from likes l where l.post_id = p.id and l.user_id = ?)
                order by p.id limit 1""", String.class, me);
        int before = jdbc.queryForObject("select like_count from posts where shortcode = ?", Integer.class, code);
        MockHttpSession session = loginAs(username);

        mvc.perform(put("/api/posts/" + code + "/like").session(session).with(csrf()))
                .andExpect(jsonPath("$.likeCount").value(before + 1));
        mvc.perform(put("/api/posts/" + code + "/like").session(session).with(csrf()))
                .andExpect(jsonPath("$.likeCount").value(before + 1));
        mvc.perform(get("/api/posts/" + code).session(session))
                .andExpect(jsonPath("$.likedByMe").value(true));
        mvc.perform(get("/api/posts/" + code + "/likes").session(session))
                .andExpect(jsonPath("$[0].user.username").value(username))
                .andExpect(jsonPath("$[0].relationship").value("SELF"));
        mvc.perform(delete("/api/posts/" + code + "/like").session(session).with(csrf()))
                .andExpect(jsonPath("$.likeCount").value(before))
                .andExpect(jsonPath("$.liked").value(false));
    }

    @Test
    void 댓글은_쓴_사람과_게시물_주인만_지울_수_있다() throws Exception {
        String code = jdbc.queryForObject("""
                select p.shortcode from posts p join users u on u.id = p.author_id
                where not u.is_private and not p.comments_disabled order by p.id limit 1""", String.class);
        String owner = jdbc.queryForObject(
                "select u.username from posts p join users u on u.id = p.author_id where p.shortcode = ?", String.class, code);
        List<String> others = jdbc.queryForList("select username from users where username <> ? order by id limit 2",
                String.class, owner);
        int before = jdbc.queryForObject("select comment_count from posts where shortcode = ?", Integer.class, code);

        MockHttpSession writer = loginAs(others.get(0));
        String body = mvc.perform(post("/api/posts/" + code + "/comments").session(writer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"  좋은 사진이네요  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("좋은 사진이네요"))
                .andExpect(jsonPath("$.canDelete").value(true))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        assertThat(jdbc.queryForObject("select comment_count from posts where shortcode = ?", Integer.class, code))
                .isEqualTo(before + 1);

        mvc.perform(post("/api/posts/" + code + "/comments").session(writer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"   \"}"))
                .andExpect(jsonPath("$.code").value("EMPTY_COMMENT"));
        mvc.perform(delete("/api/comments/" + id).session(loginAs(others.get(1))).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/comments/" + id).session(loginAs(owner)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select comment_count from posts where shortcode = ?", Integer.class, code))
                .isEqualTo(before);
    }

    @Test
    void 댓글이_꺼진_게시물에는_댓글을_달_수_없다() throws Exception {
        MockHttpSession session = loginAs(activeUser());
        String body = mvc.perform(createPost("{\"caption\":\"댓글 끔\",\"commentsDisabled\":true}", 1)
                        .session(session).with(csrf()))
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.shortcode");
        mvc.perform(post("/api/posts/" + code + "/comments").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"안녕\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMENTS_DISABLED"));
    }

    // ------------------------------------------------------------ 팔로우·비공개

    @Test
    void 비공개_계정은_팔로우_요청만_되고_승인_전에는_게시물이_안_보인다() throws Exception {
        String target = jdbc.queryForObject("""
                select u.username from users u where u.is_private and u.posts_count > 0 order by u.id limit 1""",
                String.class);
        Long targetId = jdbc.queryForObject("select id from users where username = ?", Long.class, target);
        String viewer = jdbc.queryForObject("""
                select u.username from users u where u.id <> ? and not exists
                  (select 1 from follows f where f.follower_id = u.id and f.following_id = ?)
                order by u.id limit 1""", String.class, targetId, targetId);
        String code = jdbc.queryForObject("select shortcode from posts where author_id = ? limit 1", String.class, targetId);
        MockHttpSession session = loginAs(viewer);

        mvc.perform(get("/api/users/" + target).session(session))
                .andExpect(jsonPath("$.canViewContent").value(false))
                .andExpect(jsonPath("$.relationship").value("NONE"));
        mvc.perform(get("/api/users/" + target + "/posts").session(session))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PRIVATE_ACCOUNT"));
        mvc.perform(get("/api/posts/" + code).session(session)).andExpect(status().isForbidden());
        mvc.perform(put("/api/posts/" + code + "/like").session(session).with(csrf())).andExpect(status().isForbidden());

        int followers = jdbc.queryForObject("select followers_count from users where id = ?", Integer.class, targetId);
        mvc.perform(put("/api/users/" + target + "/follow").session(session).with(csrf()))
                .andExpect(jsonPath("$.relationship").value("REQUESTED"));
        mvc.perform(get("/api/users/" + target + "/posts").session(session)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select followers_count from users where id = ?", Integer.class, targetId))
                .isEqualTo(followers); // 요청은 팔로워 수에 안 들어간다
        mvc.perform(delete("/api/users/" + target + "/follow").session(session).with(csrf()))
                .andExpect(jsonPath("$.relationship").value("NONE"));
    }

    @Test
    void 공개_계정은_바로_팔로우되고_피드와_카운터에_반영된다() throws Exception {
        String target = jdbc.queryForObject("""
                select u.username from users u where not u.is_private and u.posts_count > 0
                order by u.followers_count, u.id limit 1""", String.class);
        Long targetId = jdbc.queryForObject("select id from users where username = ?", Long.class, target);
        String viewer = jdbc.queryForObject("""
                select u.username from users u where u.id <> ? and not exists
                  (select 1 from follows f where f.follower_id = u.id and f.following_id = ?)
                order by u.id limit 1""", String.class, targetId, targetId);
        Long viewerId = jdbc.queryForObject("select id from users where username = ?", Long.class, viewer);
        int followers = jdbc.queryForObject("select followers_count from users where id = ?", Integer.class, targetId);
        int following = jdbc.queryForObject("select following_count from users where id = ?", Integer.class, viewerId);
        MockHttpSession session = loginAs(viewer);

        mvc.perform(put("/api/users/" + target + "/follow").session(session).with(csrf()))
                .andExpect(jsonPath("$.relationship").value("FOLLOWING"));
        mvc.perform(put("/api/users/" + target + "/follow").session(session).with(csrf()))
                .andExpect(jsonPath("$.relationship").value("FOLLOWING"));
        assertThat(jdbc.queryForObject("select followers_count from users where id = ?", Integer.class, targetId))
                .isEqualTo(followers + 1);
        assertThat(jdbc.queryForObject("select following_count from users where id = ?", Integer.class, viewerId))
                .isEqualTo(following + 1);
        mvc.perform(get("/api/users/" + target + "/followers").session(session))
                .andExpect(jsonPath("$[0].user.username").value(viewer));

        mvc.perform(delete("/api/users/" + target + "/follow").session(session).with(csrf()));
        assertThat(jdbc.queryForObject("select followers_count from users where id = ?", Integer.class, targetId))
                .isEqualTo(followers);
        mvc.perform(put("/api/users/" + viewer + "/follow").session(session).with(csrf()))
                .andExpect(jsonPath("$.code").value("CANNOT_FOLLOW_SELF"));
    }

    @Test
    void 추천_계정에는_이미_팔로우한_사람과_나는_없다() throws Exception {
        String username = activeUser();
        Long me = jdbc.queryForObject("select id from users where username = ?", Long.class, username);
        Set<Long> following = new HashSet<>(jdbc.queryForList(
                "select following_id from follows where follower_id = ?", Long.class, me));
        String body = mvc.perform(get("/api/suggestions/users").param("limit", "10").session(loginAs(username)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[*].user.id");
        assertThat(ids).isNotEmpty().noneMatch(id -> following.contains(id.longValue()) || id.longValue() == me);
    }

    // ------------------------------------------------------------ 프로필 편집

    @Test
    void 프로필을_편집하면_규칙을_지키고_링크를_다듬는다() throws Exception {
        String username = activeUser();
        String taken = jdbc.queryForObject("select username from users where username <> ? limit 1", String.class, username);
        MockHttpSession session = loginAs(username);

        mvc.perform(patch("/api/me/profile").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + taken + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        mvc.perform(patch("/api/me/profile").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"" + "a".repeat(151) + "\"}"))
                .andExpect(jsonPath("$.code").value("BIO_TOO_LONG"));
        mvc.perform(patch("/api/me/profile").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Renamed.User\",\"fullName\":\"새 이름\",\"bio\":\"안녕하세요\","
                                + "\"website\":\"example.com/me\",\"pronouns\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.user.username").value("renamed.user"))
                .andExpect(jsonPath("$.website").value("https://example.com/me"))
                .andExpect(jsonPath("$.pronouns").doesNotExist());
        mvc.perform(get("/api/users/renamed.user").session(session))
                .andExpect(jsonPath("$.me").value(true))
                .andExpect(jsonPath("$.user.fullName").value("새 이름"));
    }

    @Test
    void 프로필_사진을_바꾸고_지울_수_있다() throws Exception {
        MockHttpSession session = loginAs(activeUser());
        MockMultipartHttpServletRequestBuilder req = multipart("/api/me/avatar");
        req.with(r -> {
            r.setMethod("PUT");
            return r;
        });
        req.file(new MockMultipartFile("file", "a.png", "image/png", png(300, 300, Color.BLUE)));
        mvc.perform(req.session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.avatarUrl").value(org.hamcrest.Matchers.startsWith("/media/u/")));
        mvc.perform(delete("/api/me/avatar").session(session).with(csrf()))
                .andExpect(jsonPath("$.user.avatarUrl").doesNotExist());

        MockMultipartHttpServletRequestBuilder bad = multipart("/api/me/avatar");
        bad.with(r -> {
            r.setMethod("PUT");
            return r;
        });
        bad.file(new MockMultipartFile("file", "x.png", "image/png", "not an image".getBytes()));
        mvc.perform(bad.session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
    }
}
