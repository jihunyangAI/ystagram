package com.yangstagram.account;

import com.yangstagram.account.AccountService.LoginRow;
import com.yangstagram.auth.AppUserDetailsService;
import com.yangstagram.auth.AuthUser;
import com.yangstagram.auth.CurrentUser;
import com.yangstagram.auth.SessionLogin;
import com.yangstagram.auth.TwoFactorService;
import com.yangstagram.common.error.ApiException;
import com.yangstagram.user.UserService;
import com.yangstagram.user.dto.MeResponse;
import com.yangstagram.user.dto.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 계정 센터: 가입 인증 코드·비밀번호 재설정(로그인 전), 로그인 활동·다중 계정·비활성화·삭제(로그인 후) */
@RestController
public class AccountController {

    private final AccountService accounts;
    private final UserService users;
    private final SessionLogin sessionLogin;
    private final AuthenticationManager authenticationManager;
    private final TwoFactorService twoFactor;

    public AccountController(AccountService accounts, UserService users, SessionLogin sessionLogin,
                             AuthenticationManager authenticationManager, TwoFactorService twoFactor) {
        this.accounts = accounts;
        this.users = users;
        this.sessionLogin = sessionLogin;
        this.authenticationManager = authenticationManager;
        this.twoFactor = twoFactor;
    }

    public record EmailRequest(String email) {
    }

    public record LoginNameRequest(String login) {
    }

    public record ResetRequest(String login, String code, String password) {
    }

    public record PasswordRequest(String password) {
    }

    public record CredentialsRequest(String login, String password) {
    }

    /** "코드를 보냈습니다" (실제 발송 없이 Lab 화면에 표시) */
    public record CodeSent(String sentTo, int expiresInMinutes) {
    }

    // ------------------------------------------------------------ 로그인 전

    @PostMapping("/api/auth/signup/code")
    public CodeSent signupCode(@RequestBody EmailRequest req) {
        String email = req.email() == null ? "" : req.email().strip().toLowerCase();
        if (!email.matches("^[^@ ]+@[^@ ]+[.][^@ ]+$")) {
            throw ApiException.badRequest("BAD_EMAIL", "이메일 형식이 올바르지 않습니다.");
        }
        users.requireEmailFree(email);
        accounts.issueCode("SIGNUP", email);
        return new CodeSent(email, (int) AccountService.CODE_TTL.toMinutes());
    }

    @PostMapping("/api/auth/password/code")
    public CodeSent resetCode(@RequestBody LoginNameRequest req) {
        accounts.requestReset(req.login());
        return new CodeSent(req.login() == null ? "" : req.login().strip(), (int) AccountService.CODE_TTL.toMinutes());
    }

    /** 새 비밀번호로 바꾸고 다른 곳의 로그인은 모두 끊는다 (이 브라우저도 다시 로그인해야 함) */
    @PostMapping("/api/auth/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@RequestBody ResetRequest req, HttpServletRequest request) {
        accounts.resetPassword(req.login(), req.code(), req.password());
        sessionLogin.logout(request);
    }

    // ------------------------------------------------------------ 다중 계정

    public record LinkedAccounts(long currentId, List<UserSummary> accounts) {
    }

    @GetMapping("/api/auth/accounts")
    public LinkedAccounts linked(HttpServletRequest request) {
        long me = CurrentUser.requireId();
        return new LinkedAccounts(me, accounts.summaries(sessionLogin.linked(request).keySet()));
    }

    /** 다른 계정 추가: 비밀번호를 확인하고 그 계정으로 바뀐다 (이전 계정은 목록에 남음) */
    @PostMapping("/api/auth/accounts")
    public MeResponse add(@RequestBody CredentialsRequest req, HttpServletRequest request, HttpServletResponse response) {
        CurrentUser.requireId();
        if (sessionLogin.linked(request).size() >= SessionLogin.MAX_LINKED) {
            throw ApiException.badRequest("TOO_MANY_ACCOUNTS", "계정은 5개까지 추가할 수 있습니다.");
        }
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(req.login(), req.password()));
        AuthUser user = (AuthUser) auth.getPrincipal();
        if (twoFactor.enabled(user.id())) {
            throw ApiException.badRequest("TWO_FACTOR_ACCOUNT",
                    "2단계 인증을 켠 계정은 로그아웃한 뒤 로그인 화면에서 추가해 주세요.");
        }
        sessionLogin.login(auth, request, response, "ADD", true);
        return MeResponse.from(users.getActive(user.id()));
    }

    @PostMapping("/api/auth/accounts/{userId}/switch")
    public MeResponse switchTo(@PathVariable long userId, HttpServletRequest request, HttpServletResponse response) {
        CurrentUser.requireId();
        var user = users.getActive(userId);
        if (!sessionLogin.switchTo(AppUserDetailsService.toAuthUser(user), request, response)) {
            throw ApiException.badRequest("NOT_LINKED", "이 계정의 로그인이 끊겼습니다. 다시 추가해 주세요.");
        }
        return MeResponse.from(user);
    }

    // ------------------------------------------------------------ 로그인 활동

    @GetMapping("/api/me/sessions")
    public List<LoginRow> sessions(HttpServletRequest request) {
        return accounts.sessions(CurrentUser.requireId(), sessionLogin.currentRow(request));
    }

    @DeleteMapping("/api/me/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable long id) {
        accounts.revokeOwn(CurrentUser.requireId(), id);
    }

    /** 이 브라우저만 남기고 모두 로그아웃 */
    @DeleteMapping("/api/me/sessions")
    public Map<String, Integer> revokeOthers(HttpServletRequest request) {
        return Map.of("revoked", accounts.revokeAll(CurrentUser.requireId(), sessionLogin.currentRow(request)));
    }

    // ------------------------------------------------------------ 비활성화·삭제

    @PostMapping("/api/me/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@RequestBody PasswordRequest req, HttpServletRequest request) {
        accounts.deactivate(CurrentUser.requireId(), req.password());
        sessionLogin.logout(request);
    }

    public record DeletionScheduled(Instant deleteAt) {
    }

    @PostMapping("/api/me/delete")
    public DeletionScheduled delete(@RequestBody PasswordRequest req, HttpServletRequest request) {
        Instant at = accounts.requestDeletion(CurrentUser.requireId(), req.password());
        sessionLogin.logout(request);
        return new DeletionScheduled(at);
    }
}
