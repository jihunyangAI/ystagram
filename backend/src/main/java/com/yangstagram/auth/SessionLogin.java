package com.yangstagram.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 인증된 사용자를 세션에 저장한다 (일반 로그인, 가입 직후 로그인, Lab 계정 전환에서 공통 사용).
 */
@Component
public class SessionLogin {

    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();

    public void login(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
        login(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()),
                request, response);
    }

    public void login(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        // 로그인할 때마다 세션 ID를 바꿔 세션 고정 공격을 막는다
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);
        repository.saveContext(context, request, response);
    }

    public void logout(HttpServletRequest request) {
        holder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
