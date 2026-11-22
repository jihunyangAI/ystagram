package com.yangstagram.seed;

import com.yangstagram.seed.SeedService.FakeFollow;
import com.yangstagram.seed.SeedService.FakeUser;
import com.yangstagram.seed.SeedService.Persona;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M5-① 가짜 데이터: 신고(모더레이션 큐가 비지 않게), 친구 지도 위치, 인플루언서 브로드캐스트 채널.
 */
final class SeedM5 {

    record Result(int reports, int locations, int channels, int channelMessages) {
    }

    private final JdbcTemplate jdbc;
    private final Random rnd;
    private final Instant now;

    SeedM5(JdbcTemplate jdbc, Random rnd, Instant now) {
        this.jdbc = jdbc;
        this.rnd = rnd;
        this.now = now;
    }

    Result generate(List<FakeUser> users, List<FakeFollow> follows) {
        return new Result(reports(users), locations(users), channels(users, follows), channelMessages);
    }

    // ------------------------------------------------------------ 신고

    private static final String[] POST_REASONS = {"SPAM", "SPAM", "SPAM", "SCAM", "FALSE_INFO", "IP", "OTHER", "NUDITY",
            "HATE", "BULLYING"};
    private static final String[] COMMENT_REASONS = {"SPAM", "SPAM", "BULLYING", "HATE", "SCAM", "OTHER"};
    private static final String[] USER_REASONS = {"SPAM", "SCAM", "OTHER"};

    private int reports(List<FakeUser> users) {
        jdbc.queryForList("select setseed(?)", rnd.nextDouble() * 2 - 1);
        int n = 0;
        // 게시물 약 1%: 1~5명이 신고 (일부는 여러 명이 몰림)
        List<Map<String, Object>> posts = jdbc.queryForList("""
                select id, author_id from posts where deleted_at is null and random() < 0.012 order by id
                """);
        List<Long> ids = users.stream().map(u -> u.id).toList();
        for (Map<String, Object> p : posts) {
            n += reportTarget("POST", ((Number) p.get("id")).longValue(), ((Number) p.get("author_id")).longValue(),
                    POST_REASONS, ids);
        }
        List<Map<String, Object>> comments = jdbc.queryForList("""
                select id, author_id from comments where random() < 0.004 order by id
                """);
        for (Map<String, Object> c : comments) {
            n += reportTarget("COMMENT", ((Number) c.get("id")).longValue(), ((Number) c.get("author_id")).longValue(),
                    COMMENT_REASONS, ids);
        }
        for (FakeUser u : users) {
            if (rnd.nextDouble() < 0.02) n += reportTarget("USER", u.id, u.id, USER_REASONS, ids);
        }
        return n;
    }

    private int reportTarget(String type, long id, long owner, String[] reasons, List<Long> users) {
        int count = rnd.nextDouble() < 0.2 ? 3 + rnd.nextInt(5) : 1 + rnd.nextInt(2);
        String main = reasons[rnd.nextInt(reasons.length)];
        int n = 0;
        for (int i = 0; i < count; i++) {
            long reporter = users.get(rnd.nextInt(users.size()));
            if (reporter == owner) continue;
            String reason = rnd.nextDouble() < 0.7 ? main : reasons[rnd.nextInt(reasons.length)];
            n += jdbc.update("""
                    insert into reports (reporter_id, target_type, target_id, target_user_id, reason, created_at)
                    values (?, ?, ?, ?, ?, ?) on conflict do nothing
                    """, reporter, type, id, owner, reason,
                    Timestamp.from(now.minus(Duration.ofMinutes(5 + rnd.nextInt(3 * 24 * 60)))));
        }
        return n;
    }

    // ------------------------------------------------------------ 지도

    private int locations(List<FakeUser> users) {
        List<String[]> places = SeedText.LOCATIONS.stream().filter(l -> SeedText.COORDS.containsKey(l[0])
                && !l[1].equals("도쿄") && !l[1].equals("오사카") && !l[1].equals("파리")).toList();
        // 서울 장소가 많이 뽑히도록 (서울 10곳이 앞쪽)
        int n = 0;
        for (FakeUser u : users) {
            if (rnd.nextDouble() > (u.persona == Persona.LURKER ? 0.15 : 0.45)) continue;
            String[] place = places.get(rnd.nextDouble() < 0.65 ? rnd.nextInt(10) : rnd.nextInt(places.size()));
            double[] c = SeedText.COORDS.get(place[0]);
            double lat = c[0] + (rnd.nextDouble() - 0.5) * 0.02;
            double lng = c[1] + (rnd.nextDouble() - 0.5) * 0.025;
            String sharing = rnd.nextDouble() < 0.15 ? "CLOSE_FRIENDS" : "MUTUAL";
            n += jdbc.update("""
                    insert into user_locations (user_id, lat, lng, place_name, sharing, updated_at)
                    values (?, ?, ?, ?, ?, ?)
                    """, u.id, lat, lng, place[0] + " 근처", sharing,
                    Timestamp.from(now.minus(Duration.ofMinutes(3 + rnd.nextInt(60 * 60)))));
        }
        return n;
    }

    // ------------------------------------------------------------ 채널

    private int channelMessages;

    private static final Map<Topic, String[]> CHANNEL_NAMES = Map.ofEntries(
            Map.entry(Topic.FOOD, new String[] {"맛집 비밀 리스트", "오늘 뭐 먹지 클럽"}),
            Map.entry(Topic.CAFE, new String[] {"카페 투어 단톡방", "신상 카페 알림"}),
            Map.entry(Topic.TRAVEL, new String[] {"여행 꿀팁 채널", "다음 여행지 투표방"}),
            Map.entry(Topic.FASHION, new String[] {"데일리룩 비하인드", "세일 정보 공유"}),
            Map.entry(Topic.FITNESS, new String[] {"오운완 인증방", "홈트 루틴 채널"}),
            Map.entry(Topic.PETS, new String[] {"우리 집 막내 소식", "댕냥이 일상 채널"}),
            Map.entry(Topic.BEAUTY, new String[] {"뷰티 꿀템 채널", "피부 고민 상담소"}),
            Map.entry(Topic.MUSIC, new String[] {"플레이리스트 공유방", "공연 소식 채널"}));

    private static final List<String> TEXTS = List.of(
            "다들 주말 잘 보내고 있어요? 😊", "오늘 촬영 비하인드 살짝 공개할게요 🤫", "다음 주에 새 영상 올라가요!",
            "채널 참여해 주셔서 감사해요 🙏 여기서 먼저 소식 전할게요", "방금 다녀온 곳 너무 좋았어요… 위치는 곧 공개",
            "질문 많이 주셔서 하나씩 답해 볼게요", "오늘은 쉬는 날 💤", "새벽 감성 한 줄: 오늘도 고생 많았어요",
            "이번 달 협찬 없이 내돈내산만 올렸어요 ✨", "라이브 언제가 좋을까요? 댓글 대신 반응 남겨 주세요");

    private static final String[][] POLLS = {
            {"다음 영상 뭐가 좋을까요?", "브이로그", "꿀팁 정리", "Q&A"},
            {"이번 주말 어디 갈까요?", "바다", "산", "집콕"},
            {"오늘 점심 메뉴 골라 주세요 🍽", "국밥", "파스타", "샐러드", "떡볶이"},
            {"아침형 vs 저녁형?", "아침형 ☀️", "저녁형 🌙"}};

    private static final String[] EMOJIS = {"❤️", "🔥", "😂", "😮", "👏", "🥹"};

    private int channels(List<FakeUser> users, List<FakeFollow> follows) {
        Map<Long, List<Long>> followers = new HashMap<>();
        for (FakeFollow f : follows) {
            if (f.status().equals("ACCEPTED")) {
                followers.computeIfAbsent(f.following().id, k -> new ArrayList<>()).add(f.follower().id);
            }
        }
        int count = 0;
        for (FakeUser u : users) {
            if (u.persona != Persona.INFLUENCER && !(u.persona == Persona.REGULAR && rnd.nextDouble() < 0.03)) continue;
            Topic topic = u.interests.getFirst();
            String[] names = CHANNEL_NAMES.getOrDefault(topic, new String[] {u.fullName + "의 채널"});
            Instant created = now.minus(Duration.ofDays(15 + rnd.nextInt(60)));
            Long channelId = jdbc.queryForObject("""
                    insert into channels (owner_id, name, created_at) values (?, ?, ?) returning id
                    """, Long.class, u.id, names[rnd.nextInt(names.length)], Timestamp.from(created));
            List<Long> members = new ArrayList<>(List.of(u.id));
            for (Long f : followers.getOrDefault(u.id, List.of())) {
                if (rnd.nextDouble() < 0.4) members.add(f);
            }
            for (Long m : members) {
                jdbc.update("insert into channel_members (channel_id, user_id, joined_at) values (?, ?, ?)", channelId, m,
                        Timestamp.from(created.plus(Duration.ofHours(rnd.nextInt(24 * 10)))));
            }
            int messages = 6 + rnd.nextInt(10);
            Instant t = now.minus(Duration.ofDays(14));
            long lastId = 0;
            for (int i = 0; i < messages; i++) {
                t = t.plus(Duration.ofMinutes(60 + rnd.nextInt(2 * 24 * 60)));
                if (t.isAfter(now)) break;
                boolean poll = rnd.nextDouble() < 0.18;
                String[] p = POLLS[rnd.nextInt(POLLS.length)];
                String body = poll ? p[0] : TEXTS.get(rnd.nextInt(TEXTS.size()));
                String options = poll ? "[" + String.join(",", java.util.Arrays.stream(p, 1, p.length)
                        .map(o -> "\"" + o + "\"").toList()) + "]" : null;
                lastId = jdbc.queryForObject("""
                        insert into channel_messages (channel_id, body, poll_options, created_at)
                        values (?, ?, cast(? as jsonb), ?) returning id
                        """, Long.class, channelId, body, options, Timestamp.from(t));
                channelMessages++;
                for (Long m : members) {
                    if (m == u.id) continue;
                    if (rnd.nextDouble() < 0.25) {
                        jdbc.update("insert into channel_reactions (message_id, user_id, emoji) values (?, ?, ?)",
                                lastId, m, EMOJIS[rnd.nextInt(EMOJIS.length)]);
                    }
                    if (poll && rnd.nextDouble() < 0.5) {
                        // 첫 번째 보기가 조금 더 많이 뽑히게
                        int opt = rnd.nextDouble() < 0.4 ? 0 : rnd.nextInt(p.length - 1);
                        jdbc.update("insert into channel_poll_votes (message_id, user_id, option_index) values (?, ?, ?)",
                                lastId, m, opt);
                    }
                }
            }
            // 대부분은 다 읽었고, 일부는 최근 글을 안 읽음
            jdbc.update("""
                    update channel_members set last_read_id = case when random() < 0.7 then ? else greatest(0, ? - 2) end
                    where channel_id = ?
                    """, lastId, lastId, channelId);
            count++;
        }
        return count;
    }
}
