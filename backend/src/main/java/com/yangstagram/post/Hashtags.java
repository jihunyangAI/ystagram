package com.yangstagram.post;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 캡션에서 해시태그를 뽑는다. 인스타그램은 2025년 12월부터 게시물당 5개까지 (분석 문서 §6). */
public final class Hashtags {

    public static final int MAX_PER_POST = 5;
    public static final int MAX_CAPTION = 2200;

    /** '#' 뒤에 글자·숫자·밑줄. 한글 태그(#카페투어)도 포함 */
    private static final Pattern TAG = Pattern.compile("#([\\p{L}\\p{N}_]{1,100})");

    private Hashtags() {
    }

    /** 대소문자를 구분하지 않고 중복을 없앤, 나온 순서대로의 태그 목록 ('#' 제외, 소문자) */
    public static Set<String> extract(String caption) {
        Set<String> tags = new LinkedHashSet<>();
        if (caption == null) return tags;
        Matcher m = TAG.matcher(caption);
        while (m.find()) {
            tags.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return tags;
    }
}
