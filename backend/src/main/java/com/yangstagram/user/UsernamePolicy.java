package com.yangstagram.user;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 사용자 이름 규칙 (인스타그램 기준).
 * - 1~30자, 영문 소문자·숫자·마침표(.)·밑줄(_)만
 * - 마침표로 시작하거나 끝날 수 없고, 마침표를 연속으로 쓸 수 없음
 * - 화면 주소와 겹치는 이름은 예약어로 사용 불가 (예: /explore, /accounts)
 */
public final class UsernamePolicy {

    public static final int MAX_LENGTH = 30;

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9._]{1,30}$");

    private static final Set<String> RESERVED = Set.of(
            "accounts", "api", "direct", "explore", "reels", "reel", "p", "stories",
            "notifications", "lab", "media", "create", "settings", "login", "logout",
            "signup", "about", "help", "admin", "yangstagram", "static", "assets");

    private UsernamePolicy() {
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    /** 문제가 없으면 null, 있으면 사용자에게 보여 줄 메시지. */
    public static String validate(String username) {
        if (username.isEmpty()) {
            return "사용자 이름을 입력해 주세요.";
        }
        if (username.length() > MAX_LENGTH) {
            return "사용자 이름은 30자 이하여야 합니다.";
        }
        if (!PATTERN.matcher(username).matches()) {
            return "사용자 이름에는 영문 소문자, 숫자, 마침표(.), 밑줄(_)만 쓸 수 있습니다.";
        }
        if (username.startsWith(".") || username.endsWith(".")) {
            return "사용자 이름은 마침표로 시작하거나 끝날 수 없습니다.";
        }
        if (username.contains("..")) {
            return "마침표를 연속으로 쓸 수 없습니다.";
        }
        if (RESERVED.contains(username)) {
            return "사용할 수 없는 사용자 이름입니다.";
        }
        return null;
    }

    public static boolean isValid(String username) {
        return validate(username) == null;
    }
}
