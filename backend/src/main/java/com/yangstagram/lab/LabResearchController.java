package com.yangstagram.lab;

import com.yangstagram.algo.AlgorithmSettings;
import com.yangstagram.algo.Component;
import com.yangstagram.algo.Recommendations;
import com.yangstagram.algo.Recommendations.PreviewItem;
import com.yangstagram.algo.Surface;
import com.yangstagram.common.error.ApiException;
import com.yangstagram.event.EventService;
import com.yangstagram.lab.FeatureFlags.Flags;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lab 연구 도구: 알고리즘 실험실(L06), 이벤트 로그(L08), 기능 플래그(L09).
 */
@RestController
@RequestMapping("/api/lab")
@ConditionalOnProperty(prefix = "yangstagram.lab", name = "enabled", havingValue = "true")
public class LabResearchController {

    private static final int CSV_MAX = 50_000;

    private final AlgorithmSettings algorithm;
    private final Recommendations recommendations;
    private final FeatureFlags flags;
    private final NamedParameterJdbcTemplate jdbc;
    private final com.yangstagram.common.time.LabClock clock;

    public LabResearchController(AlgorithmSettings algorithm, Recommendations recommendations, FeatureFlags flags,
                                 NamedParameterJdbcTemplate jdbc, com.yangstagram.common.time.LabClock clock) {
        this.algorithm = algorithm;
        this.recommendations = recommendations;
        this.flags = flags;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------ 알고리즘 실험실

    public record ComponentInfo(String key, String label, String description, int sign, double weight,
                                double defaultWeight) {
    }

    public record SurfaceInfo(String key, String label, List<ComponentInfo> components) {
    }

    @GetMapping("/algorithm")
    public List<SurfaceInfo> algorithm() {
        List<SurfaceInfo> result = new ArrayList<>();
        for (Surface s : Surface.values()) {
            Map<Component, Double> w = algorithm.weights(s);
            List<ComponentInfo> comps = new ArrayList<>();
            s.defaults().forEach((c, d) -> comps.add(new ComponentInfo(c.name(), c.label(), c.description(), c.sign(),
                    w.get(c), d)));
            result.add(new SurfaceInfo(s.name(), s.label(), comps));
        }
        return result;
    }

    @PutMapping("/algorithm/{surface}")
    public List<SurfaceInfo> saveWeights(@PathVariable String surface, @RequestBody Map<String, Double> weights) {
        Surface s = surface(surface);
        algorithm.save(s, algorithm.validate(s, weights));
        return algorithm();
    }

    @DeleteMapping("/algorithm/{surface}")
    public List<SurfaceInfo> resetWeights(@PathVariable String surface) {
        algorithm.reset(surface(surface));
        return algorithm();
    }

    /** A/B 비교: 같은 사용자에게 두 가중치를 적용한 결과를 나란히 */
    public record PreviewRequest(String surface, String username, Map<String, Double> a, Map<String, Double> b,
                                 Integer limit) {
    }

    public record PreviewResponse(String username, List<PreviewItem> a, List<PreviewItem> b) {
    }

    @PostMapping("/algorithm/preview")
    public PreviewResponse preview(@RequestBody PreviewRequest req) {
        Surface s = surface(req.surface());
        Long userId = jdbc.queryForList("select id from users where username = :u and deleted_at is null",
                Map.of("u", String.valueOf(req.username())), Long.class).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));
        int limit = req.limit() == null ? 30 : Math.clamp(req.limit(), 1, 60);
        List<PreviewItem> a = recommendations.preview(s, userId, algorithm.validate(s, req.a()), limit);
        List<PreviewItem> b = req.b() == null ? List.of()
                : recommendations.preview(s, userId, algorithm.validate(s, req.b()), limit);
        return new PreviewResponse(req.username(), a, b);
    }

    private static Surface surface(String key) {
        try {
            return Surface.valueOf(String.valueOf(key).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_SURFACE", "영역은 FEED, EXPLORE, REELS 중 하나입니다.");
        }
    }

    // ------------------------------------------------------------ 기능 플래그

    @GetMapping("/flags")
    public Flags getFlags() {
        return flags.get();
    }

    @PutMapping("/flags")
    public Flags setFlags(@RequestBody Flags body) {
        return flags.set(body);
    }

    @DeleteMapping("/flags")
    public Flags resetFlags() {
        return flags.reset();
    }

    // ------------------------------------------------------------ 이벤트 로그

    public record EventRow(long id, String type, String username, String shortcode, String targetUsername,
                           String surface, Integer valueMs, Instant createdAt) {
    }

    public record EventPage(List<EventRow> items, Long nextBefore) {
    }

    public record TypeCount(String type, long last24h, long total) {
    }

    @GetMapping("/events")
    public EventPage events(@RequestParam(required = false) String type,
                            @RequestParam(required = false) String username,
                            @RequestParam(required = false) String shortcode,
                            @RequestParam(required = false) Long before,
                            @RequestParam(defaultValue = "50") int limit) {
        int size = Math.clamp(limit, 1, 200);
        List<EventRow> rows = query(type, username, shortcode, before, size + 1);
        Long next = rows.size() > size ? rows.get(size - 1).id() : null;
        return new EventPage(rows.size() > size ? rows.subList(0, size) : rows, next);
    }

    @GetMapping("/events/summary")
    public List<TypeCount> eventSummary() {
        Map<String, Object> p = Map.of("since", Timestamp.from(Instant.now(clock).minus(Duration.ofDays(1))));
        Map<String, long[]> counts = new java.util.LinkedHashMap<>();
        EventService.ALL_TYPES.forEach(t -> counts.put(t, new long[2]));
        jdbc.query("""
                select type, count(*) filter (where created_at > :since), count(*) from events group by type
                """, p, rs -> {
            counts.computeIfAbsent(rs.getString(1), k -> new long[2]);
            counts.get(rs.getString(1))[0] = rs.getLong(2);
            counts.get(rs.getString(1))[1] = rs.getLong(3);
        });
        List<TypeCount> out = new ArrayList<>();
        counts.forEach((t, c) -> out.add(new TypeCount(t, c[0], c[1])));
        return out;
    }

    /** 같은 조건으로 CSV 내려받기 (최대 5만 건, 최신순) */
    @GetMapping("/events.csv")
    public void eventsCsv(@RequestParam(required = false) String type,
                          @RequestParam(required = false) String username,
                          @RequestParam(required = false) String shortcode,
                          HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"yangstagram-events.csv\"");
        PrintWriter out = new PrintWriter(new java.io.OutputStreamWriter(response.getOutputStream(),
                StandardCharsets.UTF_8));
        out.print('﻿'); // 엑셀에서 한글이 깨지지 않게
        out.println("id,created_at,type,user,post,target_user,surface,value_ms");
        for (EventRow r : query(type, username, shortcode, null, CSV_MAX)) {
            out.printf("%d,%s,%s,%s,%s,%s,%s,%s%n", r.id(), r.createdAt(), r.type(), r.username(),
                    r.shortcode() == null ? "" : r.shortcode(), r.targetUsername() == null ? "" : r.targetUsername(),
                    r.surface() == null ? "" : r.surface(), r.valueMs() == null ? "" : r.valueMs());
        }
        out.flush();
    }

    private List<EventRow> query(String type, String username, String shortcode, Long before, int limit) {
        StringBuilder where = new StringBuilder("true");
        MapSqlParameterSource p = new MapSqlParameterSource("limit", limit);
        if (type != null && !type.isBlank()) {
            where.append(" and e.type = :type");
            p.addValue("type", type.trim().toUpperCase());
        }
        if (username != null && !username.isBlank()) {
            where.append(" and (u.username = :username or tu.username = :username)");
            p.addValue("username", username.trim().replaceFirst("^@", ""));
        }
        if (shortcode != null && !shortcode.isBlank()) {
            where.append(" and po.shortcode = :code");
            p.addValue("code", shortcode.trim());
        }
        if (before != null) {
            where.append(" and e.id < :before");
            p.addValue("before", before);
        }
        return jdbc.query("""
                select e.id, e.type, u.username, po.shortcode, tu.username as target, e.surface, e.value_ms,
                       e.created_at
                from events e
                join users u on u.id = e.user_id
                left join posts po on po.id = e.post_id
                left join users tu on tu.id = e.target_user_id
                where %s
                order by e.id desc
                limit :limit
                """.formatted(where), p, (rs, i) -> new EventRow(rs.getLong("id"), rs.getString("type"),
                rs.getString("username"), rs.getString("shortcode"), rs.getString("target"), rs.getString("surface"),
                (Integer) rs.getObject("value_ms"), rs.getTimestamp("created_at").toInstant()));
    }
}
