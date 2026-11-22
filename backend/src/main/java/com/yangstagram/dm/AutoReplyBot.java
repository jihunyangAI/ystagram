package com.yangstagram.dm;

import com.yangstagram.dm.DmService.MessageSent;
import com.yangstagram.lab.FeatureFlags;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 가짜 사용자 자동 응답 봇 (기획서 §7): 사람이 가짜 사용자에게 1:1 메시지를 보내면
 * 상대가 1~2초 뒤 읽고, "입력 중"을 보낸 다음 몇 초 뒤 미리 만든 문장 중 하나로 답한다.
 * 화면에서(HTTP 요청으로) 보낸 메시지에만 답한다: Lab에서 가짜 계정으로 바꿔 보내도 답이 오고,
 * 봇 자신·시뮬레이터·예약 전송은 요청 밖에서 보내므로 끝없이 주고받지 않는다. Lab 플래그로 끌 수 있다.
 * LLM 답장(선택 사항)은 아직 없다.
 */
@Service
public class AutoReplyBot {

    private static final Logger log = LoggerFactory.getLogger(AutoReplyBot.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final DmService dm;
    private final TaskScheduler scheduler;
    private final FeatureFlags flags;
    private final Random random = new Random();
    /** 답장을 기다리는 대화방 (여러 개를 연달아 보내도 한 번만 답한다) */
    private final Set<Long> pending = ConcurrentHashMap.newKeySet();

    public AutoReplyBot(NamedParameterJdbcTemplate jdbc, DmService dm, TaskScheduler scheduler, FeatureFlags flags) {
        this.jdbc = jdbc;
        this.dm = dm;
        this.scheduler = scheduler;
        this.flags = flags;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessage(MessageSent e) {
        if (!flags.get().bot()) return;
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() == null) return;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select o.id, o.is_fake as other_fake
                from conversations c
                join conversation_members cm on cm.conversation_id = c.id and cm.user_id <> :sender
                join users o on o.id = cm.user_id
                where c.id = :c and not c.is_group and o.deleted_at is null
                """, Map.of("c", e.conversationId(), "sender", e.senderId()));
        if (rows.size() != 1) return;
        Map<String, Object> row = rows.getFirst();
        if (!Boolean.TRUE.equals(row.get("other_fake"))) return;
        if (!pending.add(e.conversationId())) return;
        long bot = ((Number) row.get("id")).longValue();
        String reply = replyFor(e.kind(), e.body());

        Instant now = Instant.now();
        long readAfter = 800 + random.nextInt(1500);
        long typeAfter = readAfter + 500 + random.nextInt(800);
        long sendAfter = typeAfter + 1200 + Math.min(4000, reply.length() * 60L) + random.nextInt(1000);
        scheduler.schedule(() -> quietly(() -> dm.markRead(e.conversationId(), bot, e.messageId())),
                now.plus(Duration.ofMillis(readAfter)));
        scheduler.schedule(() -> quietly(() -> dm.typing(e.conversationId(), bot)), now.plus(Duration.ofMillis(typeAfter)));
        scheduler.schedule(() -> {
            try {
                long latest = latestMessage(e.conversationId());
                dm.markRead(e.conversationId(), bot, latest);
                dm.send(e.conversationId(), bot, reply, null);
            } catch (RuntimeException ex) {
                log.info("자동 응답 실패 (대화방 {}): {}", e.conversationId(), ex.getMessage());
            } finally {
                pending.remove(e.conversationId());
            }
        }, now.plus(Duration.ofMillis(sendAfter)));
    }

    /**
     * 가짜 사용자가 전화를 못 받았을 때 (통화 점검은 요청 밖에서 돌기 때문에 onMessage가 답하지 않는다):
     * 몇 초 뒤 "전화 못 받았어요" 메시지.
     */
    public void afterMissedCall(long conversationId, long bot) {
        if (!flags.get().bot() || !pending.add(conversationId)) return;
        String reply = CALL.get(random.nextInt(CALL.size()));
        scheduler.schedule(() -> {
            try {
                dm.markRead(conversationId, bot, latestMessage(conversationId));
                dm.send(conversationId, bot, reply, null);
            } catch (RuntimeException ex) {
                log.info("부재중 통화 답장 실패 (대화방 {}): {}", conversationId, ex.getMessage());
            } finally {
                pending.remove(conversationId);
            }
        }, Instant.now().plus(Duration.ofMillis(3000 + random.nextInt(3000))));
    }

    private long latestMessage(long conversationId) {
        Long id = jdbc.queryForObject("select max(id) from messages where conversation_id = :c",
                Map.of("c", conversationId), Long.class);
        return id == null ? 0 : id;
    }

    private void quietly(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ignored) {
            // 읽음·입력 중 표시는 실패해도 상관없다
        }
    }

    // ------------------------------------------------------------ 문장

    private static final List<String> GREETING = List.of("안녕하세요! 😊", "앗 안녕하세요 ㅎㅎ", "반가워요! 무슨 일이에요?",
            "오 안녕하세요 👋");
    private static final List<String> QUESTION = List.of("음… 잘 모르겠어요 ㅎㅎ", "네 맞아요!", "아마 그럴걸요?",
            "그건 다음에 만나서 얘기해요 ㅋㅋ", "좋은 질문이네요 🤔 생각해 볼게요", "저는 괜찮아요!");
    private static final List<String> POST = List.of("오 이거 완전 좋다 😍", "저장해 놨어요 👍", "ㅋㅋㅋ 이거 뭐예요",
            "여기 가 보고 싶어요!", "공유 고마워요 🙏");
    private static final List<String> IMAGE = List.of("사진 예쁘다 😍", "와 분위기 좋네요", "이거 어디예요?", "👍👍");
    private static final List<String> STORY = List.of("봐 줘서 고마워요 🙌", "ㅎㅎ 그쵸?", "다음에 같이 가요!");
    private static final List<String> CALL = List.of("앗 전화 못 받았어요 ㅠㅠ 무슨 일이에요?",
            "지금 통화가 좀 어려워요! 메시지로 얘기해요 🙏", "헉 방금 봤어요 이따 다시 걸게요!", "회의 중이었어요 ㅠ 무슨 일?");
    private static final List<String> NOTE = List.of("ㅋㅋ 고마워요!", "오 답장 고마워요 😊", "그쵸 그쵸");
    private static final List<String> THANKS = List.of("별말씀을요 😊", "천만에요!", "저도 고마워요 🙏");
    private static final List<String> OTHER = List.of("ㅋㅋㅋ 그러게요", "오 진짜요?", "좋아요 👍", "헐 대박",
            "나중에 자세히 얘기해 줘요!", "그쵸 ㅎㅎ", "알겠어요!", "와 신기하다");

    String replyFor(String kind, String body) {
        List<String> set = switch (kind) {
            case "POST" -> POST;
            case "IMAGE" -> IMAGE;
            case "STORY_REPLY" -> STORY;
            case "NOTE_REPLY" -> NOTE;
            case "CALL" -> CALL;
            default -> {
                String b = body == null ? "" : body;
                if (b.contains("안녕") || b.toLowerCase().startsWith("hi") || b.contains("하이")) yield GREETING;
                if (b.contains("고마") || b.contains("감사")) yield THANKS;
                if (b.contains("?") || b.endsWith("요") && (b.contains("뭐") || b.contains("어디") || b.contains("언제"))) {
                    yield QUESTION;
                }
                yield OTHER;
            }
        };
        return set.get(random.nextInt(set.size()));
    }
}
