package com.yangstagram.sim;

import com.yangstagram.comment.CommentService;
import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.dm.DmService;
import com.yangstagram.follow.FollowService;
import com.yangstagram.post.PostService;
import com.yangstagram.post.Shortcodes;
import com.yangstagram.seed.SeedText;
import com.yangstagram.seed.Topic;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 시뮬레이터 (Lab L05): 가짜 사용자들이 주기적으로 좋아요·댓글·팔로우·게시물·스토리·DM·둘러보기를 한다.
 * 실제 서비스 코드(PostService 등)를 거치므로 알림·실시간 전송·이벤트 기록이 그대로 일어난다.
 * "집중 대상"을 정하면 행동의 절반 정도가 그 사람을 향한다 (내 계정으로 알림이 오는 것을 보기 좋게).
 */
@Service
public class ActivitySimulator {

    private static final Logger log = LoggerFactory.getLogger(ActivitySimulator.class);
    private static final int LOG_SIZE = 200;

    public enum Action { LIKE, COMMENT, FOLLOW, UNFOLLOW, POST, STORY, DM, BROWSE }

    public static final Map<Action, Integer> DEFAULT_MIX = defaultMix();

    private static Map<Action, Integer> defaultMix() {
        Map<Action, Integer> m = new LinkedHashMap<>();
        m.put(Action.LIKE, 35);
        m.put(Action.COMMENT, 12);
        m.put(Action.FOLLOW, 8);
        m.put(Action.UNFOLLOW, 2);
        m.put(Action.POST, 4);
        m.put(Action.STORY, 4);
        m.put(Action.DM, 10);
        m.put(Action.BROWSE, 25);
        return m;
    }

    private static final List<String> DM_LINES = List.of("안녕하세요!", "사진 너무 좋아요 👍", "오늘 뭐 해요?",
            "거기 어디예요?", "다음에 같이 가요", "ㅋㅋㅋ 이거 봤어요?", "주말 잘 보내세요", "이 게시물 완전 내 취향",
            "요즘 잘 지내요?", "고마워요 🙏");

    /**
     * @param focusUsername 집중 대상 (없으면 null)
     * @param mix           행동별 비중 (0이면 안 함)
     */
    public record Config(boolean running, int intervalSec, int actionsPerTick, String focusUsername,
                         Map<Action, Integer> mix) {
    }

    public record LogEntry(Instant at, String actor, Action action, String target, String detail, boolean ok) {
    }

    public record Status(Config config, long totalActions, List<LogEntry> recent, Map<Action, Long> counts) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final PostService posts;
    private final CommentService comments;
    private final FollowService follows;
    private final DmService dms;
    private final LabClock clock;
    private final Random rnd = new Random();

    private volatile Config config = new Config(false, 3, 3, null, DEFAULT_MIX);
    private final Deque<LogEntry> recent = new ArrayDeque<>();
    private final Map<Action, Long> counts = new LinkedHashMap<>();
    private long total;
    private long lastRun;

    public ActivitySimulator(NamedParameterJdbcTemplate jdbc, PostService posts, CommentService comments,
                             FollowService follows, DmService dms, LabClock clock) {
        this.jdbc = jdbc;
        this.posts = posts;
        this.comments = comments;
        this.follows = follows;
        this.dms = dms;
        this.clock = clock;
    }

    public synchronized Status status() {
        return new Status(config, total, List.copyOf(recent), Map.copyOf(counts));
    }

    public synchronized Status configure(Config c) {
        Map<Action, Integer> mix = new LinkedHashMap<>(DEFAULT_MIX);
        if (c.mix() != null) c.mix().forEach((a, w) -> mix.put(a, Math.clamp(w == null ? 0 : w, 0, 100)));
        if (mix.values().stream().mapToInt(Integer::intValue).sum() == 0) {
            throw ApiException.badRequest("EMPTY_MIX", "행동 비중이 모두 0입니다.");
        }
        String focus = c.focusUsername() == null || c.focusUsername().isBlank() ? null
                : c.focusUsername().trim().replaceFirst("^@", "");
        if (focus != null && userId(focus) == null) {
            throw ApiException.notFound("집중 대상 사용자를 찾을 수 없습니다: " + focus);
        }
        config = new Config(c.running(), Math.clamp(c.intervalSec(), 1, 60), Math.clamp(c.actionsPerTick(), 1, 30),
                focus, mix);
        lastRun = 0;
        return status();
    }

    @Scheduled(fixedDelay = 500)
    void tick() {
        Config c = config;
        if (!c.running()) return;
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastRun < c.intervalSec() * 1000L) return;
        lastRun = nowMs;
        try {
            runActions(c, c.actionsPerTick());
        } catch (RuntimeException e) {
            log.warn("활동 시뮬레이터 오류: {}", e.toString());
        }
    }

    /** 지금 바로 n번 (Lab의 "한 번 실행" 버튼) */
    public Status step(int n) {
        runActions(config, Math.clamp(n, 1, 50));
        return status();
    }

    private void runActions(Config c, int n) {
        List<Long> actors = jdbc.queryForList("select id from users where is_fake and deleted_at is null",
                Map.of(), Long.class);
        if (actors.isEmpty()) return;
        Long focus = c.focusUsername() == null ? null : userId(c.focusUsername());
        for (int i = 0; i < n; i++) {
            Action action = pick(c.mix());
            long actor = actors.get(rnd.nextInt(actors.size()));
            if (focus != null && actor == focus) continue;
            boolean toFocus = focus != null && rnd.nextDouble() < 0.5;
            LogEntry entry;
            try {
                entry = perform(action, actor, toFocus ? focus : null);
            } catch (ApiException e) {
                entry = new LogEntry(Instant.now(clock), username(actor), action, null, "건너뜀: " + e.getMessage(), false);
            } catch (RuntimeException e) {
                entry = new LogEntry(Instant.now(clock), username(actor), action, null, "오류: " + e.getMessage(), false);
            }
            if (entry != null) remember(entry);
        }
    }

    private synchronized void remember(LogEntry e) {
        recent.addFirst(e);
        while (recent.size() > LOG_SIZE) recent.removeLast();
        if (e.ok()) {
            total++;
            counts.merge(e.action(), 1L, Long::sum);
        }
    }

    private LogEntry perform(Action action, long actor, Long focus) {
        Instant now = Instant.now(clock);
        String me = username(actor);
        return switch (action) {
            case LIKE -> {
                Post p = focus != null ? postOf(focus, actor) : followeePost(actor);
                if (p == null) yield null;
                posts.like(p.code(), actor);
                yield new LogEntry(now, me, action, p.author(), "게시물 " + p.code() + " 좋아요", true);
            }
            case COMMENT -> {
                Post p = focus != null ? postOf(focus, actor) : followeePost(actor);
                if (p == null) yield null;
                String body = SeedText.COMMENTS.get(rnd.nextInt(SeedText.COMMENTS.size()));
                comments.create(p.code(), actor, body, null);
                yield new LogEntry(now, me, action, p.author(), "“" + body + "”", true);
            }
            case FOLLOW -> {
                Map<String, Object> t = focus != null ? userRow(focus) : randomNotFollowed(actor);
                if (t == null) yield null;
                long target = ((Number) t.get("id")).longValue();
                follows.follow(actor, target, (Boolean) t.get("is_private"));
                yield new LogEntry(now, me, action, (String) t.get("username"),
                        Boolean.TRUE.equals(t.get("is_private")) ? "팔로우 요청" : "팔로우", true);
            }
            case UNFOLLOW -> {
                List<Map<String, Object>> rows = jdbc.queryForList("""
                        select u.id, u.username from follows f join users u on u.id = f.following_id
                        where f.follower_id = :a and f.status = 'ACCEPTED' order by random() limit 1
                        """, Map.of("a", actor));
                if (rows.isEmpty()) yield null;
                follows.unfollow(actor, ((Number) rows.getFirst().get("id")).longValue());
                yield new LogEntry(now, me, action, (String) rows.getFirst().get("username"), "언팔로우", true);
            }
            case POST -> {
                String code = createPost(actor, now);
                yield new LogEntry(now, me, action, null, "새 게시물 " + code, true);
            }
            case STORY -> {
                createStory(actor, now);
                yield new LogEntry(now, me, action, null, "새 스토리", true);
            }
            case DM -> {
                Map<String, Object> t = focus != null ? userRow(focus) : randomFollowee(actor);
                if (t == null) yield null;
                String target = (String) t.get("username");
                var conv = dms.open(actor, List.of(target), null);
                String body = DM_LINES.get(rnd.nextInt(DM_LINES.size()));
                dms.send(conv.id(), actor, body, null);
                yield new LogEntry(now, me, action, target, "“" + body + "”", true);
            }
            case BROWSE -> {
                int n = browse(actor, now);
                yield new LogEntry(now, me, action, null, "게시물 " + n + "개 둘러봄 (노출·시청 기록)", true);
            }
        };
    }

    // ------------------------------------------------------------ 대상 고르기

    private record Post(String code, String author) {
    }

    private Post followeePost(long actor) {
        return jdbc.query("""
                select p.shortcode, a.username from posts p join users a on a.id = p.author_id
                where p.author_id in (select following_id from follows where follower_id = :a and status = 'ACCEPTED')
                  and p.deleted_at is null and p.created_at <= :now
                  and not exists (select 1 from likes l where l.user_id = :a and l.post_id = p.id)
                order by p.created_at desc offset floor(random() * 30) limit 1
                """, new MapSqlParameterSource("a", actor).addValue("now", Timestamp.from(Instant.now(clock))),
                (rs, i) -> new Post(rs.getString(1), rs.getString(2))).stream().findFirst().orElse(null);
    }

    /** 집중 대상의 최근 게시물 중 하나 (비공개라 못 보면 null) */
    private Post postOf(long author, long actor) {
        return jdbc.query("""
                select p.shortcode, a.username from posts p join users a on a.id = p.author_id
                where p.author_id = :author and p.deleted_at is null and p.created_at <= :now
                  and (not a.is_private or exists (select 1 from follows f where f.follower_id = :a
                       and f.following_id = :author and f.status = 'ACCEPTED'))
                order by p.created_at desc offset floor(random() * 5) limit 1
                """, new MapSqlParameterSource("a", actor).addValue("author", author)
                        .addValue("now", Timestamp.from(Instant.now(clock))),
                (rs, i) -> new Post(rs.getString(1), rs.getString(2))).stream().findFirst().orElse(null);
    }

    private Map<String, Object> randomNotFollowed(long actor) {
        return jdbc.queryForList("""
                select id, username, is_private from users
                where id <> :a and deleted_at is null
                  and id not in (select following_id from follows where follower_id = :a)
                order by random() limit 1
                """, Map.of("a", actor)).stream().findFirst().orElse(null);
    }

    private Map<String, Object> randomFollowee(long actor) {
        return jdbc.queryForList("""
                select u.id, u.username, u.is_private from follows f join users u on u.id = f.following_id
                where f.follower_id = :a and f.status = 'ACCEPTED' order by random() limit 1
                """, Map.of("a", actor)).stream().findFirst().orElse(null);
    }

    private Map<String, Object> userRow(long id) {
        return jdbc.queryForList("select id, username, is_private from users where id = :id", Map.of("id", id))
                .stream().findFirst().orElse(null);
    }

    // ------------------------------------------------------------ 만들기 (시드 사진 재사용)

    @Transactional
    String createPost(long actor, Instant now) {
        Map<String, Object> media = jdbc.queryForList("""
                select m.url, m.thumb_url, m.width, m.height from post_media m join posts p on p.id = m.post_id
                where p.type <> 'REEL' and m.media_type = 'IMAGE' order by random() limit 1
                """, Map.of()).stream().findFirst()
                .orElseThrow(() -> ApiException.badRequest("NO_MEDIA", "재사용할 사진이 없습니다."));
        String interests = jdbc.queryForObject("select coalesce(interests, '') from users where id = :a",
                Map.of("a", actor), String.class);
        Topic topic = Topic.values()[rnd.nextInt(Topic.values().length)];
        for (String s : interests.split(",")) {
            if (!s.isBlank()) {
                topic = Topic.valueOf(s.trim());
                break;
            }
        }
        List<String> lines = SeedText.CAPTIONS.get(topic);
        String caption = lines.get(rnd.nextInt(lines.size()));
        String code = Shortcodes.random();
        Long id = jdbc.queryForObject("""
                insert into posts (shortcode, author_id, type, caption, created_at, updated_at, topic)
                values (:code, :a, 'PHOTO', :caption, :now, :now, :topic) returning id
                """, new MapSqlParameterSource("code", code).addValue("a", actor).addValue("caption", caption)
                .addValue("now", Timestamp.from(now)).addValue("topic", topic.name()), Long.class);
        jdbc.update("""
                insert into post_media (post_id, position, url, thumb_url, width, height)
                values (:id, 0, :url, :thumb, :w, :h)
                """, new MapSqlParameterSource("id", id).addValue("url", media.get("url"))
                .addValue("thumb", media.get("thumb_url")).addValue("w", media.get("width"))
                .addValue("h", media.get("height")));
        jdbc.update("update users set posts_count = posts_count + 1 where id = :a", Map.of("a", actor));
        return code;
    }

    private void createStory(long actor, Instant now) {
        Map<String, Object> media = jdbc.queryForList("""
                select url, width, height from post_media where media_type = 'IMAGE' order by random() limit 1
                """, Map.of()).stream().findFirst()
                .orElseThrow(() -> ApiException.badRequest("NO_MEDIA", "재사용할 사진이 없습니다."));
        jdbc.update("""
                insert into stories (author_id, media_url, width, height, created_at, expires_at)
                values (:a, :url, :w, :h, :now, :exp)
                """, new MapSqlParameterSource("a", actor).addValue("url", media.get("url"))
                .addValue("w", media.get("width")).addValue("h", media.get("height"))
                .addValue("now", Timestamp.from(now)).addValue("exp", Timestamp.from(now.plus(Duration.ofHours(24)))));
    }

    /** 피드를 내리며 게시물 6~12개를 보고, 릴스는 시청 시간도 남긴다 */
    private int browse(long actor, Instant now) {
        List<Map<String, Object>> seen = jdbc.queryForList("""
                select p.id, p.type, m.duration_ms from posts p
                join users a on a.id = p.author_id
                join post_media m on m.post_id = p.id and m.position = 0
                where p.deleted_at is null and p.created_at <= :now and p.author_id <> :a
                  and (p.author_id in (select following_id from follows where follower_id = :a and status = 'ACCEPTED')
                       or (not a.is_private and random() < 0.3))
                order by p.created_at desc offset floor(random() * 40) limit :n
                """, new MapSqlParameterSource("a", actor).addValue("now", Timestamp.from(now))
                .addValue("n", 6 + rnd.nextInt(7)));
        List<MapSqlParameterSource> rows = new ArrayList<>();
        for (Map<String, Object> s : seen) {
            boolean reel = "REEL".equals(s.get("type"));
            String surface = reel ? "REELS" : rnd.nextDouble() < 0.75 ? "HOME" : "EXPLORE";
            rows.add(event(actor, "IMPRESSION", s.get("id"), surface, null, now));
            if (reel && s.get("duration_ms") != null) {
                int d = ((Number) s.get("duration_ms")).intValue();
                rows.add(event(actor, "VIEW_TIME", s.get("id"), surface, (int) (d * Math.pow(rnd.nextDouble(), 0.7)), now));
            }
        }
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("""
                    insert into events (user_id, type, post_id, surface, value_ms, created_at)
                    values (:u, :t, :p, :s, :v, :now)
                    """, rows.toArray(MapSqlParameterSource[]::new));
        }
        return seen.size();
    }

    private static MapSqlParameterSource event(long u, String type, Object post, String surface, Integer value,
                                               Instant now) {
        return new MapSqlParameterSource("u", u).addValue("t", type).addValue("p", post).addValue("s", surface)
                .addValue("v", value).addValue("now", Timestamp.from(now));
    }

    // ------------------------------------------------------------ 보조

    private Action pick(Map<Action, Integer> mix) {
        int sum = mix.values().stream().mapToInt(Integer::intValue).sum();
        int r = rnd.nextInt(sum);
        for (var e : mix.entrySet()) {
            r -= e.getValue();
            if (r < 0) return e.getKey();
        }
        return Action.BROWSE;
    }

    private Long userId(String username) {
        return jdbc.queryForList("select id from users where username = :u and deleted_at is null",
                Map.of("u", username), Long.class).stream().findFirst().orElse(null);
    }

    private String username(long id) {
        return jdbc.queryForList("select username from users where id = :id", Map.of("id", id), String.class)
                .stream().findFirst().orElse("?");
    }
}
