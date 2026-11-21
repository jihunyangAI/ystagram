package com.yangstagram.seed;

import com.yangstagram.seed.SeedPhotos.Photo;
import com.yangstagram.seed.SeedService.FakeFollow;
import com.yangstagram.seed.SeedService.FakeUser;
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

/**
 * M2 가짜 데이터: 스토리·조회, DM, 저장·컬렉션, 리포스트, 댓글 좋아요, 알림 (기획서 §8.3).
 * 게시물·좋아요·댓글이 만들어진 뒤에 실행한다.
 */
final class SeedSocial {

    record Result(int stories, int conversations, int messages, int notifications) {
    }

    private final JdbcTemplate jdbc;
    private final Random rnd;
    private final Instant now;
    private final List<Photo> photos;

    private SeedSize size = SeedSize.MEDIUM;

    SeedSocial density(SeedSize size) {
        this.size = size;
        return this;
    }

    SeedSocial(JdbcTemplate jdbc, Random rnd, Instant now, List<Photo> photos) {
        this.jdbc = jdbc;
        this.rnd = rnd;
        this.now = now;
        this.photos = photos;
    }

    Result generate(List<FakeUser> users, List<FakeFollow> follows) {
        Map<Long, List<Long>> followers = new HashMap<>();
        Set<Long> accepted = new HashSet<>();
        for (FakeFollow f : follows) {
            if (f.status().equals("ACCEPTED")) {
                followers.computeIfAbsent(f.following().id, k -> new ArrayList<>()).add(f.follower().id);
                accepted.add(pair(f.follower().id, f.following().id));
            }
        }
        int stories = stories(users, followers);
        commentLikes();
        savesAndCollections(users);
        reposts();
        int[] dm = conversations(users, follows, accepted);
        int notifications = notifications();
        return new Result(stories, dm[0], dm[1], notifications);
    }

    private static long pair(long a, long b) {
        return a * 1_000_003L + b;
    }

    // ------------------------------------------------------------ 스토리

    /** 활성 스토리(최근 24시간 안) + 지난 30일의 만료된 스토리(아카이브용) */
    private int stories(List<FakeUser> users, Map<Long, List<Long>> followers) {
        if (photos.isEmpty()) return 0;
        record Story(long id, long author, Photo photo, Instant createdAt) {
        }
        List<Story> list = new ArrayList<>();
        long id = 1;
        for (FakeUser u : users) {
            double activeChance = switch (u.persona) {
                case INFLUENCER -> 0.85;
                case BUSINESS -> 0.6;
                case REGULAR -> 0.35;
                case LURKER -> 0.05;
            };
            if (rnd.nextDouble() < activeChance) {
                int n = 1 + rnd.nextInt(u.persona == SeedService.Persona.INFLUENCER ? 6 : 3);
                Instant t = now.minus(Duration.ofMinutes(10 + rnd.nextInt(23 * 60)));
                for (int i = 0; i < n && t.isBefore(now); i++) {
                    list.add(new Story(id++, u.id, photos.get(rnd.nextInt(photos.size())), t));
                    t = t.plus(Duration.ofMinutes(3 + rnd.nextInt(90)));
                }
            }
            int archived = u.persona == SeedService.Persona.LURKER ? rnd.nextInt(2) : rnd.nextInt(8);
            for (int i = 0; i < archived; i++) {
                Instant t = now.minus(Duration.ofHours(25 + rnd.nextInt(30 * 24)));
                list.add(new Story(id++, u.id, photos.get(rnd.nextInt(photos.size())), t));
            }
        }
        jdbc.batchUpdate("""
                insert into stories (id, author_id, media_url, width, height, created_at, expires_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, list, 500, (ps, s) -> {
            ps.setLong(1, s.id());
            ps.setLong(2, s.author());
            ps.setString(3, s.photo().url());
            ps.setInt(4, s.photo().width());
            ps.setInt(5, s.photo().height());
            ps.setTimestamp(6, Timestamp.from(s.createdAt()));
            ps.setTimestamp(7, Timestamp.from(s.createdAt().plus(Duration.ofHours(24))));
        });
        jdbc.execute("select setval(pg_get_serial_sequence('stories', 'id'), greatest(1, (select max(id) from stories)))");

        // 조회: 팔로워의 절반 정도가 보고, 그중 일부가 좋아요
        List<Object[]> views = new ArrayList<>();
        for (Story s : list) {
            for (Long viewer : followers.getOrDefault(s.author(), List.of())) {
                if (rnd.nextDouble() < 0.5) {
                    Instant at = s.createdAt().plus(Duration.ofMinutes(1 + rnd.nextInt(20 * 60)));
                    if (at.isAfter(now)) continue;
                    views.add(new Object[] {s.id(), viewer, rnd.nextDouble() < 0.06, Timestamp.from(at)});
                }
            }
        }
        jdbc.batchUpdate("insert into story_views (story_id, viewer_id, liked, created_at) values (?, ?, ?, ?)",
                views, 1000, (ps, v) -> {
                    ps.setLong(1, (Long) v[0]);
                    ps.setLong(2, (Long) v[1]);
                    ps.setBoolean(3, (Boolean) v[2]);
                    ps.setTimestamp(4, (Timestamp) v[3]);
                });
        return list.size();
    }

    // ------------------------------------------------------------ 댓글 좋아요·저장·리포스트

    /** 게시물에 좋아요 누른 사람 중 일부가 댓글에도 좋아요 (결정적 해시로 재현 가능) */
    private void commentLikes() {
        jdbc.update("""
                insert into comment_likes (comment_id, user_id, created_at)
                select c.id, l.user_id, c.created_at + interval '30 minutes'
                from comments c join likes l on l.post_id = c.post_id
                where l.user_id <> c.author_id and abs(hashtext(c.id || ':' || l.user_id)) % 100 < 6
                  and c.created_at + interval '30 minutes' < ?
                """, Timestamp.from(now));
        jdbc.update("update comments c set like_count = (select count(*) from comment_likes x where x.comment_id = c.id)");
    }

    private static final List<String> COLLECTION_NAMES = List.of(
            "가고 싶은 곳", "맛집", "카페 리스트", "코디 참고", "레시피", "여행", "인테리어", "운동", "사진 참고", "언젠가");

    private void savesAndCollections(List<FakeUser> users) {
        jdbc.update("""
                insert into saved_posts (user_id, post_id, created_at)
                select l.user_id, l.post_id, l.created_at from likes l
                where abs(hashtext('s' || l.user_id || ':' || l.post_id)) % 100 < 7
                """);
        jdbc.update("update posts p set save_count = (select count(*) from saved_posts s where s.post_id = p.id)");

        Timestamp ts = Timestamp.from(now);
        for (FakeUser u : users) {
            List<Long> saved = jdbc.queryForList(
                    "select post_id from saved_posts where user_id = ? order by created_at", Long.class, u.id);
            if (saved.size() < 4 || rnd.nextDouble() < 0.4) continue;
            int n = 1 + rnd.nextInt(Math.min(3, saved.size() / 4));
            List<String> names = new ArrayList<>(COLLECTION_NAMES);
            java.util.Collections.shuffle(names, rnd);
            for (int i = 0; i < n; i++) {
                Long cid = jdbc.queryForObject("""
                        insert into collections (user_id, name, created_at, updated_at) values (?, ?, ?, ?) returning id
                        """, Long.class, u.id, names.get(i), ts, ts);
                List<Object[]> items = new ArrayList<>();
                for (Long post : saved) {
                    if (rnd.nextDouble() < 0.35) items.add(new Object[] {cid, post, ts});
                }
                jdbc.batchUpdate("insert into collection_posts (collection_id, post_id, created_at) values (?, ?, ?)",
                        items);
            }
        }
    }

    /** 공개 계정의 다른 사람 게시물 중 좋아요한 일부를 리포스트 */
    private void reposts() {
        jdbc.update("""
                insert into reposts (user_id, post_id, created_at)
                select l.user_id, l.post_id, l.created_at + interval '5 minutes'
                from likes l join posts p on p.id = l.post_id join users a on a.id = p.author_id
                where not a.is_private and a.id <> l.user_id
                  and abs(hashtext('r' || l.user_id || ':' || l.post_id)) % 1000 < 12
                  and l.created_at + interval '5 minutes' < ?
                """, Timestamp.from(now));
        jdbc.update("update posts p set repost_count = (select count(*) from reposts r where r.post_id = p.id)");
    }

    // ------------------------------------------------------------ DM

    private static final List<String> OPENERS = List.of(
            "안녕! 잘 지내?", "이거 봤어?", "주말에 뭐 해?", "ㅋㅋㅋㅋ 이거 너무 웃겨", "오늘 사진 너무 잘 나왔더라",
            "혹시 그 카페 어디야?", "다음 주에 시간 돼?", "생일 축하해 🎉", "고마워!", "어제 재밌었어");
    private static final List<String> LINES = List.of(
            "ㅋㅋㅋ 진짜", "응응", "좋아!", "헐 대박", "나도 가보고 싶다", "언제 갈까?", "토요일 어때?",
            "ㅇㅋ 그때 봐", "사진 보내줘", "완전 내 스타일", "ㅠㅠ 아쉽다", "오 괜찮은데?", "알겠어~", "고마워 😊",
            "다음에 같이 가자", "나 지금 가는 중", "조금 늦을 것 같아", "도착!", "여기 분위기 좋다", "배고파",
            "뭐 먹을래?", "아무거나 좋아", "그럼 파스타?", "콜", "오늘 날씨 미쳤다", "주말에 비 온대", "🔥🔥",
            "이거 어디서 샀어?", "링크 보내줄게", "역시 센스 👍", "잘 자!", "내일 연락할게");
    private static final List<String> EMOJIS = List.of("❤️", "😂", "😮", "😢", "👍", "🔥");

    /** 서로 팔로우하는 쌍 중심으로 1:1 대화, 일부 그룹 대화. 최근 30일 */
    private int[] conversations(List<FakeUser> users, List<FakeFollow> follows, Set<Long> accepted) {
        List<long[]> mutual = new ArrayList<>();
        for (FakeFollow f : follows) {
            long a = f.follower().id, b = f.following().id;
            if (a < b && f.status().equals("ACCEPTED") && accepted.contains(pair(b, a))) {
                mutual.add(new long[] {a, b});
            }
        }
        java.util.Collections.shuffle(mutual, rnd);
        int target = Math.min(mutual.size(), size.density() > 1 ? users.size() * 4 : users.size() * 5 / 2);
        List<Long> publicPosts = jdbc.queryForList("""
                select p.id from posts p join users a on a.id = p.author_id
                where not a.is_private and p.deleted_at is null order by p.like_count desc limit 300
                """, Long.class);

        List<Object[]> convRows = new ArrayList<>();
        List<Object[]> memberRows = new ArrayList<>();
        List<Object[]> messageRows = new ArrayList<>();
        List<Object[]> reactionRows = new ArrayList<>();
        long convId = 1, msgId = 1;

        List<long[]> groups = new ArrayList<>();
        for (int i = 0; i < target; i++) groups.add(mutual.get(i));
        // 그룹 대화: 사용자 10명당 1개, 3~5명
        int groupCount = users.size() / 10;
        for (int g = 0; g < groupCount && !mutual.isEmpty(); g++) {
            long[] base = mutual.get(rnd.nextInt(mutual.size()));
            Set<Long> members = new java.util.LinkedHashSet<>(List.of(base[0], base[1]));
            for (long[] m : mutual) {
                if (members.size() >= 3 + rnd.nextInt(3)) break;
                if (members.contains(m[0]) && !members.contains(m[1])) members.add(m[1]);
            }
            if (members.size() >= 3) groups.add(members.stream().mapToLong(Long::longValue).toArray());
        }

        for (long[] members : groups) {
            boolean group = members.length > 2;
            int count = size.scale(3 + rnd.nextInt(group ? 30 : 25));
            Instant t = now.minus(Duration.ofMinutes(60 + rnd.nextInt(30 * 24 * 60)));
            long firstMsg = msgId;
            List<Long> senders = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                long sender = members[rnd.nextInt(members.length)];
                String kind = "TEXT";
                String body = i == 0 ? pick(OPENERS) : pick(LINES);
                Long post = null;
                if (!publicPosts.isEmpty() && rnd.nextDouble() < 0.07) {
                    kind = "POST";
                    body = null;
                    post = publicPosts.get(rnd.nextInt(publicPosts.size()));
                }
                messageRows.add(new Object[] {msgId, convId, sender, kind, body, post, Timestamp.from(t)});
                if (rnd.nextDouble() < 0.08) {
                    long reactor = members[rnd.nextInt(members.length)];
                    if (reactor != sender) {
                        reactionRows.add(new Object[] {msgId, reactor, pick(EMOJIS), Timestamp.from(t.plusSeconds(60))});
                    }
                }
                senders.add(sender);
                msgId++;
                Instant next = t.plus(Duration.ofSeconds(20 + rnd.nextInt(rnd.nextDouble() < 0.15 ? 86_400 : 1_800)));
                if (next.isAfter(now.minusSeconds(30))) break;
                t = next;
            }
            long lastMsg = msgId - 1;
            String title = group ? pick(java.util.Arrays.asList("주말 모임 🍻", "여행 계획", "동네 친구들", "운동 메이트", null)) : null;
            String key = group ? null : Math.min(members[0], members[1]) + ":" + Math.max(members[0], members[1]);
            convRows.add(new Object[] {convId, group, title, key, members[0], Timestamp.from(now.minus(Duration.ofDays(31))),
                    Timestamp.from(t)});
            long lastSender = senders.getLast();
            for (long m : members) {
                // 마지막 메시지를 보낸 사람이 아니면 30%는 아직 안 읽음
                long read = m == lastSender || rnd.nextDouble() >= 0.3 ? lastMsg : Math.max(firstMsg - 1, lastMsg - 1 - rnd.nextInt(3));
                memberRows.add(new Object[] {convId, m, "PRIMARY", read, Timestamp.from(now.minus(Duration.ofDays(31)))});
            }
            convId++;
        }

        jdbc.batchUpdate("""
                insert into conversations (id, is_group, title, direct_key, created_by, created_at, last_message_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, convRows);
        jdbc.batchUpdate("""
                insert into conversation_members (conversation_id, user_id, folder, last_read_message_id, joined_at)
                values (?, ?, ?, ?, ?)
                """, memberRows);
        jdbc.batchUpdate("""
                insert into messages (id, conversation_id, sender_id, kind, body, post_id, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, messageRows);
        jdbc.batchUpdate("insert into message_reactions (message_id, user_id, emoji, created_at) values (?, ?, ?, ?)",
                reactionRows);
        jdbc.execute("select setval(pg_get_serial_sequence('conversations', 'id'), greatest(1, (select max(id) from conversations)))");
        jdbc.execute("select setval(pg_get_serial_sequence('messages', 'id'), greatest(1, (select max(id) from messages)))");
        return new int[] {convRows.size(), messageRows.size()};
    }

    private String pick(List<String> list) {
        return list.get(rnd.nextInt(list.size()));
    }

    // ------------------------------------------------------------ 알림

    /** 위에서 만든 활동에서 알림을 만든다. 최근 60일만, 이틀 넘은 것은 읽음 처리 */
    private int notifications() {
        Timestamp since = Timestamp.from(now.minus(Duration.ofDays(60)));
        Timestamp readBefore = Timestamp.from(now.minus(Duration.ofDays(2)));
        int total = 0;
        total += jdbc.update("""
                insert into notifications (user_id, actor_id, type, post_id, created_at, read_at)
                select p.author_id, l.user_id, 'LIKE', l.post_id, l.created_at,
                       case when l.created_at < ? then l.created_at end
                from likes l join posts p on p.id = l.post_id
                where l.user_id <> p.author_id and l.created_at > ?
                """, readBefore, since);
        total += jdbc.update("""
                insert into notifications (user_id, actor_id, type, post_id, comment_id, body, created_at, read_at)
                select p.author_id, c.author_id, 'COMMENT', c.post_id, c.id, left(c.body, 200), c.created_at,
                       case when c.created_at < ? then c.created_at end
                from comments c join posts p on p.id = c.post_id
                where c.author_id <> p.author_id and c.created_at > ?
                """, readBefore, since);
        total += jdbc.update("""
                insert into notifications (user_id, actor_id, type, post_id, comment_id, body, created_at, read_at)
                select pc.author_id, c.author_id, 'REPLY', c.post_id, c.id, left(c.body, 200), c.created_at,
                       case when c.created_at < ? then c.created_at end
                from comments c join comments pc on pc.id = c.parent_id join posts p on p.id = c.post_id
                where c.author_id <> pc.author_id and pc.author_id <> p.author_id and c.created_at > ?
                """, readBefore, since);
        total += jdbc.update("""
                insert into notifications (user_id, actor_id, type, post_id, comment_id, body, created_at, read_at)
                select c.author_id, cl.user_id, 'COMMENT_LIKE', c.post_id, c.id, left(c.body, 200), cl.created_at,
                       case when cl.created_at < ? then cl.created_at end
                from comment_likes cl join comments c on c.id = cl.comment_id
                where cl.user_id <> c.author_id and cl.created_at > ?
                """, readBefore, since);
        total += jdbc.update("""
                insert into notifications (user_id, actor_id, type, created_at, read_at)
                select following_id, follower_id, case when status = 'ACCEPTED' then 'FOLLOW' else 'FOLLOW_REQUEST' end,
                       created_at, case when created_at < ? then created_at end
                from follows where created_at > ?
                """, readBefore, since);
        total += jdbc.update("""
                insert into notifications (user_id, actor_id, type, post_id, created_at, read_at)
                select p.author_id, r.user_id, 'REPOST', r.post_id, r.created_at,
                       case when r.created_at < ? then r.created_at end
                from reposts r join posts p on p.id = r.post_id
                where r.created_at > ?
                """, readBefore, since);
        total += jdbc.update("""
                insert into notifications (user_id, actor_id, type, story_id, created_at, read_at)
                select s.author_id, v.viewer_id, 'STORY_LIKE', s.id, v.created_at,
                       case when v.created_at < ? then v.created_at end
                from story_views v join stories s on s.id = v.story_id
                where v.liked and v.created_at > ?
                """, readBefore, since);
        return total;
    }
}
