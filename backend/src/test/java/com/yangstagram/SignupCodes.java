package com.yangstagram;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 가입 요청 JSON에 인증 코드를 붙인다 (코드를 받고 DB에서 읽어 옴 — 실제로는 Lab 화면에 보이는 값) */
final class SignupCodes {

    private SignupCodes() {
    }

    private static final Pattern EMAIL = Pattern.compile("\"email\"\s*:\s*\"([^\"]+)\"");

    static String with(MockMvc mvc, JdbcTemplate jdbc, String json) throws Exception {
        Matcher m = EMAIL.matcher(json);
        if (!m.find()) throw new IllegalArgumentException("email 없음: " + json);
        String email = m.group(1).strip().toLowerCase();
        mvc.perform(post("/api/auth/signup/code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
        String code = jdbc.queryForObject(
                "select code from auth_codes where purpose = 'SIGNUP' and target = ? order by id desc limit 1",
                String.class, email);
        String body = json.strip();
        return body.substring(0, body.lastIndexOf('}')) + ",\"code\":\"" + code + "\"}";
    }
}
