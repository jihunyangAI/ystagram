package com.yangstagram.comment;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.paging.Slice;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.post.PostQueries.PostRow;
import com.yangstagram.post.PostService;
import com.yangstagram.post.dto.PostDtos.CommentView;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 댓글 쓰기·지우기·목록. 답글 화면과 15분 수정은 M2. */
@Service
public class CommentService {

    public static final int MAX_LENGTH = 2200;
    private static final int PAGE = 30;

    private final NamedParameterJdbcTemplate jdbc;
    private final PostService postService;
    private final LabClock clock;

    public CommentService(NamedParameterJdbcTemplate jdbc, PostService postService, LabClock clock) {
        this.jdbc = jdbc;
        this.postService = postService;
        this.clock = clock;
    }

    /** 오래된 순. 커서는 마지막으로 받은 댓글 id */
    public Slice<CommentView> list(String shortcode, long viewerId, Long afterId) {
        PostRow post = postService.getVisible(shortcode, viewerId);
        List<CommentView> items = jdbc.query("""
                select c.id, c.body, c.created_at, c.parent_id, c.author_id, %s
                from comments c join users u on u.id = c.author_id
                where c.post_id = :post and u.deleted_at is null and c.id > :after
                order by c.id
                limit :limit
                """.formatted(UserRows.cols("u")),
                new MapSqlParameterSource("post", post.id()).addValue("after", afterId == null ? 0 : afterId)
                        .addValue("limit", PAGE + 1),
                (rs, i) -> toView(rs.getLong("id"), UserRows.read(rs, "u"), rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant(), (Long) rs.getObject("parent_id"),
                        viewerId, post));
        if (items.size() <= PAGE) {
            return new Slice<>(items, null);
        }
        List<CommentView> page = items.subList(0, PAGE);
        return new Slice<>(page, String.valueOf(page.getLast().id()));
    }

    @Transactional
    public CommentView create(String shortcode, long viewerId, String rawBody, Long parentId) {
        PostRow post = postService.getVisible(shortcode, viewerId);
        if (post.commentsDisabled()) {
            throw ApiException.forbidden("COMMENTS_DISABLED", "이 게시물은 댓글 기능이 꺼져 있습니다.");
        }
        String body = rawBody == null ? "" : rawBody.strip();
        if (body.isEmpty()) {
            throw ApiException.badRequest("EMPTY_COMMENT", "댓글 내용을 입력해 주세요.");
        }
        if (body.length() > MAX_LENGTH) {
            throw ApiException.badRequest("COMMENT_TOO_LONG", "댓글은 2,200자까지 쓸 수 있습니다.");
        }
        if (parentId != null) {
            Integer same = jdbc.queryForObject(
                    "select count(*) from comments where id = :id and post_id = :post and parent_id is null",
                    Map.of("id", parentId, "post", post.id()), Integer.class);
            if (same == null || same == 0) {
                throw ApiException.badRequest("BAD_PARENT", "답글을 달 댓글을 찾을 수 없습니다.");
            }
        }
        Instant now = Instant.now(clock);
        Long id = jdbc.queryForObject("""
                insert into comments (post_id, author_id, parent_id, body, created_at)
                values (:post, :me, :parent, :body, :now) returning id
                """, new MapSqlParameterSource("post", post.id()).addValue("me", viewerId)
                .addValue("parent", parentId).addValue("body", body).addValue("now", Timestamp.from(now)), Long.class);
        jdbc.update("update posts set comment_count = comment_count + 1 where id = :id", Map.of("id", post.id()));

        UserSummary me = jdbc.queryForObject("select %s from users u where u.id = :me".formatted(UserRows.cols("u")),
                Map.of("me", viewerId), (rs, i) -> UserRows.read(rs, "u"));
        return toView(id, me, body, now, parentId, viewerId, post);
    }

    /** 댓글 쓴 사람과 게시물 주인만 지울 수 있다. 답글도 함께 지워진다. */
    @Transactional
    public void delete(long commentId, long viewerId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select c.post_id, c.author_id, p.author_id as post_author,
                       (select count(*) from comments r where r.parent_id = c.id) as replies
                from comments c join posts p on p.id = c.post_id
                where c.id = :id and p.deleted_at is null
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
        jdbc.update("delete from comments where id = :id", Map.of("id", commentId));
        jdbc.update("update posts set comment_count = greatest(0, comment_count - :n) where id = :post",
                Map.of("n", removed, "post", row.get("post_id")));
    }

    private static CommentView toView(long id, UserSummary author, String body, Instant createdAt, Long parentId,
                                      long viewerId, PostRow post) {
        boolean canDelete = author.id() == viewerId || post.author().id() == viewerId;
        return new CommentView(id, author, body, createdAt, parentId, canDelete);
    }
}
