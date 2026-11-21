package com.yangstagram.story;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.paging.Slice;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.dm.DmDtos.MessageView;
import com.yangstagram.dm.DmService;
import com.yangstagram.follow.FollowService;
import com.yangstagram.media.MediaStorage;
import com.yangstagram.media.StoredImage;
import com.yangstagram.notification.NotificationService;
import com.yangstagram.notification.NotificationType;
import com.yangstagram.realtime.Realtime;
import com.yangstagram.relation.RelationSql;
import com.yangstagram.story.StoryRings.Ring;
import com.yangstagram.story.Stickers.Answer;
import com.yangstagram.story.Stickers.Result;
import com.yangstagram.story.Stickers.Sticker;
import tools.jackson.databind.json.JsonMapper;
import com.yangstagram.user.User;
import com.yangstagram.user.UserService;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 스토리 (분석 문서 §8): 24시간 뒤 트레이에서 사라지고 본인 아카이브에만 남는다.
 * 만료 판정은 가상 시계(LabClock)를 쓰므로 Lab의 시간 가속으로 바로 확인할 수 있다.
 * 스티커·하이라이트·친한 친구는 M3.
 */
@Service
public class StoryService {

    public static final Duration LIFETIME = Duration.ofHours(24);
    private static final int ARCHIVE_PAGE = 24;

    private final NamedParameterJdbcTemplate jdbc;
    private final UserService userService;
    private final FollowService followService;
    private final MediaStorage storage;
    private final NotificationService notifications;
    private final DmService dm;
    private final Realtime realtime;
    private final LabClock clock;
    private final JsonMapper json;

    public StoryService(NamedParameterJdbcTemplate jdbc, UserService userService, FollowService followService,
                        MediaStorage storage, NotificationService notifications, DmService dm, Realtime realtime,
                        LabClock clock, JsonMapper json) {
        this.json = json;
        this.jdbc = jdbc;
        this.userService = userService;
        this.followService = followService;
        this.storage = storage;
        this.notifications = notifications;
        this.dm = dm;
        this.realtime = realtime;
        this.clock = clock;
    }

    /** 트레이의 동그라미 하나 */
    public record TrayItem(UserSummary user, Ring ring, int count, Instant latestAt, boolean me) {
    }

    /**
     * 스토리 한 장.
     *
     * @param viewerCount 내 스토리일 때만 (남의 스토리는 null)
     */
    public record StoryView(long id, UserSummary author, String mediaUrl, int width, int height, String altText,
                            Instant createdAt, Instant expiresAt, boolean seen, boolean liked, boolean mine,
                            Integer viewerCount, boolean expired, String audience, List<Sticker> stickers,
                            List<Result> results) {

        StoryView withResults(List<Result> r) {
            return new StoryView(id, author, mediaUrl, width, height, altText, createdAt, expiresAt, seen, liked, mine,
                    viewerCount, expired, audience, stickers, r);
        }
    }

    /** 만들기 요청 (사진 파일은 multipart의 file 파트) */
    public record CreateStoryRequest(String altText, String audience, List<Sticker> stickers) {
    }

    public record RespondRequest(String value) {
    }

    public record HighlightView(long id, String title, String coverUrl, int count) {
    }

    public record HighlightForm(String title, List<Long> storyIds, Long coverStoryId, List<Long> removeStoryIds) {
    }

    public record Viewer(UserSummary user, boolean liked, Instant viewedAt) {
    }

    public record ReplyRequest(String body) {
    }

    // ------------------------------------------------------------ 트레이·보기

    /** 홈 위쪽 트레이: 내 스토리 → 안 본 스토리가 있는 사람 → 다 본 사람, 각 묶음 안에서는 최근 올린 순 */
    public List<TrayItem> tray(long me) {
        return jdbc.query("""
                select %s, count(*) as cnt, max(s.created_at) as latest,
                       bool_and(v.story_id is not null) as all_seen,
                       bool_or(v.story_id is null and s.audience = 'CLOSE_FRIENDS') as unseen_cf
                from stories s
                join users a on a.id = s.author_id
                left join story_views v on v.story_id = s.id and v.viewer_id = :me
                where a.deleted_at is null and %s
                  and (a.id = :me or (a.id in (select following_id from follows
                                               where follower_id = :me and status = 'ACCEPTED')
                                       and %s))
                group by a.id
                order by (a.id = :me) desc, bool_and(v.story_id is not null), max(s.created_at) desc
                limit 100
                """.formatted(UserRows.cols("a"), StoryRings.VISIBLE_ACTIVE, RelationSql.storiesNotMuted("a.id")),
                params(me),
                (rs, i) -> {
                    UserSummary u = UserRows.read(rs, "a");
                    boolean mine = u.id() == me;
                    Ring ring = rs.getBoolean("all_seen") || mine ? Ring.SEEN
                            : rs.getBoolean("unseen_cf") ? Ring.CLOSE_FRIENDS : Ring.UNSEEN;
                    return new TrayItem(u, ring, rs.getInt("cnt"), rs.getTimestamp("latest").toInstant(), mine);
                });
    }

    /** 한 사람의 활성 스토리 (오래된 순, 뷰어가 차례로 넘긴다) */
    public List<StoryView> ofUser(long me, String username) {
        User author = userService.getByUsername(username);
        followService.requireCanView(me, author.getId(), author.isPrivateAccount());
        MapSqlParameterSource p = params(me).addValue("author", author.getId());
        return withResults(jdbc.query(select() + " where s.author_id = :author and " + StoryRings.VISIBLE_ACTIVE
                + " order by s.created_at, s.id", p, (rs, i) -> read(rs, me)), me);
    }

    @Transactional
    public void markSeen(long me, long storyId) {
        StoryView story = visible(me, storyId);
        if (story.mine()) return;
        jdbc.update("""
                insert into story_views (story_id, viewer_id, created_at) values (:s, :me, :now)
                on conflict do nothing
                """, params(me).addValue("s", storyId));
    }

    @Transactional
    public StoryView like(long me, long storyId, boolean like) {
        StoryView story = visible(me, storyId);
        if (story.mine()) throw ApiException.badRequest("CANNOT_LIKE_OWN", "내 스토리에는 좋아요를 누를 수 없습니다.");
        jdbc.update("""
                insert into story_views (story_id, viewer_id, liked, created_at) values (:s, :me, :liked, :now)
                on conflict (story_id, viewer_id) do update set liked = excluded.liked
                """, params(me).addValue("s", storyId).addValue("liked", like));
        if (like && !story.liked()) {
            notifications.notify(story.author().id(), me, NotificationType.STORY_LIKE, null, null, storyId, null);
        } else if (!like) {
            notifications.retract(me, NotificationType.STORY_LIKE, null, null, storyId, null);
        }
        return visible(me, storyId);
    }

    /** 스토리 답장 → 작성자와의 DM으로 */
    @Transactional
    public MessageView reply(long me, long storyId, String body) {
        StoryView story = visible(me, storyId);
        if (story.mine()) throw ApiException.badRequest("CANNOT_REPLY_OWN", "내 스토리에는 답장할 수 없습니다.");
        return dm.storyReply(me, storyId, story.author().username(), body);
    }

    /** 내 스토리를 본 사람 (최근 순) */
    public List<Viewer> viewers(long me, long storyId) {
        own(me, storyId);
        return jdbc.query("""
                select %s, v.liked, v.created_at from story_views v join users u on u.id = v.viewer_id
                where v.story_id = :s and u.deleted_at is null
                order by v.liked desc, v.created_at desc limit 500
                """.formatted(UserRows.cols("u")), Map.of("s", storyId),
                (rs, i) -> new Viewer(UserRows.read(rs, "u"), rs.getBoolean("liked"),
                        rs.getTimestamp("created_at").toInstant()));
    }

    // ------------------------------------------------------------ 만들기·지우기·아카이브

    /** 사진 한 장 (화면에서 9:16으로 자르고 글자를 그려 넣은 상태) */
    @Transactional
    public StoryView create(long me, MultipartFile file, CreateStoryRequest req) {
        CreateStoryRequest data = req == null ? new CreateStoryRequest(null, null, null) : req;
        String audience = "CLOSE_FRIENDS".equalsIgnoreCase(data.audience()) ? "CLOSE_FRIENDS" : "PUBLIC";
        List<Sticker> stickers = Stickers.validate(data.stickers());
        StoredImage img = storage.saveImage(file);
        Instant now = Instant.now(clock);
        String alt = data.altText() == null || data.altText().isBlank() ? null : data.altText().strip();
        if (alt != null && alt.length() > 200) alt = alt.substring(0, 200);
        Long id = jdbc.queryForObject("""
                insert into stories (author_id, media_url, width, height, alt_text, created_at, expires_at, audience, stickers)
                values (:me, :url, :w, :h, :alt, :now, :expires, :audience, cast(:stickers as jsonb)) returning id
                """, new MapSqlParameterSource("me", me).addValue("url", img.url()).addValue("w", img.width())
                .addValue("h", img.height()).addValue("alt", alt).addValue("now", Timestamp.from(now))
                .addValue("expires", Timestamp.from(now.plus(LIFETIME))).addValue("audience", audience)
                .addValue("stickers", stickers.isEmpty() ? null : json.writeValueAsString(stickers)), Long.class);
        // 언급 스티커 → 그 사람에게 알림
        for (Sticker s : stickers) {
            if ("MENTION".equals(s.type())) {
                jdbc.queryForList("select id from users where username = :u and deleted_at is null",
                        Map.of("u", s.username()), Long.class)
                        .forEach(uid -> notifications.notify(uid, me, NotificationType.MENTION, null, null, id, "스토리"));
            }
        }
        List<Long> followers = jdbc.queryForList(
                "select follower_id from follows where following_id = :me and status = 'ACCEPTED'",
                Map.of("me", me), Long.class);
        realtime.send(followers, "story:new", Map.of("authorId", me));
        return visible(me, id);
    }

    // ------------------------------------------------------------ 스티커 응답

    /** 설문·퀴즈는 한 번만, 슬라이더·질문은 다시 보내면 바뀐다 */
    @Transactional
    public StoryView respond(long me, long storyId, String stickerId, String rawValue) {
        StoryView story = visible(me, storyId);
        if (story.mine()) throw ApiException.badRequest("CANNOT_RESPOND_OWN", "내 스토리에는 응답할 수 없습니다.");
        Sticker sticker = story.stickers().stream().filter(s -> s.id().equals(stickerId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("스티커를 찾을 수 없습니다."));
        if (!Stickers.INTERACTIVE.contains(sticker.type())) {
            throw ApiException.badRequest("NOT_INTERACTIVE", "응답할 수 없는 스티커입니다.");
        }
        String value = rawValue == null ? "" : rawValue.strip();
        boolean once = switch (sticker.type()) {
            case "POLL", "QUIZ" -> {
                int idx = parseInt(value, -1);
                if (idx < 0 || idx >= sticker.options().size()) {
                    throw ApiException.badRequest("BAD_VALUE", "선택지를 골라 주세요.");
                }
                yield true;
            }
            case "SLIDER" -> {
                int v = parseInt(value, -1);
                if (v < 0 || v > 100) throw ApiException.badRequest("BAD_VALUE", "0~100 사이 값이어야 합니다.");
                yield false;
            }
            default -> {
                if (value.isEmpty() || value.length() > 500) {
                    throw ApiException.badRequest("BAD_VALUE", "답변을 입력해 주세요 (500자까지).");
                }
                yield false;
            }
        };
        jdbc.update("""
                insert into story_sticker_responses (story_id, sticker_id, user_id, value, created_at)
                values (:s, :k, :me, :v, :now)
                on conflict (story_id, sticker_id, user_id) do update set value = excluded.value, created_at = excluded.created_at
                  where not :once
                """, params(me).addValue("s", storyId).addValue("k", stickerId).addValue("v", value)
                .addValue("once", once));
        markSeen(me, storyId);
        return visible(me, storyId);
    }

    private static int parseInt(String v, int fallback) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private record Resp(long storyId, String stickerId, long userId, String username, String value) {
    }

    /** 스토리들의 스티커 응답 집계를 붙인다 */
    private List<StoryView> withResults(List<StoryView> stories, long me) {
        List<Long> ids = stories.stream().filter(s -> s.stickers().stream()
                .anyMatch(k -> Stickers.INTERACTIVE.contains(k.type()))).map(StoryView::id).toList();
        if (ids.isEmpty()) return stories;
        List<Resp> all = jdbc.query("""
                select r.story_id, r.sticker_id, r.user_id, u.username, r.value
                from story_sticker_responses r join users u on u.id = r.user_id
                where r.story_id in (:ids) order by r.created_at desc
                """, Map.of("ids", ids), (rs, i) -> new Resp(rs.getLong(1), rs.getString(2), rs.getLong(3),
                rs.getString(4), rs.getString(5)));
        return stories.stream().map(story -> {
            if (!ids.contains(story.id())) return story;
            List<Result> results = new java.util.ArrayList<>();
            for (Sticker k : story.stickers()) {
                if (!Stickers.INTERACTIVE.contains(k.type())) continue;
                List<Resp> rs = all.stream()
                        .filter(r -> r.storyId() == story.id() && r.stickerId().equals(k.id())).toList();
                String mine = rs.stream().filter(r -> r.userId() == me).map(Resp::value).findFirst().orElse(null);
                List<Integer> counts = null;
                Double average = null;
                if (k.options() != null && !k.options().isEmpty()) {
                    int[] c = new int[k.options().size()];
                    rs.forEach(r -> {
                        int idx = parseInt(r.value(), -1);
                        if (idx >= 0 && idx < c.length) c[idx]++;
                    });
                    counts = java.util.Arrays.stream(c).boxed().toList();
                }
                if ("SLIDER".equals(k.type()) && !rs.isEmpty()) {
                    average = rs.stream().mapToInt(r -> parseInt(r.value(), 0)).average().orElse(0);
                }
                List<Answer> answers = "QUESTION".equals(k.type()) && story.mine()
                        ? rs.stream().limit(200).map(r -> new Answer(r.username(), r.value())).toList() : null;
                // 설문 결과는 응답했거나 주인일 때만 숫자를 보여 준다 (응답 전에는 선택지만)
                boolean reveal = story.mine() || mine != null;
                results.add(new Result(k.id(), reveal ? counts : null, mine, reveal ? average : null, rs.size(), answers));
            }
            return story.withResults(results);
        }).toList();
    }

    // ------------------------------------------------------------ 하이라이트

    /** 프로필 아래 동그라미들 (게시물을 볼 수 있는 사람에게만) */
    public List<HighlightView> highlights(long me, String username) {
        User author = userService.getByUsername(username);
        if (!followService.canViewContent(me, author.getId(), author.isPrivateAccount())) return List.of();
        return jdbc.query("""
                select h.id, h.title,
                       coalesce(c.media_url, (select s2.media_url from highlight_items i2 join stories s2 on s2.id = i2.story_id
                                              where i2.highlight_id = h.id and s2.deleted_at is null
                                              order by s2.created_at desc limit 1)) as cover,
                       (select count(*) from highlight_items i join stories s on s.id = i.story_id
                        where i.highlight_id = h.id and s.deleted_at is null
                          and (s.audience = 'PUBLIC' or h.user_id = :me
                               or exists (select 1 from close_friends cf where cf.user_id = h.user_id and cf.friend_id = :me))) as cnt
                from highlights h left join stories c on c.id = h.cover_story_id and c.deleted_at is null
                where h.user_id = :author
                order by h.position, h.created_at desc
                """, Map.of("me", me, "author", author.getId()),
                (rs, i) -> new HighlightView(rs.getLong("id"), rs.getString("title"), rs.getString("cover"),
                        rs.getInt("cnt"))).stream().filter(h -> h.count() > 0).toList();
    }

    /** 하이라이트 안의 스토리 (만료와 상관없이 보인다) */
    public List<StoryView> highlightStories(long me, long highlightId) {
        Long owner = jdbc.query("select user_id from highlights where id = :id", Map.of("id", highlightId),
                (rs, i) -> rs.getLong(1)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("하이라이트를 찾을 수 없습니다."));
        User author = userService.getActive(owner);
        followService.requireCanView(me, owner, author.isPrivateAccount());
        return withResults(jdbc.query(select() + """
                 join highlight_items hi on hi.story_id = s.id and hi.highlight_id = :h
                where s.deleted_at is null
                  and (s.audience = 'PUBLIC' or a.id = :me
                       or exists (select 1 from close_friends cf where cf.user_id = a.id and cf.friend_id = :me))
                order by s.created_at, s.id
                """, params(me).addValue("h", highlightId), (rs, i) -> read(rs, me)), me);
    }

    @Transactional
    public HighlightView createHighlight(long me, HighlightForm form) {
        String title = highlightTitle(form.title());
        List<Long> ids = ownStories(me, form.storyIds());
        if (ids.isEmpty()) throw ApiException.badRequest("NO_STORIES", "하이라이트에 넣을 스토리를 골라 주세요.");
        Timestamp now = Timestamp.from(Instant.now(clock));
        Long cover = form.coverStoryId() != null && ids.contains(form.coverStoryId()) ? form.coverStoryId() : ids.getLast();
        Long id = jdbc.queryForObject("""
                insert into highlights (user_id, title, cover_story_id, position, created_at, updated_at)
                values (:me, :title, :cover,
                        (select coalesce(min(position), 0) - 1 from highlights where user_id = :me), :now, :now)
                returning id
                """, new MapSqlParameterSource("me", me).addValue("title", title).addValue("cover", cover)
                .addValue("now", now), Long.class);
        addItems(id, ids, now);
        return highlight(me, id);
    }

    @Transactional
    public HighlightView updateHighlight(long me, long id, HighlightForm form) {
        ownHighlight(me, id);
        Timestamp now = Timestamp.from(Instant.now(clock));
        if (form.title() != null) {
            jdbc.update("update highlights set title = :t, updated_at = :now where id = :id",
                    new MapSqlParameterSource("t", highlightTitle(form.title())).addValue("now", now).addValue("id", id));
        }
        if (form.storyIds() != null) addItems(id, ownStories(me, form.storyIds()), now);
        if (form.removeStoryIds() != null && !form.removeStoryIds().isEmpty()) {
            jdbc.update("delete from highlight_items where highlight_id = :id and story_id in (:ids)",
                    new MapSqlParameterSource("id", id).addValue("ids", form.removeStoryIds()));
        }
        if (form.coverStoryId() != null) {
            jdbc.update("update highlights set cover_story_id = :c where id = :id and exists "
                            + "(select 1 from highlight_items where highlight_id = :id and story_id = :c)",
                    Map.of("c", form.coverStoryId(), "id", id));
        }
        Integer left = jdbc.queryForObject("select count(*) from highlight_items where highlight_id = :id",
                Map.of("id", id), Integer.class);
        if (left == null || left == 0) {
            jdbc.update("delete from highlights where id = :id", Map.of("id", id)); // 비면 하이라이트도 지운다
            return new HighlightView(id, "", null, 0);
        }
        return highlight(me, id);
    }

    @Transactional
    public void deleteHighlight(long me, long id) {
        ownHighlight(me, id);
        jdbc.update("delete from highlights where id = :id", Map.of("id", id));
    }

    private HighlightView highlight(long me, long id) {
        String username = jdbc.queryForObject(
                "select u.username from highlights h join users u on u.id = h.user_id where h.id = :id",
                Map.of("id", id), String.class);
        return highlights(me, username).stream().filter(h -> h.id() == id).findFirst()
                .orElse(new HighlightView(id, "", null, 0));
    }

    private void addItems(long highlightId, List<Long> storyIds, Timestamp now) {
        for (Long s : storyIds) {
            jdbc.update("""
                    insert into highlight_items (highlight_id, story_id, added_at) values (:h, :s, :now)
                    on conflict do nothing
                    """, new MapSqlParameterSource("h", highlightId).addValue("s", s).addValue("now", now));
        }
    }

    private List<Long> ownStories(long me, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return jdbc.queryForList("""
                select id from stories where id in (:ids) and author_id = :me and deleted_at is null order by created_at
                """, new MapSqlParameterSource("ids", ids).addValue("me", me), Long.class);
    }

    private void ownHighlight(long me, long id) {
        Integer n = jdbc.queryForObject("select count(*) from highlights where id = :id and user_id = :me",
                Map.of("id", id, "me", me), Integer.class);
        if (n == null || n == 0) throw ApiException.notFound("하이라이트를 찾을 수 없습니다.");
    }

    private static String highlightTitle(String raw) {
        String t = raw == null ? "" : raw.strip();
        if (t.isEmpty()) t = "하이라이트";
        if (t.length() > 30) throw ApiException.badRequest("TITLE_TOO_LONG", "하이라이트 이름은 30자까지 쓸 수 있습니다.");
        return t;
    }

    @Transactional
    public void delete(long me, long storyId) {
        own(me, storyId);
        jdbc.update("update stories set deleted_at = :now where id = :s",
                Map.of("now", Timestamp.from(Instant.now(clock)), "s", storyId));
    }

    /** 내 스토리 아카이브: 만료된 것 포함 전부 (최근 순). 커서는 마지막 id */
    public Slice<StoryView> archive(long me, Long beforeId) {
        MapSqlParameterSource p = params(me).addValue("before", beforeId == null ? Long.MAX_VALUE : beforeId)
                .addValue("limit", ARCHIVE_PAGE + 1);
        List<StoryView> items = jdbc.query(select() + """
                 where s.author_id = :me and s.deleted_at is null and s.created_at <= :now and s.id < :before
                order by s.id desc limit :limit
                """, p, (rs, i) -> read(rs, me));
        if (items.size() <= ARCHIVE_PAGE) return new Slice<>(items, null);
        List<StoryView> page = items.subList(0, ARCHIVE_PAGE);
        return new Slice<>(page, String.valueOf(page.getLast().id()));
    }

    // ------------------------------------------------------------ 도우미

    private MapSqlParameterSource params(long me) {
        return new MapSqlParameterSource("me", me).addValue("now", Timestamp.from(Instant.now(clock)));
    }

    private String select() {
        return """
                select s.id, s.media_url, s.width, s.height, s.alt_text, s.created_at, s.expires_at, s.audience,
                       s.stickers::text as stickers_json, %s,
                       v.story_id is not null as seen, coalesce(v.liked, false) as liked,
                       (select count(*) from story_views x where x.story_id = s.id) as viewer_count
                from stories s
                join users a on a.id = s.author_id
                left join story_views v on v.story_id = s.id and v.viewer_id = :me
                """.formatted(UserRows.cols("a"));
    }

    private StoryView read(java.sql.ResultSet rs, long me) throws java.sql.SQLException {
        UserSummary author = UserRows.read(rs, "a");
        boolean mine = author.id() == me;
        Instant expires = rs.getTimestamp("expires_at").toInstant();
        String stickersJson = rs.getString("stickers_json");
        List<Sticker> stickers = stickersJson == null ? List.of()
                : List.of(json.readValue(stickersJson, Sticker[].class));
        return new StoryView(rs.getLong("id"), author, rs.getString("media_url"), rs.getInt("width"),
                rs.getInt("height"), rs.getString("alt_text"), rs.getTimestamp("created_at").toInstant(), expires,
                rs.getBoolean("seen"), rs.getBoolean("liked"), mine, mine ? rs.getInt("viewer_count") : null,
                !expires.isAfter(Instant.now(clock)), rs.getString("audience"), stickers, List.of());
    }

    /** 지금 볼 수 있는 스토리 (만료·삭제·비공개면 404) */
    private StoryView visible(long me, long storyId) {
        return withResults(jdbc.query(select() + " where s.id = :s and " + StoryRings.VISIBLE_ACTIVE,
                        params(me).addValue("s", storyId), (rs, i) -> read(rs, me)), me).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("스토리를 찾을 수 없습니다. 24시간이 지나 사라졌을 수 있습니다."));
    }

    private void own(long me, long storyId) {
        Integer n = jdbc.queryForObject(
                "select count(*) from stories where id = :s and author_id = :me and deleted_at is null",
                Map.of("s", storyId, "me", me), Integer.class);
        if (n == null || n == 0) throw ApiException.notFound("스토리를 찾을 수 없습니다.");
    }
}
