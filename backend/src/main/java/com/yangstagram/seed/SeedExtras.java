package com.yangstagram.seed;

import com.yangstagram.seed.SeedService.FakeFollow;
import com.yangstagram.seed.SeedService.FakeUser;
import com.yangstagram.story.Stickers.Sticker;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * M3 가짜 데이터: 친한 친구·친한 친구 공개 스토리, 스토리 스티커와 응답, 하이라이트,
 * 사람 태그, 공동 작업, 즐겨찾기, 약간의 차단·숨기기·제한. 다른 시드가 끝난 뒤에 실행한다.
 */
final class SeedExtras {

    private final JdbcTemplate jdbc;
    private final Random rnd;
    private final Instant now;
    private final JsonMapper json;

    SeedExtras(JdbcTemplate jdbc, Random rnd, Instant now, JsonMapper json) {
        this.jdbc = jdbc;
        this.rnd = rnd;
        this.now = now;
        this.json = json;
    }

    void generate(List<FakeUser> users, List<FakeFollow> follows) {
        Map<Long, List<Long>> followers = new HashMap<>();
        Map<Long, List<Long>> following = new HashMap<>();
        Set<Long> pairs = new HashSet<>();
        for (FakeFollow f : follows) {
            pairs.add(key(f.follower().id, f.following().id));
            if (f.status().equals("ACCEPTED")) {
                followers.computeIfAbsent(f.following().id, k -> new ArrayList<>()).add(f.follower().id);
                following.computeIfAbsent(f.follower().id, k -> new ArrayList<>()).add(f.following().id);
            }
        }
        closeFriends(users, followers);
        stickers();
        highlights(users);
        tagsAndCollabs(users, following);
        favorites(users, following);
        relationControls(users, pairs, following);
    }

    private static long key(long a, long b) {
        return a * 1_000_003L + b;
    }

    // ------------------------------------------------------------ 친한 친구

    /** 사용자 60%가 자기 팔로워 중 3~8명을 친한 친구로. 그 사람들의 스토리 일부는 친한 친구 공개 */
    private void closeFriends(List<FakeUser> users, Map<Long, List<Long>> followers) {
        Timestamp ts = Timestamp.from(now);
        List<Object[]> rows = new ArrayList<>();
        for (FakeUser u : users) {
            List<Long> pool = new ArrayList<>(followers.getOrDefault(u.id, List.of()));
            if (pool.size() < 3 || rnd.nextDouble() > 0.6) continue;
            Collections.shuffle(pool, rnd);
            for (Long f : pool.subList(0, Math.min(pool.size(), 3 + rnd.nextInt(6)))) {
                rows.add(new Object[] {u.id, f, ts});
            }
        }
        jdbc.batchUpdate("insert into close_friends (user_id, friend_id, created_at) values (?, ?, ?)", rows);
        jdbc.update("""
                update stories s set audience = 'CLOSE_FRIENDS'
                where exists (select 1 from close_friends c where c.user_id = s.author_id)
                  and abs(hashtext('cf' || s.id)) % 100 < 18
                """);
        // 친한 친구가 아닌 사람의 조회 기록은 지운다 (볼 수 없었으므로)
        jdbc.update("""
                delete from story_views v using stories s
                where s.id = v.story_id and s.audience = 'CLOSE_FRIENDS'
                  and not exists (select 1 from close_friends c where c.user_id = s.author_id and c.friend_id = v.viewer_id)
                """);
    }

    // ------------------------------------------------------------ 스토리 스티커

    private static final String[][] POLLS = {
            {"오늘 점심 뭐 먹지?", "한식", "양식"}, {"주말에 뭐 할까", "집콕", "나들이"}, {"어느 쪽이 더 예뻐요?", "왼쪽", "오른쪽"},
            {"커피 vs 차", "커피", "차"}, {"여름 vs 겨울", "여름", "겨울"}, {"이 사진 필터", "있게", "없게"}};
    private static final String[][] QUIZZES = {
            {"여기는 어디일까요?", "제주", "부산", "강릉"}, {"제가 제일 좋아하는 계절은?", "봄", "여름", "가을", "겨울"},
            {"이 카페 커피 가격은?", "4,500원", "5,500원", "6,500원"}};
    private static final String[] QUESTIONS = {"무엇이든 물어보세요", "요즘 듣는 노래 추천해 주세요", "다음 여행지 추천!",
            "주말에 뭐 하세요?", "저한테 궁금한 거"};
    private static final String[] ANSWERS = {"제주도요!", "아이유 신곡", "요즘 뭐 하고 지내요?", "사진 어떻게 찍어요?",
            "그 카페 어디예요?", "부산 추천합니다", "집에서 쉬어요 ㅎㅎ", "카메라 뭐 써요?", "언제 또 와요?"};
    private static final String[] SLIDERS = {"오늘 기분은?", "이 메뉴 점수", "여기 분위기 어때요?"};
    private static final String[] PLACES = {"성수동", "제주 애월", "부산 해운대", "강릉 안목해변", "서울숲", "을지로"};

    /** 활성·지난 스토리의 약 30%에 스티커 1개, 응답은 그 스토리를 본 사람 중에서 */
    private void stickers() {
        List<Map<String, Object>> stories = jdbc.queryForList("""
                select s.id, s.author_id, s.created_at from stories s
                where abs(hashtext('st' || s.id)) % 100 < 30 order by s.id
                """);
        List<String> usernames = jdbc.queryForList("select username from users order by id", String.class);
        List<Object[]> updates = new ArrayList<>();
        List<Object[]> responses = new ArrayList<>();
        for (Map<String, Object> st : stories) {
            long id = ((Number) st.get("id")).longValue();
            double x = 0.3 + rnd.nextDouble() * 0.4, y = 0.55 + rnd.nextDouble() * 0.25;
            int kind = rnd.nextInt(8);
            Sticker s = switch (kind) {
                case 0, 1 -> {
                    String[] p = POLLS[rnd.nextInt(POLLS.length)];
                    yield new Sticker("s0", "POLL", x, y, p[0], List.of(p[1], p[2]), null, null, null, null, null, null, null, null);
                }
                case 2 -> {
                    String[] q = QUIZZES[rnd.nextInt(QUIZZES.length)];
                    List<String> opts = List.of(q).subList(1, q.length);
                    yield new Sticker("s0", "QUIZ", x, y, q[0], opts, rnd.nextInt(opts.size()), null, null, null, null, null, null, null);
                }
                case 3 -> new Sticker("s0", "QUESTION", x, y, QUESTIONS[rnd.nextInt(QUESTIONS.length)], null, null, null,
                        null, null, null, null, null, null);
                case 4 -> new Sticker("s0", "SLIDER", x, y, SLIDERS[rnd.nextInt(SLIDERS.length)], null, null,
                        List.of("😍", "🔥", "😂", "☕").get(rnd.nextInt(4)), null, null, null, null, null, null);
                case 5 -> new Sticker("s0", "MENTION", x, y, null, null, null, null,
                        usernames.get(rnd.nextInt(usernames.size())), null, null, null, null, null);
                case 6 -> new Sticker("s0", "LOCATION", x, y, null, null, null, null, null, null,
                        PLACES[rnd.nextInt(PLACES.length)], null, null, null);
                default -> new Sticker("s0", "COUNTDOWN", x, y, "다음 여행까지", null, null, null, null, null, null, null, null,
                        now.plus(Duration.ofDays(3 + rnd.nextInt(40))).toString());
            };
            updates.add(new Object[] {json.writeValueAsString(List.of(s)), id});
            if (List.of("POLL", "QUIZ", "QUESTION", "SLIDER").contains(s.type())) {
                List<Long> viewers = jdbc.queryForList("select viewer_id from story_views where story_id = ?", Long.class, id);
                Instant at = ((Timestamp) st.get("created_at")).toInstant().plus(Duration.ofMinutes(20));
                for (Long v : viewers) {
                    if (rnd.nextDouble() > (s.type().equals("QUESTION") ? 0.15 : 0.6)) continue;
                    String value = switch (s.type()) {
                        case "POLL", "QUIZ" -> String.valueOf(rnd.nextInt(s.options().size()));
                        case "SLIDER" -> String.valueOf(Math.min(100, Math.max(0, (int) (70 + rnd.nextGaussian() * 20))));
                        default -> ANSWERS[rnd.nextInt(ANSWERS.length)];
                    };
                    responses.add(new Object[] {id, "s0", v, value, Timestamp.from(at.isAfter(now) ? now : at)});
                }
            }
        }
        jdbc.batchUpdate("update stories set stickers = cast(? as jsonb) where id = ?", updates);
        jdbc.batchUpdate("""
                insert into story_sticker_responses (story_id, sticker_id, user_id, value, created_at) values (?, ?, ?, ?, ?)
                """, responses);
    }

    // ------------------------------------------------------------ 하이라이트

    private static final String[] HIGHLIGHT_TITLES = {"여행", "맛집", "일상", "카페", "운동", "가족", "작업", "🐶", "☕", "✈️", "Q&A"};

    /** 지난 스토리가 3개 이상인 사람 중 70%가 하이라이트 1~3개 */
    private void highlights(List<FakeUser> users) {
        Timestamp ts = Timestamp.from(now);
        for (FakeUser u : users) {
            List<Long> archived = jdbc.queryForList(
                    "select id from stories where author_id = ? and expires_at <= ? order by created_at", Long.class,
                    u.id, ts);
            if (archived.size() < 3 || rnd.nextDouble() > 0.7) continue;
            int n = 1 + rnd.nextInt(Math.min(3, archived.size() / 2));
            List<String> titles = new ArrayList<>(List.of(HIGHLIGHT_TITLES));
            Collections.shuffle(titles, rnd);
            for (int h = 0; h < n; h++) {
                List<Long> pick = new ArrayList<>(archived);
                Collections.shuffle(pick, rnd);
                pick = pick.subList(0, Math.min(pick.size(), 2 + rnd.nextInt(5)));
                Long hid = jdbc.queryForObject("""
                        insert into highlights (user_id, title, cover_story_id, position, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?) returning id
                        """, Long.class, u.id, titles.get(h), pick.getFirst(), h, ts, ts);
                for (Long s : pick) {
                    jdbc.update("insert into highlight_items (highlight_id, story_id, added_at) values (?, ?, ?)", hid, s, ts);
                }
            }
        }
    }

    // ------------------------------------------------------------ 사람 태그·공동 작업

    /** 사진 게시물의 12%에 작성자가 팔로우하는 사람 1~3명 태그, 2%는 공동 작업(수락됨) */
    private void tagsAndCollabs(List<FakeUser> users, Map<Long, List<Long>> following) {
        List<Map<String, Object>> posts = jdbc.queryForList("""
                select p.id, p.author_id, p.created_at, (select count(*) from post_media m where m.post_id = p.id) as n
                from posts p where p.type <> 'REEL' and abs(hashtext('tg' || p.id)) % 100 < 14 order by p.id
                """);
        List<Object[]> tags = new ArrayList<>();
        List<Object[]> collabs = new ArrayList<>();
        List<Object[]> notes = new ArrayList<>();
        for (Map<String, Object> p : posts) {
            long id = ((Number) p.get("id")).longValue();
            long author = ((Number) p.get("author_id")).longValue();
            int n = ((Number) p.get("n")).intValue();
            Timestamp at = (Timestamp) p.get("created_at");
            List<Long> pool = new ArrayList<>(following.getOrDefault(author, List.of()));
            if (pool.isEmpty()) continue;
            Collections.shuffle(pool, rnd);
            boolean collab = rnd.nextDouble() < 0.15;
            if (collab) {
                long c = pool.getFirst();
                collabs.add(new Object[] {id, c, at});
                notes.add(new Object[] {c, author, "COLLAB_INVITE", id, at});
                pool = pool.subList(1, pool.size());
            }
            for (Long t : pool.subList(0, Math.min(pool.size(), 1 + rnd.nextInt(3)))) {
                tags.add(new Object[] {id, t, rnd.nextInt(n), 0.15 + rnd.nextDouble() * 0.7, 0.2 + rnd.nextDouble() * 0.6});
                notes.add(new Object[] {t, author, "TAG", id, at});
            }
        }
        jdbc.batchUpdate("insert into post_tags (post_id, user_id, media_index, x, y) values (?, ?, ?, ?, ?) on conflict do nothing",
                tags);
        jdbc.batchUpdate("""
                insert into post_collaborators (post_id, user_id, status, created_at) values (?, ?, 'ACCEPTED', ?)
                on conflict do nothing
                """, collabs);
        Timestamp readBefore = Timestamp.from(now.minus(Duration.ofDays(2)));
        Timestamp since = Timestamp.from(now.minus(Duration.ofDays(60)));
        List<Object[]> recent = notes.stream().filter(r -> ((Timestamp) r[4]).after(since))
                .map(r -> new Object[] {r[0], r[1], r[2], r[3], r[4], ((Timestamp) r[4]).before(readBefore) ? r[4] : null})
                .toList();
        jdbc.batchUpdate("""
                insert into notifications (user_id, actor_id, type, post_id, created_at, read_at) values (?, ?, ?, ?, ?, ?)
                """, recent);
    }

    // ------------------------------------------------------------ 즐겨찾기·차단·숨기기·제한

    private void favorites(List<FakeUser> users, Map<Long, List<Long>> following) {
        List<Object[]> rows = new ArrayList<>();
        for (FakeUser u : users) {
            List<Long> pool = new ArrayList<>(following.getOrDefault(u.id, List.of()));
            if (pool.isEmpty() || rnd.nextDouble() > 0.5) continue;
            Collections.shuffle(pool, rnd);
            for (Long f : pool.subList(0, Math.min(pool.size(), 1 + rnd.nextInt(5)))) rows.add(new Object[] {u.id, f});
        }
        jdbc.batchUpdate("update follows set is_favorite = true where follower_id = ? and following_id = ?", rows);
    }

    /** 사용자 50명당 차단 2쌍·숨기기 4쌍·제한 2쌍 (서로 팔로우하지 않는 쌍만 차단) */
    private void relationControls(List<FakeUser> users, Set<Long> pairs, Map<Long, List<Long>> following) {
        Timestamp ts = Timestamp.from(now);
        int scale = Math.max(1, users.size() / 50);
        int blocks = 0, guard = 0;
        while (blocks < 2 * scale && guard++ < 1000) {
            FakeUser a = users.get(rnd.nextInt(users.size()));
            FakeUser b = users.get(rnd.nextInt(users.size()));
            if (a.id == b.id || pairs.contains(key(a.id, b.id)) || pairs.contains(key(b.id, a.id))) continue;
            if (jdbc.update("insert into blocks (blocker_id, blocked_id, created_at) values (?, ?, ?) on conflict do nothing",
                    a.id, b.id, ts) == 1) blocks++;
        }
        for (int i = 0; i < 4 * scale; i++) {
            FakeUser a = users.get(rnd.nextInt(users.size()));
            List<Long> f = following.getOrDefault(a.id, List.of());
            if (f.isEmpty()) continue;
            jdbc.update("""
                    insert into mutes (user_id, muted_id, posts, stories, created_at) values (?, ?, ?, ?, ?) on conflict do nothing
                    """, a.id, f.get(rnd.nextInt(f.size())), rnd.nextBoolean(), true, ts);
        }
        for (int i = 0; i < 2 * scale; i++) {
            FakeUser a = users.get(rnd.nextInt(users.size()));
            FakeUser b = users.get(rnd.nextInt(users.size()));
            if (a.id == b.id) continue;
            jdbc.update("insert into restricts (user_id, restricted_id, created_at) values (?, ?, ?) on conflict do nothing",
                    a.id, b.id, ts);
        }
        // 차단한 쌍 사이의 알림·DM 대화는 남기지 않는다
        jdbc.update("""
                delete from notifications n using blocks b
                where (n.user_id = b.blocker_id and n.actor_id = b.blocked_id) or (n.user_id = b.blocked_id and n.actor_id = b.blocker_id)
                """);
    }
}
