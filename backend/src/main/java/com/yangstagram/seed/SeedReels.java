package com.yangstagram.seed;

import com.yangstagram.post.Shortcodes;
import com.yangstagram.seed.SeedService.FakeFollow;
import com.yangstagram.seed.SeedService.FakeUser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 가짜 릴스 (기획서 §8.3). seed-media/reels/ 의 영상(gradlew reelClips로 만든 것)을 돌려 쓴다.
 * 영상이 없으면 만들지 않는다. 좋아요는 팔로워 + 추천으로 본 사람, 조회수는 좋아요의 5~15배.
 */
final class SeedReels {

    record Clip(String file, String poster, int width, int height, int durationMs) {
    }

    private static final List<String> CAPTIONS = List.of(
            "오늘 하루 30초 요약", "이 노래랑 찰떡", "주말 브이로그 🎬", "끝까지 보세요", "따라 해 보세요!",
            "요즘 빠진 것", "출근 전 루틴", "산책하다 찍음 🌿", "여기 꼭 가 보세요", "1분 레시피",
            "운동 기록 💪", "비 오는 날 감성", "작업실 일상", "노을 맛집 발견", "우리 집 고양이 근황");
    private static final List<String> TAGS = List.of("릴스", "reels", "브이로그", "일상", "데일리", "추천");

    private final JdbcTemplate jdbc;
    private final Random rnd;
    private final Instant now;
    private final Path reelsDir;
    private final JsonMapper json;

    private SeedSize size = SeedSize.MEDIUM;

    SeedReels density(SeedSize size) {
        this.size = size;
        return this;
    }

    SeedReels(JdbcTemplate jdbc, Random rnd, Instant now, Path reelsDir, JsonMapper json) {
        this.jdbc = jdbc;
        this.rnd = rnd;
        this.now = now;
        this.reelsDir = reelsDir;
        this.json = json;
    }

    List<Clip> clips() {
        Path manifest = reelsDir.resolve("manifest.json");
        if (!Files.exists(manifest)) return List.of();
        try {
            return List.of(json.readValue(Files.readString(manifest), Clip[].class));
        } catch (Exception e) {
            return List.of();
        }
    }

    /** @return 만든 릴스 수 */
    int generate(List<FakeUser> users, List<FakeFollow> follows) {
        List<Clip> clips = clips();
        if (clips.isEmpty()) return 0;

        Map<Long, List<Long>> followers = new HashMap<>();
        for (FakeFollow f : follows) {
            if (f.status().equals("ACCEPTED")) {
                followers.computeIfAbsent(f.following().id, k -> new ArrayList<>()).add(f.follower().id);
            }
        }
        List<Long> publicUsers = users.stream().filter(u -> !u.privateAccount).map(u -> u.id).toList();
        long nextPostId = jdbc.queryForObject("select coalesce(max(id), 0) + 1 from posts", Long.class);
        long nextCommentId = jdbc.queryForObject("select coalesce(max(id), 0) + 1 from comments", Long.class);

        List<Object[]> posts = new ArrayList<>();
        List<Object[]> media = new ArrayList<>();
        List<Object[]> likes = new ArrayList<>();
        List<Object[]> comments = new ArrayList<>();
        Set<String> codes = new HashSet<>(jdbc.queryForList("select shortcode from posts", String.class));

        for (FakeUser u : users) {
            int count = switch (u.persona) {
                case INFLUENCER -> 4 + rnd.nextInt(5);
                case BUSINESS -> 1 + rnd.nextInt(3);
                case REGULAR -> rnd.nextDouble() < 0.5 ? rnd.nextInt(3) : 0;
                case LURKER -> 0;
            };
            count = size.scale(count); // large: 사람당 릴스 2.5배
            for (int i = 0; i < count; i++) {
                long id = nextPostId++;
                Clip clip = clips.get(rnd.nextInt(clips.size()));
                Instant at = now.minus(Duration.ofMinutes(30 + rnd.nextInt(90 * 24 * 60)));
                String code;
                do {
                    code = Shortcodes.random(rnd);
                } while (!codes.add(code));
                String caption = CAPTIONS.get(rnd.nextInt(CAPTIONS.size())) + " #" + TAGS.get(rnd.nextInt(TAGS.size()));
                posts.add(new Object[] {id, code, u.id, caption, Timestamp.from(at)});
                media.add(new Object[] {id, "/media/reels/" + clip.file(), "/media/reels/" + clip.poster(),
                        clip.width(), clip.height(), clip.durationMs()});

                // 좋아요: 팔로워 일부 + (공개 계정이면) 추천으로 본 사람들
                double quality = Math.exp(rnd.nextGaussian() * 0.7) * (u.persona == SeedService.Persona.INFLUENCER ? 1.6 : 1);
                Set<Long> likers = new HashSet<>();
                for (Long f : followers.getOrDefault(u.id, List.of())) {
                    if (rnd.nextDouble() < Math.min(0.85, 0.25 * quality)) likers.add(f);
                }
                if (!u.privateAccount) {
                    int outsiders = (int) (publicUsers.size() * Math.min(0.4, 0.05 * quality));
                    for (int k = 0; k < outsiders; k++) {
                        long who = publicUsers.get(rnd.nextInt(publicUsers.size()));
                        if (who != u.id) likers.add(who);
                    }
                }
                for (Long l : likers) {
                    Instant la = at.plus(Duration.ofMinutes(1 + rnd.nextInt(3 * 24 * 60)));
                    if (la.isBefore(now)) likes.add(new Object[] {l, id, Timestamp.from(la)});
                }
                List<Long> likerList = new ArrayList<>(likers);
                int c = likerList.isEmpty() ? 0 : rnd.nextInt(Math.min(6, likerList.size()) + 1);
                for (int k = 0; k < c; k++) {
                    long who = likerList.get(rnd.nextInt(likerList.size()));
                    Instant ca = at.plus(Duration.ofMinutes(2 + rnd.nextInt(2 * 24 * 60)));
                    if (ca.isBefore(now)) {
                        comments.add(new Object[] {nextCommentId++, id, who,
                                SeedText.COMMENTS.get(rnd.nextInt(SeedText.COMMENTS.size())), Timestamp.from(ca)});
                    }
                }
            }
        }

        jdbc.batchUpdate("""
                insert into posts (id, shortcode, author_id, type, caption, created_at, updated_at, recommendable)
                values (?, ?, ?, 'REEL', ?, ?, ?, true)
                """, posts, 500, (ps, p) -> {
            ps.setLong(1, (Long) p[0]);
            ps.setString(2, (String) p[1]);
            ps.setLong(3, (Long) p[2]);
            ps.setString(4, (String) p[3]);
            ps.setTimestamp(5, (Timestamp) p[4]);
            ps.setTimestamp(6, (Timestamp) p[4]);
        });
        jdbc.execute("select setval(pg_get_serial_sequence('posts', 'id'), greatest(1, (select max(id) from posts)))");
        jdbc.batchUpdate("""
                insert into post_media (post_id, position, url, thumb_url, width, height, media_type, duration_ms)
                values (?, 0, ?, ?, ?, ?, 'VIDEO', ?)
                """, media, 500, (ps, m) -> {
            ps.setLong(1, (Long) m[0]);
            ps.setString(2, (String) m[1]);
            ps.setString(3, (String) m[2]);
            ps.setInt(4, (Integer) m[3]);
            ps.setInt(5, (Integer) m[4]);
            ps.setInt(6, (Integer) m[5]);
        });
        jdbc.batchUpdate("insert into likes (user_id, post_id, created_at) values (?, ?, ?) on conflict do nothing",
                likes, 1000, (ps, l) -> {
                    ps.setLong(1, (Long) l[0]);
                    ps.setLong(2, (Long) l[1]);
                    ps.setTimestamp(3, (Timestamp) l[2]);
                });
        jdbc.batchUpdate("insert into comments (id, post_id, author_id, body, created_at) values (?, ?, ?, ?, ?)",
                comments, 1000, (ps, c) -> {
                    ps.setLong(1, (Long) c[0]);
                    ps.setLong(2, (Long) c[1]);
                    ps.setLong(3, (Long) c[2]);
                    ps.setString(4, (String) c[3]);
                    ps.setTimestamp(5, (Timestamp) c[4]);
                });
        jdbc.execute("select setval(pg_get_serial_sequence('comments', 'id'), greatest(1, (select max(id) from comments)))");
        // 캡션의 해시태그 연결 (릴스용 태그는 새로 만들 수 있다)
        for (String t : TAGS) {
            jdbc.update("insert into hashtags (name, post_count) values (?, 0) on conflict (name) do nothing", t);
        }
        jdbc.update("""
                insert into post_hashtags (post_id, hashtag_id)
                select p.id, h.id from posts p join hashtags h on p.caption like '%#' || h.name
                where p.type = 'REEL' on conflict do nothing
                """);
        // 조회수: 좋아요의 5~15배
        jdbc.update("""
                update posts p set view_count = (select count(*) from likes l where l.post_id = p.id)
                                                * (5 + abs(hashtext(p.shortcode)) % 11)
                where p.type = 'REEL'
                """);
        return posts.size();
    }
}
