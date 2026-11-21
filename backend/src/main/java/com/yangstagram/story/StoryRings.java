package com.yangstagram.story;

import com.yangstagram.common.time.LabClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 프로필 사진 둘레의 스토리 링 상태 (분석 문서 §8: 안 본 스토리 = 그라데이션, 다 봄 = 회색).
 * 볼 수 없는 사람(비공개 계정이고 팔로우하지 않음)의 스토리는 없는 것으로 친다.
 */
@Component
public class StoryRings {

    public enum Ring { NONE, UNSEEN, SEEN }

    /** 보는 사람이 볼 수 있는 활성 스토리 조건 (s: stories, a: 작성자 users) */
    public static final String VISIBLE_ACTIVE = """
            s.deleted_at is null and s.expires_at > :now and s.created_at <= :now
            and (a.id = :me or not a.is_private or exists (
                 select 1 from follows vf where vf.follower_id = :me and vf.following_id = a.id
                 and vf.status = 'ACCEPTED'))
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;

    public StoryRings(NamedParameterJdbcTemplate jdbc, LabClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Map<Long, Ring> rings(long viewerId, Collection<Long> authorIds) {
        Map<Long, Ring> result = new HashMap<>();
        authorIds.forEach(id -> result.put(id, Ring.NONE));
        if (authorIds.isEmpty()) return result;
        jdbc.query("""
                select s.author_id, bool_and(v.story_id is not null) as all_seen
                from stories s
                join users a on a.id = s.author_id
                left join story_views v on v.story_id = s.id and v.viewer_id = :me
                where s.author_id in (:ids) and %s
                group by s.author_id
                """.formatted(VISIBLE_ACTIVE),
                new MapSqlParameterSource("ids", authorIds).addValue("me", viewerId)
                        .addValue("now", Timestamp.from(Instant.now(clock))),
                rs -> {
                    result.put(rs.getLong(1), rs.getBoolean(2) ? Ring.SEEN : Ring.UNSEEN);
                });
        return result;
    }

    public Ring ring(long viewerId, long authorId) {
        return rings(viewerId, java.util.List.of(authorId)).get(authorId);
    }
}
