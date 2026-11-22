package com.yangstagram.live;

import com.yangstagram.lab.FeatureFlags;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 가짜 라이브 (기획서 §4.7 "가짜 시청자는 미리 녹화된 영상 재생 + 봇 댓글").
 * <ul>
 *   <li>1분마다: 가짜 인플루언서의 라이브(릴스 영상 반복 재생)가 {@value #TARGET}개 켜져 있게 하고, 오래된 것은 끝낸다</li>
 *   <li>3초마다: 라이브마다 가짜 시청자 수를 조금씩 바꾸고, 실제 사람이 보고 있으면 팔로워 이름으로 댓글·하트를 보낸다.
 *       사람이 브라우저 카메라로 켠 라이브에도 가짜 팔로워가 들어와 인사한다</li>
 * </ul>
 * Lab 기능 플래그 liveBots로 끈다 (끄면 가짜 라이브도 끝낸다).
 */
@Component
class LiveSimulator {

    private static final Logger log = LoggerFactory.getLogger(LiveSimulator.class);
    static final int TARGET = 3;

    private static final List<String> CHAT = List.of(
            "안녕하세요~", "와 라이브 켜셨네요 🙌", "오늘 뭐 해요?", "❤️❤️", "목소리 너무 좋다", "어디예요?", "ㅋㅋㅋㅋㅋ",
            "저도 왔어요 👋", "화질 좋네요", "다음엔 Q&A 해주세요!", "오늘 텐션 좋다 ㅋㅋ", "🔥🔥🔥", "보고 있어요!",
            "늦게 왔다 ㅠㅠ 뭐 했어요?", "각도 좋아요 👍", "퇴근길에 보는 중", "헐 대박", "저 이거 궁금했는데",
            "반가워요!!", "알림 보고 바로 들어옴", "오늘 너무 예뻐요", "이거 어디서 샀어요?", "꿀팁 감사합니다 🙏",
            "소리가 살짝 작아요", "ㅎㅎ 귀여워", "와 진짜 잘한다", "저장각", "부산에서 보고 있어요", "인사해 주세요!",
            "오 이 노래 뭐예요?", "멋져요 👏", "매일 해 주세요", "중간에 들어왔는데 재밌네요", "😍😍");
    private static final List<String> FIRST_LIVE = List.of(
            "첫 라이브 축하해요 🎉", "오 라이브 처음 보는 듯!", "알림 떠서 들어왔어요", "안녕하세요 👋👋");
    private static final List<String> TITLES = List.of(
            "같이 수다 떨어요 ☕", "Q&A 받아요!", "오늘 하루 같이 마무리해요", "작업하면서 라이브", "언박싱 라이브 📦",
            "질문 받아요 아무거나", "저녁 먹으면서 수다", "주말 라이브 ✨");

    private final LiveService lives;
    private final NamedParameterJdbcTemplate jdbc;
    private final FeatureFlags flags;
    private final Random random = new Random();
    /** 라이브 id → 댓글을 쓸 가짜 팔로워 후보 */
    private final Map<Long, List<Long>> chatters = new ConcurrentHashMap<>();
    private long lastMaintain;

    LiveSimulator(LiveService lives, NamedParameterJdbcTemplate jdbc, FeatureFlags flags) {
        this.lives = lives;
        this.jdbc = jdbc;
        this.flags = flags;
    }

    @Scheduled(fixedDelay = 3000, initialDelay = 15_000)
    void tick() {
        try {
            boolean on = flags.get().lives();
            long now = System.currentTimeMillis();
            if (now - lastMaintain > 60_000) {
                lastMaintain = now;
                maintain(on);
            }
            for (Map<String, Object> row : lives.liveRows()) {
                long id = ((Number) row.get("id")).longValue();
                if (on || "CAMERA".equals(row.get("kind"))) chatter(row, on);
            }
        } catch (RuntimeException e) {
            log.warn("가짜 라이브 오류: {}", e.toString());
        }
    }

    /** 가짜 라이브 개수 맞추기 */
    void maintain(boolean on) {
        List<Map<String, Object>> rows = lives.liveRows();
        List<Long> liveIds = rows.stream().map(r -> ((Number) r.get("id")).longValue()).toList();
        chatters.keySet().retainAll(liveIds);
        int videoLives = 0;
        Instant now = Instant.now();
        for (Map<String, Object> r : rows) {
            if (!"VIDEO".equals(r.get("kind"))) continue;
            long id = ((Number) r.get("id")).longValue();
            Instant started = ((java.sql.Timestamp) r.get("started_at")).toInstant();
            long minutes = 20 + Math.floorMod(id * 7919, 40); // 20~59분 방송
            boolean old = java.time.Duration.between(started, now).toMinutes() >= minutes;
            // 실제 사람이 보고 있으면 끊지 않는다
            if (!on || (old && lives.realViewers(id) == 0)) {
                lives.finish(id);
            } else {
                videoLives++;
            }
        }
        if (!on) return;
        for (int i = videoLives; i < TARGET; i++) {
            if (!startOne()) break;
        }
    }

    /** 가짜 인플루언서 한 명이 자기 릴스 영상으로 라이브를 켠다 */
    private boolean startOne() {
        List<Map<String, Object>> hosts = jdbc.queryForList("""
                select u.id, m.url, m.thumb_url, p.caption
                from users u
                join lateral (select p.id, p.caption from posts p where p.author_id = u.id and p.type = 'REEL'
                              and p.deleted_at is null and p.archived_at is null order by random() limit 1) p on true
                join post_media m on m.post_id = p.id and m.position = 0 and m.media_type = 'VIDEO'
                where u.is_fake and u.deleted_at is null and u.account_type = 'CREATOR' and not u.is_private
                  and (u.suspended_until is null or u.suspended_until <= current_timestamp)
                  and not exists (select 1 from lives l where l.host_id = u.id and l.status = 'LIVE')
                order by random() limit 1
                """, Map.of());
        if (hosts.isEmpty()) return false;
        Map<String, Object> h = hosts.getFirst();
        String caption = (String) h.get("caption");
        String title = caption != null && !caption.isBlank() && random.nextBoolean()
                ? caption.lines().findFirst().orElse("").strip()
                : TITLES.get(random.nextInt(TITLES.size()));
        if (title.length() > LiveService.MAX_TITLE) title = title.substring(0, LiveService.MAX_TITLE);
        long id = lives.startFake(((Number) h.get("id")).longValue(), title, (String) h.get("url"),
                (String) h.get("thumb_url"));
        int followers = jdbc.queryForObject("select followers_count from users where id = :id",
                Map.of("id", h.get("id")), Integer.class);
        lives.setFakeAudience(id, 15 + followers / 40 + random.nextInt(30));
        return true;
    }

    /** 시청자 수 출렁이기 + (사람이 보고 있으면) 댓글·하트 */
    private void chatter(Map<String, Object> row, boolean on) {
        long id = ((Number) row.get("id")).longValue();
        boolean camera = "CAMERA".equals(row.get("kind"));
        int followers = ((Number) row.get("followers_count")).intValue();
        int cap = camera ? Math.min(40, 3 + followers / 30) : 15 + followers / 30 + 60;
        int fake = lives.fakeAudience(id);
        if (!on) {
            if (fake > 0) lives.setFakeAudience(id, 0);
            return;
        }
        int drift = camera ? (fake < cap && random.nextDouble() < 0.5 ? 1 : random.nextDouble() < 0.1 ? -1 : 0)
                : random.nextInt(7) - 3 + (fake < cap / 2 ? 2 : 0);
        lives.setFakeAudience(id, Math.min(cap, fake + drift));

        boolean someoneWatching = camera || lives.realViewers(id) > 0;
        if (!someoneWatching) return;
        List<Long> pool = chatters.computeIfAbsent(id, k -> candidates(((Number) row.get("host_id")).longValue()));
        if (pool.isEmpty()) return;
        long who = pool.get(random.nextInt(pool.size()));
        double p = camera ? 0.35 : 0.6;
        if (random.nextDouble() < p) {
            if (camera && drift > 0 && random.nextDouble() < 0.5) {
                lives.botComment(id, who, null); // "OO님이 참여했습니다"
            } else {
                List<String> lines = camera && random.nextDouble() < 0.3 ? FIRST_LIVE : CHAT;
                lives.botComment(id, who, lines.get(random.nextInt(lines.size())));
            }
        }
        if (random.nextDouble() < (camera ? 0.3 : 0.5)) {
            lives.botHearts(id, pool.get(random.nextInt(pool.size())), 1 + random.nextInt(5));
        }
    }

    /** 진행자의 가짜 팔로워 (없으면 아무 가짜 사용자) 40명 */
    private List<Long> candidates(long host) {
        List<Long> ids = jdbc.queryForList("""
                select u.id from follows f join users u on u.id = f.follower_id
                where f.following_id = :h and f.status = 'ACCEPTED' and u.is_fake and u.deleted_at is null
                order by random() limit 40
                """, Map.of("h", host), Long.class);
        if (ids.size() >= 5) return ids;
        return jdbc.queryForList("""
                select id from users where is_fake and deleted_at is null and id <> :h order by random() limit 40
                """, Map.of("h", host), Long.class);
    }
}
