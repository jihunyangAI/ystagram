package com.yangstagram.realtime;

import com.yangstagram.common.time.LabClock;
import com.yangstagram.realtime.WebSocketConfig.UserPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * 사용자에게 실시간 이벤트를 보내고, 누가 접속 중인지(활동 상태) 기억한다.
 * 트랜잭션 안에서 부르면 커밋된 뒤에 보낸다 — 화면이 이벤트를 받고 다시 조회했을 때 바뀐 데이터가 보이도록.
 */
@Component
public class Realtime {

    /** 화면으로 가는 이벤트 한 건 */
    public record Event(String type, Object data) {
    }

    private final SimpMessagingTemplate template;
    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;
    /** 사용자 id → 열린 연결 수 (브라우저 탭 여러 개) */
    private final Map<Long, Integer> online = new ConcurrentHashMap<>();

    public Realtime(SimpMessagingTemplate template, NamedParameterJdbcTemplate jdbc, LabClock clock) {
        this.template = template;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void send(long userId, String type, Object data) {
        afterCommit(() -> template.convertAndSendToUser(String.valueOf(userId), "/queue/events", new Event(type, data)));
    }

    public void send(Collection<Long> userIds, String type, Object data) {
        afterCommit(() -> {
            Event event = new Event(type, data);
            userIds.forEach(id -> template.convertAndSendToUser(String.valueOf(id), "/queue/events", event));
        });
    }

    private static void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    // ------------------------------------------------------------ 활동 상태

    public boolean isOnline(long userId) {
        return online.getOrDefault(userId, 0) > 0;
    }

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        if (event.getUser() instanceof UserPrincipal p) {
            online.merge(p.id(), 1, Integer::sum);
            touch(p.id());
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() instanceof UserPrincipal p) {
            online.computeIfPresent(p.id(), (k, v) -> v <= 1 ? null : v - 1);
            touch(p.id());
        }
    }

    private void touch(long userId) {
        jdbc.update("update users set last_active_at = :now where id = :id",
                Map.of("now", Timestamp.from(Instant.now(clock)), "id", userId));
    }
}
