package com.yangstagram.auth;

import com.yangstagram.auth.TwoFactorService.Setup;
import com.yangstagram.auth.TwoFactorService.Status;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 설정 → 보안 → 2단계 인증 */
@RestController
@RequestMapping("/api/me/2fa")
public class TwoFactorController {

    private final TwoFactorService twoFactor;

    public TwoFactorController(TwoFactorService twoFactor) {
        this.twoFactor = twoFactor;
    }

    public record CodeRequest(String code) {
    }

    @GetMapping
    public Status status() {
        return twoFactor.status(CurrentUser.requireId());
    }

    /** 비밀 키·QR 발급 (아직 꺼진 상태) */
    @PostMapping("/setup")
    public Setup setup() {
        return twoFactor.setup(CurrentUser.requireId());
    }

    /** 앱 코드로 확인해서 켜기 → 백업 코드 (이때만 보여 준다) */
    @PostMapping("/enable")
    public Map<String, List<String>> enable(@RequestBody CodeRequest req) {
        return Map.of("backupCodes", twoFactor.enable(CurrentUser.requireId(), req.code()));
    }

    @PostMapping("/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@RequestBody CodeRequest req) {
        twoFactor.disable(CurrentUser.requireId(), req.code());
    }

    @PostMapping("/backup-codes")
    public Map<String, List<String>> backupCodes(@RequestBody CodeRequest req) {
        return Map.of("backupCodes", twoFactor.regenerateBackupCodes(CurrentUser.requireId(), req.code()));
    }
}
