package com.yangstagram.search;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.paging.Cursor;
import com.yangstagram.common.paging.Slice;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.follow.FollowService;
import com.yangstagram.follow.Relationship;
import com.yangstagram.post.PostQueries;
import com.yangstagram.post.dto.PostDtos.GridItem;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색 (분석 문서 §11): 계정·태그·장소 통합 검색, 최근 검색 기록, 해시태그·장소 페이지.
 * 탐색 그리드와 키워드·오디오 검색은 M3.
 */
@RestController
@RequestMapping("/api")
public class SearchController {

    private static final int GRID_PAGE = 12;
    private static final int TOP = 9;
    private static final int RECENT_MAX = 30;
    private static final Set<String> KINDS = Set.of("USER", "TAG", "PLACE");

    private final NamedParameterJdbcTemplate jdbc;
    private final FollowService followService;
    private final PostQueries queries;
    private final LabClock clock;
    private final com.yangstagram.audio.AudioService audio;

    public SearchController(NamedParameterJdbcTemplate jdbc, FollowService followService, PostQueries queries,
                            LabClock clock, com.yangstagram.audio.AudioService audio) {
        this.audio = audio;
        this.jdbc = jdbc;
        this.followService = followService;
        this.queries = queries;
        this.clock = clock;
    }

    /** subtitle: "팔로우 중" / "팔로워 1.2만명" 같은 보조 문구는 화면에서 만든다 */
    public record UserHit(UserSummary user, int followersCount, Relationship relationship, boolean followsMe) {
    }

    public record TagHit(String name, int postCount) {
    }

    public record PlaceHit(long id, String name, String city, int postCount) {
    }

    public record SearchResult(List<UserHit> users, List<TagHit> tags, List<PlaceHit> places,
                               List<com.yangstagram.audio.AudioService.AudioRef> audios) {
    }

    /** 최근 검색 한 줄 (kind에 맞는 것 하나만 채워짐) */
    public record RecentItem(String kind, String term, UserHit user, TagHit tag, PlaceHit place) {
    }

    public record RecentForm(String kind, String term) {
    }

    public record TagPage(String name, int postCount, List<GridItem> top) {
    }

    public record PlacePage(long id, String name, String city, int postCount, List<GridItem> top) {
    }

    // ------------------------------------------------------------ 검색

    @GetMapping("/search")
    public SearchResult search(@RequestParam(defaultValue = "") String q) {
        long me = CurrentUser.requireId();
        String term = q.strip().toLowerCase();
        if (term.startsWith("#")) {
            return new SearchResult(List.of(), tags(term.substring(1), 20), List.of(), List.of());
        }
        if (term.startsWith("@")) term = term.substring(1);
        if (term.isEmpty()) {
            return new SearchResult(List.of(), List.of(), List.of(), List.of());
        }
        return new SearchResult(users(me, term, 20), tags(term, 8), places(term, 5), audio.search(term, 5));
    }

    /** 키워드 검색: 캡션에 검색어가 들어간 게시물 */
    @GetMapping("/search/posts")
    public com.yangstagram.common.paging.Slice<com.yangstagram.post.dto.PostDtos.GridItem> keyword(
            @RequestParam(defaultValue = "") String q, @RequestParam(required = false) String cursor) {
        long me = CurrentUser.requireId();
        String term = q.strip().toLowerCase();
        if (term.startsWith("#")) term = term.substring(1);
        if (term.length() < 2) return new com.yangstagram.common.paging.Slice<>(List.of(), null);
        return queries.keywordGrid(term, me, com.yangstagram.common.paging.Cursor.decode(cursor), 24);
    }

    private List<UserHit> users(long me, String term, int limit) {
        String like = escape(term);
        List<UserHit> hits = jdbc.query("""
                select %s, u.followers_count,
                       exists (select 1 from follows f where f.follower_id = u.id and f.following_id = :me
                               and f.status = 'ACCEPTED') as follows_me
                from users u
                where u.deleted_at is null
                  and not exists (select 1 from blocks b where b.blocker_id = u.id and b.blocked_id = :me)
                  and (u.username like :prefix escape '\\' or lower(u.full_name) like :contains escape '\\'
                       or u.username like :contains escape '\\')
                order by (u.username = :term) desc,
                         exists (select 1 from follows f where f.follower_id = :me and f.following_id = u.id) desc,
                         (u.username like :prefix escape '\\') desc,
                         u.followers_count desc, u.id
                limit :limit
                """.formatted(UserRows.cols("u")),
                new MapSqlParameterSource("me", me).addValue("term", term).addValue("prefix", like + "%")
                        .addValue("contains", "%" + like + "%").addValue("limit", limit),
                (rs, i) -> new UserHit(UserRows.read(rs, "u"), rs.getInt("followers_count"), null,
                        rs.getBoolean("follows_me")));
        return withRelationships(me, hits);
    }

    private List<UserHit> withRelationships(long me, List<UserHit> hits) {
        Map<Long, Relationship> rel = followService.relationships(me, hits.stream().map(h -> h.user().id()).toList());
        return hits.stream().map(h -> new UserHit(h.user(), h.followersCount(), rel.get(h.user().id()),
                h.followsMe())).toList();
    }

    private List<TagHit> tags(String term, int limit) {
        if (term.isEmpty()) return List.of();
        return jdbc.query("""
                select name, post_count from hashtags
                where name like :prefix escape '\\' and post_count > 0
                order by (name = :term) desc, post_count desc, name
                limit :limit
                """, new MapSqlParameterSource("prefix", escape(term) + "%").addValue("term", term)
                .addValue("limit", limit), (rs, i) -> new TagHit(rs.getString(1), rs.getInt(2)));
    }

    private List<PlaceHit> places(String term, int limit) {
        return jdbc.query("""
                select l.id, l.name, l.city, count(p.id) as posts
                from locations l left join posts p on p.location_id = l.id and p.deleted_at is null and p.archived_at is null
                where lower(l.name) like :contains escape '\\' or lower(coalesce(l.city, '')) like :contains escape '\\'
                group by l.id
                order by posts desc, l.name
                limit :limit
                """, new MapSqlParameterSource("contains", "%" + escape(term) + "%").addValue("limit", limit),
                (rs, i) -> new PlaceHit(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ------------------------------------------------------------ 최근 검색

    @GetMapping("/search/recent")
    public List<RecentItem> recent() {
        long me = CurrentUser.requireId();
        List<String[]> rows = jdbc.query("""
                select kind, term from search_history where user_id = :me order by created_at desc limit :limit
                """, Map.of("me", me, "limit", RECENT_MAX), (rs, i) -> new String[] {rs.getString(1), rs.getString(2)});
        List<RecentItem> items = new ArrayList<>();
        for (String[] r : rows) {
            RecentItem item = switch (r[0]) {
                case "USER" -> jdbc.query("""
                        select %s, u.followers_count from users u where u.username = :t and u.deleted_at is null
                        """.formatted(UserRows.cols("u")), Map.of("t", r[1]),
                        (rs, i) -> new UserHit(UserRows.read(rs, "u"), rs.getInt("followers_count"), null, false))
                        .stream().findFirst().map(u -> new RecentItem("USER", r[1],
                                withRelationships(me, List.of(u)).getFirst(), null, null)).orElse(null);
                case "TAG" -> jdbc.query("select name, post_count from hashtags where name = :t", Map.of("t", r[1]),
                        (rs, i) -> new TagHit(rs.getString(1), rs.getInt(2))).stream().findFirst()
                        .map(t -> new RecentItem("TAG", r[1], null, t, null)).orElse(null);
                case "PLACE" -> placeById(r[1]).map(p -> new RecentItem("PLACE", r[1], null, null, p)).orElse(null);
                default -> null;
            };
            if (item != null) items.add(item);
        }
        return items;
    }

    @PostMapping("/search/recent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addRecent(@RequestBody RecentForm form) {
        long me = CurrentUser.requireId();
        if (form.kind() == null || !KINDS.contains(form.kind()) || form.term() == null || form.term().isBlank()
                || form.term().length() > 100) {
            throw ApiException.badRequest("BAD_RECENT", "저장할 수 없는 검색 기록입니다.");
        }
        jdbc.update("""
                insert into search_history (user_id, kind, term, created_at) values (:me, :kind, :term, :now)
                on conflict (user_id, kind, term) do update set created_at = excluded.created_at
                """, new MapSqlParameterSource("me", me).addValue("kind", form.kind()).addValue("term", form.term())
                .addValue("now", Timestamp.from(Instant.now(clock))));
    }

    @DeleteMapping("/search/recent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRecent(@RequestParam(required = false) String kind, @RequestParam(required = false) String term) {
        long me = CurrentUser.requireId();
        if (kind == null) {
            jdbc.update("delete from search_history where user_id = :me", Map.of("me", me)); // 모두 지우기
        } else {
            jdbc.update("delete from search_history where user_id = :me and kind = :kind and term = :term",
                    Map.of("me", me, "kind", kind, "term", term == null ? "" : term));
        }
    }

    // ------------------------------------------------------------ 해시태그·장소 페이지

    @GetMapping("/tags/{name}")
    public TagPage tag(@PathVariable String name) {
        long me = CurrentUser.requireId();
        TagHit tag = findTag(name);
        long id = tagId(tag.name());
        List<GridItem> top = queries.topPosts(
                "post_hashtags ph join posts p on p.id = ph.post_id join users a on a.id = p.author_id "
                        + "where ph.hashtag_id = :tag and " + PostQueries.VISIBLE,
                new MapSqlParameterSource("tag", id), me, TOP);
        return new TagPage(tag.name(), tag.postCount(), top);
    }

    @GetMapping("/tags/{name}/posts")
    public Slice<GridItem> tagPosts(@PathVariable String name, @RequestParam(required = false) String cursor) {
        TagHit tag = findTag(name);
        return queries.tagGrid(tagId(tag.name()), CurrentUser.requireId(), Cursor.decode(cursor), GRID_PAGE);
    }

    @GetMapping("/locations/{id}")
    public PlacePage place(@PathVariable long id) {
        long me = CurrentUser.requireId();
        PlaceHit place = placeById(String.valueOf(id))
                .orElseThrow(() -> ApiException.notFound("장소를 찾을 수 없습니다."));
        List<GridItem> top = queries.topPosts(
                "posts p join users a on a.id = p.author_id where p.location_id = :loc and " + PostQueries.VISIBLE,
                new MapSqlParameterSource("loc", id), me, TOP);
        return new PlacePage(place.id(), place.name(), place.city(), place.postCount(), top);
    }

    @GetMapping("/locations/{id}/posts")
    public Slice<GridItem> placePosts(@PathVariable long id, @RequestParam(required = false) String cursor) {
        return queries.locationGrid(id, CurrentUser.requireId(), Cursor.decode(cursor), GRID_PAGE);
    }

    private TagHit findTag(String raw) {
        String name = raw.strip().toLowerCase();
        if (name.startsWith("#")) name = name.substring(1);
        return jdbc.query("select name, post_count from hashtags where name = :n", Map.of("n", name),
                        (rs, i) -> new TagHit(rs.getString(1), rs.getInt(2))).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("해시태그를 찾을 수 없습니다."));
    }

    private long tagId(String name) {
        return jdbc.queryForObject("select id from hashtags where name = :n", Map.of("n", name), Long.class);
    }

    private java.util.Optional<PlaceHit> placeById(String id) {
        long value;
        try {
            value = Long.parseLong(id);
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
        return jdbc.query("""
                select l.id, l.name, l.city, count(p.id) as posts
                from locations l left join posts p on p.location_id = l.id and p.deleted_at is null and p.archived_at is null
                where l.id = :id group by l.id
                """, Map.of("id", value),
                (rs, i) -> new PlaceHit(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4)))
                .stream().findFirst();
    }
}
