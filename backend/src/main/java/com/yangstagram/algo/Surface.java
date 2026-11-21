package com.yangstagram.algo;

import static com.yangstagram.algo.Component.AFFINITY;
import static com.yangstagram.algo.Component.COMPLETION;
import static com.yangstagram.algo.Component.EXPLORATION;
import static com.yangstagram.algo.Component.INTEREST;
import static com.yangstagram.algo.Component.POPULARITY;
import static com.yangstagram.algo.Component.RECENCY;
import static com.yangstagram.algo.Component.REEL;
import static com.yangstagram.algo.Component.SEEN;
import static com.yangstagram.algo.Component.SHARES;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 영역별 점수식 (기획서 §9.2 표). 영역마다 쓰는 구성 요소와 기본 가중치가 다르다.
 */
public enum Surface {

    /** 홈 피드: 관계 친밀도·최신성 비중이 크다 */
    FEED("홈 피드", Map.of(AFFINITY, 1.5, POPULARITY, 0.4, INTEREST, 0.8, RECENCY, 3.0, SEEN, 1.0, EXPLORATION, 0.2)),
    /** 탐색: 인기도·관심 일치 비중이 크고 팔로우하지 않는 사람 위주 */
    EXPLORE("탐색", Map.of(POPULARITY, 1.0, INTEREST, 0.8, RECENCY, 0.5, SEEN, 3.0, REEL, 0.3, EXPLORATION, 1.0)),
    /** 릴스: 시청 완료율·공유 비중이 크다 */
    REELS("릴스", Map.of(COMPLETION, 2.0, SHARES, 0.8, POPULARITY, 0.6, INTEREST, 0.8, RECENCY, 0.3, SEEN, 3.0,
            EXPLORATION, 0.5));

    private final String label;
    private final Map<Component, Double> defaults;

    Surface(String label, Map<Component, Double> defaults) {
        this.label = label;
        this.defaults = Collections.unmodifiableMap(new EnumMap<>(defaults));
    }

    public String label() {
        return label;
    }

    /** 이 영역이 쓰는 구성 요소와 기본 가중치 (순서는 Component 선언 순) */
    public Map<Component, Double> defaults() {
        return defaults;
    }
}
