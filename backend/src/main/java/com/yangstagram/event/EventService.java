package com.yangstagram.event;

import com.yangstagram.common.time.LabClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;

/**
 * 행동 이벤트 기록 (기획서 §9.3). 인사이트·추천·Lab 이벤트 로그에 공통으로 쓴다.
 */
@Service
public class EventService {

    /** 화면이 보낼 수 있는 종류. 좋아요·댓글·저장·공유·팔로우는 서버가 직접 남긴다 */
    public static final Set<String> CLIENT_TYPES = Set.of("IMPRESSION", "VIEW_TIME", "PROFILE_VISIT", "SKIP",
            "NOT_INTERESTED", "AD_CLICK", "AD_HIDE");
    /** AD: 광고 자리에서 보인 노출 (광고 성과 집계용) */
    public static final Set<String> SURFACES = Set.of("HOME", "EXPLORE", "REELS", "PROFILE", "POST", "SEARCH", "SIM",
            "AD");
    public static final List<String> ALL_TYPES = List.of("IMPRESSION", "VIEW_TIME", "PROFILE_VISIT", "SKIP",
            "NOT_INTERESTED", "LIKE", "COMMENT", "SAVE", "SHARE", "FOLLOW", "UNFOLLOW", "AD_CLICK", "AD_HIDE");

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;

    public EventService(NamedParameterJdbcTemplate jdbc, LabClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** 화면에서 받은 이벤트 한 건 (게시물은 shortcode, 사용자는 username) */
    public record ClientEvent(String type, String shortcode, String username, String surface, Integer valueMs) {
    }

    public void record(long userId, String type, Long postId, Long targetUserId, String surface) {
        jdbc.update("""
                insert into events (user_id, type, post_id, target_user_id, surface, created_at)
                values (:u, :t, :p, :tu, :s, :now)
                """, new MapSqlParameterSource("u", userId).addValue("t", type).addValue("p", postId)
                .addValue("tu", targetUserId).addValue("s", surface)
                .addValue("now", Timestamp.from(Instant.now(clock))));
    }

    /** 한 번에 최대 100건. 모르는 게시물·사용자·종류는 조용히 버린다 (로그 수집이라 오류로 막지 않는다) */
    public int recordClient(long userId, List<ClientEvent> events) {
        if (events == null || events.isEmpty()) return 0;
        List<ClientEvent> valid = events.stream().limit(100)
                .filter(e -> e != null && CLIENT_TYPES.contains(e.type()))
                .toList();
        if (valid.isEmpty()) return 0;

        Map<String, Long> posts = ids("select shortcode, id from posts where shortcode in (:keys)",
                valid.stream().map(ClientEvent::shortcode).filter(s -> s != null).distinct().toList());
        Map<String, Long> users = ids("select username, id from users where username in (:keys)",
                valid.stream().map(ClientEvent::username).filter(s -> s != null).distinct().toList());

        Timestamp now = Timestamp.from(Instant.now(clock));
        List<SqlParameterSource> rows = new ArrayList<>();
        for (ClientEvent e : valid) {
            Long post = e.shortcode() == null ? null : posts.get(e.shortcode());
            Long target = e.username() == null ? null : users.get(e.username());
            if (post == null && target == null) continue;
            if (e.shortcode() != null && post == null) continue;
            Integer value = e.valueMs() == null ? null : Math.clamp(e.valueMs(), 0, 3_600_000);
            String surface = e.surface() != null && SURFACES.contains(e.surface()) ? e.surface() : null;
            rows.add(new MapSqlParameterSource("u", userId).addValue("t", e.type()).addValue("p", post)
                    .addValue("tu", target).addValue("s", surface).addValue("v", value).addValue("now", now));
        }
        if (rows.isEmpty()) return 0;
        jdbc.batchUpdate("""
                insert into events (user_id, type, post_id, target_user_id, surface, value_ms, created_at)
                values (:u, :t, :p, :tu, :s, :v, :now)
                """, rows.toArray(SqlParameterSource[]::new));
        return rows.size();
    }

    private Map<String, Long> ids(String sql, List<String> keys) {
        Map<String, Long> result = new HashMap<>();
        if (keys.isEmpty()) return result;
        jdbc.query(sql, Map.of("keys", keys), rs -> {
            result.put(rs.getString(1), rs.getLong(2));
        });
        return result;
    }
}
