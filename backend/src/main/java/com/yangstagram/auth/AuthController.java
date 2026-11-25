package com.yangstagram.auth;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.user.User;
import com.yangstagram.user.UserRepository;
import com.yangstagram.user.UserService;
import com.yangstagram.user.dto.LoginRequest;
import com.yangstagram.user.dto.MeResponse;
import com.yangstagram.user.dto.SignupRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final SessionLogin sessionLogin;
    private final TwoFactorService twoFactor;
    private final com.yangstagram.moderation.ModerationService moderation;
    private final com.yangstagram.account.AccountService accounts;

    /** 비밀번호는 맞고 2단계 인증 코드를 기다리는 중 (세션에 5분, 틀리면 5번까지) */
    private static final String PENDING = "2fa.pendingUser";
    private static final String PENDING_AT = "2fa.pendingAt";
    private static final String ATTEMPTS = "2fa.attempts";

    public AuthController(UserService userService, UserRepository userRepository,
                          AuthenticationManager authenticationManager, SessionLogin sessionLogin,
                          TwoFactorService twoFactor, com.yangstagram.moderation.ModerationService moderation,
                          com.yangstagram.account.AccountService accounts) {
        this.accounts = accounts;
        this.twoFactor = twoFactor;
        this.moderation = moderation;
        this.userService = userService;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.sessionLogin = sessionLogin;
    }

    /** 화면이 처음 뜰 때 호출해서 CSRF 쿠키(XSRF-TOKEN)를 받는다. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName());
    }

    @PostMapping("/signup")
    public ResponseEntity<MeResponse> signUp(@Valid @RequestBody SignupRequest req,
                                             HttpServletRequest request, HttpServletResponse response) {
        // 가입 인증 코드: 다른 검사를 먼저 하고(중복 이메일 등), 코드는 마지막에 확인한다
        userService.validateSignup(req);
        accounts.consumeCode("SIGNUP", req.email().strip().toLowerCase(), req.code());
        User user = userService.signUp(req);
        sessionLogin.login(AppUserDetailsService.toAuthUser(user), request, response, "SIGNUP");
        return ResponseEntity.status(HttpStatus.CREATED).body(MeResponse.from(user));
    }

    /** 2단계 인증이 켜진 계정: 비밀번호 다음 단계 */
    public record TwoFactorChallenge(boolean twoFactorRequired, String username) {
    }

    /**
     * 비밀번호 확인. 2단계 인증이 켜져 있으면 로그인하지 않고 {twoFactorRequired: true}를 돌려준다
     * → 화면이 코드를 받아 /login/2fa 로 보낸다. 정지된 계정은 403(ACCOUNT_SUSPENDED).
     */
    @PostMapping("/login")
    public Object login(@Valid @RequestBody LoginRequest req,
                        HttpServletRequest request, HttpServletResponse response) {
        // 비활성화했거나 삭제를 요청한(30일 안) 계정은 맞는 비밀번호로 로그인하면 되살아난다
        accounts.reactivateIfDormant(req.login(), req.password());
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(req.login(), req.password()));
        AuthUser user = (AuthUser) auth.getPrincipal();
        requireNotSuspended(user.id());
        if (twoFactor.enabled(user.id())) {
            var session = request.getSession(true);
            session.setAttribute(PENDING, user.id());
            session.setAttribute(PENDING_AT, System.currentTimeMillis());
            session.setAttribute(ATTEMPTS, 0);
            return new TwoFactorChallenge(true, user.getUsername());
        }
        sessionLogin.login(auth, request, response);
        return MeResponse.from(userService.getActive(user.id()));
    }

    public record CodeRequest(String code) {
    }

    @PostMapping("/login/2fa")
    public MeResponse loginTwoFactor(@RequestBody CodeRequest req, HttpServletRequest request,
                                     HttpServletResponse response) {
        var session = request.getSession(false);
        Long userId = session == null ? null : (Long) session.getAttribute(PENDING);
        Long at = session == null ? null : (Long) session.getAttribute(PENDING_AT);
        if (userId == null || at == null || System.currentTimeMillis() - at > 5 * 60_000) {
            throw ApiException.badRequest("TWO_FACTOR_EXPIRED", "시간이 지났습니다. 처음부터 다시 로그인해 주세요.");
        }
        int attempts = (Integer) session.getAttribute(ATTEMPTS) + 1;
        session.setAttribute(ATTEMPTS, attempts);
        if (!twoFactor.verify(userId, req.code())) {
            if (attempts >= 5) {
                session.removeAttribute(PENDING);
                throw ApiException.badRequest("TWO_FACTOR_EXPIRED", "코드를 여러 번 틀렸습니다. 처음부터 다시 로그인해 주세요.");
            }
            throw ApiException.badRequest("BAD_CODE", "보안 코드가 맞지 않습니다. 다시 확인해 주세요.");
        }
        session.removeAttribute(PENDING);
        User user = userService.getActive(userId);
        sessionLogin.login(AppUserDetailsService.toAuthUser(user), request, response, "TWO_FACTOR");
        return MeResponse.from(user);
    }

    private void requireNotSuspended(long userId) {
        java.time.Instant until = moderation.suspendedUntil(userId);
        if (until != null) {
            String when = until.isAfter(java.time.Instant.now().plus(java.time.Duration.ofDays(3650)))
                    ? "영구적으로"
                    : java.time.format.DateTimeFormatter.ofPattern("yyyy년 M월 d일 H시")
                            .withZone(java.time.ZoneId.of("Asia/Seoul")).format(until) + "까지";
            throw ApiException.forbidden("ACCOUNT_SUSPENDED",
                    "커뮤니티 가이드라인 위반으로 계정이 " + when + " 정지되었습니다.");
        }
    }

    /** 로그인 상태면 본인 정보, 로그인 안 했으면 204(내용 없음). 오류가 아니므로 401을 쓰지 않는다. */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(HttpServletRequest request) {
        if (CurrentUser.id().isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return CurrentUser.id()
                .flatMap(userRepository::findById)
                .filter(u -> u.getDeletedAt() == null)
                .map(u -> ResponseEntity.ok(MeResponse.from(u)))
                .orElseGet(() -> {
                    // 세션은 있는데 사용자가 사라진 경우(시드 재생성 등) 세션을 정리한다
                    sessionLogin.logout(request);
                    return ResponseEntity.noContent().build();
                });
    }
}
