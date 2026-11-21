package com.yangstagram.discover;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.post.PostQueries;
import com.yangstagram.post.dto.PostDtos.GridItem;
import com.yangstagram.post.dto.PostDtos.PostView;
import com.yangstagram.relation.RelationSql;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 탐색 그리드와 릴스 피드 (분석 문서 §9, §11).
 * M3에서는 단순한 점수식을 쓴다: 인기(좋아요·댓글·조회 로그) − 오래된 정도 + 관심사 일치 + 릴스 가산 + 사람마다 다른 작은 흔들기,
 * 이미 본 것은 뒤로. 가중치 조절·점수 내역 표시는 M4 알고리즘 실험실에서.
 */
@RestController
@RequestMapping("/api")
public class DiscoverController {

    private static final int EXPLORE_PAGE = 24;
    private static final int REELS_PAGE = 6;

    private final NamedParameterJdbcTemplate jdbc;
    private final PostQueries queries;
    private final LabClock clock;

    public DiscoverController(NamedParameterJdbcTemplate jdbc, PostQueries queries, LabClock clock) {
        this.jdbc = jdbc;
        this.queries = queries;
        this.clock = clock;
    }

    public record Page<T>(List<T> items, Integer nextPage) {
    }

    /** 공통 점수식 (게시물 p, 작성자 a) */
    private static final String SCORE = """
            ( ln(1 + p.like_count) + 0.6 * ln(1 + p.comment_count) + 0.3 * ln(1 + p.view_count)
              - extract(epoch from (:now - p.created_at)) / 86400.0 / 20
              + case when string_to_array(coalesce(a.interests, ''), ',') &&
                          string_to_array((select coalesce(interests, '') from users where id = :me), ',')
                     then 0.8 else 0 end
              + (abs(hashtext(p.id::text || ':' || :me::text)) % 100) / 100.0
              - case when exists (select 1 from post_views pv where pv.user_id = :me and pv.post_id = p.id)
                     then 3 else 0 end )
            """;

    /** 탐색: 팔로우하지 않는 공개 계정의 게시물·릴스 (3분 넘는 릴스 제외) */
    @GetMapping("/explore")
    public Page<GridItem> explore(@RequestParam(defaultValue = "0") int page) {
        long me = CurrentUser.requireId();
        int p = Math.max(0, page);
        List<GridItem> items = jdbc.query("""
                select p.id, p.shortcode, p.like_count, p.comment_count, p.hide_like_count, p.author_id,
                       p.type, p.view_count, m.thumb_url,
                       (select count(*) from post_media x where x.post_id = p.id) as media_count
                from posts p
                join users a on a.id = p.author_id
                join post_media m on m.post_id = p.id and m.position = 0
                where p.deleted_at is null and a.deleted_at is null and not a.is_private and a.id <> :me
                  and p.created_at <= :now and p.recommendable
                  and not exists (select 1 from follows f where f.follower_id = :me and f.following_id = a.id
                                  and f.status = 'ACCEPTED')
                  and %s
                order by %s + case when p.type = 'REEL' then 0.4 else 0 end desc, p.id desc
                limit :limit offset :offset
                """.formatted(RelationSql.notBlocked("a.id"), SCORE), params(me, EXPLORE_PAGE + 1, p * EXPLORE_PAGE),
                (rs, i) -> PostQueries.gridItem(rs, me));
        return page(items, EXPLORE_PAGE, p);
    }

    /**
     * 릴스 피드.
     * recommended: 볼 수 있는 모든 릴스 (팔로우하지 않는 사람 것은 3분 이하만), 점수 순
     * friends: 팔로우하는 사람이 올렸거나 좋아요한 릴스, 최신 순
     */
    @GetMapping("/reels")
    public Page<PostView> reels(@RequestParam(defaultValue = "recommended") String tab,
                                @RequestParam(defaultValue = "0") int page) {
        long me = CurrentUser.requireId();
        int p = Math.max(0, page);
        String sql = "friends".equals(tab) ? """
                select p.id from posts p join users a on a.id = p.author_id
                where p.type = 'REEL' and p.deleted_at is null and a.deleted_at is null and p.created_at <= :now
                  and %s and %s
                  and (p.author_id in (select following_id from follows where follower_id = :me and status = 'ACCEPTED')
                       or exists (select 1 from likes l join follows f on f.following_id = l.user_id
                                  and f.follower_id = :me and f.status = 'ACCEPTED' where l.post_id = p.id))
                order by p.created_at desc, p.id desc
                limit :limit offset :offset
                """.formatted(PostQueries.VISIBLE, RelationSql.postsNotMuted("a.id")) : """
                select p.id from posts p join users a on a.id = p.author_id
                where p.type = 'REEL' and p.deleted_at is null and a.deleted_at is null and p.created_at <= :now
                  and %s
                  and a.id <> :me
                  and (p.recommendable or exists (select 1 from follows f where f.follower_id = :me
                       and f.following_id = a.id and f.status = 'ACCEPTED'))
                order by %s desc, p.id desc
                limit :limit offset :offset
                """.formatted(PostQueries.VISIBLE, SCORE);
        List<Long> ids = jdbc.queryForList(sql, params(me, REELS_PAGE + 1, p * REELS_PAGE), Long.class);
        Integer next = ids.size() > REELS_PAGE ? p + 1 : null;
        List<Long> pageIds = ids.size() > REELS_PAGE ? ids.subList(0, REELS_PAGE) : ids;
        return new Page<>(queries.views(pageIds, me), next);
    }

    private MapSqlParameterSource params(long me, int limit, int offset) {
        return new MapSqlParameterSource("me", me).addValue("limit", limit).addValue("offset", offset)
                .addValue("now", Timestamp.from(Instant.now(clock)));
    }

    private static <T> Page<T> page(List<T> items, int size, int page) {
        if (items.size() <= size) return new Page<>(items, null);
        return new Page<>(items.subList(0, size), page + 1);
    }
}
