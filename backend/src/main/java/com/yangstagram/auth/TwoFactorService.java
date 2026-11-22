package com.yangstagram.auth;

import com.yangstagram.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2단계 인증 (기획서 §5 P3, TOTP). 켜기: 비밀 키 발급(QR) → 앱의 코드로 확인 → 백업 코드 10개(한 번씩만).
 * 로그인할 때 비밀번호 다음에 인증 앱 코드나 백업 코드를 묻는다. Lab 계정 전환은 이 단계를 건너뛴다.
 */
@Service
public class TwoFactorService {

    public static final String ISSUER = "Yangstagram";
    private static final int BACKUP_CODES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NamedParameterJdbcTemplate jdbc;

    public TwoFactorService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Status(boolean enabled, int backupCodesLeft) {
    }

    /** @param otpauthUri 인증 앱에 등록할 주소 (화면이 QR 코드로 그린다) */
    public record Setup(String secret, String otpauthUri) {
    }

    public Status status(long me) {
        Boolean enabled = jdbc.queryForObject("select totp_enabled from users where id = :id", Map.of("id", me),
                Boolean.class);
        Integer left = jdbc.queryForObject("select count(*) from backup_codes where user_id = :id and used_at is null",
                Map.of("id", me), Integer.class);
        return new Status(Boolean.TRUE.equals(enabled), left == null ? 0 : left);
    }

    public boolean enabled(long userId) {
        return status(userId).enabled();
    }

    /** 새 비밀 키를 만든다 (아직 켜지지 않음, 코드를 확인해야 켜진다) */
    @Transactional
    public Setup setup(long me) {
        if (enabled(me)) throw ApiException.badRequest("ALREADY_ENABLED", "이미 2단계 인증이 켜져 있습니다.");
        String username = jdbc.queryForObject("select username from users where id = :id", Map.of("id", me), String.class);
        String secret = Totp.newSecret();
        jdbc.update("update users set totp_secret = :s where id = :id", Map.of("s", secret, "id", me));
        String uri = Totp.uri(ISSUER, username, secret);
        return new Setup(secret, uri);
    }

    /** 앱에 뜬 코드로 확인하고 켠다. 백업 코드를 한 번만 돌려준다 */
    @Transactional
    public List<String> enable(long me, String code) {
        String secret = secret(me);
        if (secret == null) throw ApiException.badRequest("NO_SETUP", "먼저 설정을 시작해 주세요.");
        if (!Totp.verify(secret, code, now())) {
            throw ApiException.badRequest("BAD_CODE", "코드가 맞지 않습니다. 인증 앱의 6자리 숫자를 다시 확인해 주세요.");
        }
        jdbc.update("update users set totp_enabled = true where id = :id", Map.of("id", me));
        return newBackupCodes(me);
    }

    @Transactional
    public void disable(long me, String code) {
        if (!enabled(me)) return;
        if (!verify(me, code)) throw ApiException.badRequest("BAD_CODE", "코드가 맞지 않습니다.");
        jdbc.update("update users set totp_enabled = false, totp_secret = null where id = :id", Map.of("id", me));
        jdbc.update("delete from backup_codes where user_id = :id", Map.of("id", me));
    }

    @Transactional
    public List<String> regenerateBackupCodes(long me, String code) {
        if (!enabled(me)) throw ApiException.badRequest("NOT_ENABLED", "2단계 인증이 꺼져 있습니다.");
        if (!verify(me, code)) throw ApiException.badRequest("BAD_CODE", "코드가 맞지 않습니다.");
        return newBackupCodes(me);
    }

    /** 인증 앱 코드(6자리) 또는 백업 코드(한 번 쓰면 사라짐) */
    @Transactional
    public boolean verify(long userId, String code) {
        if (code == null) return false;
        String c = code.replace(" ", "").replace("-", "").strip().toLowerCase();
        if (c.matches("\\d{6}")) return Totp.verify(secret(userId), c, now());
        int used = jdbc.update("""
                update backup_codes set used_at = :now where user_id = :id and code_hash = :h and used_at is null
                """, Map.of("now", Timestamp.from(Instant.now()), "id", userId, "h", hash(c)));
        return used > 0;
    }

    /** Lab: 지금 코드 (휴대폰 없이 연구할 때) */
    public String currentCode(long userId) {
        String secret = secret(userId);
        if (secret == null) throw ApiException.badRequest("NO_SECRET", "이 계정은 2단계 인증을 설정하지 않았습니다.");
        return Totp.code(secret, now());
    }

    private String secret(long userId) {
        return jdbc.queryForList("select totp_secret from users where id = :id", Map.of("id", userId), String.class)
                .stream().findFirst().orElse(null);
    }

    private List<String> newBackupCodes(long me) {
        jdbc.update("delete from backup_codes where user_id = :id", Map.of("id", me));
        List<String> codes = new ArrayList<>();
        String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
        for (int i = 0; i < BACKUP_CODES; i++) {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < 8; k++) sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
            String code = sb.toString();
            codes.add(code.substring(0, 4) + "-" + code.substring(4));
            jdbc.update("insert into backup_codes (user_id, code_hash) values (:id, :h)",
                    Map.of("id", me, "h", hash(code)));
        }
        return codes;
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    private static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
