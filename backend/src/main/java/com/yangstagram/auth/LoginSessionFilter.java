package com.yangstagram.auth;

import com.yangstagram.account.AccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 원격 로그아웃: 이 브라우저의 로그인이 다른 곳에서 끊겼거나(로그인 활동), 비밀번호를 바꾸거나 계정을 비활성화했으면
 * 세션을 지워서 로그아웃 상태로 만든다. SecurityConfig에서 보안 컨텍스트를 읽은 바로 뒤에 넣는다.
 */
public class LoginSessionFilter extends OncePerRequestFilter {

    private final AccountService accounts;

    public LoginSessionFilter(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (session != null && auth != null && auth.getPrincipal() instanceof AuthUser user
                && session.getAttribute(SessionLogin.ROW) instanceof Long row && !accounts.touch(row, user.id())) {
            SecurityContextHolder.clearContext();
            session.invalidate();
        }
        chain.doFilter(request, response);
    }
}
