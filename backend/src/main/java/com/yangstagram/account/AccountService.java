package com.yangstagram.account;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 센터 (기획서 §4.1): 가입 인증 코드, 비밀번호 재설정, 로그인 기록·원격 로그아웃, 비활성화·삭제(30일 유예).
 * 코드는 실제로 보내지 않고 Lab 화면(`/api/lab/codes`)에 보인다.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    public static final Duration CODE_TTL = Duration.ofMinutes(10);
    public static final int CODE_ATTEMPTS = 5;
    public static final Duration DELETE_GRACE = Duration.ofDays(30);

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    public AccountService(NamedParameterJdbcTemplate jdbc, LabClock clock, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.encoder = encoder;
    }

    private Timestamp now() {
        return Timestamp.from(Instant.now(clock));
    }

    // ------------------------------------------------------------ 코드

    /** 새 코드를 만든다 (같은 대상의 예전 코드는 쓸 수 없게) */
    @Transactional
    public String issueCode(String purpose, String target) {
        Integer recent = jdbc.queryForObject("""
                select count(*) from auth_codes where purpose = :p and target = :t and created_at > :since
                """, new MapSqlParameterSource("p", purpose).addValue("t", target)
                .addValue("since", Timestamp.from(Instant.now(clock).minus(Duration.ofMinutes(10)))), Integer.class);
        if (recent != null && recent >= 5) {
            throw ApiException.badRequest("TOO_MANY_CODES", "코드를 너무 자주 요청했습니다. 잠시 후 다시 시도해 주세요.");
        }
        jdbc.update("update auth_codes set used_at = :now where purpose = :p and target = :t and used_at is null",
                new MapSqlParameterSource("p", purpose).addValue("t", target).addValue("now", now()));
        String code = "%06d".formatted(random.nextInt(1_000_000));
        jdbc.update("""
                insert into auth_codes (purpose, target, code, created_at, expires_at)
                values (:p, :t, :code, :now, :exp)
                """, new MapSqlParameterSource("p", purpose).addValue("t", target).addValue("code", code)
                .addValue("now", now()).addValue("exp", Timestamp.from(Instant.now(clock).plus(CODE_TTL))));
        return code;
    }

    /** 코드를 확인하고 쓴 것으로 표시한다. 틀리면 400, 5번 틀리면 그 코드는 버린다 */
    @Transactional
    public void consumeCode(String purpose, String target, String code) {
        Map<String, Object> row = jdbc.queryForList("""
                select id, code, expires_at, attempts from auth_codes
                where purpose = :p and target = :t and used_at is null order by id desc limit 1
                """, new MapSqlParameterSource("p", purpose).addValue("t", target)).stream().findFirst()
                .orElseThrow(() -> ApiException.badRequest("CODE_REQUIRED", "먼저 인증 코드를 받아 주세요."));
        long id = ((Number) row.get("id")).longValue();
        if (((Timestamp) row.get("expires_at")).toInstant().isBefore(Instant.now(clock))) {
            throw ApiException.badRequest("CODE_EXPIRED", "코드의 유효 시간(10분)이 지났습니다. 새 코드를 받아 주세요.");
        }
        String given = code == null ? "" : code.strip();
        if (!row.get("code").equals(given)) {
            int attempts = ((Number) row.get("attempts")).intValue() + 1;
            jdbc.update(attempts >= CODE_ATTEMPTS
                            ? "update auth_codes set attempts = :a, used_at = :now where id = :id"
                            : "update auth_codes set attempts = :a where id = :id",
                    new MapSqlParameterSource("a", attempts).addValue("id", id).addValue("now", now()));
            throw ApiException.badRequest("BAD_CODE", attempts >= CODE_ATTEMPTS
                    ? "코드를 여러 번 틀렸습니다. 새 코드를 받아 주세요." : "코드가 맞지 않습니다. 다시 확인해 주세요.");
        }
        jdbc.update("update auth_codes set used_at = :now where id = :id", Map.of("id", id, "now", now()));
    }

    public record LabCode(String purpose, String target, String username, String code, Instant createdAt,
                          Instant expiresAt) {
    }

    /** Lab: 아직 쓰지 않은 최근 코드 */
    public List<LabCode> recentCodes() {
        return jdbc.query("""
                select c.purpose, c.target, u.username, c.code, c.created_at, c.expires_at from auth_codes c
                left join users u on c.purpose = 'RESET' and u.id::text = c.target
                where c.used_at is null and c.expires_at > :now order by c.id desc limit 20
                """, Map.of("now", now()), (rs, i) -> new LabCode(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant()));
    }

    // ------------------------------------------------------------ 비밀번호 재설정

    /** 사용자 이름 또는 이메일 → 재설정 코드. 없는 계정이어도 같은 응답(계정 존재 여부를 흘리지 않음) */
    public void requestReset(String login) {
        findUserId(login, false).ifPresent(id -> issueCode("RESET", String.valueOf(id)));
    }

    @Transactional
    public long resetPassword(String login, String code, String newPassword) {
        long id = findUserId(login, false)
                .orElseThrow(() -> ApiException.badRequest("CODE_REQUIRED", "먼저 인증 코드를 받아 주세요."));
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 100) {
            throw ApiException.badRequest("BAD_PASSWORD", "비밀번호는 6자 이상이어야 합니다.");
        }
        consumeCode("RESET", String.valueOf(id), code);
        jdbc.update("update users set password_hash = :h, updated_at = :now where id = :id",
                Map.of("h", encoder.encode(newPassword), "now", now(), "id", id));
        revokeAll(id, null); // 다른 곳의 로그인은 모두 끊는다
        return id;
    }

    /** 로그인 이름 → 사용자 id. dormant=true면 비활성화·삭제 요청 중인 계정도 찾는다 */
    private Optional<Long> findUserId(String login, boolean dormant) {
        if (login == null || login.isBlank()) return Optional.empty();
        String key = login.strip().toLowerCase();
        String col = key.contains("@") ? "lower(email)" : "username";
        return jdbc.queryForList("select id from users where " + col + " = :k and (deleted_at is null"
                        + (dormant ? " or deactivated_at is not null or delete_requested_at is not null)" : ")"),
                Map.of("k", key), Long.class).stream().findFirst();
    }

    // ------------------------------------------------------------ 비활성화·삭제

    public void requirePassword(long userId, String password) {
        String hash = jdbc.queryForObject("select password_hash from users where id = :id", Map.of("id", userId),
                String.class);
        if (password == null || !encoder.matches(password, hash)) {
            throw ApiException.badRequest("BAD_PASSWORD", "비밀번호가 맞지 않습니다.");
        }
    }

    @Transactional
    public void deactivate(long userId, String password) {
        requirePassword(userId, password);
        jdbc.update("update users set deactivated_at = :now, deleted_at = :now where id = :id",
                Map.of("now", now(), "id", userId));
        revokeAll(userId, null);
    }

    @Transactional
    public Instant requestDeletion(long userId, String password) {
        requirePassword(userId, password);
        jdbc.update("update users set delete_requested_at = :now, deleted_at = :now where id = :id",
                Map.of("now", now(), "id", userId));
        revokeAll(userId, null);
        return Instant.now(clock).plus(DELETE_GRACE);
    }

    public record Reactivated(long userId, boolean wasDeletionPending) {
    }

    /**
     * 비활성화했거나 삭제를 요청한(30일 안) 계정이 맞는 비밀번호로 로그인하면 되살린다.
     * 그런 계정이 아니면 아무것도 하지 않는다(보통 로그인으로 이어진다).
     */
    @Transactional
    public Optional<Reactivated> reactivateIfDormant(String login, String password) {
        if (login == null || login.isBlank()) return Optional.empty();
        String key = login.strip().toLowerCase();
        String col = key.contains("@") ? "lower(email)" : "username";
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, password_hash, delete_requested_at from users
                where %s = :k and deleted_at is not null
                  and (deactivated_at is not null or delete_requested_at > :cutoff)
                """.formatted(col), new MapSqlParameterSource("k", key)
                .addValue("cutoff", Timestamp.from(Instant.now(clock).minus(DELETE_GRACE))));
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> row = rows.getFirst();
        if (password == null || !encoder.matches(password, (String) row.get("password_hash"))) return Optional.empty();
        long id = ((Number) row.get("id")).longValue();
        jdbc.update("""
                update users set deleted_at = null, deactivated_at = null, delete_requested_at = null where id = :id
                """, Map.of("id", id));
        return Optional.of(new Reactivated(id, row.get("delete_requested_at") != null));
    }

    /** 삭제를 요청하고 30일이 지난 계정을 영구 삭제한다 (게시물·댓글·메시지 등은 외래 키로 함께 지워진다) */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    @Transactional
    public int purgeExpired() {
        int n = jdbc.update("delete from users where delete_requested_at is not null and delete_requested_at < :cutoff",
                Map.of("cutoff", Timestamp.from(Instant.now(clock).minus(DELETE_GRACE))));
        if (n > 0) log.info("삭제 유예 30일이 지난 계정 영구 삭제: {}", n);
        return n;
    }

    // ------------------------------------------------------------ 로그인 기록

    public long recordLogin(long userId, String method, String userAgent, String ip) {
        String ua = userAgent == null ? null : userAgent.length() > 300 ? userAgent.substring(0, 300) : userAgent;
        return jdbc.queryForObject("""
                insert into login_sessions (user_id, method, user_agent, ip, created_at, last_seen_at)
                values (:u, :m, :ua, :ip, :now, :now) returning id
                """, new MapSqlParameterSource("u", userId).addValue("m", method).addValue("ua", ua)
                .addValue("ip", ip).addValue("now", now()), Long.class);
    }

    /** 이 로그인이 아직 살아 있는지 (원격 로그아웃 확인). 1분에 한 번 last_seen을 갱신한다 */
    public boolean touch(long sessionRowId, long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select user_id, revoked_at, last_seen_at from login_sessions where id = :id", Map.of("id", sessionRowId));
        if (rows.isEmpty()) return false;
        Map<String, Object> r = rows.getFirst();
        if (((Number) r.get("user_id")).longValue() != userId || r.get("revoked_at") != null) return false;
        Instant seen = ((Timestamp) r.get("last_seen_at")).toInstant();
        if (seen.isBefore(Instant.now().minusSeconds(60))) {
            jdbc.update("update login_sessions set last_seen_at = :now where id = :id",
                    Map.of("now", Timestamp.from(Instant.now()), "id", sessionRowId));
        }
        return true;
    }

    public void revoke(long sessionRowId) {
        jdbc.update("update login_sessions set revoked_at = coalesce(revoked_at, :now) where id = :id",
                Map.of("now", now(), "id", sessionRowId));
    }

    /** 이 사람의 로그인을 모두 끊는다 (except가 있으면 그 하나는 남김) */
    public int revokeAll(long userId, Long except) {
        return jdbc.update("""
                update login_sessions set revoked_at = :now
                where user_id = :u and revoked_at is null and (cast(:except as bigint) is null or id <> :except)
                """, new MapSqlParameterSource("u", userId).addValue("now", now()).addValue("except", except));
    }

    public record LoginRow(long id, String method, String userAgent, String ip, Instant createdAt, Instant lastSeenAt,
                           boolean current) {
    }

    public List<LoginRow> sessions(long userId, Long current) {
        return jdbc.query("""
                select id, method, user_agent, ip, created_at, last_seen_at from login_sessions
                where user_id = :u and revoked_at is null order by last_seen_at desc, id desc limit 30
                """, Map.of("u", userId), (rs, i) -> new LoginRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant(),
                current != null && current == rs.getLong(1)));
    }

    /** 내 로그인 하나 끊기 (남의 것이면 404) */
    public void revokeOwn(long userId, long sessionRowId) {
        int n = jdbc.update("""
                update login_sessions set revoked_at = :now where id = :id and user_id = :u and revoked_at is null
                """, Map.of("now", now(), "id", sessionRowId, "u", userId));
        if (n == 0) throw ApiException.notFound("로그인 기록을 찾을 수 없습니다.");
    }

    // ------------------------------------------------------------ 다중 계정

    public List<UserSummary> summaries(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        Map<Long, UserSummary> by = new java.util.HashMap<>();
        jdbc.query("select %s from users u where u.id in (:ids) and u.deleted_at is null".formatted(UserRows.cols("u")),
                Map.of("ids", ids), rs -> {
                    UserSummary s = UserRows.read(rs, "u");
                    by.put(s.id(), s);
                });
        return ids.stream().map(by::get).filter(java.util.Objects::nonNull).toList();
    }
}
