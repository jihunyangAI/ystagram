package com.yangstagram.user;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.user.dto.SignupRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    /** 인스타그램 가입 최소 나이 */
    public static final int MIN_AGE = 13;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LabClock clock;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, LabClock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** 가입 전 검사 (사용자 이름 규칙·중복, 이메일 중복, 나이). 통과하면 아무것도 하지 않는다 */
    @Transactional(readOnly = true)
    public void validateSignup(SignupRequest req) {
        check(req);
    }

    /** 가입 인증 코드를 보내기 전에 이메일만 확인 */
    @Transactional(readOnly = true)
    public void requireEmailFree(String rawEmail) {
        if (userRepository.existsByEmail(rawEmail.trim().toLowerCase())) {
            throw ApiException.conflict("EMAIL_TAKEN", "이미 가입된 이메일입니다.");
        }
    }

    private void check(SignupRequest req) {
        String username = UsernamePolicy.normalize(req.username());
        String problem = UsernamePolicy.validate(username);
        if (problem != null) {
            throw ApiException.badRequest("INVALID_USERNAME", problem);
        }
        if (userRepository.existsByUsername(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "이미 사용 중인 사용자 이름입니다.");
        }
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "이미 가입된 이메일입니다.");
        }
        LocalDate today = LocalDate.now(clock);
        if (req.birthDate().isAfter(today) || Period.between(req.birthDate(), today).getYears() < MIN_AGE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_YOUNG", "만 13세 이상만 가입할 수 있습니다.");
        }
    }

    @Transactional
    public User signUp(SignupRequest req) {
        check(req);
        String username = UsernamePolicy.normalize(req.username());
        String email = req.email().trim().toLowerCase();
        LocalDate today = LocalDate.now(clock);
        User user = User.signUp(username, email, passwordEncoder.encode(req.password()),
                req.fullName().trim(), req.birthDate(), Instant.now(clock));
        if (Teens.isTeen(req.birthDate(), today)) {
            user.setPrivateAccount(true); // 10대 계정은 비공개로 시작
        }
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getActive(Long id) {
        return userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findByUsernameAndDeletedAtIsNull(UsernamePolicy.normalize(username))
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));
    }
}
