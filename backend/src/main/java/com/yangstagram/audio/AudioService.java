package com.yangstagram.audio;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 가짜 음원 (기획서 §4 "음악은 가짜 음원 목록"). 실제 소리 파일은 없고, 화면이 seed·bpm으로 짧은 멜로디를 합성한다.
 * 음악 없이 올린 릴스는 "원본 오디오" 곡이 하나 생겨서 다른 사람이 "이 오디오 사용"으로 쓸 수 있다.
 */
@Service
public class AudioService {

    /** 게시물·스토리에 붙는 곡 정보 */
    public record AudioRef(long id, String title, String artist, boolean original, int bpm, int seed, int durationMs) {
    }

    /** 오디오 페이지 머리 */
    public record AudioPage(AudioRef audio, String genre, String originShortcode, int reelCount) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;

    public AudioService(NamedParameterJdbcTemplate jdbc, LabClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    static final String COLS = "t.id, t.title, t.artist, t.bpm, t.seed, t.duration_ms, t.origin_post_id";

    public static AudioRef read(ResultSet rs, String prefix) throws SQLException {
        long id = rs.getLong(prefix + "id");
        if (rs.wasNull()) return null;
        return new AudioRef(id, rs.getString(prefix + "title"), rs.getString(prefix + "artist"),
                rs.getObject(prefix + "origin_post_id") != null, rs.getInt(prefix + "bpm"), rs.getInt(prefix + "seed"),
                rs.getInt(prefix + "duration_ms"));
    }

    /** 음원 목록 검색 (빈 검색어면 인기순). 원본 오디오도 제목·사용자 이름으로 찾는다 */
    public List<AudioRef> search(String q, int limit) {
        String term = q == null ? "" : q.strip().toLowerCase();
        String like = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.query("""
                select %s, (select count(*) from posts p where p.audio_id = t.id and p.deleted_at is null
                            and p.archived_at is null) as uses
                from audio_tracks t
                where (:term = '' and t.origin_post_id is null)
                   or (:term <> '' and (lower(t.title) like :like escape '\\' or lower(t.artist) like :like escape '\\'))
                order by (t.origin_post_id is null) desc, uses desc, t.id
                limit :limit
                """.formatted(COLS), new MapSqlParameterSource("term", term).addValue("like", like)
                .addValue("limit", limit), (rs, i) -> read(rs, ""));
    }

    public AudioPage page(long id) {
        return jdbc.query("""
                select %s, t.genre, op.shortcode as origin_code,
                       (select count(*) from posts p join users a on a.id = p.author_id where p.audio_id = t.id
                          and p.type = 'REEL' and p.deleted_at is null and p.archived_at is null
                          and not a.is_private and not p.trial) as reels
                from audio_tracks t left join posts op on op.id = t.origin_post_id and op.deleted_at is null
                where t.id = :id
                """.formatted(COLS), Map.of("id", id), (rs, i) -> new AudioPage(read(rs, ""), rs.getString("genre"),
                rs.getString("origin_code"), rs.getInt("reels"))).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("오디오를 찾을 수 없습니다."));
    }

    /** 고른 곡이 실제로 있는지 확인 (없으면 400) */
    public Long require(Long audioId) {
        if (audioId == null) return null;
        Integer n = jdbc.queryForObject("select count(*) from audio_tracks where id = :id", Map.of("id", audioId),
                Integer.class);
        if (n == null || n == 0) throw ApiException.badRequest("BAD_AUDIO", "고른 음악을 찾을 수 없습니다.");
        return audioId;
    }

    /** 음악 없이 올린 릴스의 "원본 오디오" */
    public long createOriginal(long postId, long userId, String username, long durationMs) {
        int seed = (int) (postId * 2654435761L % 100000);
        return jdbc.queryForObject("""
                insert into audio_tracks (title, artist, genre, bpm, seed, duration_ms, origin_post_id, origin_user_id,
                                          created_at)
                values ('원본 오디오', :artist, null, :bpm, :seed, :dur, :post, :user, :now) returning id
                """, new MapSqlParameterSource("artist", username).addValue("bpm", 80 + seed % 60)
                .addValue("seed", seed).addValue("dur", (int) Math.min(durationMs, 60_000)).addValue("post", postId)
                .addValue("user", userId).addValue("now", Timestamp.from(Instant.now(clock))), Long.class);
    }
}
