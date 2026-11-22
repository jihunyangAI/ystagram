package com.yangstagram.comment;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.paging.Slice;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.event.EventService;
import com.yangstagram.notification.Mentions;
import com.yangstagram.notification.NotificationService;
import com.yangstagram.notification.NotificationType;
import com.yangstagram.post.PostQueries;
import com.yangstagram.post.PostQueries.PostRow;
import com.yangstagram.post.PostService;
import com.yangstagram.post.dto.PostDtos.CommentView;
import com.yangstagram.post.dto.PostDtos.LikeResult;
import com.yangstagram.relation.RelationService;
import com.yangstagram.relation.RelationSql;
import com.yangstagram.user.dto.UserRows;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 (분석 문서 §14): 답글 1단계, 고정(최대 3개), 수정(15분), 좋아요, 삭제.
 */
@Service
public class CommentService {

    public static final int MAX_LENGTH = 2200;
    public static final int MAX_PINNED = 3;
    private static final int PAGE = 30;

    private static final String SELECT = """
            select c.id, c.post_id, c.body, c.created_at, c.edited_at, c.pinned_at, c.like_count, c.parent_id,
                   c.restricted,
                   exists (select 1 from comment_likes cl where cl.comment_id = c.id and cl.user_id = :me)
                     as liked_by_me,
                   """ + PostQueries.SUBSCRIBER_COL + """
                   ,
                   %s
            from comments c join users u on u.id = c.author_id
            """.formatted(UserRows.cols("u"));

    /** 보는 사람(:me)에게 보이는 댓글 (:owner = 게시물 주인) */
    private static final String SHOWN = " and (not c.restricted or c.author_id = :me or :owner = :me) and "
            + RelationSql.notBlocked("u.id");

    private final NamedParameterJdbcTemplate jdbc;
    private final PostService postService;
    private final NotificationService notifications;
    private final Mentions mentions;
    private final RelationService relations;
    private final LabClock clock;
    private final EventService events;

    public CommentService(NamedParameterJdbcTemplate jdbc, PostService postService,
                          NotificationService notifications, Mentions mentions, RelationService relations,
                          LabClock clock, EventService events) {
        this.jdbc = jdbc;
        this.postService = postService;
        this.notifications = notifications;
        this.mentions = mentions;
        this.relations = relations;
        this.clock = clock;
        this.events = events;
    }

    // ------------------------------------------------------------ 목록

    /**
     * 최상위 댓글: 첫 페이지 맨 위에 고정 댓글, 그다음 오래된 순. 커서는 마지막으로 받은 댓글 id.
     * 답글은 {@link #replies}로 따로 연다 ("답글 N개 보기").
     */
    public Slice<CommentView> list(String shortcode, long viewerId, Long afterId) {
        PostRow post = postService.getVisible(shortcode, viewerId);
        Instant now = Instant.now(clock);
        MapSqlParameterSource p = new MapSqlParameterSource("post", post.id()).addValue("me", viewerId)
                .addValue("owner", post.author().id())
                .addValue("after", afterId == null ? 0 : afterId).addValue("limit", PAGE + 1);

        List<CommentView> pinned = afterId != null ? List.of() : jdbc.query(SELECT + """
                where c.post_id = :post and c.parent_id is null and c.pinned_at is not null and u.deleted_at is null
                """ + SHOWN + " order by c.pinned_at", p, (rs, i) -> PostQueries.comment(rs, viewerId, post.author().id(), now, 0));

        List<CommentView> page = jdbc.query(SELECT + """
                where c.post_id = :post and c.parent_id is null and c.pinned_at is null and u.deleted_at is null
                  and c.id > :after
                """ + SHOWN + " order by c.id limit :limit", p, (rs, i) -> PostQueries.comment(rs, viewerId, post.author().id(), now, 0));

        String next = null;
        if (page.size() > PAGE) {
            page = page.subList(0, PAGE);
            next = String.valueOf(page.getLast().id());
        }
        List<CommentView> all = new ArrayList<>(pinned);
        all.addAll(page);
        return new Slice<>(withReplyCounts(all), next);
    }

    /** 답글 전체 (오래된 순). 한 댓글의 답글은 많지 않으므로 페이지 없이 */
    public List<CommentView> replies(long commentId, long viewerId) {
        Map<String, Object> parent = parentInfo(commentId);
        PostRow post = postService.getVisible((String) parent.get("shortcode"), viewerId);
        Instant now = Instant.now(clock);
        return jdbc.query(SELECT + """
                where c.parent_id = :parent and u.deleted_at is null
                """ + SHOWN + " order by c.id limit 500",
                new MapSqlParameterSource("parent", commentId).addValue("me", viewerId)
                        .addValue("owner", post.author().id()),
                (rs, i) -> PostQueries.comment(rs, viewerId, post.author().id(), now, 0));
    }

    private List<CommentView> withReplyCounts(List<CommentView> comments) {
        if (comments.isEmpty()) return comments;
        Map<Long, Integer> counts = new HashMap<>();
        jdbc.query("select parent_id, count(*) from comments where parent_id in (:ids) group by parent_id",
                Map.of("ids", comments.stream().map(CommentView::id).toList()),
                rs -> {
                    counts.put(rs.getLong(1), rs.getInt(2));
                });
        return comments.stream().map(c -> new CommentView(c.id(), c.author(), c.body(), c.createdAt(), c.parentId(),
                c.canDelete(), c.canEdit(), c.canPin(), c.edited(), c.pinned(), c.likeCount(), c.likedByMe(),
                counts.getOrDefault(c.id(), 0), c.restricted(), c.subscriber())).toList();
    }

    // ------------------------------------------------------------ 쓰기·수정·삭제

    @Transactional
    public CommentView create(String shortcode, long viewerId, String rawBody, Long parentId) {
        PostRow post = postService.getVisible(shortcode, viewerId);
        if (post.commentsDisabled()) {
            throw ApiException.forbidden("COMMENTS_DISABLED", "이 게시물은 댓글 기능이 꺼져 있습니다.");
        }
        String body = validBody(rawBody);
        Long parentAuthor = null;
        if (parentId != null) {
            List<Long> rows = jdbc.queryForList(
                    "select author_id from comments where id = :id and post_id = :post and parent_id is null",
                    Map.of("id", parentId, "post", post.id()), Long.class);
            if (rows.isEmpty()) {
                throw ApiException.badRequest("BAD_PARENT", "답글을 달 댓글을 찾을 수 없습니다.");
            }
            parentAuthor = rows.getFirst();
        }
        if (relations.blockedEitherWay(viewerId, post.author().id())) {
            throw ApiException.forbidden("BLOCKED", "이 게시물에는 댓글을 달 수 없습니다.");
        }
        boolean restricted = relations.restricts(post.author().id(), viewerId);
        Instant now = Instant.now(clock);
        Long id = jdbc.queryForObject("""
                insert into comments (post_id, author_id, parent_id, body, created_at, restricted)
                values (:post, :me, :parent, :body, :now, :restricted) returning id
                """, new MapSqlParameterSource("post", post.id()).addValue("me", viewerId)
                .addValue("parent", parentId).addValue("body", body).addValue("now", Timestamp.from(now))
                .addValue("restricted", restricted), Long.class);
        jdbc.update("update posts set comment_count = comment_count + 1 where id = :id", Map.of("id", post.id()));

        if (restricted) {
            return get(id, viewerId, post); // 제한된 댓글: 승인 전에는 아무에게도 알리지 않는다
        }
        Set<Long> notified = new HashSet<>();
        notified.add(viewerId);
        events.record(viewerId, "COMMENT", post.id(), post.author().id(), null);
        notifications.notify(post.author().id(), viewerId, NotificationType.COMMENT, post.id(), id, null, body);
        notified.add(post.author().id());
        if (parentAuthor != null && notified.add(parentAuthor)) {
            notifications.notify(parentAuthor, viewerId, NotificationType.REPLY, post.id(), id, null, body);
        }
        mentions.notifyAll(body, viewerId, post.id(), id, notified);
        return get(id, viewerId, post);
    }

    /** 내 댓글을 쓴 지 15분 안에만 고칠 수 있다 (Lab 시간 가속 적용) */
    @Transactional
    public CommentView edit(long commentId, long viewerId, String rawBody) {
        Map<String, Object> row = parentInfo(commentId);
        PostRow post = postService.getVisible((String) row.get("shortcode"), viewerId);
        if (((Number) row.get("author_id")).longValue() != viewerId) {
            throw ApiException.forbidden("NOT_ALLOWED", "내 댓글만 수정할 수 있습니다.");
        }
        Instant createdAt = ((Timestamp) row.get("created_at")).toInstant();
        Instant now = Instant.now(clock);
        if (Duration.between(createdAt, now).compareTo(PostQueries.EDIT_WINDOW) > 0) {
            throw ApiException.forbidden("EDIT_WINDOW_EXPIRED", "댓글은 작성 후 15분 안에만 수정할 수 있습니다.");
        }
        String body = validBody(rawBody);
        jdbc.update("update comments set body = :body, edited_at = :now where id = :id",
                new MapSqlParameterSource("body", body).addValue("now", Timestamp.from(now)).addValue("id", commentId));
        return get(commentId, viewerId, post);
    }

    /** 댓글 쓴 사람과 게시물 주인만 지울 수 있다. 답글도 함께 지워진다. */
    @Transactional
    public void delete(long commentId, long viewerId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select c.post_id, c.author_id, p.author_id as post_author,
                       (select count(*) from comments r where r.parent_id = c.id) as replies
                from comments c join posts p on p.id = c.post_id
                where c.id = :id and p.deleted_at is null and p.archived_at is null
                """, Map.of("id", commentId));
        if (rows.isEmpty()) {
            throw ApiException.notFound("댓글을 찾을 수 없습니다.");
        }
        Map<String, Object> row = rows.getFirst();
        long author = ((Number) row.get("author_id")).longValue();
        long postAuthor = ((Number) row.get("post_author")).longValue();
        if (viewerId != author && viewerId != postAuthor) {
            throw ApiException.forbidden("NOT_ALLOWED", "이 댓글을 삭제할 수 없습니다.");
        }
        long removed = 1 + ((Number) row.get("replies")).longValue();
        jdbc.update("delete from comments where id = :id", Map.of("id", commentId)); // 알림도 함께 지워짐 (FK)
        jdbc.update("update posts set comment_count = greatest(0, comment_count - :n) where id = :post",
                Map.of("n", removed, "post", row.get("post_id")));
    }

    /** 제한한 사람의 댓글을 게시물 주인이 승인하면 모두에게 보인다 */
    @Transactional
    public CommentView approve(long commentId, long viewerId) {
        Map<String, Object> row = parentInfo(commentId);
        PostRow post = postService.getVisible((String) row.get("shortcode"), viewerId);
        if (post.author().id() != viewerId) {
            throw ApiException.forbidden("NOT_ALLOWED", "게시물 주인만 승인할 수 있습니다.");
        }
        jdbc.update("update comments set restricted = false where id = :id", Map.of("id", commentId));
        return get(commentId, viewerId, post);
    }

    // ------------------------------------------------------------ 고정·좋아요

    /** 게시물 주인이 최상위 댓글을 최대 3개까지 맨 위에 고정 */
    @Transactional
    public CommentView pin(long commentId, long viewerId, boolean pinned) {
        Map<String, Object> row = parentInfo(commentId);
        PostRow post = postService.getVisible((String) row.get("shortcode"), viewerId);
        if (post.author().id() != viewerId) {
            throw ApiException.forbidden("NOT_ALLOWED", "게시물 주인만 댓글을 고정할 수 있습니다.");
        }
        if (row.get("parent_id") != null) {
            throw ApiException.badRequest("CANNOT_PIN_REPLY", "답글은 고정할 수 없습니다.");
        }
        if (pinned && row.get("pinned_at") == null) {
            Integer count = jdbc.queryForObject(
                    "select count(*) from comments where post_id = :post and pinned_at is not null",
                    Map.of("post", post.id()), Integer.class);
            if (count != null && count >= MAX_PINNED) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_PINNED",
                        "댓글은 " + MAX_PINNED + "개까지 고정할 수 있습니다.");
            }
            jdbc.update("update comments set pinned_at = :now where id = :id",
                    Map.of("now", Timestamp.from(Instant.now(clock)), "id", commentId));
        } else if (!pinned) {
            jdbc.update("update comments set pinned_at = null where id = :id", Map.of("id", commentId));
        }
        return get(commentId, viewerId, post);
    }

    @Transactional
    public LikeResult like(long commentId, long viewerId, boolean like) {
        Map<String, Object> row = parentInfo(commentId);
        PostRow post = postService.getVisible((String) row.get("shortcode"), viewerId);
        int changed;
        if (like) {
            changed = jdbc.update("""
                    insert into comment_likes (comment_id, user_id, created_at) values (:c, :me, :now)
                    on conflict do nothing
                    """, new MapSqlParameterSource("c", commentId).addValue("me", viewerId)
                    .addValue("now", Timestamp.from(Instant.now(clock))));
            if (changed == 1) {
                notifications.notify(((Number) row.get("author_id")).longValue(), viewerId,
                        NotificationType.COMMENT_LIKE, post.id(), commentId, null, (String) row.get("body"));
            }
        } else {
            changed = -jdbc.update("delete from comment_likes where comment_id = :c and user_id = :me",
                    Map.of("c", commentId, "me", viewerId));
            notifications.retract(viewerId, NotificationType.COMMENT_LIKE, null, commentId, null, null);
        }
        Integer count = jdbc.queryForObject(
                "update comments set like_count = greatest(0, like_count + :d) where id = :id returning like_count",
                Map.of("d", changed, "id", commentId), Integer.class);
        return new LikeResult(like, count == null ? 0 : count);
    }

    // ------------------------------------------------------------ 도우미

    private Map<String, Object> parentInfo(long commentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select c.author_id, c.parent_id, c.created_at, c.pinned_at, c.body, p.shortcode
                from comments c join posts p on p.id = c.post_id
                where c.id = :id and p.deleted_at is null and p.archived_at is null
                """, Map.of("id", commentId));
        if (rows.isEmpty()) {
            throw ApiException.notFound("댓글을 찾을 수 없습니다.");
        }
        return rows.getFirst();
    }

    private CommentView get(long id, long viewerId, PostRow post) {
        Instant now = Instant.now(clock);
        List<CommentView> list = jdbc.query(SELECT + " where c.id = :id",
                new MapSqlParameterSource("id", id).addValue("me", viewerId).addValue("owner", post.author().id()),
                (rs, i) -> PostQueries.comment(rs, viewerId, post.author().id(), now, 0));
        return withReplyCounts(list).getFirst();
    }

    private static String validBody(String rawBody) {
        String body = rawBody == null ? "" : rawBody.strip();
        if (body.isEmpty()) {
            throw ApiException.badRequest("EMPTY_COMMENT", "댓글 내용을 입력해 주세요.");
        }
        if (body.length() > MAX_LENGTH) {
            throw ApiException.badRequest("COMMENT_TOO_LONG", "댓글은 2,200자까지 쓸 수 있습니다.");
        }
        return body;
    }
}
