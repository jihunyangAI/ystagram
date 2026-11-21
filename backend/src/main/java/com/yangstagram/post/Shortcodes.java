package com.yangstagram.post;

import java.security.SecureRandom;
import java.util.Random;

/** 게시물 주소용 무작위 11자 코드 (/p/{shortcode}). 순번 id를 화면 주소에 드러내지 않는다. */
public final class Shortcodes {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final SecureRandom SECURE = new SecureRandom();

    private Shortcodes() {
    }

    public static String random() {
        return random(SECURE);
    }

    /** 가짜 데이터는 시드값으로 만든 Random을 넘겨서 매번 같은 코드가 나오게 한다. */
    public static String random(Random rnd) {
        StringBuilder sb = new StringBuilder(11);
        // 첫 글자는 영문자로 (주소가 '-'나 '_'로 시작하지 않게)
        sb.append(ALPHABET.charAt(rnd.nextInt(52)));
        for (int i = 1; i < 11; i++) {
            sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
