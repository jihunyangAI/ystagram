package com.yangstagram.seed;

import com.yangstagram.post.Shortcodes;
import com.yangstagram.seed.SeedPhotos.Photo;
import com.yangstagram.seed.SeedService.FakeFollow;
import com.yangstagram.seed.SeedService.FakeUser;
import com.yangstagram.seed.SeedService.Persona;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 가짜 게시물·좋아요·댓글 생성 (기획서 §8.3).
 *
 * <ul>
 *   <li>게시물 수는 페르소나별로 다르다 (인플루언서 많이, 눈팅족 거의 없음)</li>
 *   <li>최근 1년에 분포하되 최근일수록 많고, 저녁 시간대에 몰린다</li>
 *   <li>좋아요는 주로 팔로워가 누르고, 게시물마다 "품질 점수"가 달라 떡상하는 글이 생긴다</li>
 *   <li>댓글은 좋아요 누른 사람 일부가 달고, 게시물 주인이 가끔 답글을 단다</li>
 * </ul>
 */
final class SeedPosts {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    record Result(int posts, int likes, int comments) {
    }

    private static final class FakePost {
        long id;
        FakeUser author;
        String shortcode;
        String type;
        String caption;
        Topic topic;
        Long locationId;
        boolean hideLikeCount;
        boolean commentsDisabled;
        Instant createdAt;
        double quality;
        List<Photo> photos;
        List<String> tags;
    }

    private record FakeLike(long userId, long postId, Instant createdAt) {
    }

    private record FakeComment(long id, long postId, long authorId, Long parentId, String body, Instant createdAt) {
    }

    private final JdbcTemplate jdbc;
    private final Random rnd;
    private final Instant now;
    private final Map<String, List<Photo>> photosByRatio;
    private final List<Photo> allPhotos;

    SeedPosts(JdbcTemplate jdbc, Random rnd, Instant now, Map<String, List<Photo>> photosByRatio) {
        this.jdbc = jdbc;
        this.rnd = rnd;
        this.now = now;
        this.photosByRatio = photosByRatio;
        this.allPhotos = photosByRatio.values().stream().flatMap(List::stream).toList();
    }

    Result generate(List<FakeUser> users, List<FakeFollow> follows) {
        insertLocations();

        // 승인된 팔로워 목록
        Map<FakeUser, List<FakeUser>> followers = new HashMap<>();
        users.forEach(u -> followers.put(u, new ArrayList<>()));
        for (FakeFollow f : follows) {
            if (f.status().equals("ACCEPTED")) {
                followers.get(f.following()).add(f.follower());
            }
        }

        List<FakePost> posts = buildPosts(users);
        insertPosts(posts);

        List<FakeLike> likes = new ArrayList<>();
        List<FakeComment> comments = new ArrayList<>();
        Map<Long, List<FakeLike>> likesByPost = new HashMap<>();
        for (FakePost post : posts) {
            List<FakeLike> postLikes = buildLikes(post, users, followers.get(post.author));
            likes.addAll(postLikes);
            likesByPost.put(post.id, postLikes);
        }
        long[] nextCommentId = {1};
        Map<Long, FakeUser> byId = new HashMap<>();
        users.forEach(u -> byId.put(u.id, u));
        for (FakePost post : posts) {
            if (!post.commentsDisabled) {
                comments.addAll(buildComments(post, likesByPost.get(post.id), byId, nextCommentId));
            }
        }
        insertLikes(likes);
        insertComments(comments);
        return new Result(posts.size(), likes.size(), comments.size());
    }

    // ------------------------------------------------------------ 게시물

    private List<FakePost> buildPosts(List<FakeUser> users) {
        List<FakePost> posts = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (FakeUser u : users) {
            int count = switch (u.persona) {
                case INFLUENCER -> 25 + rnd.nextInt(16);
                case BUSINESS -> 15 + rnd.nextInt(11);
                case REGULAR -> 4 + rnd.nextInt(15);
                case LURKER -> rnd.nextInt(3);
            };
            for (int i = 0; i < count; i++) {
                FakePost p = new FakePost();
                p.author = u;
                do {
                    p.shortcode = Shortcodes.random(rnd);
                } while (!codes.add(p.shortcode));
                p.createdAt = postTime(u);
                p.quality = Math.exp(rnd.nextGaussian() * 0.45) * (rnd.nextDouble() < 0.03 ? 2.2 : 1.0);
                p.photos = pickPhotos(u);
                p.type = p.photos.size() > 1 ? "CAROUSEL" : "PHOTO";
                Topic topic = u.interests.get(rnd.nextInt(u.interests.size()));
                p.tags = pickTags(topic, u);
                p.caption = caption(topic, u, p.tags);
                p.topic = topic;
                p.locationId = rnd.nextDouble() < (u.persona == Persona.BUSINESS ? 0.6 : 0.35)
                        ? 1L + rnd.nextInt(SeedText.LOCATIONS.size()) : null;
                p.hideLikeCount = rnd.nextDouble() < (u.persona == Persona.INFLUENCER ? 0.10 : 0.03);
                p.commentsDisabled = rnd.nextDouble() < 0.02;
                posts.add(p);
            }
        }
        // 시간순으로 id를 매긴다 (오래된 글이 작은 id)
        posts.sort(Comparator.comparing((FakePost p) -> p.createdAt).thenComparing(p -> p.shortcode));
        for (int i = 0; i < posts.size(); i++) {
            posts.get(i).id = i + 1;
        }
        return posts;
    }

    /** 최근 1년 안, 최근일수록 촘촘하게, 저녁 7~11시에 절반 */
    private Instant postTime(FakeUser u) {
        double maxDays = Math.min(365, Duration.between(u.createdAt, now).toDays());
        double daysAgo = maxDays * Math.pow(rnd.nextDouble(), 1.8);
        ZonedDateTime t = now.minus(Duration.ofMinutes((long) (daysAgo * 24 * 60))).atZone(SEOUL);
        int hour = rnd.nextDouble() < 0.5 ? 19 + rnd.nextInt(5) : 8 + rnd.nextInt(11);
        t = t.withHour(hour).withMinute(rnd.nextInt(60)).withSecond(rnd.nextInt(60));
        Instant result = t.toInstant();
        if (result.isAfter(now)) {
            result = result.minus(Duration.ofDays(1));
        }
        if (result.isBefore(u.createdAt)) {
            result = u.createdAt.plus(Duration.ofHours(1 + rnd.nextInt(48)));
        }
        return result.isAfter(now) ? now.minus(Duration.ofMinutes(5 + rnd.nextInt(120))) : result;
    }

    /** 캐러셀은 같은 비율 사진끼리 (인스타그램은 첫 장 비율로 통일) */
    private List<Photo> pickPhotos(FakeUser u) {
        Photo first = allPhotos.get(rnd.nextInt(allPhotos.size()));
        int count = 1;
        if (rnd.nextDouble() > 0.72) {
            count = 2 + rnd.nextInt(u.persona == Persona.INFLUENCER ? 8 : 4);
        }
        List<Photo> group = photosByRatio.get(first.ratio());
        LinkedHashSet<Photo> chosen = new LinkedHashSet<>();
        chosen.add(first);
        int guard = 0;
        while (chosen.size() < Math.min(count, group.size()) && guard++ < 100) {
            chosen.add(group.get(rnd.nextInt(group.size())));
        }
        return List.copyOf(chosen);
    }

    private List<String> pickTags(Topic topic, FakeUser u) {
        double r = rnd.nextDouble();
        int n = r < 0.35 ? 0 : r < 0.50 ? 1 : r < 0.65 ? 2 : r < 0.80 ? 3 : r < 0.90 ? 4 : 5;
        if (u.persona == Persona.BUSINESS || u.persona == Persona.INFLUENCER) n = Math.max(n, 2);
        List<String> pool = new ArrayList<>(SeedText.TAGS.get(topic));
        Collections.shuffle(pool, rnd);
        List<String> tags = new ArrayList<>();
        for (String tag : pool) {
            if (tags.size() >= n) break;
            tags.add(tag.toLowerCase(Locale.ROOT));
        }
        return tags;
    }

    private String caption(Topic topic, FakeUser u, List<String> tags) {
        if (rnd.nextDouble() < 0.08 && tags.isEmpty()) {
            return null; // 캡션 없이 사진만
        }
        StringBuilder sb = new StringBuilder();
        List<String> lines = SeedText.CAPTIONS.get(topic);
        sb.append(lines.get(rnd.nextInt(lines.size())));
        String tail = SeedText.TAILS.get(rnd.nextInt(SeedText.TAILS.size()));
        if (!tail.isEmpty()) sb.append(' ').append(tail);
        if (u.persona == Persona.BUSINESS) {
            sb.append("\n\n📍 매장 위치와 영업시간은 프로필에서 확인해 주세요");
        }
        if (!tags.isEmpty()) {
            sb.append(rnd.nextBoolean() ? "\n\n" : " ");
            sb.append(String.join(" ", tags.stream().map(t -> "#" + t).toList()));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ 좋아요·댓글

    private List<FakeLike> buildLikes(FakePost post, List<FakeUser> users, List<FakeUser> authorFollowers) {
        List<FakeLike> likes = new ArrayList<>();
        Set<FakeUser> likers = new HashSet<>();
        double ageHours = Math.max(1, Duration.between(post.createdAt, now).toHours());
        double fresh = ageHours < 48 ? 0.4 + 0.6 * ageHours / 48 : 1.0;   // 올린 지 얼마 안 됐으면 아직 덜 모임
        double authorBoost = switch (post.author.persona) {
            case INFLUENCER -> 1.25;
            case BUSINESS -> 0.75;
            default -> 1.0;
        };
        for (FakeUser f : authorFollowers) {
            double base = switch (f.persona) {
                case LURKER -> 0.30;
                case REGULAR -> 0.42;
                case INFLUENCER -> 0.12;
                case BUSINESS -> 0.10;
            };
            if (rnd.nextDouble() < Math.min(0.95, base * post.quality * authorBoost * fresh)) {
                likers.add(f);
            }
        }
        // 공개 계정 글은 팔로워가 아닌 사람도 탐색 등에서 보고 누른다
        if (!post.author.privateAccount) {
            double outside = 0.03 * post.quality * (post.author.persona == Persona.INFLUENCER ? 3 : 1);
            for (FakeUser u : users) {
                if (u != post.author && !likers.contains(u) && rnd.nextDouble() < outside * fresh) {
                    likers.add(u);
                }
            }
        }
        for (FakeUser u : users) { // users 순서로 돌려야 결과가 매번 같다
            if (likers.contains(u)) {
                likes.add(new FakeLike(u.id, post.id, reactionTime(post.createdAt)));
            }
        }
        return likes;
    }

    private List<FakeComment> buildComments(FakePost post, List<FakeLike> likes, Map<Long, FakeUser> byId,
                                            long[] nextId) {
        List<FakeComment> comments = new ArrayList<>();
        for (FakeLike like : likes) {
            FakeUser u = byId.get(like.userId());
            double chance = (u.persona == Persona.LURKER ? 0.03 : 0.11) * Math.min(2, post.quality);
            if (rnd.nextDouble() >= chance) continue;
            Instant at = later(like.createdAt());
            FakeComment c = new FakeComment(nextId[0]++, post.id, u.id, null,
                    SeedText.COMMENTS.get(rnd.nextInt(SeedText.COMMENTS.size())), at);
            comments.add(c);
            // 게시물 주인의 답글
            if (post.author.persona != Persona.LURKER && rnd.nextDouble() < 0.25) {
                comments.add(new FakeComment(nextId[0]++, post.id, post.author.id, c.id(),
                        "@" + u.username + " " + SeedText.REPLIES.get(rnd.nextInt(SeedText.REPLIES.size())),
                        later(at)));
            }
        }
        return comments;
    }

    /** 게시물이 올라온 뒤 반응 시각: 대부분 처음 하루 안 */
    private Instant reactionTime(Instant postedAt) {
        long maxMinutes = Math.max(1, Duration.between(postedAt, now).toMinutes());
        double hours = -Math.log(1 - rnd.nextDouble()) * 10; // 평균 10시간 지수분포
        long minutes = Math.min(maxMinutes, (long) (hours * 60) + 1);
        return postedAt.plus(Duration.ofMinutes(minutes));
    }

    private Instant later(Instant t) {
        Instant next = t.plus(Duration.ofMinutes(1 + rnd.nextInt(180)));
        return next.isAfter(now) ? now : next;
    }

    // ------------------------------------------------------------ 저장

    private void insertLocations() {
        Timestamp ts = Timestamp.from(now);
        jdbc.batchUpdate("insert into locations (name, city, created_at) values (?, ?, ?)",
                SeedText.LOCATIONS, 100, (ps, loc) -> {
                    ps.setString(1, loc[0]);
                    ps.setString(2, loc[1]);
                    ps.setTimestamp(3, ts);
                });
    }

    private void insertPosts(List<FakePost> posts) {
        jdbc.batchUpdate("""
                insert into posts (id, shortcode, author_id, type, caption, location_id, hide_like_count,
                                   comments_disabled, created_at, updated_at, topic)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, posts, 500, (ps, p) -> {
            ps.setLong(1, p.id);
            ps.setString(2, p.shortcode);
            ps.setLong(3, p.author.id);
            ps.setString(4, p.type);
            ps.setString(5, p.caption);
            ps.setObject(6, p.locationId);
            ps.setBoolean(7, p.hideLikeCount);
            ps.setBoolean(8, p.commentsDisabled);
            ps.setTimestamp(9, Timestamp.from(p.createdAt));
            ps.setTimestamp(10, Timestamp.from(p.createdAt));
            ps.setString(11, p.topic.name());
        });
        jdbc.execute("select setval(pg_get_serial_sequence('posts', 'id'), greatest(1, (select max(id) from posts)))");

        List<Object[]> media = new ArrayList<>();
        for (FakePost p : posts) {
            for (int i = 0; i < p.photos.size(); i++) {
                Photo ph = p.photos.get(i);
                media.add(new Object[] {p.id, i, ph.url(), ph.thumbUrl(), ph.width(), ph.height()});
            }
        }
        jdbc.batchUpdate("""
                insert into post_media (post_id, position, url, thumb_url, width, height) values (?, ?, ?, ?, ?, ?)
                """, media);

        // 해시태그
        Map<String, Long> tagIds = new LinkedHashMap<>();
        for (FakePost p : posts) {
            for (String t : p.tags) tagIds.putIfAbsent(t, (long) tagIds.size() + 1);
        }
        jdbc.batchUpdate("insert into hashtags (id, name) values (?, ?)",
                new ArrayList<>(tagIds.entrySet()), 500, (ps, e) -> {
                    ps.setLong(1, e.getValue());
                    ps.setString(2, e.getKey());
                });
        jdbc.execute("select setval(pg_get_serial_sequence('hashtags', 'id'), greatest(1, (select coalesce(max(id), 1) from hashtags)))");
        List<Object[]> links = new ArrayList<>();
        for (FakePost p : posts) {
            for (String t : p.tags) links.add(new Object[] {p.id, tagIds.get(t)});
        }
        jdbc.batchUpdate("insert into post_hashtags (post_id, hashtag_id) values (?, ?)", links);
    }

    private void insertLikes(List<FakeLike> likes) {
        jdbc.batchUpdate("insert into likes (user_id, post_id, created_at) values (?, ?, ?)", likes, 1000,
                (ps, l) -> {
                    ps.setLong(1, l.userId());
                    ps.setLong(2, l.postId());
                    ps.setTimestamp(3, Timestamp.from(l.createdAt()));
                });
    }

    private void insertComments(List<FakeComment> comments) {
        jdbc.batchUpdate("""
                insert into comments (id, post_id, author_id, parent_id, body, created_at) values (?, ?, ?, ?, ?, ?)
                """, comments, 1000, (ps, c) -> {
            ps.setLong(1, c.id());
            ps.setLong(2, c.postId());
            ps.setLong(3, c.authorId());
            ps.setObject(4, c.parentId());
            ps.setString(5, c.body());
            ps.setTimestamp(6, Timestamp.from(c.createdAt()));
        });
        jdbc.execute("select setval(pg_get_serial_sequence('comments', 'id'), greatest(1, (select coalesce(max(id), 1) from comments)))");
    }
}
