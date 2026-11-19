package com.yangstagram.seed;

/** 가짜 데이터 규모 (기획서 §8.1). */
public enum SeedSize {
    SMALL(50),
    MEDIUM(200),
    LARGE(500);

    private final int users;

    SeedSize(int users) {
        this.users = users;
    }

    public int users() {
        return users;
    }
}
