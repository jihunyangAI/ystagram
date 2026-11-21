package com.yangstagram.seed;

import com.yangstagram.common.time.LabClock;
import com.yangstagram.user.AccountType;
import com.yangstagram.user.UsernamePolicy;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import net.datafaker.Faker;
import com.yangstagram.media.MediaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가짜 데이터 생성기 (기획서 §8).
 * 같은 규모·시드값이면 항상 같은 데이터가 나온다 (실험 재현용).
 *
 * <p>진짜처럼 보이게 하는 장치
 * <ul>
 *   <li>페르소나: 인플루언서 / 비즈니스 / 일반 / 눈팅족 비율을 나눔</li>
 *   <li>팔로워 분포: 인기도 가중치 때문에 소수에게 팔로워가 몰림(멱법칙)</li>
 *   <li>관심사 군집: 관심 주제가 겹치면 팔로우할 확률이 높아짐</li>
 *   <li>맞팔로우: 관심사가 겹치는 일반 사용자끼리 일정 확률로 서로 팔로우</li>
 * </ul>
 */
@Service
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final LabClock clock;
    private final SeedProperties properties;
    private final SeedPhotos seedPhotos;
    private final MediaProperties media;
    private final JsonMapper json;

    public SeedService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, LabClock clock,
                       SeedProperties properties, SeedPhotos seedPhotos, MediaProperties media, JsonMapper json) {
        this.media = media;
        this.json = json;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.properties = properties;
        this.seedPhotos = seedPhotos;
    }

    public record SeedResult(SeedSize size, long seed, int users, int follows, int posts, int likes,
                             int comments, int reels, int stories, int conversations, int messages, int notifications,
                             long elapsedMillis) {
    }

    enum Persona { INFLUENCER, BUSINESS, REGULAR, LURKER }

    /** 생성 중에만 쓰는 가짜 사용자 정보 */
    static final class FakeUser {
        long id;
        Persona persona;
        String username;
        String fullName;
        String bio;
        AccountType accountType = AccountType.PERSONAL;
        String category;
        boolean privateAccount;
        boolean verified;
        List<Topic> interests;
        LocalDate birthDate;
        Instant createdAt;
        double popularity;
    }

    record FakeFollow(FakeUser follower, FakeUser following, String status, boolean favorite, Instant createdAt) {
    }

    @Transactional
    public SeedResult generate(SeedSize size, long seed) {
        long started = System.currentTimeMillis();
        jdbc.execute("""
                truncate table comments, likes, post_hashtags, hashtags, post_media, posts, locations,
                               follows, users, stories, story_views, notifications, conversations,
                               conversation_members, messages, message_reactions, collections,
                               collection_posts, saved_posts, reposts, comment_likes, search_history,
                               blocks, restricts, mutes, close_friends, story_sticker_responses, highlights,
                               highlight_items, post_tags, post_collaborators, post_views,
                               events, user_topic_prefs, scheduled_posts, scheduled_messages, notes, ads
                               restart identity cascade
                """);

        Random rnd = new Random(seed);
        Faker ko = new Faker(Locale.KOREAN, new Random(seed + 1));
        Faker en = new Faker(Locale.ENGLISH, new Random(seed + 2));
        Instant now = Instant.now(clock);

        List<FakeUser> users = buildUsers(size.users(), rnd, ko, en, now);
        insertUsers(users, now);

        List<FakeFollow> follows = buildFollows(users, rnd, now, size);
        insertFollows(follows);

        var photos = seedPhotos.load();
        SeedPosts.Result content = new SeedPosts(jdbc, rnd, now, photos).generate(users, follows);
        int reels = new SeedReels(jdbc, rnd, now, media.reels(), json).density(size).generate(users, follows);
        recomputeCounts();
        SeedSocial.Result social = new SeedSocial(jdbc, rnd, now,
                photos.values().stream().flatMap(List::stream).toList()).density(size).generate(users, follows);
        new SeedExtras(jdbc, rnd, now, json).generate(users, follows);
        int events = new SeedEvents(jdbc, now, seed).generate();
        SeedFeatures.Result features = new SeedFeatures(jdbc, rnd, now).generate();
        log.info("M4-② 데이터: AI 프로필={}, AI 라벨 게시물={}, 광고={}, 노트={}, 시험 릴스={}", features.aiProfiles(),
                features.aiPosts(), features.ads(), features.notes(), features.trialReels());

        jdbc.execute("analyze"); // 대량으로 넣은 뒤 통계를 새로 (쿼리 계획이 틀어지지 않게)
        long elapsed = System.currentTimeMillis() - started;
        log.info("가짜 데이터 생성 완료: 규모={}, 시드={}, 사용자={}, 팔로우={}, 게시물={}, 좋아요={}, 댓글={}, "
                        + "릴스={}, 스토리={}, 대화방={}, 메시지={}, 알림={}, 이벤트={}, {}ms",
                size, seed, users.size(), follows.size(), content.posts(), content.likes(), content.comments(), reels,
                social.stories(), social.conversations(), social.messages(), social.notifications(), events, elapsed);
        return new SeedResult(size, seed, users.size(), follows.size(), content.posts(), content.likes(),
                content.comments(), reels, social.stories(), social.conversations(), social.messages(),
                social.notifications(), elapsed);
    }

    // ---------------------------------------------------------------- 사용자

    private List<FakeUser> buildUsers(int n, Random rnd, Faker ko, Faker en, Instant now) {
        // 페르소나 배분
        List<Persona> personas = new ArrayList<>();
        int influencers = Math.max(2, Math.round(n * 0.05f));
        int businesses = Math.max(2, Math.round(n * 0.06f));
        int lurkers = Math.round(n * 0.15f);
        for (int i = 0; i < n; i++) {
            if (i < influencers) personas.add(Persona.INFLUENCER);
            else if (i < influencers + businesses) personas.add(Persona.BUSINESS);
            else if (i < influencers + businesses + lurkers) personas.add(Persona.LURKER);
            else personas.add(Persona.REGULAR);
        }
        Collections.shuffle(personas, rnd);

        Set<String> usedUsernames = new HashSet<>();
        List<FakeUser> users = new ArrayList<>(n);
        boolean firstInfluencer = true;
        for (Persona persona : personas) {
            FakeUser u = new FakeUser();
            u.persona = persona;
            u.interests = pickInterests(persona, rnd);

            if (persona == Persona.BUSINESS) {
                fillBusiness(u, rnd, usedUsernames);
            } else {
                boolean korean = rnd.nextDouble() < 0.7;
                u.fullName = korean
                        ? ko.name().lastName() + ko.name().firstName()
                        : en.name().firstName() + " " + en.name().lastName();
                String token = korean ? pick(KOREAN_ROMANIZED_NAMES, rnd)
                        : en.name().firstName().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
                u.username = uniqueUsername(personalUsername(token, u.interests, rnd), usedUsernames, rnd);
                u.bio = personalBio(u, rnd);
            }

            switch (persona) {
                case INFLUENCER -> {
                    u.accountType = AccountType.CREATOR;
                    u.category = pick(List.of("디지털 크리에이터", "크리에이터", "사진작가", "블로거", "아티스트"), rnd);
                    u.verified = firstInfluencer || rnd.nextDouble() < 0.5;
                    firstInfluencer = false;
                    u.popularity = 20 + rnd.nextDouble() * 15;
                }
                case BUSINESS -> u.popularity = 4 + rnd.nextDouble() * 3;
                case REGULAR -> {
                    u.privateAccount = rnd.nextDouble() < 0.30;
                    u.popularity = 0.6 + rnd.nextDouble() * 2.4;
                }
                case LURKER -> {
                    u.privateAccount = rnd.nextDouble() < 0.40;
                    u.popularity = 0.3 + rnd.nextDouble() * 0.4;
                }
            }

            u.birthDate = birthDate(persona, rnd, now);
            int maxDaysAgo = persona == Persona.INFLUENCER ? 2500 : 1500;
            u.createdAt = now.minus(Duration.ofDays(30 + rnd.nextInt(maxDaysAgo)))
                    .minus(Duration.ofMinutes(rnd.nextInt(24 * 60)));
            users.add(u);
        }
        return users;
    }

    private List<Topic> pickInterests(Persona persona, Random rnd) {
        List<Topic> all = new ArrayList<>(List.of(Topic.values()));
        if (persona == Persona.BUSINESS) {
            all.retainAll(BUSINESS_NAMES.keySet());
        }
        Collections.shuffle(all, rnd);
        int count = persona == Persona.BUSINESS ? 1 : 1 + rnd.nextInt(3);
        return List.copyOf(all.subList(0, count));
    }

    private void fillBusiness(FakeUser u, Random rnd, Set<String> used) {
        Topic topic = u.interests.getFirst();
        String[] word = pick(BUSINESS_WORDS, rnd); // {한글, 로마자}
        String[] kind = BUSINESS_NAMES.get(topic);  // {한글 업종, 영문 업종, 카테고리}
        u.fullName = word[0] + " " + kind[0];
        u.username = uniqueUsername(word[1] + "." + kind[1], used, rnd);
        u.accountType = AccountType.BUSINESS;
        u.category = kind[2];
        u.bio = topic.emoji() + " " + kind[0] + " · 매일 " + (9 + rnd.nextInt(3)) + ":00-" + (20 + rnd.nextInt(3)) + ":00"
                + "\n📍" + pick(CITIES, rnd) + "\n예약·문의는 DM";
    }

    private String personalUsername(String token, List<Topic> interests, Random rnd) {
        if (token.isEmpty()) {
            token = pick(KOREAN_ROMANIZED_NAMES, rnd);
        }
        String slug = pick(interests.getFirst().slugs(), rnd);
        int year = 85 + rnd.nextInt(20); // 85~04
        String yy = String.format("%02d", year % 100);
        return switch (rnd.nextInt(7)) {
            case 0 -> token + "." + slug;
            case 1 -> slug + "_" + token;
            case 2 -> token + yy;
            case 3 -> token + "." + slug + "_" + rnd.nextInt(100);
            case 4 -> "the." + slug + "." + token;
            case 5 -> token + "_" + pick(KOREAN_ROMANIZED_NAMES, rnd);
            default -> token + "." + yy + "." + slug;
        };
    }

    private String uniqueUsername(String candidate, Set<String> used, Random rnd) {
        String base = candidate.length() > 26 ? candidate.substring(0, 26) : candidate;
        base = base.replaceAll("\\.+$", "");
        String name = base;
        while (!UsernamePolicy.isValid(name) || used.contains(name)) {
            name = base + "_" + (10 + rnd.nextInt(990));
        }
        used.add(name);
        return name;
    }

    private String personalBio(FakeUser u, Random rnd) {
        double emptyChance = u.persona == Persona.LURKER ? 0.6 : u.persona == Persona.REGULAR ? 0.3 : 0.0;
        if (rnd.nextDouble() < emptyChance) {
            return null;
        }
        Topic main = u.interests.getFirst();
        StringBuilder bio = new StringBuilder(main.emoji()).append(' ').append(pick(main.bioPhrases(), rnd));
        if (u.interests.size() > 1 && rnd.nextBoolean()) {
            Topic second = u.interests.get(1);
            bio.append(" | ").append(second.emoji()).append(' ').append(second.label());
        }
        if (rnd.nextDouble() < 0.4) {
            bio.append("\n📍").append(pick(CITIES, rnd));
        }
        if (u.persona == Persona.INFLUENCER) {
            bio.append("\n📩 협업 문의는 DM");
        }
        return bio.length() > 150 ? bio.substring(0, 150) : bio.toString();
    }

    private LocalDate birthDate(Persona persona, Random rnd, Instant now) {
        int age;
        if (persona == Persona.REGULAR && rnd.nextDouble() < 0.05) {
            age = 14 + rnd.nextInt(4);              // 10대 (10대 계정 기능용)
        } else if (persona == Persona.INFLUENCER) {
            age = 21 + rnd.nextInt(15);
        } else {
            age = 19 + (int) Math.abs(rnd.nextGaussian() * 9) + rnd.nextInt(6); // 20대 중후반에 몰림
            age = Math.min(age, 58);
        }
        return LocalDate.ofInstant(now, SEOUL).minusYears(age).minusDays(rnd.nextInt(365));
    }

    private void insertUsers(List<FakeUser> users, Instant now) {
        String hash = passwordEncoder.encode(properties.fakePassword()); // 한 번만 계산해서 모든 가짜 계정에 사용
        Timestamp nowTs = Timestamp.from(now);
        jdbc.batchUpdate("""
                insert into users (username, email, password_hash, full_name, bio, avatar_seed, account_type,
                                   category, is_private, is_verified, birth_date, is_fake, interests,
                                   created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?, ?)
                """, users, 200, (ps, u) -> {
            ps.setString(1, u.username);
            ps.setString(2, u.username + "@example.com");
            ps.setString(3, hash);
            ps.setString(4, u.fullName);
            ps.setString(5, u.bio);
            ps.setString(6, u.username);
            ps.setString(7, u.accountType.name());
            ps.setString(8, u.category);
            ps.setBoolean(9, u.privateAccount);
            ps.setBoolean(10, u.verified);
            ps.setDate(11, Date.valueOf(u.birthDate));
            ps.setString(12, u.interests.stream().map(Enum::name).collect(Collectors.joining(",")));
            ps.setTimestamp(13, Timestamp.from(u.createdAt));
            ps.setTimestamp(14, nowTs);
        });

        Map<String, Long> ids = new HashMap<>();
        jdbc.query("select id, username from users", rs -> {
            ids.put(rs.getString("username"), rs.getLong("id"));
        });
        users.forEach(u -> u.id = ids.get(u.username));
    }

    // ---------------------------------------------------------------- 팔로우

    private List<FakeFollow> buildFollows(List<FakeUser> users, Random rnd, Instant now, SeedSize size) {
        int n = users.size();
        Set<Long> pairs = new HashSet<>();
        List<FakeFollow> follows = new ArrayList<>();

        for (FakeUser u : users) {
            int target = switch (u.persona) {
                case LURKER -> 25 + rnd.nextInt(21);
                case REGULAR -> 12 + rnd.nextInt(24);
                case INFLUENCER -> 8 + rnd.nextInt(13);
                case BUSINESS -> 5 + rnd.nextInt(11);
            };
            target = Math.min(size.scale(target), n - 1);

            // 인기도 × 관심사 겹침 가중치로 팔로우 대상을 뽑는다 (중복 없이)
            double[] weights = new double[n];
            for (int i = 0; i < n; i++) {
                FakeUser v = users.get(i);
                if (v == u) continue;
                long shared = v.interests.stream().filter(u.interests::contains).count();
                weights[i] = v.popularity * (1 + 2.5 * shared) * (v.privateAccount ? 0.7 : 1.0);
            }
            for (int k = 0; k < target; k++) {
                int idx = weightedPick(weights, rnd);
                if (idx < 0) break;
                weights[idx] = 0;
                addFollow(u, users.get(idx), rnd, now, pairs, follows);
            }
        }

        // 맞팔로우: 관심사가 겹치는 일반 사용자끼리 일정 확률로 서로 팔로우
        for (FakeFollow f : List.copyOf(follows)) {
            FakeUser a = f.follower();
            FakeUser b = f.following();
            boolean shared = b.interests.stream().anyMatch(a.interests::contains);
            if (f.status().equals("ACCEPTED") && b.persona == Persona.REGULAR && shared && rnd.nextDouble() < 0.35) {
                addFollow(b, a, rnd, now, pairs, follows);
            }
        }
        return follows;
    }

    private void addFollow(FakeUser from, FakeUser to, Random rnd, Instant now, Set<Long> pairs,
                           List<FakeFollow> follows) {
        long key = from.id * 1_000_003L + to.id;
        if (from == to || !pairs.add(key)) {
            return;
        }
        // 비공개 계정에는 일부 요청이 아직 승인 대기 상태
        String status = to.privateAccount && rnd.nextDouble() < 0.15 ? "PENDING" : "ACCEPTED";
        boolean favorite = status.equals("ACCEPTED") && rnd.nextDouble() < 0.03;
        Instant start = from.createdAt.isAfter(to.createdAt) ? from.createdAt : to.createdAt;
        long span = Math.max(1, Duration.between(start, now).toMinutes());
        Instant createdAt = start.plus(Duration.ofMinutes((long) (rnd.nextDouble() * span)));
        follows.add(new FakeFollow(from, to, status, favorite, createdAt));
    }

    private void insertFollows(List<FakeFollow> follows) {
        jdbc.batchUpdate("""
                insert into follows (follower_id, following_id, status, is_favorite, created_at)
                values (?, ?, ?, ?, ?)
                """, follows, 500, (ps, f) -> {
            ps.setLong(1, f.follower().id);
            ps.setLong(2, f.following().id);
            ps.setString(3, f.status());
            ps.setBoolean(4, f.favorite());
            ps.setTimestamp(5, Timestamp.from(f.createdAt()));
        });
    }

    private void recomputeCounts() {
        jdbc.update("""
                update users u set
                  followers_count = (select count(*) from follows f where f.following_id = u.id and f.status = 'ACCEPTED'),
                  following_count = (select count(*) from follows f where f.follower_id = u.id and f.status = 'ACCEPTED'),
                  posts_count = (select count(*) from posts p where p.author_id = u.id and p.deleted_at is null)
                """);
        jdbc.update("""
                update posts p set
                  like_count = (select count(*) from likes l where l.post_id = p.id),
                  comment_count = (select count(*) from comments c where c.post_id = p.id)
                """);
        jdbc.update("""
                update hashtags h set post_count = (select count(*) from post_hashtags ph where ph.hashtag_id = h.id)
                """);
    }

    // ---------------------------------------------------------------- 도우미

    private static int weightedPick(double[] weights, Random rnd) {
        double total = 0;
        for (double w : weights) total += w;
        if (total <= 0) return -1;
        double r = rnd.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r <= 0 && weights[i] > 0) return i;
        }
        for (int i = weights.length - 1; i >= 0; i--) {
            if (weights[i] > 0) return i;
        }
        return -1;
    }

    private static <T> T pick(List<T> list, Random rnd) {
        return list.get(rnd.nextInt(list.size()));
    }

    private static final List<String> KOREAN_ROMANIZED_NAMES = List.of(
            "minji", "jiwoo", "seoyeon", "hyun", "jun", "yuna", "dohyun", "sora", "hana", "taeyang",
            "eunbi", "jisoo", "minho", "sehun", "yejin", "haneul", "jaewon", "soyeon", "woojin", "nari",
            "seojun", "hayoon", "jihoo", "chaewon", "yerin", "siwoo", "dain", "gyuri", "hojun", "sumin");

    private static final List<String> CITIES = List.of(
            "서울 성수", "서울 연남", "서울 을지로", "서울 한남", "부산 해운대", "부산 전포", "제주 애월",
            "대구 동성로", "인천 송도", "대전 둔산", "광주 동명동", "강릉", "전주 한옥마을", "수원 행궁동");

    private static final List<String[]> BUSINESS_WORDS = List.of(
            new String[] {"모노", "mono"}, new String[] {"오늘", "oneul"}, new String[] {"하루", "haru"},
            new String[] {"소소", "soso"}, new String[] {"온기", "ongi"}, new String[] {"달빛", "dalbit"},
            new String[] {"숲속", "supsok"}, new String[] {"바다", "bada"}, new String[] {"구름", "gureum"},
            new String[] {"봄날", "bomnal"}, new String[] {"노을", "noeul"}, new String[] {"별빛", "byeolbit"});

    /** 주제 → {한글 업종, 영문 업종, 카테고리} */
    private static final Map<Topic, String[]> BUSINESS_NAMES = Map.of(
            Topic.CAFE, new String[] {"커피", "coffee", "카페"},
            Topic.FASHION, new String[] {"쇼룸", "showroom", "의류(브랜드)"},
            Topic.FOOD, new String[] {"식당", "kitchen", "음식점"},
            Topic.BEAUTY, new String[] {"뷰티", "beauty", "미용실"},
            Topic.FITNESS, new String[] {"피트니스", "fitness", "피트니스 센터"},
            Topic.COOKING, new String[] {"베이커리", "bakery", "베이커리"},
            Topic.INTERIOR, new String[] {"리빙", "living", "가정용품 매장"},
            Topic.PETS, new String[] {"펫샵", "pets", "반려동물 용품점"},
            Topic.BOOKS, new String[] {"책방", "books", "서점"},
            Topic.PHOTO, new String[] {"사진관", "studio", "사진 스튜디오"});
}
