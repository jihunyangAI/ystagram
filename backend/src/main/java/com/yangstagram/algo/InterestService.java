package com.yangstagram.algo;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.seed.Topic;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원님의 알고리즘 (분석 문서 §21): 추천에 쓰는 관심 주제.
 * 관심 주제 = (프로필 관심사 ∪ 최근 활동에서 많이 본 주제 ∪ 직접 추가) − 직접 뺀 것.
 */
@Service
public class InterestService {

    /** 활동에서 관심 주제로 볼 최소 건수(좋아요·저장·댓글·릴스 시청) */
    private static final int ACTIVITY_MIN = 3;
    private static final int ACTIVITY_TOP = 3;

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;

    public InterestService(NamedParameterJdbcTemplate jdbc, LabClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public enum Source { PROFILE, ACTIVITY, ADDED }

    public record TopicItem(String topic, String label, String emoji, List<Source> sources, boolean removed,
                            int activityCount) {
    }

    public record AlgorithmView(List<TopicItem> topics, List<TopicItem> others) {
    }

    /** 추천 점수의 "관심 주제 일치"에 쓰는 주제 목록 */
    public List<String> effective(long userId) {
        return view(userId).topics().stream().filter(t -> !t.removed()).map(TopicItem::topic).toList();
    }

    public AlgorithmView view(long userId) {
        String profile = jdbc.queryForObject("select coalesce(interests, '') from users where id = :id",
                Map.of("id", userId), String.class);
        Set<String> fromProfile = new LinkedHashSet<>(Arrays.stream(profile.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());

        Map<String, Integer> activity = new java.util.LinkedHashMap<>();
        jdbc.query("""
                select p.topic, count(*) from (
                  select post_id from likes where user_id = :me and created_at > :since
                  union all select post_id from saved_posts where user_id = :me and created_at > :since
                  union all select post_id from comments where author_id = :me and created_at > :since
                  union all select post_id from events where user_id = :me and type = 'VIEW_TIME'
                            and value_ms >= 5000 and created_at > :since
                ) x join posts p on p.id = x.post_id
                where p.topic is not null
                group by p.topic having count(*) >= :min
                order by count(*) desc limit :top
                """, new MapSqlParameterSource("me", userId).addValue("min", ACTIVITY_MIN).addValue("top", ACTIVITY_TOP)
                        .addValue("since", Timestamp.from(Instant.now(clock).minus(Duration.ofDays(30)))),
                rs -> {
                    activity.put(rs.getString(1), rs.getInt(2));
                });

        Map<String, String> prefs = new java.util.HashMap<>();
        jdbc.query("select topic, mode from user_topic_prefs where user_id = :me", Map.of("me", userId),
                rs -> {
                    prefs.put(rs.getString(1), rs.getString(2));
                });

        List<TopicItem> topics = new ArrayList<>();
        List<TopicItem> others = new ArrayList<>();
        for (Topic t : Topic.values()) {
            String key = t.name();
            List<Source> sources = new ArrayList<>();
            if (fromProfile.contains(key)) sources.add(Source.PROFILE);
            if (activity.containsKey(key)) sources.add(Source.ACTIVITY);
            if ("ADD".equals(prefs.get(key))) sources.add(Source.ADDED);
            boolean removed = "REMOVE".equals(prefs.get(key));
            TopicItem item = new TopicItem(key, t.label(), t.emoji(), sources, removed, activity.getOrDefault(key, 0));
            (sources.isEmpty() ? others : topics).add(item);
        }
        return new AlgorithmView(topics, others);
    }

    /**
     * @param mode ADD(관심 주제로 추가), REMOVE(추천에서 빼기), RESET(직접 설정 지우기)
     */
    @Transactional
    public AlgorithmView set(long userId, String topic, String mode) {
        Topic t;
        try {
            t = Topic.valueOf(topic);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_TOPIC", "알 수 없는 주제입니다.");
        }
        if (!EnumSet.of(Mode.ADD, Mode.REMOVE, Mode.RESET).contains(parse(mode))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_MODE", "mode는 ADD, REMOVE, RESET 중 하나입니다.");
        }
        jdbc.update("delete from user_topic_prefs where user_id = :me and topic = :t",
                Map.of("me", userId, "t", t.name()));
        if (!"RESET".equals(mode)) {
            jdbc.update("insert into user_topic_prefs (user_id, topic, mode, created_at) values (:me, :t, :m, :now)",
                    Map.of("me", userId, "t", t.name(), "m", mode, "now", Timestamp.from(Instant.now(clock))));
        }
        return view(userId);
    }

    private enum Mode { ADD, REMOVE, RESET, INVALID }

    private static Mode parse(String mode) {
        try {
            return Mode.valueOf(String.valueOf(mode));
        } catch (IllegalArgumentException e) {
            return Mode.INVALID;
        }
    }
}
