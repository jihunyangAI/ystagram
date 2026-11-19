package com.yangstagram.auth;

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

    public AuthController(UserService userService, UserRepository userRepository,
                          AuthenticationManager authenticationManager, SessionLogin sessionLogin) {
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
        User user = userService.signUp(req);
        sessionLogin.login(AppUserDetailsService.toAuthUser(user), request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(MeResponse.from(user));
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest req,
                            HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(req.login(), req.password()));
        sessionLogin.login(auth, request, response);
        AuthUser user = (AuthUser) auth.getPrincipal();
        return MeResponse.from(userService.getActive(user.id()));
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
