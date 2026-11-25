package com.yangstagram.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

/**
 * 세션 쿠키 기반 로그인 + CSRF 보호.
 * 화면(React)은 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보내야 POST/PUT/DELETE가 통과한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SessionLogin sessionLogin,
                                                   com.yangstagram.account.AccountService accounts) throws Exception {
        http
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/lab/**").permitAll() // 연구 도구 (로컬 전용)
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/ws/**").authenticated() // 실시간 연결도 로그인 필요
                        .anyRequest().permitAll())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        // 로그인 기록도 끊긴 것으로 (이 브라우저에 추가한 계정 모두)
                        .addLogoutHandler((req, res, auth) -> sessionLogin.revokeBrowser(req))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(ex -> ex
                        // 로그인이 필요하면 로그인 페이지로 보내지 않고 401만 돌려준다 (화면이 처리)
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // 다른 곳에서 끊은 로그인(원격 로그아웃)은 요청을 처리하기 전에 로그아웃 상태로
                .addFilterAfter(new LoginSessionFilter(accounts),
                        org.springframework.security.web.context.SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
