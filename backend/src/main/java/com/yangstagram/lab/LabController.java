package com.yangstagram.lab;

import com.yangstagram.auth.AppUserDetailsService;
import com.yangstagram.auth.SessionLogin;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.seed.SeedProperties;
import com.yangstagram.seed.SeedService;
import com.yangstagram.seed.SeedService.SeedResult;
import com.yangstagram.seed.SeedSize;
import com.yangstagram.user.User;
import com.yangstagram.user.UserRepository;
import com.yangstagram.user.UserService;
import com.yangstagram.user.dto.MeResponse;
import com.yangstagram.user.dto.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 연구 도구(Lab) API. 실제 서비스에는 없는 기능으로, 로컬 연구 환경에서만 켠다.
 */
@RestController
@RequestMapping("/api/lab")
@ConditionalOnProperty(prefix = "yangstagram.lab", name = "enabled", havingValue = "true")
public class LabController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final SessionLogin sessionLogin;
    private final SeedService seedService;
    private final SeedProperties seedProperties;
    private final LabClock clock;
    private final JdbcTemplate jdbc;

    public LabController(UserRepository userRepository, UserService userService, SessionLogin sessionLogin,
                         SeedService seedService, SeedProperties seedProperties, LabClock clock, JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.sessionLogin = sessionLogin;
        this.seedService = seedService;
        this.seedProperties = seedProperties;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    public record LabStatus(long users, long fakeUsers, long follows, long pendingFollows,
                            long posts, long likes, long comments, long hashtags,
                            long activeStories, long conversations, long messages, long notifications,
                            Instant now, long clockOffsetMinutes, String fakePassword) {
    }

    public record LabUser(UserSummary user, String accountType, int followersCount, int followingCount,
                          String interests, boolean fake) {
        static LabUser from(User u) {
            return new LabUser(UserSummary.from(u), u.getAccountType().name(), u.getFollowersCount(),
                    u.getFollowingCount(), u.getInterests(), u.isFake());
        }
    }

    public record SeedRequest(SeedSize size, Long seed) {
    }

    @GetMapping("/status")
    public LabStatus status() {
        Long fake = jdbc.queryForObject("select count(*) from users where is_fake", Long.class);
        Long follows = jdbc.queryForObject("select count(*) from follows where status = 'ACCEPTED'", Long.class);
        Long pending = jdbc.queryForObject("select count(*) from follows where status = 'PENDING'", Long.class);
        Long posts = jdbc.queryForObject("select count(*) from posts where deleted_at is null", Long.class);
        Long likes = jdbc.queryForObject("select count(*) from likes", Long.class);
        Long comments = jdbc.queryForObject("select count(*) from comments", Long.class);
        Long hashtags = jdbc.queryForObject("select count(*) from hashtags", Long.class);
        Long stories = jdbc.queryForObject(
                "select count(*) from stories where deleted_at is null and expires_at > ? and created_at <= ?",
                Long.class, java.sql.Timestamp.from(Instant.now(clock)), java.sql.Timestamp.from(Instant.now(clock)));
        Long conversations = jdbc.queryForObject("select count(*) from conversations", Long.class);
        Long messages = jdbc.queryForObject("select count(*) from messages", Long.class);
        Long notifications = jdbc.queryForObject("select count(*) from notifications", Long.class);
        return new LabStatus(userRepository.count(), fake, follows, pending, posts, likes, comments, hashtags,
                stories, conversations, messages, notifications, Instant.now(clock),
                clock.getOffset().toMinutes(), seedProperties.fakePassword());
    }

    /** 계정 전환기 목록: 검색어가 없으면 팔로워 많은 순. */
    @GetMapping("/users")
    public List<LabUser> users(@RequestParam(defaultValue = "") String q,
                               @RequestParam(defaultValue = "100") int limit) {
        return userRepository.searchForLab(q.trim(), PageRequest.of(0, Math.min(limit, 500)))
                .stream().map(LabUser::from).toList();
    }

    /** 비밀번호 없이 해당 사용자로 로그인 (계정 전환). */
    @PostMapping("/impersonate/{username}")
    public MeResponse impersonate(@PathVariable String username,
                                  HttpServletRequest request, HttpServletResponse response) {
        User user = userService.getByUsername(username);
        sessionLogin.login(AppUserDetailsService.toAuthUser(user), request, response);
        return MeResponse.from(user);
    }

    /** addMinutes만큼 가상 시계를 앞으로 (reset이면 실제 시간으로 되돌림) */
    public record ClockRequest(Long addMinutes, Boolean reset) {
    }

    public record ClockStatus(Instant now, long offsetMinutes) {
    }

    /**
     * 시간 가속 (기획서 §7 L04): 스토리 24시간 만료, 15분 수정 제한 등을 기다리지 않고 확인.
     * 뒤로 돌리는 것은 막는다 (이미 만든 데이터가 "미래"에 있게 되므로).
     */
    @PostMapping("/clock")
    public ClockStatus clock(@RequestBody ClockRequest req) {
        if (Boolean.TRUE.equals(req.reset())) {
            clock.setOffset(java.time.Duration.ZERO);
        } else if (req.addMinutes() != null && req.addMinutes() > 0) {
            clock.setOffset(clock.getOffset().plusMinutes(Math.min(req.addMinutes(), 60L * 24 * 60)));
        }
        return new ClockStatus(Instant.now(clock), clock.getOffset().toMinutes());
    }

    /** 가짜 데이터를 모두 지우고 다시 만든다. 직접 가입한 계정도 지워지므로 세션도 끊는다. */
    @PostMapping("/seed")
    public SeedResult reseed(@RequestBody SeedRequest req, HttpServletRequest request) {
        SeedSize size = req.size() != null ? req.size() : seedProperties.size();
        long seed = req.seed() != null ? req.seed() : seedProperties.value();
        SeedResult result = seedService.generate(size, seed);
        sessionLogin.logout(request);
        return result;
    }
}
