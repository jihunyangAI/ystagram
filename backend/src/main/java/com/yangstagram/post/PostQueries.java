package com.yangstagram.post;

import com.yangstagram.common.paging.Cursor;
import com.yangstagram.common.paging.Slice;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.lab.FeatureFlags;
import com.yangstagram.follow.FollowService;
import com.yangstagram.follow.Relationship;
import com.yangstagram.post.dto.PostDtos.CommentView;
import com.yangstagram.post.dto.PostDtos.GridItem;
import com.yangstagram.post.dto.PostDtos.LocationView;
import com.yangstagram.post.dto.PostDtos.MediaView;
import com.yangstagram.post.dto.PostDtos.PostView;
import com.yangstagram.post.dto.PostDtos.Repost;
import com.yangstagram.post.dto.PostDtos.TagView;
import com.yangstagram.relation.RelationSql;
import com.yangstagram.story.StoryRings;
import com.yangstagram.story.StoryRings.Ring;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Duration;
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

    /** 댓글·DM 수정 가능 시간 (기획서 §6.2) */
    public static final Duration EDIT_WINDOW = Duration.ofMinutes(15);

    /**
     * 보는 사람(:me)이 작성자(a)의 게시물을 볼 수 있는가 (§6.1의 SQL판).
     * 목록 쿼리에서 users a를 조인하고 이 조건을 붙인다.
     */
    public static final String VISIBLE = """
            (a.id = :me or not a.is_private or exists (
             select 1 from follows vf where vf.follower_id = :me and vf.following_id = a.id
             and vf.status = 'ACCEPTED'))
            and (not p.trial or a.id = :me or not exists (
             select 1 from follows tf where tf.follower_id = :me and tf.following_id = a.id and tf.status = 'ACCEPTED'))
            and (a.id = :me or a.suspended_until is null or a.suspended_until <= current_timestamp)
            and """ + RelationSql.notBlocked("a.id");

    /** 정지된 계정(신고 처리)의 콘텐츠는 다른 사람에게 안 보인다 */
    public static final String NOT_SUSPENDED = " (a.suspended_until is null or a.suspended_until <= current_timestamp) ";

    /** 시험 릴스는 작성자 본인 말고는 프로필·팔로잉 피드에 나오지 않는다 */
    public static final String NOT_TRIAL = " (not p.trial or p.author_id = :me) ";

    private final NamedParameterJdbcTemplate jdbc;
    private final FollowService followService;
    private final StoryRings storyRings;
    private final LabClock clock;
    private final FeatureFlags flags;

    public PostQueries(NamedParameterJdbcTemplate jdbc, FollowService followService, StoryRings storyRings,
                       LabClock clock, FeatureFlags flags) {
        this.jdbc = jdbc;
        this.followService = followService;
        this.storyRings = storyRings;
        this.clock = clock;
        this.flags = flags;
    }

    /** 권한 판정·수정에 필요한 게시물 기본 정보 */
    public record PostRow(long id, String shortcode, UserSummary author, String caption, String location,
                          int likeCount, int commentCount, boolean hideLikeCount, boolean commentsDisabled,
                          Instant createdAt, int repostCount, String type, int viewCount, boolean aiLabeled,
                          boolean trial) {
    }

    private static final String ROW_SELECT = """
            select p.id, p.shortcode, p.caption, p.like_count, p.comment_count, p.hide_like_count,
                   p.comments_disabled, p.created_at, p.repost_count, p.type, p.view_count, p.ai_labeled, p.trial,
                   l.id as location_id, l.name as location_name, %s
            from posts p
            join users a on a.id = p.author_id
            left join locations l on l.id = p.location_id
            """.formatted(UserRows.cols("a"));

    private static PostRow readRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PostRow(rs.getLong("id"), rs.getString("shortcode"), UserRows.read(rs, "a"),
                rs.getString("caption"), rs.getString("location_name"),
                rs.getInt("like_count"), rs.getInt("comment_count"), rs.getBoolean("hide_like_count"),
                rs.getBoolean("comments_disabled"), rs.getTimestamp("created_at").toInstant(),
                rs.getInt("repost_count"), rs.getString("type"), rs.getInt("view_count"),
                rs.getBoolean("ai_labeled"), rs.getBoolean("trial"));
    }

    public Optional<PostRow> findByShortcode(String shortcode) {
        return jdbc.query(ROW_SELECT + " where p.shortcode = :code and p.deleted_at is null and a.deleted_at is null",
                Map.of("code", shortcode), (rs, i) -> readRow(rs)).stream().findFirst();
    }

    // ------------------------------------------------------------ 홈 피드

    private record FeedRow(long postId, Instant ts, Long reposterId) {
    }

    /**
     * 홈 피드: 내가 팔로우(승인됨)하는 사람과 내 게시물 + 팔로우하는 사람이 리포스트한 다른 사람의 공개 게시물.
     * 같은 게시물은 한 번만 (가장 최근 리포스트 기준). 정렬 기준은 올린 시각 또는 리포스트한 시각.
     */
    public Slice<PostView> feed(long viewerId, Cursor cursor, int limit) {
        return feed(viewerId, cursor, limit, false);
    }

    /**
     * @param favoritesOnly 홈의 "즐겨찾기" 보기: 즐겨찾기한 사람의 글만 (리포스트·내 글 제외)
     */
    public Slice<PostView> feed(long viewerId, Cursor cursor, int limit, boolean favoritesOnly) {
        MapSqlParameterSource p = new MapSqlParameterSource("me", viewerId).addValue("limit", limit + 1)
                .addValue("fav", favoritesOnly);
        String after = "";
        if (cursor != null) {
            p.addValue("cts", Timestamp.from(cursor.createdAt())).addValue("cid", cursor.id());
            after = "where (ts, post_id) < (:cts, :cid)";
        }
        List<FeedRow> rows = jdbc.query("""
                with followees as (
                  select following_id as id from follows
                  where follower_id = :me and status = 'ACCEPTED' and (not :fav or is_favorite)
                ), items as (
                  select p.id as post_id, p.created_at as ts, null::bigint as reposter
                  from posts p join users a on a.id = p.author_id
                  where p.deleted_at is null and a.deleted_at is null
                    and ((p.author_id = :me and not :fav) or p.author_id in (select id from followees)
                         or exists (select 1 from post_collaborators pc where pc.post_id = p.id and pc.status = 'ACCEPTED'
                                    and (pc.user_id in (select id from followees) or (pc.user_id = :me and not :fav))))
                    and %s and (not p.trial or p.author_id = :me)
                    and (a.id = :me or a.suspended_until is null or a.suspended_until <= current_timestamp)
                  union all
                  select r.post_id, r.created_at, r.user_id
                  from reposts r
                  join posts p on p.id = r.post_id
                  join users a on a.id = p.author_id
                  where not :fav and r.user_id in (select id from followees)
                    and p.deleted_at is null and a.deleted_at is null and not a.is_private and not p.trial
                    and (a.suspended_until is null or a.suspended_until <= current_timestamp)
                    and p.author_id <> :me and p.author_id not in (select id from followees)
                    and %s and %s and %s
                ), dedup as (
                  select distinct on (post_id) post_id, ts, reposter from items order by post_id, ts desc
                )
                select post_id, ts, reposter from dedup %s
                order by ts desc, post_id desc
                limit :limit
                """.formatted(RelationSql.postsNotMuted("p.author_id"), RelationSql.notBlocked("a.id"),
                        RelationSql.postsNotMuted("r.user_id"), RelationSql.postsNotMuted("p.author_id"), after), p,
                (rs, i) -> new FeedRow(rs.getLong(1), rs.getTimestamp(2).toInstant(), (Long) rs.getObject(3)));

        Map<Long, Repost> reposts = repostInfo(rows);
        List<PostView> views = views(rows.stream().map(FeedRow::postId).toList(), viewerId, reposts);
        if (rows.size() <= limit) {
            return new Slice<>(views, null);
        }
        FeedRow last = rows.get(limit - 1);
        return new Slice<>(views.subList(0, Math.min(limit, views.size())),
                new Cursor(last.ts(), last.postId()).encode());
    }

    /**
     * 순위 피드용: 주어진 게시물 중 "팔로우하는 사람이 리포스트해서" 들어온 것(작성자를 팔로우하지 않음)의 리포스트 정보.
     */
    public Map<Long, Repost> followeeReposts(long viewerId, List<Long> postIds) {
        if (postIds.isEmpty()) return Map.of();
        List<FeedRow> rows = jdbc.query("""
                select distinct on (r.post_id) r.post_id, r.created_at, r.user_id
                from reposts r join posts p on p.id = r.post_id
                where r.post_id in (:ids) and p.author_id <> :me
                  and r.user_id in (select following_id from follows where follower_id = :me and status = 'ACCEPTED')
                  and p.author_id not in (select following_id from follows where follower_id = :me and status = 'ACCEPTED')
                order by r.post_id, r.created_at desc
                """, new MapSqlParameterSource("ids", postIds).addValue("me", viewerId),
                (rs, i) -> new FeedRow(rs.getLong(1), rs.getTimestamp(2).toInstant(), rs.getLong(3)));
        return repostInfo(rows);
    }

    private Map<Long, Repost> repostInfo(List<FeedRow> rows) {
        Map<Long, Repost> result = new HashMap<>();
        List<FeedRow> reposted = rows.stream().filter(r -> r.reposterId() != null).toList();
        if (reposted.isEmpty()) return result;
        Map<Long, UserSummary> users = new HashMap<>();
        jdbc.query("select %s from users u where u.id in (:ids)".formatted(UserRows.cols("u")),
                Map.of("ids", reposted.stream().map(FeedRow::reposterId).toList()),
                rs -> {
                    UserSummary u = UserRows.read(rs, "u");
                    users.put(u.id(), u);
                });
        Map<String, String> notes = new HashMap<>();
        jdbc.query("select user_id, post_id, note from reposts where post_id in (:posts)",
                Map.of("posts", reposted.stream().map(FeedRow::postId).toList()), rs -> {
                    notes.put(rs.getLong(1) + ":" + rs.getLong(2), rs.getString(3));
                });
        for (FeedRow r : reposted) {
            String note = notes.get(r.reposterId() + ":" + r.postId());
            result.put(r.postId(), new Repost(users.get(r.reposterId()), note, r.ts()));
        }
        return result;
    }

    // ------------------------------------------------------------ 그리드

    /** 프로필 그리드: 최신순. 권한 확인은 호출하는 쪽에서 한다. */
    public Slice<GridItem> grid(long authorId, long viewerId, Cursor cursor, int limit) {
        return gridOf("posts p join users a on a.id = p.author_id where (p.author_id = :author or exists ("
                        + "select 1 from post_collaborators pc where pc.post_id = p.id and pc.user_id = :author "
                        + "and pc.status = 'ACCEPTED')) and" + NOT_TRIAL,
                "p.created_at", new MapSqlParameterSource("author", authorId), viewerId, cursor, limit);
    }

    /** 프로필 "릴스" 탭 */
    public Slice<GridItem> reelGrid(long authorId, long viewerId, Cursor cursor, int limit) {
        return gridOf("posts p join users a on a.id = p.author_id where p.author_id = :author and p.type = 'REEL' and"
                        + NOT_TRIAL,
                "p.created_at", new MapSqlParameterSource("author", authorId), viewerId, cursor, limit);
    }

    /** 프로필 "태그됨" 탭: 이 사람이 태그된 게시물 (볼 수 있는 것만) */
    public Slice<GridItem> taggedGrid(long userId, long viewerId, Cursor cursor, int limit) {
        return gridOf("post_tags pt join posts p on p.id = pt.post_id join users a on a.id = p.author_id "
                        + "where pt.user_id = :uid and " + VISIBLE,
                "p.created_at", new MapSqlParameterSource("uid", userId), viewerId, cursor, limit);
    }

    /** 이 사람이 리포스트한 게시물 (리포스트한 순) */
    public Slice<GridItem> repostGrid(long userId, long viewerId, Cursor cursor, int limit) {
        return gridOf("reposts r join posts p on p.id = r.post_id join users a on a.id = p.author_id "
                        + "where r.user_id = :uid and " + VISIBLE,
                "r.created_at", new MapSqlParameterSource("uid", userId), viewerId, cursor, limit);
    }

    /** 내가 저장한 게시물 (저장한 순), collectionId가 있으면 그 컬렉션만 */
    public Slice<GridItem> savedGrid(long viewerId, Long collectionId, Cursor cursor, int limit) {
        if (collectionId == null) {
            return gridOf("saved_posts sp join posts p on p.id = sp.post_id join users a on a.id = p.author_id "
                            + "where sp.user_id = :me and " + VISIBLE,
                    "sp.created_at", new MapSqlParameterSource(), viewerId, cursor, limit);
        }
        return gridOf("collection_posts cp join posts p on p.id = cp.post_id join users a on a.id = p.author_id "
                        + "where cp.collection_id = :cid and " + VISIBLE,
                "cp.created_at", new MapSqlParameterSource("cid", collectionId), viewerId, cursor, limit);
    }

    /** 해시태그·장소 페이지의 최근 게시물 */
    public Slice<GridItem> tagGrid(long tagId, long viewerId, Cursor cursor, int limit) {
        return gridOf("post_hashtags ph join posts p on p.id = ph.post_id join users a on a.id = p.author_id "
                        + "where ph.hashtag_id = :tag and " + VISIBLE,
                "p.created_at", new MapSqlParameterSource("tag", tagId), viewerId, cursor, limit);
    }

    public Slice<GridItem> locationGrid(long locationId, long viewerId, Cursor cursor, int limit) {
        return gridOf("posts p join users a on a.id = p.author_id where p.location_id = :loc and " + VISIBLE,
                "p.created_at", new MapSqlParameterSource("loc", locationId), viewerId, cursor, limit);
    }

    /** 해시태그·장소 페이지 위쪽 "인기 게시물": 좋아요 많은 순 */
    public List<GridItem> topPosts(String fromWhere, MapSqlParameterSource p, long viewerId, int limit) {
        p.addValue("me", viewerId).addValue("limit", limit);
        return jdbc.query("""
                select p.id, p.shortcode, p.like_count, p.comment_count, p.hide_like_count, p.author_id,
                       p.type, p.view_count,
                       m.thumb_url, (select count(*) from post_media x where x.post_id = p.id) as media_count
                from %s and p.deleted_at is null and a.deleted_at is null
                order by p.like_count desc, p.id desc
                limit :limit
                """.formatted(joinMedia(fromWhere)), p, (rs, i) -> gridItem(rs, viewerId));
    }

    /**
     * 그리드 공통. fromWhere는 "from"을 뺀 "테이블들 where 조건" (posts p, users a 별칭 필수),
     * ts는 정렬·커서 기준 시각 컬럼.
     */
    private Slice<GridItem> gridOf(String fromWhere, String ts, MapSqlParameterSource p, long viewerId,
                                   Cursor cursor, int limit) {
        p.addValue("me", viewerId).addValue("limit", limit + 1);
        String after = "";
        if (cursor != null) {
            p.addValue("cts", Timestamp.from(cursor.createdAt())).addValue("cid", cursor.id());
            after = "and (%s, p.id) < (:cts, :cid)".formatted(ts);
        }
        List<Object[]> keys = new ArrayList<>();
        List<GridItem> items = jdbc.query("""
                select p.id, p.shortcode, p.like_count, p.comment_count, p.hide_like_count, p.author_id,
                       p.type, p.view_count, %s as ts, m.thumb_url,
                       (select count(*) from post_media x where x.post_id = p.id) as media_count
                from %s and p.deleted_at is null and a.deleted_at is null %s
                order by %s desc, p.id desc
                limit :limit
                """.formatted(ts, joinMedia(fromWhere), after, ts), p, (rs, i) -> {
            keys.add(new Object[] {rs.getTimestamp("ts").toInstant(), rs.getLong("id")});
            return gridItem(rs, viewerId);
        });
        if (items.size() <= limit) {
            return new Slice<>(items, null);
        }
        Object[] last = keys.get(limit - 1);
        return new Slice<>(items.subList(0, limit), new Cursor((Instant) last[0], (Long) last[1]).encode());
    }

    /** "... where 조건" 앞에 첫 번째 사진 조인을 끼워 넣는다 */
    private static String joinMedia(String fromWhere) {
        int at = fromWhere.indexOf(" where ");
        return fromWhere.substring(0, at) + " join post_media m on m.post_id = p.id and m.position = 0"
                + fromWhere.substring(at);
    }

    public static GridItem gridItem(java.sql.ResultSet rs, long viewerId) throws java.sql.SQLException {
        boolean hidden = rs.getBoolean("hide_like_count") && rs.getLong("author_id") != viewerId;
        return new GridItem(rs.getString("shortcode"), rs.getString("thumb_url"), rs.getInt("media_count"),
                hidden ? null : rs.getInt("like_count"), rs.getInt("comment_count"), rs.getString("type"),
                rs.getInt("view_count"), null);
    }

    // ------------------------------------------------------------ 채우기

    public List<PostView> views(List<Long> ids, long viewerId) {
        return views(ids, viewerId, Map.of());
    }

    /** id 순서를 유지한 채 화면용 게시물로 만든다. 지워진 게시물은 빠진다. */
    public List<PostView> views(List<Long> ids, long viewerId, Map<Long, Repost> reposts) {
        if (ids.isEmpty()) return List.of();
        Instant now = Instant.now(clock);
        boolean hideAll = flags.get().hideLikeCounts();
        MapSqlParameterSource p = new MapSqlParameterSource("ids", ids).addValue("me", viewerId);

        Map<Long, PostRow> rows = new LinkedHashMap<>();
        Map<Long, LocationView> locations = new HashMap<>();
        jdbc.query(ROW_SELECT + " where p.id in (:ids) and p.deleted_at is null", p, rs -> {
            PostRow row = readRow(rs);
            rows.put(row.id(), row);
            long locationId = rs.getLong("location_id");
            if (!rs.wasNull()) {
                locations.put(row.id(), new LocationView(locationId, rs.getString("location_name")));
            }
        });

        Map<Long, List<MediaView>> media = new HashMap<>();
        jdbc.query("""
                select post_id, url, thumb_url, width, height, alt_text, media_type, duration_ms from post_media
                where post_id in (:ids) order by post_id, position
                """, p, rs -> {
            media.computeIfAbsent(rs.getLong("post_id"), k -> new ArrayList<>()).add(new MediaView(
                    rs.getString("url"), rs.getString("thumb_url"), rs.getInt("width"), rs.getInt("height"),
                    rs.getString("alt_text"), rs.getString("media_type"), (Integer) rs.getObject("duration_ms")));
        });

        Map<Long, List<TagView>> tags = new HashMap<>();
        jdbc.query("""
                select pt.post_id, pt.media_index, pt.x, pt.y, %s from post_tags pt join users u on u.id = pt.user_id
                where pt.post_id in (:ids) and u.deleted_at is null and %s order by u.username
                """.formatted(UserRows.cols("u"), RelationSql.notBlocked("u.id")), p, rs -> {
            tags.computeIfAbsent(rs.getLong("post_id"), k -> new ArrayList<>()).add(new TagView(UserRows.read(rs, "u"),
                    rs.getInt("media_index"), rs.getDouble("x"), rs.getDouble("y")));
        });
        Map<Long, List<UserSummary>> collaborators = new HashMap<>();
        Set<Long> invited = new HashSet<>();
        jdbc.query("""
                select pc.post_id, pc.status, %s from post_collaborators pc join users u on u.id = pc.user_id
                where pc.post_id in (:ids) and u.deleted_at is null order by pc.created_at
                """.formatted(UserRows.cols("u")), p, rs -> {
            UserSummary u = UserRows.read(rs, "u");
            if ("ACCEPTED".equals(rs.getString("status"))) {
                collaborators.computeIfAbsent(rs.getLong("post_id"), k -> new ArrayList<>()).add(u);
            } else if (u.id() == viewerId) {
                invited.add(rs.getLong("post_id"));
            }
        });

        Set<Long> liked = idSet("select post_id from likes where user_id = :me and post_id in (:ids)", p);
        Set<Long> saved = idSet("select post_id from saved_posts where user_id = :me and post_id in (:ids)", p);
        Set<Long> reposted = idSet("select post_id from reposts where user_id = :me and post_id in (:ids)", p);

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
                  select c.id, c.post_id, c.body, c.created_at, c.edited_at, c.pinned_at, c.like_count,
                         exists (select 1 from comment_likes cl where cl.comment_id = c.id and cl.user_id = :me)
                           as liked_by_me,
                         %s,
                         row_number() over (partition by c.post_id order by c.created_at desc, c.id desc) as rn
                  from comments c join users u on u.id = c.author_id
                  where c.post_id in (:ids) and c.parent_id is null and u.deleted_at is null
                    and (not c.restricted or c.author_id = :me) and %s
                ) t where rn <= 2
                order by post_id, created_at, id
                """.formatted(UserRows.cols("u"), RelationSql.notBlocked("u.id")), p, rs -> {
            long postId = rs.getLong("post_id");
            previews.computeIfAbsent(postId, k -> new ArrayList<>()).add(comment(rs, viewerId,
                    postAuthor.getOrDefault(postId, -1L), now, 0));
        });

        Map<Long, Relationship> rel = followService.relationships(viewerId, authorIds(rows.values()));
        Map<Long, Ring> rings = storyRings.rings(viewerId, authorIds(rows.values()));

        List<PostView> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            PostRow r = rows.get(id);
            if (r == null) continue;
            boolean mine = r.author().id() == viewerId;
            result.add(new PostView(r.id(), r.shortcode(), r.author(), rel.get(r.author().id()), r.caption(),
                    locations.get(id), media.getOrDefault(id, List.of()),
                    (r.hideLikeCount() || hideAll) && !mine ? null : r.likeCount(), r.commentCount(), liked.contains(id),
                    likedBy.get(id), r.hideLikeCount(), r.commentsDisabled(), r.createdAt(), mine,
                    previews.getOrDefault(id, List.of()), saved.contains(id), reposted.contains(id),
                    r.repostCount(), reposts.get(id), rings.get(r.author().id()), r.type(), r.viewCount(),
                    tags.getOrDefault(id, List.of()), collaborators.getOrDefault(id, List.of()), invited.contains(id),
                    r.aiLabeled(), r.trial() && mine, null, null));
        }
        return result;
    }

    /**
     * 댓글 한 줄을 화면용으로. 쿼리에는 c.id, c.body, c.created_at, c.edited_at, c.pinned_at, c.like_count,
     * liked_by_me, 작성자 컬럼(u), (답글이면) parent_id가 있어야 한다.
     */
    public static CommentView comment(java.sql.ResultSet rs, long viewerId, long postAuthorId, Instant now,
                                      int replyCount) throws java.sql.SQLException {
        UserSummary author = UserRows.read(rs, "u");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Long parentId = hasColumn(rs, "parent_id") ? (Long) rs.getObject("parent_id") : null;
        boolean mine = author.id() == viewerId;
        return new CommentView(rs.getLong("id"), author, rs.getString("body"), createdAt, parentId,
                mine || postAuthorId == viewerId,
                mine && Duration.between(createdAt, now).compareTo(EDIT_WINDOW) <= 0,
                postAuthorId == viewerId && parentId == null,
                rs.getTimestamp("edited_at") != null, rs.getTimestamp("pinned_at") != null,
                rs.getInt("like_count"), rs.getBoolean("liked_by_me"), replyCount,
                hasColumn(rs, "restricted") && rs.getBoolean("restricted"));
    }

    private static boolean hasColumn(java.sql.ResultSet rs, String name) throws java.sql.SQLException {
        var meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (meta.getColumnLabel(i).equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private Set<Long> idSet(String sql, MapSqlParameterSource p) {
        return new HashSet<>(jdbc.queryForList(sql, p, Long.class));
    }

    private static Collection<Long> authorIds(Collection<PostRow> rows) {
        Set<Long> ids = new HashSet<>();
        rows.forEach(r -> ids.add(r.author().id()));
        return ids;
    }
}
