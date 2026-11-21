package com.yangstagram.common.paging;

import java.util.List;

/** 한 페이지 분량. nextCursor가 null이면 마지막 페이지. */
public record Slice<T>(List<T> items, String nextCursor) {
}
