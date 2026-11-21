package com.yangstagram.seed;

/** 가짜 데이터 규모 (기획서 §8.1). */
public enum SeedSize {
    SMALL(50, 1),
    MEDIUM(200, 1),
    LARGE(500, 2.5);

    private final int users;
    private final double density;

    SeedSize(int users, double density) {
        this.users = users;
        this.density = density;
    }

    public int users() {
        return users;
    }

    /**
     * 관계 밀도: 사람이 많아지면 한 사람이 팔로우하는 수·릴스·대화도 늘린다 (large만 1보다 큼).
     * 1이면 small·medium과 똑같은 데이터가 나온다 (같은 시드값이면 재현).
     */
    public double density() {
        return density;
    }

    /** n에 밀도를 곱한 값 (밀도 1이면 그대로) */
    public int scale(int n) {
        return density == 1 ? n : (int) Math.round(n * density);
    }
}
