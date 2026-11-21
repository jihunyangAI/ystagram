package com.yangstagram.notification;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** 캡션·댓글 속 @사용자이름을 찾아 멘션 알림을 보낸다 (캡션 @태그 최대 20개, 기획서 §6.3) */
@Component
public class Mentions {

    public static final int MAX = 20;
    private static final Pattern MENTION = Pattern.compile("@([a-zA-Z0-9._]{1,30})");

    private final NamedParameterJdbcTemplate jdbc;
    private final NotificationService notifications;

    public Mentions(NamedParameterJdbcTemplate jdbc, NotificationService notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    public static Set<String> extract(String text) {
        Set<String> names = new LinkedHashSet<>();
        if (text == null) return names;
        Matcher m = MENTION.matcher(text);
        while (m.find() && names.size() < MAX) {
            String name = m.group(1).toLowerCase();
            while (name.endsWith(".")) name = name.substring(0, name.length() - 1); // 문장 끝 마침표
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }

    /** 이미 다른 알림(댓글 등)을 받는 사람은 exclude로 빼서 중복 알림을 막는다 */
    public void notifyAll(String text, long actor, Long postId, Long commentId, Set<Long> exclude) {
        Set<String> names = extract(text);
        if (names.isEmpty()) return;
        List<Long> ids = jdbc.queryForList(
                "select id from users where username in (:names) and deleted_at is null",
                Map.of("names", names), Long.class);
        for (Long id : ids) {
            if (!exclude.contains(id)) {
                notifications.notify(id, actor, NotificationType.MENTION, postId, commentId, null, text);
            }
        }
    }
}
