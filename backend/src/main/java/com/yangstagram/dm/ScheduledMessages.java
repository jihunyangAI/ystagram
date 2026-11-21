package com.yangstagram.dm;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 메시지 예약 (기획서 §6.2: 최대 29일 후). 시각이 되면 보낸 사람 이름으로 보내고 예약을 지운다.
 * 예약한 내용은 보낸 사람에게만 보인다. 시각은 Lab 가상 시계를 따른다.
 */
@Service
public class ScheduledMessages {

    private static final Logger log = LoggerFactory.getLogger(ScheduledMessages.class);

    public static final Duration MIN_AHEAD = Duration.ofMinutes(1);
    public static final Duration MAX_AHEAD = Duration.ofDays(29);
    public static final int MAX_PER_CONVERSATION = 20;

    private final NamedParameterJdbcTemplate jdbc;
    private final DmService dm;
    private final LabClock clock;

    public ScheduledMessages(NamedParameterJdbcTemplate jdbc, DmService dm, LabClock clock) {
        this.jdbc = jdbc;
        this.dm = dm;
        this.clock = clock;
    }

    public record ScheduledMessage(long id, long conversationId, String body, Instant scheduledAt, Instant createdAt) {
    }

    public ScheduledMessage schedule(long conversationId, long me, String rawBody, Instant at) {
        requireMember(conversationId, me);
        String body = rawBody == null ? "" : rawBody.strip();
        if (body.isEmpty()) throw ApiException.badRequest("EMPTY_MESSAGE", "메시지를 입력해 주세요.");
        if (body.length() > DmService.MAX_BODY) {
            throw ApiException.badRequest("MESSAGE_TOO_LONG", "메시지는 " + DmService.MAX_BODY + "자까지 쓸 수 있습니다.");
        }
        Instant now = Instant.now(clock);
        if (at == null || at.isBefore(now.plus(MIN_AHEAD))) {
            throw ApiException.badRequest("TOO_SOON", "예약 시각은 1분 뒤 이후로 골라 주세요.");
        }
        if (at.isAfter(now.plus(MAX_AHEAD))) {
            throw ApiException.badRequest("TOO_LATE", "메시지는 29일 안으로만 예약할 수 있습니다.");
        }
        Integer n = jdbc.queryForObject("""
                select count(*) from scheduled_messages where conversation_id = :c and sender_id = :me
                """, Map.of("c", conversationId, "me", me), Integer.class);
        if (n != null && n >= MAX_PER_CONVERSATION) {
            throw ApiException.badRequest("TOO_MANY_SCHEDULED", "대화방마다 예약 메시지는 " + MAX_PER_CONVERSATION
                    + "개까지 둘 수 있습니다.");
        }
        Long id = jdbc.queryForObject("""
                insert into scheduled_messages (conversation_id, sender_id, body, scheduled_at, created_at)
                values (:c, :me, :body, :at, :now) returning id
                """, new MapSqlParameterSource("c", conversationId).addValue("me", me).addValue("body", body)
                .addValue("at", Timestamp.from(at)).addValue("now", Timestamp.from(now)), Long.class);
        return new ScheduledMessage(id, conversationId, body, at, now);
    }

    public List<ScheduledMessage> list(long conversationId, long me) {
        requireMember(conversationId, me);
        return jdbc.query("""
                select id, conversation_id, body, scheduled_at, created_at from scheduled_messages
                where conversation_id = :c and sender_id = :me order by scheduled_at
                """, Map.of("c", conversationId, "me", me), (rs, i) -> new ScheduledMessage(rs.getLong(1),
                rs.getLong(2), rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getTimestamp(5).toInstant()));
    }

    public void cancel(long id, long me) {
        int n = jdbc.update("delete from scheduled_messages where id = :id and sender_id = :me",
                Map.of("id", id, "me", me));
        if (n == 0) throw ApiException.notFound("예약 메시지를 찾을 수 없습니다.");
    }

    /** 지금 바로 보내기 */
    public DmDtos.MessageView sendNow(long id, long me) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                delete from scheduled_messages where id = :id and sender_id = :me returning conversation_id, body
                """, Map.of("id", id, "me", me));
        if (rows.isEmpty()) throw ApiException.notFound("예약 메시지를 찾을 수 없습니다.");
        return dm.send(((Number) rows.getFirst().get("conversation_id")).longValue(), me,
                (String) rows.getFirst().get("body"), null);
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 7000)
    public void sendDue() {
        List<Map<String, Object>> due = jdbc.queryForList("""
                delete from scheduled_messages where id in (
                  select id from scheduled_messages where scheduled_at <= :now order by scheduled_at limit 100)
                returning id, conversation_id, sender_id, body
                """, Map.of("now", Timestamp.from(Instant.now(clock))));
        for (Map<String, Object> row : due) {
            try {
                dm.send(((Number) row.get("conversation_id")).longValue(), ((Number) row.get("sender_id")).longValue(),
                        (String) row.get("body"), null);
            } catch (RuntimeException e) {
                // 그 사이 차단·대화방 나감 등: 보내지 않고 버린다
                log.info("예약 메시지 {}를 보내지 못해 버림: {}", row.get("id"), e.getMessage());
            }
        }
    }

    private void requireMember(long conversationId, long me) {
        Integer n = jdbc.queryForObject("""
                select count(*) from conversation_members where conversation_id = :c and user_id = :me
                """, Map.of("c", conversationId, "me", me), Integer.class);
        if (n == null || n == 0) throw ApiException.notFound("대화방을 찾을 수 없습니다.");
    }
}
