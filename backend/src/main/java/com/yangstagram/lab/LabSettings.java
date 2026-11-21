package com.yangstagram.lab;

import com.yangstagram.common.time.LabClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lab 설정 저장소 (lab_settings 테이블, 값은 JSON). 자주 읽으므로 메모리에 담아 둔다.
 * 알고리즘 가중치(algo.FEED 등)와 기능 플래그(flags)가 여기에 들어간다.
 */
@Component
public class LabSettings {

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;
    private final LabClock clock;
    private final Map<String, Optional<String>> cache = new ConcurrentHashMap<>();

    public LabSettings(NamedParameterJdbcTemplate jdbc, JsonMapper json, LabClock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        Optional<String> raw = cache.computeIfAbsent(key, k -> jdbc.queryForList(
                "select value::text from lab_settings where key = :k", Map.of("k", k), String.class)
                .stream().findFirst());
        return raw.map(v -> json.readValue(v, type));
    }

    public void put(String key, Object value) {
        jdbc.update("""
                insert into lab_settings (key, value, updated_at) values (:k, cast(:v as jsonb), :now)
                on conflict (key) do update set value = excluded.value, updated_at = excluded.updated_at
                """, Map.of("k", key, "v", json.writeValueAsString(value), "now", Timestamp.from(Instant.now(clock))));
        cache.remove(key);
    }

    public void remove(String key) {
        jdbc.update("delete from lab_settings where key = :k", Map.of("k", key));
        cache.remove(key);
    }

    /** 시드 재생성 등으로 테이블이 비워졌을 때 */
    public void clearCache() {
        cache.clear();
    }
}
