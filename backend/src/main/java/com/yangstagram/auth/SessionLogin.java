package com.yangstagram.auth;

import com.yangstagram.account.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * 로그인마다 login_sessions에 한 줄을 남겨 "로그인 활동"에 보이고, 다른 곳에서 끊을 수 있다(원격 로그아웃).
 * 같은 브라우저에서 비밀번호로 추가한 계정들은 LINKED에 남아 비밀번호 없이 오갈 수 있다(다중 계정).
 */
@Component
public class SessionLogin {

    /** 지금 로그인의 login_sessions.id */
    public static final String ROW = "login.sessionRow";
    /** 이 브라우저에 추가한 계정: 사용자 id → login_sessions.id (넣은 순서) */
    public static final String LINKED = "login.linked";
    public static final int MAX_LINKED = 5;

    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();
    private final AccountService accounts;

    public SessionLogin(AccountService accounts) {
        this.accounts = accounts;
    }

    public void login(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
        login(user, request, response, "PASSWORD");
    }

    public void login(AuthUser user, HttpServletRequest request, HttpServletResponse response, String method) {
        login(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()),
                request, response, method, false);
    }

    public void login(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        login(authentication, request, response, "PASSWORD", false);
    }

    /**
     * @param keepLinked true면 이 브라우저에 이미 추가된 계정 목록을 유지하고 이 계정을 더한다(계정 추가).
     *                   false면 이 계정 하나로 새로 시작한다.
     */
    @SuppressWarnings("unchecked")
    public void login(Authentication authentication, HttpServletRequest request, HttpServletResponse response,
                      String method, boolean keepLinked) {
        // 로그인할 때마다 세션 ID를 바꿔 세션 고정 공격을 막는다
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        setContext(authentication, request, response);
        AuthUser user = (AuthUser) authentication.getPrincipal();
        long row = accounts.recordLogin(user.id(), method, request.getHeader("User-Agent"), request.getRemoteAddr());
        HttpSession session = request.getSession(true);
        Map<Long, Long> linked = keepLinked && session.getAttribute(LINKED) instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<Long, Long>) m) : new LinkedHashMap<>();
        if (!keepLinked) linked.values().forEach(accounts::revoke);
        linked.remove(user.id());
        linked.put(user.id(), row);
        session.setAttribute(LINKED, linked);
        session.setAttribute(ROW, row);
    }

    /** 이 브라우저에 추가해 둔 계정으로 바꾼다 (비밀번호 없이). 그 계정의 로그인이 끊겼으면 false */
    public boolean switchTo(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
        Map<Long, Long> linked = linked(request);
        Long row = linked.get(user.id());
        if (row == null || !accounts.touch(row, user.id())) {
            linked.remove(user.id());
            return false;
        }
        request.changeSessionId();
        setContext(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()), request, response);
        request.getSession().setAttribute(ROW, row);
        return true;
    }

    @SuppressWarnings("unchecked")
    public Map<Long, Long> linked(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(LINKED) instanceof Map<?, ?> m) return (Map<Long, Long>) m;
        return new LinkedHashMap<>();
    }

    public Long currentRow(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Long) session.getAttribute(ROW);
    }

    private void setContext(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);
        repository.saveContext(context, request, response);
    }

    /** 이 브라우저에 추가한 모든 계정의 로그인 기록을 끊긴 것으로 (세션은 그대로) */
    public void revokeBrowser(HttpServletRequest request) {
        linked(request).values().forEach(accounts::revoke);
        Long row = currentRow(request);
        if (row != null) accounts.revoke(row);
    }

    /** 이 브라우저의 모든 계정에서 로그아웃 (기록도 끊긴 것으로) */
    public void logout(HttpServletRequest request) {
        revokeBrowser(request);
        holder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
