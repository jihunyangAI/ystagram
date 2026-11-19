package com.yangstagram.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UsernamePolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"minji", "minji.cafe", "travel_jun", "a", "abc123", "the.cafe.minji", "_under_"})
    void 올바른_사용자_이름(String name) {
        assertThat(UsernamePolicy.validate(name)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",                                   // 비어 있음
            ".minji", "minji.",                   // 마침표로 시작/끝
            "min..ji",                            // 마침표 연속
            "Minji",                              // 대문자 (normalize 전)
            "min ji", "민지", "min-ji", "min@ji",  // 허용되지 않는 문자
            "abcdefghijabcdefghijabcdefghijk",    // 31자
            "explore", "accounts", "lab"          // 예약어 (화면 주소와 충돌)
    })
    void 잘못된_사용자_이름(String name) {
        assertThat(UsernamePolicy.validate(name)).isNotNull();
    }

    @org.junit.jupiter.api.Test
    void 앞뒤_공백을_지우고_소문자로_바꾼다() {
        assertThat(UsernamePolicy.normalize("  Yang.Tester ")).isEqualTo("yang.tester");
    }
}
