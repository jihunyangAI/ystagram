package com.yangstagram.common.paging;

import com.yangstagram.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 무한 스크롤용 커서: (생성 시각, id) 한 쌍을 문자열 하나로 감싼다.
 * "이 항목보다 오래된 것부터 다음 페이지"라는 뜻이라 중간에 새 글이 생겨도 중복·누락이 없다.
 */
public record Cursor(Instant createdAt, long id) {

    public String encode() {
        String raw = createdAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 비어 있으면 null (첫 페이지) */
    public static Cursor decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int bar = raw.indexOf('|');
            return new Cursor(Instant.parse(raw.substring(0, bar)), Long.parseLong(raw.substring(bar + 1)));
        } catch (RuntimeException e) {
            throw ApiException.badRequest("BAD_CURSOR", "잘못된 페이지 정보입니다.");
        }
    }
}
