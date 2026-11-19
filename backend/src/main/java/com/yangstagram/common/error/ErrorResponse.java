package com.yangstagram.common.error;

import java.util.Map;

/** 모든 오류 응답의 공통 형식. fieldErrors는 입력값 검증 실패 시 필드별 메시지. */
public record ErrorResponse(String code, String message, Map<String, String> fieldErrors) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Map.of());
    }
}
