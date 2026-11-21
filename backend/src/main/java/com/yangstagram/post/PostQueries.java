package com.yangstagram.post;

import com.yangstagram.common.paging.Cursor;
import com.yangstagram.common.paging.Slice;
import com.yangstagram.follow.FollowService;
import com.yangstagram.follow.Relationship;
import com.yangstagram.post.dto.PostDtos.CommentView;
import com.yangstagram.post.dto.PostDtos.GridItem;
import com.yangstagram.post.dto.PostDtos.LocationView;
import com.yangstagram.post.dto.PostDtos.MediaView;
import com.yangstagram.post.dto.PostDtos.PostView;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 게시물 조회 전용. 목록 하나를 그릴 때 쿼리 수가 게시물 수와 상관없이 일정하도록
 * id 목록을 먼저 고르고, 미디어·좋아요·댓글 미리보기를 한 번씩 모아서 채운다.
 */
@Repository
public class PostQueries {

    private final NamedParameterJdbcTemplate jdbc;
    private final FollowService followService;

    public PostQueries(NamedParameterJdbcTemplate jdbc, FollowService followService) {
        this.jdbc = jdbc;
        this.followService = followService;
    }

    /** 권한 판정·수정에 필요한 게시물 기본 정보 */
    public record PostRow(long id, String shortcode, UserSummary author, String caption, String location,
                          int likeCount, int commentCount, boolean hideLikeCount, boolean commentsDisabled,
                          Instant createdAt) {
    }

    private static final String ROW_SELECT = """
            select p.id, p.shortcode, p.caption, p.like_count, p.comment_count, p.hide_like_count,
                   p.comments_disabled, p.created_at, l.id as location_id, l.name as location_name, %s
            from posts p
            join users a on a.id = p.author_id
            left join locations l on l.id = p.location_id
            """.formatted(UserRows.cols("a"));

    public Optional<PostRow> findByShortcode(String shortcode) {
        return jdbc.query(ROW_SELECT + " where p.shortcode = :code and p.deleted_at is null and a.deleted_at is null",
                Map.of("code", shortcode), (rs, i) -> new PostRow(
                        rs.getLong("id"), rs.getString("shortcode"), UserRows.read(rs, "a"),
                        rs.getString("caption"), rs.getString("location_name"),
                        rs.getInt("like_count"), rs.getInt("comment_count"), rs.getBoolean("hide_like_count"),
                        rs.getBoolean("comments_disabled"), rs.getTimestamp("created_at").toInstant()))
                .stream().findFirst();
    }

    // ------------------------------------------------------------ 목록

    /** 홈 피드: 내가 팔로우(승인됨)하는 사람과 내 게시물, 최신순 */
    public Slice<PostView> feed(long viewerId, Cursor cursor, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("me", viewerId).addValue("limit", limit + 1);
        String after = applyCursor(cursor, p);
        List<long[]> rows = jdbc.query("""
                select p.id from posts p join users a on a.id = p.author_id
                where p.deleted_at is null and a.deleted_at is null
                  and (p.author_id = :me or p.author_id in
                       (select following_id from follows where follower_id = :me and status = 'ACCEPTED'))
                  %s
                order by p.created_at desc, p.id desc
                limit :limit
                """.formatted(after), p, (rs, i) -> new long[] {rs.getLong(1)});
        List<Long> ids = rows.stream().map(r -> r[0]).toList();
        return sliceOf(views(ids, viewerId), limit);
    }

    /** 프로필 그리드: 최신순. 권한 확인은 호출하는 쪽에서 한다. */
    public Slice<GridItem> grid(long authorId, long viewerId, Cursor cursor, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("author", authorId).addValue("limit", limit + 1);
        String after = applyCursor(cursor, p);
        List<Object[]> rows = new ArrayList<>();
        List<GridItem> items = jdbc.query("""
                select p.id, p.shortcode, p.like_count, p.comment_count, p.hide_like_count, p.created_at,
                       m.thumb_url, (select count(*) from post_media x where x.post_id = p.id) as media_count
                from posts p
                join post_media m on m.post_id = p.id and m.position = 0
                where p.author_id = :author and p.deleted_at is null %s
                order by p.created_at desc, p.id desc
                limit :limit
                """.formatted(after), p, (rs, i) -> {
            rows.add(new Object[] {rs.getTimestamp("created_at").toInstant(), rs.getLong("id")});
            boolean hidden = rs.getBoolean("hide_like_count") && authorId != viewerId;
            return new GridItem(rs.getString("shortcode"), rs.getString("thumb_url"), rs.getInt("media_count"),
                    hidden ? null : rs.getInt("like_count"), rs.getInt("comment_count"));
        });
        if (items.size() <= limit) {
            return new Slice<>(items, null);
        }
        Object[] last = rows.get(limit - 1);
        return new Slice<>(items.subList(0, limit), new Cursor((Instant) last[0], (Long) last[1]).encode());
    }

    private static String applyCursor(Cursor cursor, MapSqlParameterSource p) {
        if (cursor == null) return "";
        p.addValue("cts", Timestamp.from(cursor.createdAt())).addValue("cid", cursor.id());
        return "and (p.created_at, p.id) < (:cts, :cid)";
    }

    private static Slice<PostView> sliceOf(List<PostView> views, int limit) {
        if (views.size() <= limit) {
            return new Slice<>(views, null);
        }
        PostView last = views.get(limit - 1);
        return new Slice<>(views.subList(0, limit), new Cursor(last.createdAt(), last.id()).encode());
    }

    // ------------------------------------------------------------ 채우기

    /** id 순서를 유지한 채 화면용 게시물로 만든다. 지워진 게시물은 빠진다. */
    public List<PostView> views(List<Long> ids, long viewerId) {
        if (ids.isEmpty()) return List.of();
        MapSqlParameterSource p = new MapSqlParameterSource("ids", ids).addValue("me", viewerId);

        Map<Long, PostRow> rows = new LinkedHashMap<>();
        Map<Long, LocationView> locations = new HashMap<>();
        jdbc.query(ROW_SELECT + " where p.id in (:ids) and p.deleted_at is null", p, rs -> {
            long id = rs.getLong("id");
            rows.put(id, new PostRow(id, rs.getString("shortcode"), UserRows.read(rs, "a"), rs.getString("caption"),
                    rs.getString("location_name"), rs.getInt("like_count"), rs.getInt("comment_count"),
                    rs.getBoolean("hide_like_count"), rs.getBoolean("comments_disabled"),
                    rs.getTimestamp("created_at").toInstant()));
            long locationId = rs.getLong("location_id");
            if (!rs.wasNull()) {
                locations.put(id, new LocationView(locationId, rs.getString("location_name")));
            }
        });

        Map<Long, List<MediaView>> media = new HashMap<>();
        jdbc.query("""
                select post_id, url, thumb_url, width, height, alt_text from post_media
                where post_id in (:ids) order by post_id, position
                """, p, rs -> {
            media.computeIfAbsent(rs.getLong("post_id"), k -> new ArrayList<>()).add(new MediaView(
                    rs.getString("url"), rs.getString("thumb_url"), rs.getInt("width"), rs.getInt("height"),
                    rs.getString("alt_text")));
        });

        Set<Long> liked = new HashSet<>(jdbc.queryForList(
                "select post_id from likes where user_id = :me and post_id in (:ids)", p, Long.class));

        Map<Long, UserSummary> likedBy = new HashMap<>();
        jdbc.query("""
                select distinct on (l.post_id) l.post_id, %s
                from likes l
                join follows f on f.following_id = l.user_id and f.follower_id = :me and f.status = 'ACCEPTED'
                join users u on u.id = l.user_id
                where l.post_id in (:ids)
                order by l.post_id, u.followers_count desc, l.created_at desc
                """.formatted(UserRows.cols("u")), p, rs -> {
            likedBy.put(rs.getLong("post_id"), UserRows.read(rs, "u"));
        });

        Map<Long, List<CommentView>> previews = new HashMap<>();
        Map<Long, Long> postAuthor = new HashMap<>();
        rows.values().forEach(r -> postAuthor.put(r.id(), r.author().id()));
        jdbc.query("""
                select * from (
                  select c.id, c.post_id, c.body, c.created_at, c.parent_id, %s,
                         row_number() over (partition by c.post_id order by c.created_at desc, c.id desc) as rn
                  from comments c join users u on u.id = c.author_id
                  where c.post_id in (:ids) and c.parent_id is null and u.deleted_at is null
                ) t where rn <= 2
                order by post_id, created_at, id
                """.formatted(UserRows.cols("u")), p, rs -> {
            long postId = rs.getLong("post_id");
            UserSummary author = UserRows.read(rs, "u");
            boolean canDelete = author.id() == viewerId || postAuthor.getOrDefault(postId, -1L) == viewerId;
            previews.computeIfAbsent(postId, k -> new ArrayList<>()).add(new CommentView(
                    rs.getLong("id"), author, rs.getString("body"), rs.getTimestamp("created_at").toInstant(),
                    null, canDelete));
        });

        Map<Long, Relationship> rel = followService.relationships(viewerId, authorIds(rows.values()));

        List<PostView> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            PostRow r = rows.get(id);
            if (r == null) continue;
            boolean mine = r.author().id() == viewerId;
            result.add(new PostView(r.id(), r.shortcode(), r.author(), rel.get(r.author().id()), r.caption(),
                    locations.get(id), media.getOrDefault(id, List.of()),
                    r.hideLikeCount() && !mine ? null : r.likeCount(), r.commentCount(), liked.contains(id),
                    likedBy.get(id), r.hideLikeCount(), r.commentsDisabled(), r.createdAt(), mine,
                    previews.getOrDefault(id, List.of())));
        }
        return result;
    }

    private static Collection<Long> authorIds(Collection<PostRow> rows) {
        Set<Long> ids = new HashSet<>();
        rows.forEach(r -> ids.add(r.author().id()));
        return ids;
    }
}
