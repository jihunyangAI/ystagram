package com.yangstagram.settings;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.settings.SettingsService.Patch;
import com.yangstagram.settings.SettingsService.Settings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 안전·알림 설정 (숨겨진 단어·댓글 허용 범위·알림·민감 콘텐츠·사용 시간 알림·수면 모드) */
@RestController
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/api/me/settings")
    public Settings get() {
        return settings.get(CurrentUser.requireId());
    }

    @PatchMapping("/api/me/settings")
    public Settings update(@RequestBody Patch patch) {
        return settings.update(CurrentUser.requireId(), patch);
    }
}
