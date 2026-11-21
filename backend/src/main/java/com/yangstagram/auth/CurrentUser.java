package com.yangstagram.auth;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 현재 요청의 로그인 사용자를 꺼내는 도우미. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthUser> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static Optional<Long> id() {
        return get().map(AuthUser::id);
    }

    /** 로그인이 필요한 API에서 사용 (보안 설정이 이미 막지만, 혹시 몰라 401로 응답) */
    public static long requireId() {
        return id().orElseThrow(() -> new com.yangstagram.common.error.ApiException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED", "로그인이 필요합니다."));
    }
}
