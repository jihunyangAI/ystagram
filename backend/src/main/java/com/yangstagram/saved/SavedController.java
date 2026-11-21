package com.yangstagram.saved;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.paging.Cursor;
import com.yangstagram.common.paging.Slice;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.post.PostQueries;
import com.yangstagram.post.PostQueries.PostRow;
import com.yangstagram.post.PostService;
import com.yangstagram.post.dto.PostDtos.GridItem;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 저장됨·컬렉션 (분석 문서 §14). 저장한 게시물은 본인만 볼 수 있다.
 * 컬렉션에 넣으면 자동으로 저장되고, 저장을 취소하면 모든 컬렉션에서 빠진다.
 */
@RestController
@RequestMapping("/api/me")
public class SavedController {

    private static final int GRID_PAGE = 12;
    private static final int MAX_NAME = 100;

    private final NamedParameterJdbcTemplate jdbc;
    private final PostQueries queries;
    private final PostService postService;
    private final LabClock clock;

    public SavedController(NamedParameterJdbcTemplate jdbc, PostQueries queries, PostService postService,
                           LabClock clock) {
        this.jdbc = jdbc;
        this.queries = queries;
        this.postService = postService;
        this.clock = clock;
    }

    public record CollectionView(Long id, String name, int count, List<String> covers) {
    }

    /** 저장됨 첫 화면: "모든 게시물" + 내 컬렉션들 */
    public record SavedOverview(CollectionView all, List<CollectionView> collections) {
    }

    public record CollectionForm(String name, String shortcode) {
    }

    @GetMapping("/saved")
    public SavedOverview overview() {
        long me = CurrentUser.requireId();
        MapSqlParameterSource p = new MapSqlParameterSource("me", me);
        Integer total = jdbc.queryForObject("""
                select count(*) from saved_posts sp join posts p on p.id = sp.post_id
                where sp.user_id = :me and p.deleted_at is null
                """, p, Integer.class);
        List<String> allCovers = jdbc.queryForList("""
                select m.thumb_url from saved_posts sp
                join posts p on p.id = sp.post_id and p.deleted_at is null
                join post_media m on m.post_id = p.id and m.position = 0
                where sp.user_id = :me order by sp.created_at desc limit 4
                """, p, String.class);

        List<CollectionView> collections = new ArrayList<>();
        Map<Long, List<String>> covers = new HashMap<>();
        jdbc.query("""
                select cp.collection_id, m.thumb_url from (
                  select cp.*, row_number() over (partition by cp.collection_id order by cp.created_at desc) rn
                  from collection_posts cp join collections c on c.id = cp.collection_id
                  join posts p on p.id = cp.post_id and p.deleted_at is null
                  where c.user_id = :me
                ) cp join post_media m on m.post_id = cp.post_id and m.position = 0
                where cp.rn <= 4 order by cp.collection_id, cp.rn
                """, p, rs -> {
            covers.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
        });
        jdbc.query("""
                select c.id, c.name,
                       (select count(*) from collection_posts cp join posts p on p.id = cp.post_id
                        where cp.collection_id = c.id and p.deleted_at is null) as cnt
                from collections c where c.user_id = :me order by c.updated_at desc, c.id desc
                """, p, rs -> {
            long id = rs.getLong("id");
            collections.add(new CollectionView(id, rs.getString("name"), rs.getInt("cnt"),
                    covers.getOrDefault(id, List.of())));
        });
        return new SavedOverview(new CollectionView(null, "모든 게시물", total == null ? 0 : total, allCovers),
                collections);
    }

    @GetMapping("/saved/posts")
    public Slice<GridItem> savedPosts(@RequestParam(required = false) String cursor) {
        return queries.savedGrid(CurrentUser.requireId(), null, Cursor.decode(cursor), GRID_PAGE);
    }

    /** 저장 시트에서 이 게시물이 들어 있는 내 컬렉션 표시 */
    @GetMapping("/saved/{shortcode}/collections")
    public List<Long> collectionsOf(@PathVariable String shortcode) {
        long me = CurrentUser.requireId();
        PostRow post = postService.getVisible(shortcode, me);
        return jdbc.queryForList("""
                select cp.collection_id from collection_posts cp join collections c on c.id = cp.collection_id
                where c.user_id = :me and cp.post_id = :post
                """, Map.of("me", me, "post", post.id()), Long.class);
    }

    // ------------------------------------------------------------ 컬렉션

    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CollectionView create(@RequestBody CollectionForm form) {
        long me = CurrentUser.requireId();
        String name = validName(form.name());
        Timestamp now = Timestamp.from(Instant.now(clock));
        Long id = jdbc.queryForObject("""
                insert into collections (user_id, name, created_at, updated_at) values (:me, :name, :now, :now)
                returning id
                """, new MapSqlParameterSource("me", me).addValue("name", name).addValue("now", now), Long.class);
        if (form.shortcode() != null) {
            add(id, form.shortcode());
        }
        return get(me, id);
    }

    @PatchMapping("/collections/{id}")
    @Transactional
    public CollectionView rename(@PathVariable long id, @RequestBody CollectionForm form) {
        long me = CurrentUser.requireId();
        own(me, id);
        jdbc.update("update collections set name = :name, updated_at = :now where id = :id",
                new MapSqlParameterSource("name", validName(form.name())).addValue("id", id)
                        .addValue("now", Timestamp.from(Instant.now(clock))));
        return get(me, id);
    }

    /** 컬렉션만 지운다 (저장한 게시물은 "모든 게시물"에 남는다) */
    @DeleteMapping("/collections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        long me = CurrentUser.requireId();
        own(me, id);
        jdbc.update("delete from collections where id = :id", Map.of("id", id));
    }

    @GetMapping("/collections/{id}")
    public CollectionView get(@PathVariable long id) {
        return get(CurrentUser.requireId(), id);
    }

    @GetMapping("/collections/{id}/posts")
    public Slice<GridItem> collectionPosts(@PathVariable long id, @RequestParam(required = false) String cursor) {
        long me = CurrentUser.requireId();
        own(me, id);
        return queries.savedGrid(me, id, Cursor.decode(cursor), GRID_PAGE);
    }

    @PutMapping("/collections/{id}/posts/{shortcode}")
    @Transactional
    public CollectionView add(@PathVariable long id, @PathVariable String shortcode) {
        long me = CurrentUser.requireId();
        own(me, id);
        postService.save(shortcode, me);
        PostRow post = postService.getVisible(shortcode, me);
        Timestamp now = Timestamp.from(Instant.now(clock));
        jdbc.update("""
                insert into collection_posts (collection_id, post_id, created_at) values (:c, :p, :now)
                on conflict do nothing
                """, new MapSqlParameterSource("c", id).addValue("p", post.id()).addValue("now", now));
        jdbc.update("update collections set updated_at = :now where id = :id",
                new MapSqlParameterSource("id", id).addValue("now", now));
        return get(me, id);
    }

    @DeleteMapping("/collections/{id}/posts/{shortcode}")
    public CollectionView remove(@PathVariable long id, @PathVariable String shortcode) {
        long me = CurrentUser.requireId();
        own(me, id);
        PostRow post = postService.getVisible(shortcode, me);
        jdbc.update("delete from collection_posts where collection_id = :c and post_id = :p",
                Map.of("c", id, "p", post.id()));
        return get(me, id);
    }

    // ------------------------------------------------------------ 도우미

    private CollectionView get(long me, long id) {
        own(me, id);
        String name = jdbc.queryForObject("select name from collections where id = :id", Map.of("id", id),
                String.class);
        Integer count = jdbc.queryForObject("""
                select count(*) from collection_posts cp join posts p on p.id = cp.post_id
                where cp.collection_id = :id and p.deleted_at is null
                """, Map.of("id", id), Integer.class);
        List<String> covers = jdbc.queryForList("""
                select m.thumb_url from collection_posts cp
                join posts p on p.id = cp.post_id and p.deleted_at is null
                join post_media m on m.post_id = p.id and m.position = 0
                where cp.collection_id = :id order by cp.created_at desc limit 4
                """, Map.of("id", id), String.class);
        return new CollectionView(id, name, count == null ? 0 : count, covers);
    }

    private void own(long me, long id) {
        Integer n = jdbc.queryForObject("select count(*) from collections where id = :id and user_id = :me",
                Map.of("id", id, "me", me), Integer.class);
        if (n == null || n == 0) {
            throw ApiException.notFound("컬렉션을 찾을 수 없습니다.");
        }
    }

    private static String validName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()) throw ApiException.badRequest("EMPTY_NAME", "컬렉션 이름을 입력해 주세요.");
        if (name.length() > MAX_NAME) {
            throw ApiException.badRequest("NAME_TOO_LONG", "컬렉션 이름은 " + MAX_NAME + "자까지 쓸 수 있습니다.");
        }
        return name;
    }
}
