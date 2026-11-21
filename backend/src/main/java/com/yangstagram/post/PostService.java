package com.yangstagram.post;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.follow.FollowService;
import com.yangstagram.media.MediaStorage;
import com.yangstagram.media.StoredImage;
import com.yangstagram.post.PostQueries.PostRow;
import com.yangstagram.post.dto.PostDtos.CreatePostRequest;
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

    public PostService(NamedParameterJdbcTemplate jdbc, PostQueries queries, FollowService followService,
                       MediaStorage storage, LabClock clock, NotificationService notifications, Mentions mentions) {
        this.jdbc = jdbc;
        this.queries = queries;
        this.followService = followService;
        this.storage = storage;
        this.clock = clock;
        this.notifications = notifications;
        this.mentions = mentions;
    }

    // ------------------------------------------------------------ 조회 (권한 포함)

    /** 볼 수 있는 게시물만. 없거나 지워졌으면 404, 비공개 계정이면 403 */
    public PostRow getVisible(String shortcode, long viewerId) {
        PostRow row = queries.findByShortcode(shortcode)
                .orElseThrow(() -> ApiException.notFound("게시물을 찾을 수 없습니다. 삭제되었거나 주소가 잘못되었습니다."));
        followService.requireCanView(viewerId, row.author().id(), row.author().isPrivate());
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
        if (files == null || files.isEmpty()) {
            throw ApiException.badRequest("MEDIA_REQUIRED", "사진을 한 장 이상 선택해 주세요.");
        }
        if (files.size() > MAX_MEDIA) {
            throw ApiException.badRequest("TOO_MANY_MEDIA", "사진은 최대 " + MAX_MEDIA + "장까지 올릴 수 있습니다.");
        }
        String caption = validateCaption(req.caption());
        String location = trimToNull(req.location());
        if (location != null && location.length() > MAX_LOCATION) {
            throw ApiException.badRequest("LOCATION_TOO_LONG", "위치 이름은 " + MAX_LOCATION + "자까지 쓸 수 있습니다.");
        }
        List<String> altTexts = req.altTexts() == null ? List.of() : req.altTexts();

        List<StoredImage> images = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            images.add(storage.saveImage(file));
        }

        Instant now = Instant.now(clock);
        String shortcode = Shortcodes.random();
        Long postId = jdbc.queryForObject("""
                insert into posts (shortcode, author_id, type, caption, location_id, hide_like_count,
                                   comments_disabled, created_at, updated_at)
                values (:code, :author, :type, :caption, :location, :hide, :noComments, :now, :now)
                returning id
                """, new MapSqlParameterSource("code", shortcode).addValue("author", authorId)
                .addValue("type", images.size() > 1 ? "CAROUSEL" : "PHOTO").addValue("caption", caption)
                .addValue("location", location == null ? null : locationId(location, now))
                .addValue("hide", req.hideLikes()).addValue("noComments", req.noComments())
                .addValue("now", Timestamp.from(now)), Long.class);

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
        mentions.notifyAll(caption, authorId, postId, null, Set.of());
        return queries.views(List.of(postId), authorId).getFirst();
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
        jdbc.update("update posts set " + set + " where id = :id", p);
        return queries.views(List.of(row.id()), viewerId).getFirst();
    }

    /** 삭제 표시만 한다 (최근 삭제됨 30일 보관은 M2에서 화면을 붙인다) */
    @Transactional
    public void delete(String shortcode, long viewerId) {
        PostRow row = getOwn(shortcode, viewerId);
        jdbc.update("update posts set deleted_at = :now where id = :id",
                new MapSqlParameterSource("id", row.id()).addValue("now", Timestamp.from(Instant.now(clock))));
        detachHashtags(row.id());
        jdbc.update("update users set posts_count = greatest(0, posts_count - 1) where id = :id",
                Map.of("id", viewerId));
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

    private static String validateCaption(String caption) {
        String value = caption == null ? null : caption.strip();
        if (value == null || value.isEmpty()) return null;
        if (value.length() > Hashtags.MAX_CAPTION) {
            throw ApiException.badRequest("CAPTION_TOO_LONG",
                    "캡션은 " + String.format("%,d", Hashtags.MAX_CAPTION) + "자까지 쓸 수 있습니다.");
        }
        if (Hashtags.extract(value).size() > Hashtags.MAX_PER_POST) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_HASHTAGS",
                    "해시태그는 게시물당 " + Hashtags.MAX_PER_POST + "개까지 쓸 수 있습니다.");
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
