package com.yangstagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yangstagram.seed.SeedService;
import com.yangstagram.seed.SeedSize;
import java.util.List;
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
}
