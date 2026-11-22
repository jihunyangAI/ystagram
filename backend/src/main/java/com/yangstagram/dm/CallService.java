package com.yangstagram.dm;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.realtime.Realtime;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1:1 음성·영상 통화 (기획서 §4.11, 🟡 "WebRTC, 같은 PC 두 브라우저").
 * 서버는 누가 누구에게 거는지와 상태만 관리하고, 소리·영상은 브라우저끼리 WebRTC로 바로 주고받는다.
 * 신호(offer/answer)는 {@code /api/rtc/signal}로 전달한다.
 * <pre>
 * RINGING ─ 받기 ─▶ ACTIVE ─ 끊기 ─▶ ENDED
 *    ├ 거절 ─▶ DECLINED   ├ 건 사람이 취소 ─▶ CANCELED   └ 30초 무응답(가짜 사용자는 12초) ─▶ MISSED
 * </pre>
 * 끝나면 대화방에 통화 기록 메시지(CALL)가 남는다.
 */
@Service
public class CallService {

    private static final Logger log = LoggerFactory.getLogger(CallService.class);
    static final Duration RING_TIMEOUT = Duration.ofSeconds(30);
    static final Duration FAKE_RING_TIMEOUT = Duration.ofSeconds(12);

    private final NamedParameterJdbcTemplate jdbc;
    private final Realtime realtime;
    private final DmService dm;
    private final AutoReplyBot bot;
    /** 통화 id → 연결이 끊긴 채로 지난 점검 횟수 */
    private final Map<Long, Integer> misses = new ConcurrentHashMap<>();

    public CallService(NamedParameterJdbcTemplate jdbc, Realtime realtime, DmService dm,
                       AutoReplyBot bot) {
        this.jdbc = jdbc;
        this.realtime = realtime;
        this.dm = dm;
        this.bot = bot;
    }

    public record CallView(long id, long conversationId, UserSummary caller, UserSummary callee, boolean video,
                           String status, Instant createdAt, Instant answeredAt, Instant endedAt, boolean incoming) {
    }

    public record Ended(long id, String status) {
    }

    // ------------------------------------------------------------ 걸기·받기·끊기

    @Transactional
    public CallView start(long conversationId, long me, boolean video) {
        Map<String, Object> c = jdbc.queryForList("""
                select c.is_group, c.direct_key from conversations c
                join conversation_members m on m.conversation_id = c.id and m.user_id = :me
                where c.id = :c
                """, Map.of("c", conversationId, "me", me)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("대화방을 찾을 수 없습니다."));
        if (Boolean.TRUE.equals(c.get("is_group")) || c.get("direct_key") == null) {
            throw ApiException.badRequest("GROUP_CALL", "통화는 1:1 대화방에서만 할 수 있습니다.");
        }
        long other = 0;
        for (String part : ((String) c.get("direct_key")).split(":")) {
            if (Long.parseLong(part) != me) other = Long.parseLong(part);
        }
        if (other == 0) throw ApiException.badRequest("SELF_CALL", "나에게는 전화할 수 없습니다.");
        Integer blocked = jdbc.queryForObject("""
                select count(*) from blocks where (blocker_id = :a and blocked_id = :b) or (blocker_id = :b and blocked_id = :a)
                """, Map.of("a", me, "b", other), Integer.class);
        if (blocked != null && blocked > 0) throw ApiException.forbidden("BLOCKED", "이 사용자에게 전화할 수 없습니다.");
        if (busy(me)) throw ApiException.conflict("ALREADY_IN_CALL", "이미 통화 중입니다.");
        if (busy(other)) throw ApiException.conflict("BUSY", "상대가 다른 통화 중입니다.");
        Long id = jdbc.queryForObject("""
                insert into calls (conversation_id, caller_id, callee_id, video, status, created_at)
                values (:c, :me, :other, :video, 'RINGING', :now) returning id
                """, new MapSqlParameterSource("c", conversationId).addValue("me", me).addValue("other", other)
                .addValue("video", video).addValue("now", Timestamp.from(Instant.now())), Long.class);
        realtime.send(other, "call:incoming", view(id, other));
        return view(id, me);
    }

    private boolean busy(long user) {
        Integer n = jdbc.queryForObject("""
                select count(*) from calls where status in ('RINGING', 'ACTIVE') and (caller_id = :u or callee_id = :u)
                """, Map.of("u", user), Integer.class);
        return n != null && n > 0;
    }

    @Transactional
    public CallView accept(long id, long me) {
        Map<String, Object> call = call(id, me);
        if (((Number) call.get("callee_id")).longValue() != me) {
            throw ApiException.forbidden("NOT_CALLEE", "받는 사람만 받을 수 있습니다.");
        }
        int n = jdbc.update("update calls set status = 'ACTIVE', answered_at = :now where id = :id and status = 'RINGING'",
                new MapSqlParameterSource("now", Timestamp.from(Instant.now())).addValue("id", id));
        if (n == 0) throw ApiException.conflict("CALL_OVER", "이미 끝난 통화입니다.");
        realtime.send(((Number) call.get("caller_id")).longValue(), "call:accepted", view(id, 0));
        return view(id, me);
    }

    @Transactional
    public CallView decline(long id, long me) {
        Map<String, Object> call = call(id, me);
        if (((Number) call.get("callee_id")).longValue() != me) {
            throw ApiException.forbidden("NOT_CALLEE", "받는 사람만 거절할 수 있습니다.");
        }
        finish(id, "DECLINED");
        return view(id, me);
    }

    /** 끊기: 울리는 중에 건 사람이 끊으면 취소, 받는 사람이 끊으면 거절, 통화 중이면 종료 */
    @Transactional
    public CallView hangUp(long id, long me) {
        Map<String, Object> call = call(id, me);
        String status = (String) call.get("status");
        boolean caller = ((Number) call.get("caller_id")).longValue() == me;
        switch (status) {
            case "RINGING" -> finish(id, caller ? "CANCELED" : "DECLINED");
            case "ACTIVE" -> finish(id, "ENDED");
            default -> {
                // 이미 끝났으면 그대로
            }
        }
        return view(id, me);
    }

    public CallView get(long id, long me) {
        call(id, me);
        return view(id, me);
    }

    /** WebRTC 신호를 주고받아도 되는 사이인가 (울리는 중이거나 통화 중인 두 사람) */
    public boolean canSignal(long id, long from, long to) {
        Integer n = jdbc.queryForObject("""
                select count(*) from calls where id = :id and status in ('RINGING', 'ACTIVE')
                  and ((caller_id = :a and callee_id = :b) or (caller_id = :b and callee_id = :a))
                """, Map.of("id", id, "a", from, "b", to), Integer.class);
        return n != null && n > 0;
    }

    /** 상태를 바꾸고, 대화방에 기록을 남기고, 두 사람에게 "call:ended" */
    void finish(long id, String status) {
        int n = jdbc.update("update calls set status = :s, ended_at = :now where id = :id and status in ('RINGING', 'ACTIVE')",
                new MapSqlParameterSource("s", status).addValue("id", id)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (n == 0) return;
        misses.remove(id);
        Map<String, Object> call = jdbc.queryForMap("select * from calls where id = :id", Map.of("id", id));
        long conversation = ((Number) call.get("conversation_id")).longValue();
        long caller = ((Number) call.get("caller_id")).longValue();
        long callee = ((Number) call.get("callee_id")).longValue();
        try {
            dm.callLog(conversation, caller, id);
        } catch (RuntimeException e) {
            log.info("통화 기록 메시지를 남기지 못함 (통화 {}): {}", id, e.getMessage());
        }
        realtime.send(List.of(caller, callee), "call:ended", new Ended(id, status));
    }

    private Map<String, Object> call(long id, long me) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select * from calls where id = :id and (caller_id = :me or callee_id = :me)
                """, Map.of("id", id, "me", me));
        if (rows.isEmpty()) throw ApiException.notFound("통화를 찾을 수 없습니다.");
        return rows.getFirst();
    }

    private CallView view(long id, long me) {
        return jdbc.queryForObject("""
                select c.*, %s, %s from calls c join users a on a.id = c.caller_id join users b on b.id = c.callee_id
                where c.id = :id
                """.formatted(UserRows.cols("a"), UserRows.cols("b")), Map.of("id", id), (rs, i) -> {
            Timestamp answered = rs.getTimestamp("answered_at");
            Timestamp ended = rs.getTimestamp("ended_at");
            return new CallView(rs.getLong("id"), rs.getLong("conversation_id"), UserRows.read(rs, "a"),
                    UserRows.read(rs, "b"), rs.getBoolean("video"), rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant(), answered == null ? null : answered.toInstant(),
                    ended == null ? null : ended.toInstant(), rs.getLong("callee_id") == me);
        });
    }

    // ------------------------------------------------------------ 점검

    /**
     * 3초마다: 오래 울린 전화는 부재중(가짜 사용자는 12초 만에, 그리고 "전화 못 받았어요" 답장),
     * 통화 중인데 한쪽 연결이 세 번(약 9초) 연속 끊겨 있으면 종료.
     */
    @Scheduled(fixedDelay = 3000, initialDelay = 5000)
    void sweep() {
        try {
            Instant now = Instant.now();
            List<Map<String, Object>> open = jdbc.queryForList("""
                    select c.id, c.status, c.created_at, c.caller_id, c.callee_id, c.conversation_id, u.is_fake as callee_fake
                    from calls c join users u on u.id = c.callee_id where c.status in ('RINGING', 'ACTIVE')
                    """, Map.of());
            for (Map<String, Object> c : open) {
                long id = ((Number) c.get("id")).longValue();
                long caller = ((Number) c.get("caller_id")).longValue();
                long callee = ((Number) c.get("callee_id")).longValue();
                if ("RINGING".equals(c.get("status"))) {
                    boolean fake = Boolean.TRUE.equals(c.get("callee_fake"));
                    Instant at = ((Timestamp) c.get("created_at")).toInstant();
                    // 가짜 사용자라도 Lab에서 그 계정으로 접속해 있으면 사람처럼 30초 기다린다
                    boolean botCallee = fake && !realtime.isOnline(callee);
                    Duration timeout = botCallee ? FAKE_RING_TIMEOUT : RING_TIMEOUT;
                    if (Duration.between(at, now).compareTo(timeout) >= 0) {
                        finish(id, "MISSED");
                        if (botCallee) bot.afterMissedCall(((Number) c.get("conversation_id")).longValue(), callee);
                    }
                } else if (realtime.isOnline(caller) && realtime.isOnline(callee)) {
                    misses.remove(id);
                } else if (misses.merge(id, 1, Integer::sum) >= 3) {
                    finish(id, "ENDED");
                }
            }
        } catch (RuntimeException e) {
            log.warn("통화 점검 오류: {}", e.toString());
        }
    }
}
