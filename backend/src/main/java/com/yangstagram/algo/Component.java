package com.yangstagram.algo;

/**
 * 추천 점수의 구성 요소 (기획서 §9.2). 점수 = Σ 부호 × 가중치 × 값.
 * SQL 식은 게시물 p, 작성자 a, 그리고 {@link Ranker}가 붙이는 보조 조인(aff, sn, cr, sh)을 쓴다.
 *
 * <p>파라미터: :me(보는 사람), :now(가상 시계), :since(이 목록을 처음 연 시각, 이미 본 횟수는 이보다 앞선 노출만 센다),
 * :topics(보는 사람의 관심 주제).
 */
public enum Component {

    AFFINITY("관계 친밀도", "최근 30일 동안 이 작성자에게 남긴 좋아요·댓글·DM·프로필 방문 수 (로그)", 1,
            "ln(1 + coalesce(aff.n, 0))"),
    POPULARITY("인기도", "좋아요 + 2×댓글 + 3×리포스트 + 3×저장 (로그)", 1,
            "ln(1 + p.like_count + 2 * p.comment_count + 3 * p.repost_count + 3 * p.save_count)"),
    INTEREST("관심 주제 일치", "게시물 주제가 내 관심 주제(회원님의 알고리즘)에 있으면 1", 1,
            "case when p.topic in (:topics) then 1 else 0 end"),
    RECENCY("최신성", "exp(−경과 시간 / 72시간): 막 올린 글 1, 3일 지나면 0.37", 1,
            "exp(-greatest(extract(epoch from (:now - p.created_at)), 0) / 259200.0)"),
    SEEN("이미 본 횟수", "이 목록을 열기 전에 노출된 횟수 + 게시물을 연 적 있으면 1 (최대 5, 감점)", -1,
            "least(coalesce(sn.n, 0), 5)"),
    COMPLETION("시청 완료율", "다른 사람들이 이 릴스를 끝까지 본 비율의 평균 (기록 없으면 0.3)", 1,
            "coalesce(cr.r, 0.3)"),
    SHARES("공유", "리포스트 + DM 공유 수 (로그)", 1,
            "ln(1 + p.repost_count + coalesce(sh.n, 0))"),
    REEL("릴스 가산", "릴스면 1 (탐색에서 영상을 조금 더 보여 준다)", 1,
            "case when p.type = 'REEL' then 1 else 0 end"),
    EXPLORATION("사람별 흔들기", "사람·게시물마다 다른 고정 난수 0~1 (같은 결과만 보이지 않게)", 1,
            "(abs(hashtext(p.id::text || ':' || :me::text)) % 100) / 100.0");

    private final String label;
    private final String description;
    private final int sign;
    private final String sql;

    Component(String label, String description, int sign, String sql) {
        this.label = label;
        this.description = description;
        this.sign = sign;
        this.sql = sql;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** 1이면 더하고 −1이면 뺀다 */
    public int sign() {
        return sign;
    }

    String sql() {
        return sql;
    }

    String column() {
        return "c_" + name().toLowerCase();
    }
}
