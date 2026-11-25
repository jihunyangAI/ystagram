package com.yangstagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yangstagram.seed.SeedService;
import com.yangstagram.seed.SeedSize;
import java.util.List;
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

/** 실제 PostgreSQL(Testcontainers) 위에서 가입·로그인·계정 전환·시드를 확인한다. */
@SpringBootTest(properties = "yangstagram.seed.size=small")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthAndLabIntegrationTest {

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

    private static String signupJson(String username, String email, String birthDate) {
        return """
                {"email":"%s","fullName":"테스트 사용자","username":"%s","password":"secret1","birthDate":"%s"}
                """.formatted(email, username, birthDate);
    }

    @Test
    void 가입하면_바로_로그인되고_내_프로필을_볼_수_있다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/signup").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SignupCodes.with(mvc, jdbc, signupJson("New.User", "New@Example.com", "1995-05-05"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("new.user"))   // 소문자로 정규화
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.fake").value(false));

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("new.user"));

        mvc.perform(get("/api/users/new.user").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me").value(true))
                .andExpect(jsonPath("$.followersCount").value(0));
    }

    @Test
    void 가입_규칙을_어기면_알맞은_오류_코드를_준다() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("explore", "a@example.com", "1995-05-05")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USERNAME"));

        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("young.one", "b@example.com", "2020-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_YOUNG"));

        String existing = jdbc.queryForObject("select username from users order by id limit 1", String.class);
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(existing, "c@example.com", "1995-05-05")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void CSRF_토큰이_없으면_변경_요청이_막힌다() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"x\",\"password\":\"yang1234\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 가짜_계정은_공통_비밀번호로_로그인되고_틀리면_거부된다() throws Exception {
        String username = jdbc.queryForObject("select username from users where is_fake order by id limit 1", String.class);

        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"password\":\"wrong-pw\"}".formatted(username)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));

        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"password\":\"yang1234\"}".formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(username));
    }

    @Test
    void 로그인하지_않으면_me는_204이고_보호된_API는_401이다() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/anyone")).andExpect(status().isUnauthorized());
    }

    @Test
    void Lab_계정_전환은_비밀번호_없이_그_사용자로_로그인한다() throws Exception {
        String username = jdbc.queryForObject("select username from users order by followers_count desc limit 1", String.class);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/lab/impersonate/" + username).session(session).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.user.username").value(username));
    }

    @Test
    void 같은_시드값이면_같은_가짜_데이터가_나온다() {
        List<String> first = jdbc.queryForList("select username from users order by id", String.class);
        Integer firstFollows = jdbc.queryForObject("select count(*) from follows", Integer.class);

        seedService.generate(SeedSize.SMALL, 42);
        List<String> second = jdbc.queryForList("select username from users order by id", String.class);
        Integer secondFollows = jdbc.queryForObject("select count(*) from follows", Integer.class);

        assertThat(first).hasSize(50).isEqualTo(second);
        assertThat(firstFollows).isEqualTo(secondFollows);

        seedService.generate(SeedSize.SMALL, 7);
        assertThat(jdbc.queryForList("select username from users order by id", String.class)).isNotEqualTo(first);
    }

    @Test
    void 가짜_데이터는_진짜처럼_분포한다() {
        // 팔로워 수가 고르게 퍼지지 않고 상위 계정에 몰려 있어야 한다
        Integer top = jdbc.queryForObject("select max(followers_count) from users", Integer.class);
        Double median = jdbc.queryForObject(
                "select percentile_cont(0.5) within group (order by followers_count) from users", Double.class);
        assertThat(top).isGreaterThan((int) (median * 1.5));

        // 비공개 계정·승인 대기 요청·비즈니스·크리에이터 계정이 섞여 있어야 한다
        assertThat(jdbc.queryForObject("select count(*) from users where is_private", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from follows where status = 'PENDING'", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from users where account_type = 'BUSINESS'", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from users where account_type = 'CREATOR'", Integer.class)).isPositive();

        // 카운터 컬럼이 실제 팔로우 수와 일치해야 한다
        Integer mismatched = jdbc.queryForObject("""
                select count(*) from users u
                where u.followers_count <> (select count(*) from follows f where f.following_id = u.id and f.status = 'ACCEPTED')
                """, Integer.class);
        assertThat(mismatched).isZero();
    }
}
