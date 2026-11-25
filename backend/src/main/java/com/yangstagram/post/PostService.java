package com.yangstagram.post;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.event.EventService;
import com.yangstagram.lab.FeatureFlags;
import com.yangstagram.follow.FollowService;
import com.yangstagram.media.MediaStorage;
import com.yangstagram.media.StoredImage;
import com.yangstagram.post.PostQueries.PostRow;
import com.yangstagram.post.dto.PostDtos.CreatePostRequest;
import com.yangstagram.post.dto.PostDtos.CreateReelRequest;
import com.yangstagram.post.dto.PostDtos.TagInput;
import com.yangstagram.post.dto.PostDtos.LikeResult;
import com.yangstagram.notification.Mentions;
import com.yangstagram.notification.NotificationService;
import com.yangstagram.notification.NotificationType;
import com.yangstagram.post.dto.PostDtos.PostView;
import com.yangstagram.post.dto.PostDtos.RepostResult;
import com.yangstagram.post.dto.PostDtos.SaveResult;
import com.yangstagram.post.dto.PostDtos.UpdatePostRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 게시물 만들기·수정·삭제·좋아요 */
@Service
public class PostService {

    public static final int MAX_MEDIA = 20;
    public static final int MAX_LOCATION = 100;
    public static final int MAX_ALT_TEXT = 200;

    private final NamedParameterJdbcTemplate jdbc;
    private final PostQueries queries;
    private final FollowService followService;
    private final MediaStorage storage;
    private final LabClock clock;
    private final NotificationService notifications;
    private final Mentions mentions;
    private final EventService events;
    private final FeatureFlags flags;
    private final com.yangstagram.audio.AudioService audio;

    public PostService(NamedParameterJdbcTemplate jdbc, PostQueries queries, FollowService followService,
                       MediaStorage storage, LabClock clock, NotificationService notifications, Mentions mentions,
                       EventService events, FeatureFlags flags, com.yangstagram.audio.AudioService audio) {
        this.audio = audio;
        this.jdbc = jdbc;
        this.queries = queries;
        this.followService = followService;
        this.storage = storage;
        this.clock = clock;
        this.notifications = notifications;
        this.mentions = mentions;
        this.events = events;
        this.flags = flags;
    }

    // ------------------------------------------------------------ 조회 (권한 포함)

    /** 볼 수 있는 게시물만. 없거나 지워졌으면 404, 비공개 계정이면 403 */
    public PostRow getVisible(String shortcode, long viewerId) {
        PostRow row = queries.findByShortcode(shortcode)
                .orElseThrow(() -> ApiException.notFound("게시물을 찾을 수 없습니다. 삭제되었거나 주소가 잘못되었습니다."));
        followService.requireCanView(viewerId, row.author().id(), row.author().isPrivate());
        if (row.author().id() != viewerId && isArchived(row.id())) {
            // 보관한 게시물은 주인만 볼 수 있다
            throw ApiException.notFound("게시물을 찾을 수 없습니다. 삭제되었거나 주소가 잘못되었습니다.");
        }
        if (row.author().id() != viewerId) {
            Boolean locked = jdbc.queryForObject("""
                    select p.subscribers_only and not exists (select 1 from subscriptions s where s.subscriber_id = :me
                           and s.creator_id = p.author_id and s.status = 'ACTIVE')
                    from posts p where p.id = :id
                    """, Map.of("me", viewerId, "id", row.id()), Boolean.class);
            if (Boolean.TRUE.equals(locked)) {
                throw ApiException.forbidden("SUBSCRIBERS_ONLY", "구독자 전용 게시물입니다. 구독하면 볼 수 있어요.");
            }
        }
        return row;
    }

    public PostView view(String shortcode, long viewerId) {
        PostRow row = getVisible(shortcode, viewerId);
        return queries.views(List.of(row.id()), viewerId).getFirst();
    }

    private PostRow getOwn(String shortcode, long viewerId) {
        PostRow row = getVisible(shortcode, viewerId);
        if (row.author().id() != viewerId) {
            throw ApiException.forbidden("NOT_AUTHOR", "본인 게시물만 바꿀 수 있습니다.");
        }
        return row;
    }

    // ------------------------------------------------------------ 만들기

    @Transactional
    public PostView create(long authorId, List<MultipartFile> files, CreatePostRequest req) {
        List<StoredImage> images = validateAndStore(files, req);
        return publish(authorId, images, req);
    }

    /** 만들기 요청을 검사하고 사진을 저장한다 (예약 게시도 여기서 미리 저장해 둔다) */
    public List<StoredImage> validateAndStore(List<MultipartFile> files, CreatePostRequest req) {
        if (files == null || files.isEmpty()) {
            throw ApiException.badRequest("MEDIA_REQUIRED", "사진을 한 장 이상 선택해 주세요.");
        }
        if (files.size() > MAX_MEDIA) {
            throw ApiException.badRequest("TOO_MANY_MEDIA", "사진은 최대 " + MAX_MEDIA + "장까지 올릴 수 있습니다.");
        }
        validateCaption(req.caption());
        String location = trimToNull(req.location());
        if (location != null && location.length() > MAX_LOCATION) {
            throw ApiException.badRequest("LOCATION_TOO_LONG", "위치 이름은 " + MAX_LOCATION + "자까지 쓸 수 있습니다.");
        }
        if (req.tags() != null && req.tags().size() > MAX_TAGS) {
            throw ApiException.badRequest("TOO_MANY_TAGS", "사람 태그는 " + MAX_TAGS + "명까지 할 수 있습니다.");
        }
        List<StoredImage> images = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            images.add(storage.saveImage(file));
        }
        return images;
    }

    /** 저장해 둔 사진으로 게시물을 만든다 (바로 게시·예약 게시 공통). 게시 시각은 지금 */
    @Transactional
    public PostView publish(long authorId, List<StoredImage> images, CreatePostRequest req) {
        String caption = validateCaption(req.caption());
        String location = trimToNull(req.location());
        if (location != null && location.length() > MAX_LOCATION) location = location.substring(0, MAX_LOCATION);
        List<String> altTexts = req.altTexts() == null ? List.of() : req.altTexts();
        Instant now = Instant.now(clock);
        String shortcode = Shortcodes.random();
        Long postId = jdbc.queryForObject("""
                insert into posts (shortcode, author_id, type, caption, location_id, hide_like_count,
                                   comments_disabled, created_at, updated_at, filter, ai_labeled, subscribers_only,
                                   audio_id)
                values (:code, :author, :type, :caption, :location, :hide, :noComments, :now, :now, :filter, :ai,
                        :subs, :audio)
                returning id
                """, new MapSqlParameterSource("code", shortcode).addValue("author", authorId)
                .addValue("audio", audio.require(req.audioId()))
                .addValue("type", images.size() > 1 ? "CAROUSEL" : "PHOTO").addValue("caption", caption)
                .addValue("location", location == null ? null : locationId(location, now))
                .addValue("hide", req.hideLikes()).addValue("noComments", req.noComments())
                .addValue("filter", req.filter() == null || req.filter().length() > 20 ? null : req.filter())
                .addValue("ai", req.ai()).addValue("now", Timestamp.from(now))
                .addValue("subs", subscribersOnly(authorId, req.subscribersOnly())), Long.class);

        for (int i = 0; i < images.size(); i++) {
            StoredImage img = images.get(i);
            String alt = i < altTexts.size() ? trimToNull(altTexts.get(i)) : null;
            if (alt != null && alt.length() > MAX_ALT_TEXT) alt = alt.substring(0, MAX_ALT_TEXT);
            jdbc.update("""
                    insert into post_media (post_id, position, url, thumb_url, width, height, alt_text)
                    values (:post, :pos, :url, :thumb, :w, :h, :alt)
                    """, new MapSqlParameterSource("post", postId).addValue("pos", i).addValue("url", img.url())
                    .addValue("thumb", img.thumbUrl()).addValue("w", img.width()).addValue("h", img.height())
                    .addValue("alt", alt));
        }
        attachHashtags(postId, Hashtags.extract(caption));
        jdbc.update("update users set posts_count = posts_count + 1 where id = :id", Map.of("id", authorId));
        attachTags(postId, authorId, req.tags(), images.size());
        inviteCollaborators(postId, authorId, req.collaborators());
        mentions.notifyAll(caption, authorId, postId, null, Set.of());
        return queries.views(List.of(postId), authorId).getFirst();
    }

    // ------------------------------------------------------------ 사람 태그·공동 작업자

    public static final int MAX_TAGS = 20;
    public static final int MAX_COLLABORATORS = 5;

    /** 태그한 사람에게 알림. 차단 관계거나 없는 사용자는 조용히 뺀다 */
    private void attachTags(long postId, long authorId, List<TagInput> tags, int mediaCount) {
        if (tags == null || tags.isEmpty()) return;
        if (tags.size() > MAX_TAGS) {
            throw ApiException.badRequest("TOO_MANY_TAGS", "사람 태그는 " + MAX_TAGS + "명까지 할 수 있습니다.");
        }
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (TagInput t : tags) {
            if (t.username() == null) continue;
            List<Long> ids = jdbc.queryForList("select id from users where username = :u and deleted_at is null",
                    Map.of("u", t.username().strip().toLowerCase().replace("@", "")), Long.class);
            if (ids.isEmpty()) continue;
            long uid = ids.getFirst();
            if (followService.blocked(authorId, uid) || !done.add(uid) || !reachable(uid, authorId)) continue;
            int index = t.mediaIndex() == null ? 0 : Math.max(0, Math.min(mediaCount - 1, t.mediaIndex()));
            double x = t.x() == null ? 0.5 : Math.max(0, Math.min(1, t.x()));
            double y = t.y() == null ? 0.5 : Math.max(0, Math.min(1, t.y()));
            jdbc.update("""
                    insert into post_tags (post_id, user_id, media_index, x, y) values (:p, :u, :i, :x, :y)
                    on conflict do nothing
                    """, new MapSqlParameterSource("p", postId).addValue("u", uid).addValue("i", index)
                    .addValue("x", x).addValue("y", y));
            notifications.notify(uid, authorId, NotificationType.TAG, postId, null, null, null);
        }
    }

    /** 10대 계정은 자기가 팔로우하는 사람에게서만 태그·초대를 받는다 */
    private boolean reachable(long userId, long actorId) {
        Integer n = jdbc.queryForObject("select count(*) from users u where u.id = :u and "
                + com.yangstagram.user.Teens.reachableBy("u", ":actor"), Map.of("u", userId, "actor", actorId),
                Integer.class);
        return n != null && n > 0;
    }

    /** 공동 작업자 초대: 받은 사람이 수락하면 그 사람 프로필과 팔로워 피드에도 보인다 */
    private void inviteCollaborators(long postId, long authorId, List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) return;
        List<String> names = usernames.stream().filter(java.util.Objects::nonNull)
                .map(n -> n.strip().toLowerCase().replace("@", "")).filter(n -> !n.isEmpty()).distinct().toList();
        if (names.size() > MAX_COLLABORATORS) {
            throw ApiException.badRequest("TOO_MANY_COLLABORATORS", "공동 작업자는 " + MAX_COLLABORATORS + "명까지 초대할 수 있습니다.");
        }
        Timestamp now = Timestamp.from(Instant.now(clock));
        for (String name : names) {
            List<Long> ids = jdbc.queryForList("select id from users where username = :u and deleted_at is null",
                    Map.of("u", name), Long.class);
            if (ids.isEmpty() || ids.getFirst() == authorId || followService.blocked(authorId, ids.getFirst())
                    || !reachable(ids.getFirst(), authorId)) continue;
            jdbc.update("""
                    insert into post_collaborators (post_id, user_id, status, created_at) values (:p, :u, 'PENDING', :now)
                    on conflict do nothing
                    """, new MapSqlParameterSource("p", postId).addValue("u", ids.getFirst()).addValue("now", now));
            notifications.notify(ids.getFirst(), authorId, NotificationType.COLLAB_INVITE, postId, null, null, null);
        }
    }

    /** 공동 작업 초대 수락·거절 (거절하면 초대가 사라진다) */
    @Transactional
    public PostView answerCollab(String shortcode, long viewerId, boolean accept) {
        PostRow row = getVisible(shortcode, viewerId);
        int changed = accept
                ? jdbc.update("""
                        update post_collaborators set status = 'ACCEPTED'
                        where post_id = :p and user_id = :me and status = 'PENDING'
                        """, Map.of("p", row.id(), "me", viewerId))
                : jdbc.update("delete from post_collaborators where post_id = :p and user_id = :me",
                        Map.of("p", row.id(), "me", viewerId));
        if (changed == 0) throw ApiException.notFound("공동 작업 초대를 찾을 수 없습니다.");
        notifications.retract(row.author().id(), NotificationType.COLLAB_INVITE, row.id(), null, null, viewerId);
        return queries.views(List.of(row.id()), viewerId).getFirst();
    }

    /** 태그된 사람이 스스로 태그를 뺀다 */
    @Transactional
    public PostView removeMyTag(String shortcode, long viewerId) {
        PostRow row = getVisible(shortcode, viewerId);
        jdbc.update("delete from post_tags where post_id = :p and user_id = :me", Map.of("p", row.id(), "me", viewerId));
        notifications.retract(row.author().id(), NotificationType.TAG, row.id(), null, null, viewerId);
        return queries.views(List.of(row.id()), viewerId).getFirst();
    }

    // ------------------------------------------------------------ 릴스

    public static final long MAX_REEL_MS = 20 * 60 * 1000;     // 20분
    public static final long RECOMMEND_LIMIT_MS = 3 * 60 * 1000; // 3분 넘으면 비팔로워 추천 제외

    /** 릴스 올리기: 영상 1개 + 첫 화면 사진(poster). 길이는 화면이 영상을 읽어서 보낸다 */
    @Transactional
    public PostView createReel(long authorId, MultipartFile video, MultipartFile poster, CreateReelRequest req) {
        if (video == null || video.isEmpty()) throw ApiException.badRequest("MEDIA_REQUIRED", "영상을 선택해 주세요.");
        if (poster == null || poster.isEmpty()) throw ApiException.badRequest("POSTER_REQUIRED", "첫 화면 사진이 필요합니다.");
        long duration = req.durationMs() == null ? 0 : req.durationMs();
        if (duration <= 0) throw ApiException.badRequest("BAD_DURATION", "영상 길이를 알 수 없습니다.");
        if (duration > MAX_REEL_MS) throw ApiException.badRequest("REEL_TOO_LONG", "릴스는 20분까지 올릴 수 있습니다.");
        String caption = validateCaption(req.caption());
        PostRow original = null;
        if (req.remixOf() != null && !req.remixOf().isBlank()) {
            original = getVisible(req.remixOf(), authorId);
            Boolean allowed = jdbc.queryForObject("select allow_remix from posts where id = :id",
                    Map.of("id", original.id()), Boolean.class);
            if (!"REEL".equals(original.type()) || !Boolean.TRUE.equals(allowed)) {
                throw ApiException.badRequest("REMIX_NOT_ALLOWED", "이 릴스는 리믹스할 수 없습니다.");
            }
        }
        MediaStorage.StoredVideo stored = storage.saveVideo(video);
        StoredImage cover = storage.saveImage(poster);
        Instant now = Instant.now(clock);
        Long postId = jdbc.queryForObject("""
                insert into posts (shortcode, author_id, type, caption, hide_like_count, comments_disabled,
                                   recommendable, created_at, updated_at, ai_labeled, trial, remix_of_id, allow_remix,
                                   subscribers_only, audio_id)
                values (:code, :author, 'REEL', :caption, :hide, :noComments, :rec, :now, :now, :ai, :trial, :remix,
                        :allowRemix, :subs, :audio) returning id
                """, new MapSqlParameterSource("code", Shortcodes.random()).addValue("author", authorId)
                .addValue("audio", audio.require(req.audioId()))
                .addValue("remix", original == null ? null : original.id())
                .addValue("allowRemix", !Boolean.FALSE.equals(req.allowRemix()))
                .addValue("subs", subscribersOnly(authorId, req.subscribersOnly()))
                .addValue("caption", caption).addValue("hide", Boolean.TRUE.equals(req.hideLikeCount()))
                .addValue("noComments", Boolean.TRUE.equals(req.commentsDisabled()))
                .addValue("rec", duration <= RECOMMEND_LIMIT_MS).addValue("now", Timestamp.from(now))
                .addValue("ai", Boolean.TRUE.equals(req.aiLabel())).addValue("trial", Boolean.TRUE.equals(req.trial())),
                Long.class);
        jdbc.update("""
                insert into post_media (post_id, position, url, thumb_url, width, height, media_type, duration_ms)
                values (:p, 0, :url, :thumb, :w, :h, 'VIDEO', :d)
                """, new MapSqlParameterSource("p", postId).addValue("url", stored.url())
                .addValue("thumb", cover.thumbUrl()).addValue("w", cover.width()).addValue("h", cover.height())
                .addValue("d", duration));
        attachHashtags(postId, Hashtags.extract(caption));
        jdbc.update("update users set posts_count = posts_count + 1 where id = :id", Map.of("id", authorId));
        if (req.audioId() == null) {
            // 음악을 고르지 않으면 이 릴스의 소리가 "원본 오디오"가 된다 (리믹스는 원본의 소리를 그대로)
            Long originalAudio = original == null ? null : jdbc.queryForObject(
                    "select audio_id from posts where id = :id", Map.of("id", original.id()), Long.class);
            long audioId = originalAudio != null ? originalAudio : audio.createOriginal(postId, authorId,
                    jdbc.queryForObject("select username from users where id = :id", Map.of("id", authorId), String.class),
                    duration);
            jdbc.update("update posts set audio_id = :a where id = :id", Map.of("a", audioId, "id", postId));
        }
        mentions.notifyAll(caption, authorId, postId, null, Set.of());
        if (original != null) {
            notifications.notify(original.author().id(), authorId, NotificationType.REMIX, postId, null, null, null);
        }
        return queries.views(List.of(postId), authorId).getFirst();
    }

    /** 이 릴스를 리믹스한 릴스 */
    public List<com.yangstagram.post.dto.PostDtos.GridItem> remixes(String shortcode, long viewerId) {
        return queries.remixes(getVisible(shortcode, viewerId).id(), viewerId);
    }

    /** 리믹스 허용 바꾸기 (작성자만) */
    @Transactional
    public void setAllowRemix(String shortcode, long viewerId, boolean allow) {
        PostRow row = getVisible(shortcode, viewerId);
        if (row.author().id() != viewerId) throw ApiException.forbidden("NOT_AUTHOR", "작성자만 바꿀 수 있습니다.");
        jdbc.update("update posts set allow_remix = :a where id = :id", Map.of("a", allow, "id", row.id()));
    }

    /** 구독자 전용은 구독 가격을 정한 계정만 */
    private boolean subscribersOnly(long authorId, Boolean requested) {
        if (!Boolean.TRUE.equals(requested)) return false;
        Integer price = jdbc.queryForObject("select subscription_price from users where id = :id",
                Map.of("id", authorId), Integer.class);
        if (price == null) {
            throw ApiException.badRequest("NO_SUBSCRIPTION", "구독 가격을 먼저 정해야 구독자 전용으로 올릴 수 있습니다.");
        }
        return true;
    }

    /** 릴스 등 조회 기록: 사람당 한 번만 센다 */
    @Transactional
    public int recordView(String shortcode, long viewerId) {
        PostRow row = getVisible(shortcode, viewerId);
        int inserted = jdbc.update("""
                insert into post_views (user_id, post_id, created_at) values (:me, :p, :now) on conflict do nothing
                """, new MapSqlParameterSource("me", viewerId).addValue("p", row.id())
                .addValue("now", Timestamp.from(Instant.now(clock))));
        return jdbc.queryForObject("update posts set view_count = view_count + :d where id = :id returning view_count",
                Map.of("d", inserted, "id", row.id()), Integer.class);
    }

    // ------------------------------------------------------------ 수정·삭제

    @Transactional
    public PostView update(String shortcode, long viewerId, UpdatePostRequest req) {
        PostRow row = getOwn(shortcode, viewerId);
        Instant now = Instant.now(clock);
        MapSqlParameterSource p = new MapSqlParameterSource("id", row.id()).addValue("now", Timestamp.from(now));
        StringBuilder set = new StringBuilder("updated_at = :now");
        if (req.caption() != null) {
            String caption = validateCaption(req.caption());
            set.append(", caption = :caption");
            p.addValue("caption", caption);
            detachHashtags(row.id());
            attachHashtags(row.id(), Hashtags.extract(caption));
        }
        if (req.location() != null) {
            String location = trimToNull(req.location());
            set.append(", location_id = :location");
            p.addValue("location", location == null ? null : locationId(location, now));
        }
        if (req.hideLikeCount() != null) {
            set.append(", hide_like_count = :hide");
            p.addValue("hide", req.hideLikeCount());
        }
        if (req.commentsDisabled() != null) {
            set.append(", comments_disabled = :noComments");
            p.addValue("noComments", req.commentsDisabled());
        }
        if (req.aiLabel() != null) {
            set.append(", ai_labeled = :ai");
            p.addValue("ai", req.aiLabel());
        }
        jdbc.update("update posts set " + set + " where id = :id", p);
        return queries.views(List.of(row.id()), viewerId).getFirst();
    }

    /** 시험 릴스를 팔로워에게도 공유한다 (되돌릴 수 없음) */
    @Transactional
    public PostView shareTrial(String shortcode, long viewerId) {
        PostRow row = getOwn(shortcode, viewerId);
        int n = jdbc.update("update posts set trial = false, trial_shared_at = :now where id = :id and trial",
                new MapSqlParameterSource("id", row.id()).addValue("now", Timestamp.from(Instant.now(clock))));
        if (n == 0) throw ApiException.badRequest("NOT_TRIAL", "시험 릴스가 아닙니다.");
        return queries.views(List.of(row.id()), viewerId).getFirst();
    }

    /** 삭제 표시만 한다. 30일 동안 "최근 삭제됨"에서 되살릴 수 있다 */
    @Transactional
    public void delete(String shortcode, long viewerId) {
        PostRow row = getOwn(shortcode, viewerId);
        boolean archived = isArchived(row.id());
        jdbc.update("update posts set deleted_at = :now, pinned_at = null where id = :id",
                new MapSqlParameterSource("id", row.id()).addValue("now", Timestamp.from(Instant.now(clock))));
        detachHashtags(row.id());
        if (!archived) adjustPostsCount(viewerId, -1);
    }

    public static final java.time.Duration RESTORE_WINDOW = java.time.Duration.ofDays(30);

    /** 최근 삭제됨에서 되살리기 (30일 안) */
    @Transactional
    public PostView restore(String shortcode, long viewerId) {
        Map<String, Object> row = jdbc.queryForList("""
                select id, author_id, deleted_at, archived_at is not null as archived, caption from posts
                where shortcode = :code and deleted_at is not null
                """, Map.of("code", shortcode)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("최근 삭제된 게시물이 아닙니다."));
        if (((Number) row.get("author_id")).longValue() != viewerId) {
            throw ApiException.forbidden("NOT_AUTHOR", "본인 게시물만 되살릴 수 있습니다.");
        }
        Instant deletedAt = ((Timestamp) row.get("deleted_at")).toInstant();
        if (deletedAt.isBefore(Instant.now(clock).minus(RESTORE_WINDOW))) {
            throw ApiException.badRequest("RESTORE_EXPIRED", "삭제한 지 30일이 지나 되살릴 수 없습니다.");
        }
        long id = ((Number) row.get("id")).longValue();
        jdbc.update("update posts set deleted_at = null where id = :id", Map.of("id", id));
        String caption = (String) row.get("caption");
        if (caption != null) attachHashtags(id, Hashtags.extract(caption));
        if (!Boolean.TRUE.equals(row.get("archived"))) adjustPostsCount(viewerId, 1);
        return queries.views(List.of(id), viewerId).getFirst();
    }

    /** 보관: 나만 보고, 프로필·피드·검색에서는 빠진다. 되돌리면 원래 자리로 */
    @Transactional
    public PostView archive(String shortcode, long viewerId, boolean archive) {
        PostRow row = getOwn(shortcode, viewerId);
        int n = jdbc.update(archive
                        ? "update posts set archived_at = :now, pinned_at = null where id = :id and archived_at is null"
                        : "update posts set archived_at = null where id = :id and archived_at is not null",
                new MapSqlParameterSource("id", row.id()).addValue("now", Timestamp.from(Instant.now(clock))));
        if (n == 1) adjustPostsCount(viewerId, archive ? -1 : 1);
        return queries.views(List.of(row.id()), viewerId).getFirst();
    }

    public static final int MAX_PINNED = 3;

    /** 프로필 맨 앞에 고정 (최대 3개) */
    @Transactional
    public PostView pin(String shortcode, long viewerId, boolean pin) {
        PostRow row = getOwn(shortcode, viewerId);
        if (pin) {
            if (isArchived(row.id())) throw ApiException.badRequest("ARCHIVED", "보관한 게시물은 고정할 수 없습니다.");
            Integer pinned = jdbc.queryForObject("""
                    select count(*) from posts where author_id = :me and pinned_at is not null and deleted_at is null
                      and archived_at is null and id <> :id
                    """, Map.of("me", viewerId, "id", row.id()), Integer.class);
            if (pinned != null && pinned >= MAX_PINNED) {
                throw ApiException.badRequest("PIN_LIMIT", "게시물은 최대 3개까지 고정할 수 있습니다.");
            }
        }
        jdbc.update(pin ? "update posts set pinned_at = coalesce(pinned_at, :now) where id = :id"
                        : "update posts set pinned_at = null where id = :id",
                new MapSqlParameterSource("id", row.id()).addValue("now", Timestamp.from(Instant.now(clock))));
        return queries.views(List.of(row.id()), viewerId).getFirst();
    }

    /** 프로필 그리드 재배치: 한 칸 앞(earlier=true, 그리드 위쪽)이나 뒤로. 고정·보관·시험 릴스·공동 작업자 글은 제외 */
    @Transactional
    public void moveInGrid(String shortcode, long viewerId, boolean towardFront) {
        PostRow row = getOwn(shortcode, viewerId);
        Map<String, Object> me = jdbc.queryForMap("select grid_sort, pinned_at, trial from posts where id = :id",
                Map.of("id", row.id()));
        if (me.get("pinned_at") != null || isArchived(row.id()) || Boolean.TRUE.equals(me.get("trial"))) {
            throw ApiException.badRequest("NOT_MOVABLE", "고정했거나 보관한 게시물·시험 릴스는 옮길 수 없습니다.");
        }
        List<Map<String, Object>> neighbor = jdbc.queryForList("""
                select id, grid_sort from posts where author_id = :me and id <> :id and deleted_at is null
                  and archived_at is null and pinned_at is null and not trial
                  and (grid_sort, id) %s (cast(:sort as timestamptz), :id)
                order by grid_sort %s, id %s limit 1
                """.formatted(towardFront ? ">" : "<", towardFront ? "asc" : "desc", towardFront ? "asc" : "desc"),
                new MapSqlParameterSource("me", viewerId).addValue("id", row.id()).addValue("sort", me.get("grid_sort")));
        if (neighbor.isEmpty()) {
            throw ApiException.badRequest("EDGE", towardFront ? "이미 맨 앞입니다." : "이미 맨 뒤입니다.");
        }
        Object mine = me.get("grid_sort");
        Object theirs = neighbor.getFirst().get("grid_sort");
        long otherId = ((Number) neighbor.getFirst().get("id")).longValue();
        if (mine.equals(theirs)) {
            // 같은 시각이면 1밀리초 차이를 둬서 순서가 확실히 바뀌게
            Instant t = ((Timestamp) theirs).toInstant();
            theirs = Timestamp.from(towardFront ? t.plusMillis(1) : t.minusMillis(1));
        }
        jdbc.update("update posts set grid_sort = :s where id = :id", Map.of("s", theirs, "id", row.id()));
        jdbc.update("update posts set grid_sort = :s where id = :id", Map.of("s", mine, "id", otherId));
    }

    public static final java.time.Duration NOTE_LIFETIME = java.time.Duration.ofDays(3);

    /** 게시물 위 노트 남기기·바꾸기 (맞팔로우만, 60자, 3일) */
    @Transactional
    public PostView putNote(String shortcode, long viewerId, String rawBody) {
        PostRow row = getVisible(shortcode, viewerId);
        if (row.author().id() == viewerId) throw ApiException.badRequest("OWN_POST", "내 게시물에는 노트를 남길 수 없습니다.");
        Integer mutual = jdbc.queryForObject("""
                select count(*) from follows a join follows b on b.follower_id = a.following_id and b.following_id = a.follower_id
                where a.follower_id = :me and a.following_id = :author and a.status = 'ACCEPTED' and b.status = 'ACCEPTED'
                """, Map.of("me", viewerId, "author", row.author().id()), Integer.class);
        if (mutual == null || mutual == 0) {
            throw ApiException.forbidden("NOT_MUTUAL", "서로 팔로우하는 사람의 게시물에만 노트를 남길 수 있습니다.");
        }
        String body = rawBody == null ? "" : rawBody.strip();
        if (body.isEmpty() || body.codePointCount(0, body.length()) > 60) {
            throw ApiException.badRequest("BAD_NOTE", "노트는 1~60자로 써 주세요.");
        }
        Instant now = Instant.now(clock);
        jdbc.update("""
                insert into post_notes (post_id, user_id, body, created_at, expires_at) values (:p, :me, :b, :now, :exp)
                on conflict (post_id, user_id) do update set body = excluded.body, created_at = excluded.created_at,
                    expires_at = excluded.expires_at
                """, new MapSqlParameterSource("p", row.id()).addValue("me", viewerId).addValue("b", body)
                .addValue("now", Timestamp.from(now)).addValue("exp", Timestamp.from(now.plus(NOTE_LIFETIME))));
        return queries.views(List.of(row.id()), viewerId).getFirst();
    }

    @Transactional
    public PostView deleteNote(String shortcode, long viewerId) {
        PostRow row = getVisible(shortcode, viewerId);
        jdbc.update("delete from post_notes where post_id = :p and user_id = :me", Map.of("p", row.id(), "me", viewerId));
        return queries.views(List.of(row.id()), viewerId).getFirst();
    }

    private boolean isArchived(long postId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select archived_at is not null from posts where id = :id",
                Map.of("id", postId), Boolean.class));
    }

    private void adjustPostsCount(long userId, int delta) {
        jdbc.update("update users set posts_count = greatest(0, posts_count + :d) where id = :id",
                Map.of("id", userId, "d", delta));
    }

    // ------------------------------------------------------------ 좋아요

    @Transactional
    public LikeResult like(String shortcode, long viewerId) {
        PostRow row = getVisible(shortcode, viewerId);
        int inserted = jdbc.update("""
                insert into likes (user_id, post_id, created_at) values (:me, :post, :now)
                on conflict do nothing
                """, new MapSqlParameterSource("me", viewerId).addValue("post", row.id())
                .addValue("now", Timestamp.from(Instant.now(clock))));
        if (inserted == 1) {
            notifications.notify(row.author().id(), viewerId, NotificationType.LIKE, row.id(), null, null, null);
            events.record(viewerId, "LIKE", row.id(), row.author().id(), null);
        }
        return new LikeResult(true, bumpLikes(row.id(), inserted));
    }

    @Transactional
    public LikeResult unlike(String shortcode, long viewerId) {
        PostRow row = getVisible(shortcode, viewerId);
        int deleted = jdbc.update("delete from likes where user_id = :me and post_id = :post",
                Map.of("me", viewerId, "post", row.id()));
        notifications.retract(viewerId, NotificationType.LIKE, row.id(), null, null, null);
        return new LikeResult(false, bumpLikes(row.id(), -deleted));
    }

    // ------------------------------------------------------------ 저장 (컬렉션은 SavedService)

    @Transactional
    public SaveResult save(String shortcode, long viewerId) {
        PostRow row = getVisible(shortcode, viewerId);
        int inserted = jdbc.update("""
                insert into saved_posts (user_id, post_id, created_at) values (:me, :post, :now)
                on conflict do nothing
                """, new MapSqlParameterSource("me", viewerId).addValue("post", row.id())
                .addValue("now", Timestamp.from(Instant.now(clock))));
        jdbc.update("update posts set save_count = save_count + :d where id = :id",
                Map.of("d", inserted, "id", row.id()));
        if (inserted == 1) events.record(viewerId, "SAVE", row.id(), row.author().id(), null);
        return new SaveResult(true);
    }

    /** 저장 취소하면 모든 컬렉션에서도 빠진다 */
    @Transactional
    public SaveResult unsave(String shortcode, long viewerId) {
        PostRow row = getVisible(shortcode, viewerId);
        int deleted = jdbc.update("delete from saved_posts where user_id = :me and post_id = :post",
                Map.of("me", viewerId, "post", row.id()));
        jdbc.update("""
                delete from collection_posts where post_id = :post
                  and collection_id in (select id from collections where user_id = :me)
                """, Map.of("me", viewerId, "post", row.id()));
        jdbc.update("update posts set save_count = greatest(0, save_count - :d) where id = :id",
                Map.of("d", deleted, "id", row.id()));
        return new SaveResult(false);
    }

    // ------------------------------------------------------------ 리포스트

    /** 다른 사람의 공개 계정 게시물만 리포스트할 수 있다. 내 팔로워 피드와 프로필 "리포스트" 탭에 보인다. */
    @Transactional
    public RepostResult repost(String shortcode, long viewerId, String rawNote) {
        PostRow row = getVisible(shortcode, viewerId);
        if (row.author().id() == viewerId) {
            throw ApiException.badRequest("CANNOT_REPOST_OWN", "내 게시물은 리포스트할 수 없습니다.");
        }
        if (row.author().isPrivate()) {
            throw ApiException.forbidden("REPOST_NOT_ALLOWED", "비공개 계정의 게시물은 리포스트할 수 없습니다.");
        }
        String note = trimToNull(rawNote);
        if (note != null && note.length() > 60) {
            throw ApiException.badRequest("NOTE_TOO_LONG", "메모는 60자까지 쓸 수 있습니다.");
        }
        Integer existing = jdbc.queryForObject("select count(*) from reposts where user_id = :me and post_id = :post",
                Map.of("me", viewerId, "post", row.id()), Integer.class);
        jdbc.update("""
                insert into reposts (user_id, post_id, note, created_at) values (:me, :post, :note, :now)
                on conflict (user_id, post_id) do update set note = excluded.note
                """, new MapSqlParameterSource("me", viewerId).addValue("post", row.id()).addValue("note", note)
                .addValue("now", Timestamp.from(Instant.now(clock))));
        int count = bumpReposts(row.id());
        if (existing == null || existing == 0) {
            notifications.notify(row.author().id(), viewerId, NotificationType.REPOST, row.id(), null, null, note);
            events.record(viewerId, "SHARE", row.id(), row.author().id(), null);
        }
        return new RepostResult(true, count);
    }

    @Transactional
    public RepostResult unrepost(String shortcode, long viewerId) {
        PostRow row = getVisible(shortcode, viewerId);
        jdbc.update("delete from reposts where user_id = :me and post_id = :post",
                Map.of("me", viewerId, "post", row.id()));
        notifications.retract(viewerId, NotificationType.REPOST, row.id(), null, null, null);
        return new RepostResult(false, bumpReposts(row.id()));
    }

    private int bumpReposts(long postId) {
        return jdbc.queryForObject("""
                update posts set repost_count = (select count(*) from reposts where post_id = :id)
                where id = :id returning repost_count
                """, Map.of("id", postId), Integer.class);
    }

    private int bumpLikes(long postId, int delta) {
        return jdbc.queryForObject(
                "update posts set like_count = greatest(0, like_count + :d) where id = :id returning like_count",
                Map.of("d", delta, "id", postId), Integer.class);
    }

    // ------------------------------------------------------------ 도우미

    private String validateCaption(String caption) {
        String value = caption == null ? null : caption.strip();
        if (value == null || value.isEmpty()) return null;
        if (value.length() > Hashtags.MAX_CAPTION) {
            throw ApiException.badRequest("CAPTION_TOO_LONG",
                    "캡션은 " + String.format("%,d", Hashtags.MAX_CAPTION) + "자까지 쓸 수 있습니다.");
        }
        int limit = flags.get().hashtagLimit();
        if (Hashtags.extract(value).size() > limit) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_HASHTAGS",
                    "해시태그는 게시물당 " + limit + "개까지 쓸 수 있습니다.");
        }
        return value;
    }

    private long locationId(String name, Instant now) {
        return jdbc.queryForObject("""
                insert into locations (name, created_at) values (:name, :now)
                on conflict (name) do update set name = excluded.name
                returning id
                """, new MapSqlParameterSource("name", name).addValue("now", Timestamp.from(now)), Long.class);
    }

    private void attachHashtags(long postId, Set<String> tags) {
        for (String tag : tags) {
            Long tagId = jdbc.queryForObject("""
                    insert into hashtags (name, post_count) values (:name, 1)
                    on conflict (name) do update set post_count = hashtags.post_count + 1
                    returning id
                    """, Map.of("name", tag), Long.class);
            jdbc.update("insert into post_hashtags (post_id, hashtag_id) values (:p, :t) on conflict do nothing",
                    Map.of("p", postId, "t", tagId));
        }
    }

    private void detachHashtags(long postId) {
        jdbc.update("""
                update hashtags set post_count = greatest(0, post_count - 1)
                where id in (select hashtag_id from post_hashtags where post_id = :p)
                """, Map.of("p", postId));
        jdbc.update("delete from post_hashtags where post_id = :p", Map.of("p", postId));
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
