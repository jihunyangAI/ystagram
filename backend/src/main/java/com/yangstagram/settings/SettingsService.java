package com.yangstagram.settings;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.notification.NotificationType;
import com.yangstagram.user.Teens;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 안전·알림 설정 (기획서 §4: 숨겨진 단어·댓글 허용 범위·알림 설정·민감 콘텐츠·사용 시간 알림·수면 모드).
 * 행이 없으면 기본값.
 */
@Service
public class SettingsService {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 기본 숨김 목록 (연구용, 짧게) */
    public static final List<String> OFFENSIVE = List.of("바보", "멍청", "꺼져", "닥쳐", "시발", "ㅅㅂ", "병신", "ㅂㅅ",
            "idiot", "stupid", "loser", "shut up");

    public static final Set<String> AUDIENCES = Set.of("EVERYONE", "FOLLOWING", "FOLLOWERS", "BOTH", "OFF");
    public static final Set<String> LEVELS = Set.of("LESS", "STANDARD", "MORE");
    /** 알림 묶음 → 알림 종류 */
    public static final Map<String, Set<NotificationType>> GROUPS = Map.of(
            "LIKES", Set.of(NotificationType.LIKE, NotificationType.COMMENT_LIKE, NotificationType.STORY_LIKE),
            "COMMENTS", Set.of(NotificationType.COMMENT, NotificationType.REPLY, NotificationType.MENTION,
                    NotificationType.TAG),
            "FOLLOWS", Set.of(NotificationType.FOLLOW, NotificationType.FOLLOW_REQUEST, NotificationType.FOLLOW_ACCEPTED),
            "SHARING", Set.of(NotificationType.REPOST, NotificationType.REMIX, NotificationType.COLLAB_INVITE),
            "SUPPORT", Set.of(NotificationType.SUBSCRIBE, NotificationType.GIFT));

    public record Settings(boolean hideOffensive, List<String> hiddenWords, String commentAudience, Set<String> notifOff,
                           Instant notifPausedUntil, String sensitiveLevel, boolean sensitiveLocked,
                           Integer dailyLimitMin, boolean sleepOn, int sleepStart, int sleepEnd, boolean sleepingNow) {
    }

    public record Patch(Boolean hideOffensive, List<String> hiddenWords, String commentAudience, List<String> notifOff,
                        Integer pauseMinutes, Boolean resumeNotifications, String sensitiveLevel, Integer dailyLimitMin,
                        Boolean clearDailyLimit, Boolean sleepOn, Integer sleepStart, Integer sleepEnd) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;

    public SettingsService(NamedParameterJdbcTemplate jdbc, LabClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Settings get(long userId) {
        boolean teen = isTeen(userId);
        List<Settings> rows = jdbc.query("select * from user_settings where user_id = :u", Map.of("u", userId),
                (rs, i) -> {
                    Timestamp paused = rs.getTimestamp("notif_paused_until");
                    int start = rs.getInt("sleep_start");
                    int end = rs.getInt("sleep_end");
                    boolean sleepOn = rs.getBoolean("sleep_on");
                    return new Settings(rs.getBoolean("hide_offensive"), words(rs.getString("hidden_words")),
                            rs.getString("comment_audience"), split(rs.getString("notif_off")),
                            paused == null || paused.toInstant().isBefore(Instant.now(clock)) ? null : paused.toInstant(),
                            teen ? "LESS" : rs.getString("sensitive_level"), teen, (Integer) rs.getObject("daily_limit_min"),
                            sleepOn, start, end, sleepOn && inSleep(start, end));
                });
        if (!rows.isEmpty()) return rows.getFirst();
        return new Settings(false, List.of(), "EVERYONE", Set.of(), null, teen ? "LESS" : "STANDARD", teen, null,
                false, 23, 7, false);
    }

    public Settings update(long userId, Patch p) {
        Settings cur = get(userId);
        if (p.commentAudience() != null && !AUDIENCES.contains(p.commentAudience())) {
            throw ApiException.badRequest("BAD_AUDIENCE", "알 수 없는 댓글 허용 범위입니다.");
        }
        if (p.sensitiveLevel() != null && !LEVELS.contains(p.sensitiveLevel())) {
            throw ApiException.badRequest("BAD_LEVEL", "알 수 없는 민감 콘텐츠 단계입니다.");
        }
        if (p.sensitiveLevel() != null && cur.sensitiveLocked() && !"LESS".equals(p.sensitiveLevel())) {
            throw ApiException.forbidden("TEEN_PROTECTED", "10대 계정은 민감한 콘텐츠를 '덜 보기'로만 둘 수 있습니다.");
        }
        if (p.notifOff() != null && !GROUPS.keySet().containsAll(p.notifOff())) {
            throw ApiException.badRequest("BAD_GROUP", "알 수 없는 알림 종류입니다.");
        }
        if (p.dailyLimitMin() != null && (p.dailyLimitMin() < 5 || p.dailyLimitMin() > 24 * 60)) {
            throw ApiException.badRequest("BAD_LIMIT", "사용 시간 알림은 5분에서 24시간 사이로 정해 주세요.");
        }
        for (Integer h : new Integer[] {p.sleepStart(), p.sleepEnd()}) {
            if (h != null && (h < 0 || h > 23)) throw ApiException.badRequest("BAD_HOUR", "시각은 0~23시로 정해 주세요.");
        }
        if (p.hiddenWords() != null && String.join(",", p.hiddenWords()).length() > 1000) {
            throw ApiException.badRequest("TOO_MANY_WORDS", "숨길 단어가 너무 많습니다.");
        }
        Instant paused = Boolean.TRUE.equals(p.resumeNotifications()) ? null
                : p.pauseMinutes() != null ? Instant.now(clock).plusSeconds(Math.max(15, Math.min(p.pauseMinutes(), 8 * 60)) * 60L)
                : cur.notifPausedUntil();
        Integer limit = Boolean.TRUE.equals(p.clearDailyLimit()) ? null
                : p.dailyLimitMin() != null ? p.dailyLimitMin() : cur.dailyLimitMin();
        jdbc.update("""
                insert into user_settings (user_id, hide_offensive, hidden_words, comment_audience, notif_off,
                    notif_paused_until, sensitive_level, daily_limit_min, sleep_on, sleep_start, sleep_end, updated_at)
                values (:u, :ho, :hw, :ca, :no, :np, :sl, :dl, :so, :ss, :se, :now)
                on conflict (user_id) do update set hide_offensive = excluded.hide_offensive,
                    hidden_words = excluded.hidden_words, comment_audience = excluded.comment_audience,
                    notif_off = excluded.notif_off, notif_paused_until = excluded.notif_paused_until,
                    sensitive_level = excluded.sensitive_level, daily_limit_min = excluded.daily_limit_min,
                    sleep_on = excluded.sleep_on, sleep_start = excluded.sleep_start, sleep_end = excluded.sleep_end,
                    updated_at = excluded.updated_at
                """, new MapSqlParameterSource("u", userId)
                .addValue("ho", p.hideOffensive() != null ? p.hideOffensive() : cur.hideOffensive())
                .addValue("hw", String.join(",", p.hiddenWords() != null ? clean(p.hiddenWords()) : cur.hiddenWords()))
                .addValue("ca", p.commentAudience() != null ? p.commentAudience() : cur.commentAudience())
                .addValue("no", String.join(",", p.notifOff() != null ? p.notifOff() : cur.notifOff()))
                .addValue("np", paused == null ? null : Timestamp.from(paused))
                .addValue("sl", p.sensitiveLevel() != null ? p.sensitiveLevel() : cur.sensitiveLevel())
                .addValue("dl", limit)
                .addValue("so", p.sleepOn() != null ? p.sleepOn() : cur.sleepOn())
                .addValue("ss", p.sleepStart() != null ? p.sleepStart() : cur.sleepStart())
                .addValue("se", p.sleepEnd() != null ? p.sleepEnd() : cur.sleepEnd())
                .addValue("now", Timestamp.from(Instant.now(clock))));
        return get(userId);
    }

    // ------------------------------------------------------------ 다른 기능에서 쓰는 판단

    /** 이 댓글을 게시물 주인의 숨김 설정으로 숨겨야 하나 */
    public boolean shouldHide(long ownerId, String body) {
        Settings s = get(ownerId);
        String text = body.toLowerCase(Locale.ROOT);
        if (s.hideOffensive() && OFFENSIVE.stream().anyMatch(text::contains)) return true;
        return s.hiddenWords().stream().anyMatch(w -> !w.isBlank() && text.contains(w.toLowerCase(Locale.ROOT)));
    }

    /** 댓글 허용 범위: viewer가 owner의 게시물에 댓글을 달 수 있나 */
    public boolean canComment(long ownerId, long viewerId) {
        if (ownerId == viewerId) return true;
        String audience = get(ownerId).commentAudience();
        return switch (audience) {
            case "OFF" -> false;
            case "FOLLOWING" -> follows(ownerId, viewerId);
            case "FOLLOWERS" -> follows(viewerId, ownerId);
            case "BOTH" -> follows(ownerId, viewerId) || follows(viewerId, ownerId);
            default -> true;
        };
    }

    public enum Delivery { PUSH, QUIET, DROP }

    /** 알림 설정: 끈 종류는 남기지 않음(DROP), 일시 중지·수면 모드는 기록만 남기고 실시간 알림은 보내지 않음(QUIET) */
    public Delivery delivery(long userId, NotificationType type) {
        Settings s = get(userId);
        for (String g : s.notifOff()) {
            if (GROUPS.getOrDefault(g, Set.of()).contains(type)) return Delivery.DROP;
        }
        if (s.notifPausedUntil() != null || s.sleepingNow()) return Delivery.QUIET;
        return Delivery.PUSH;
    }

    /**
     * 추천에서 민감한 콘텐츠를 거르는 SQL 조각 (posts p, users a 별칭).
     * 덜 보기(10대는 항상): 어디서도 추천 안 함 / 기본: 탐색에서만 뺌 / 더 보기: 거르지 않음
     */
    public String sensitiveFilter(long userId, String surface) {
        String level = get(userId).sensitiveLevel();
        if ("MORE".equals(level)) return "";
        if ("STANDARD".equals(level) && !"EXPLORE".equals(surface)) return "";
        return " and not p.sensitive";
    }

    private boolean follows(long follower, long following) {
        Integer n = jdbc.queryForObject("""
                select count(*) from follows where follower_id = :a and following_id = :b and status = 'ACCEPTED'
                """, Map.of("a", follower, "b", following), Integer.class);
        return n != null && n > 0;
    }

    private boolean isTeen(long userId) {
        List<java.sql.Date> d = jdbc.queryForList("select birth_date from users where id = :u", Map.of("u", userId),
                java.sql.Date.class);
        return !d.isEmpty() && Teens.isTeen(d.getFirst().toLocalDate(), LocalDate.now(clock.withZone(KST)));
    }

    private boolean inSleep(int start, int end) {
        int h = ZonedDateTime.now(clock.withZone(KST)).getHour();
        return start == end ? false : start < end ? h >= start && h < end : h >= start || h < end;
    }

    private static List<String> words(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(",")).map(String::strip).filter(w -> !w.isEmpty()).toList();
    }

    private static Set<String> split(String raw) {
        return raw == null || raw.isBlank() ? Set.of()
                : Arrays.stream(raw.split(",")).map(String::strip).filter(w -> !w.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> clean(List<String> words) {
        return words.stream().map(w -> w.replace(",", " ").strip()).filter(w -> !w.isEmpty() && w.length() <= 40)
                .distinct().limit(50).toList();
    }
}
