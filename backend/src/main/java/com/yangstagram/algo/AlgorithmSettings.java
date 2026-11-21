package com.yangstagram.algo;

import com.yangstagram.algo.Ranker.Scored;
import com.yangstagram.common.error.ApiException;
import com.yangstagram.lab.LabSettings;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 영역별 가중치 (Lab 알고리즘 실험실에서 바꾼 값). 저장한 값이 없으면 {@link Surface#defaults()}.
 */
@Service
public class AlgorithmSettings {

    private static final double MAX_WEIGHT = 10;

    private final LabSettings settings;

    public AlgorithmSettings(LabSettings settings) {
        this.settings = settings;
    }

    /** 점수 내역의 한 줄 */
    public record Part(String key, String label, String description, double value, double weight,
                       double contribution) {
    }

    /**
     * 게시물이 이 자리에 온 이유.
     *
     * @param source   FOLLOWING(팔로우), SUGGESTED(추천), REPOST(팔로우하는 사람의 리포스트)
     * @param caughtUp 홈 피드에서 팔로우 게시물을 다 본 뒤 처음 나오는 추천이면 true ("모두 확인했습니다" 표시)
     */
    public record Ranking(String surface, String source, double score, int rank, List<Part> parts, boolean caughtUp) {
    }

    public Map<Component, Double> weights(Surface surface) {
        @SuppressWarnings("unchecked")
        Map<String, Object> saved = settings.get(key(surface), Map.class).orElse(Map.of());
        Map<Component, Double> result = new EnumMap<>(surface.defaults());
        saved.forEach((k, v) -> {
            try {
                Component c = Component.valueOf(k);
                if (result.containsKey(c) && v instanceof Number n) result.put(c, n.doubleValue());
            } catch (IllegalArgumentException ignored) {
                // 없어진 구성 요소는 무시
            }
        });
        return result;
    }

    /** 요청으로 받은 가중치를 검사해 이 영역에 맞는 맵으로 (빠진 것은 현재 값) */
    public Map<Component, Double> validate(Surface surface, Map<String, Double> input) {
        Map<Component, Double> result = new EnumMap<>(weights(surface));
        if (input == null) return result;
        input.forEach((k, v) -> {
            Component c;
            try {
                c = Component.valueOf(k);
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_COMPONENT", "알 수 없는 구성 요소: " + k);
            }
            if (!result.containsKey(c)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_COMPONENT",
                        surface.label() + "에서 쓰지 않는 구성 요소: " + c.label());
            }
            if (v == null || v.isNaN() || v < 0 || v > MAX_WEIGHT) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_WEIGHT", "가중치는 0~" + (int) MAX_WEIGHT + " 사이입니다.");
            }
            result.put(c, v);
        });
        return result;
    }

    public void save(Surface surface, Map<Component, Double> weights) {
        Map<String, Double> raw = new HashMap<>();
        weights.forEach((c, w) -> raw.put(c.name(), w));
        settings.put(key(surface), raw);
    }

    public void reset(Surface surface) {
        settings.remove(key(surface));
    }

    public static Ranking ranking(Surface surface, String source, Scored s, Map<Component, Double> weights, int rank,
                                  boolean caughtUp) {
        List<Part> parts = new ArrayList<>();
        s.values().forEach((c, v) -> {
            double w = weights.get(c);
            parts.add(new Part(c.name(), c.label(), c.description(), round(v), w, round(c.sign() * w * v)));
        });
        return new Ranking(surface.name(), source, round(s.score()), rank, parts, caughtUp);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static String key(Surface surface) {
        return "algo." + surface.name();
    }
}
