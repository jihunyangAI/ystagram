package com.yangstagram.lab;

import com.yangstagram.auth.TwoFactorService;
import com.yangstagram.user.UserService;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lab: 2단계 인증을 켠 계정의 지금 코드 (휴대폰 인증 앱 없이 로그인 흐름을 시험할 때) */
@RestController
@RequestMapping("/api/lab/2fa")
@ConditionalOnProperty(prefix = "yangstagram.lab", name = "enabled", havingValue = "true")
public class LabSecurityController {

    private final TwoFactorService twoFactor;
    private final UserService users;

    public LabSecurityController(TwoFactorService twoFactor, UserService users) {
        this.twoFactor = twoFactor;
        this.users = users;
    }

    @GetMapping("/{username}/code")
    public Map<String, String> code(@PathVariable String username) {
        return Map.of("code", twoFactor.currentCode(users.getByUsername(username).getId()));
    }
}
