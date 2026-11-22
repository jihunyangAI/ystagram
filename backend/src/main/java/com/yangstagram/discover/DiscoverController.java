package com.yangstagram.discover;

import com.yangstagram.algo.Recommendations;
import com.yangstagram.algo.Recommendations.Page;
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
 * 탐색 그리드와 릴스 피드 (분석 문서 §9, §11). 점수식은 {@link com.yangstagram.algo.Surface}의 EXPLORE·REELS,
 * 가중치는 Lab 알고리즘 실험실에서 바꾼다. 응답의 각 항목에 점수 내역(ranking)이 붙는다.
 */
@RestController
@RequestMapping("/api")
public class DiscoverController {

    private final Recommendations recommendations;
    private final NamedParameterJdbcTemplate jdbc;
    private final PostQueries queries;
    private final LabClock clock;

    public DiscoverController(Recommendations recommendations, NamedParameterJdbcTemplate jdbc, PostQueries queries,
                              LabClock clock) {
        this.recommendations = recommendations;
        this.jdbc = jdbc;
        this.queries = queries;
        this.clock = clock;
    }

    /** 탐색: 팔로우하지 않는 공개 계정의 게시물·릴스 */
    @GetMapping("/explore")
    public Page<GridItem> explore(@RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Long since) {
        return recommendations.explore(CurrentUser.requireId(), page, since);
    }

    /**
     * 릴스 피드.
     * recommended: 볼 수 있는 릴스, 점수 순 (팔로우하지 않는 사람 것은 추천 허용된 것만)
     * friends: 팔로우하는 사람이 올렸거나 좋아요한 릴스, 최신 순
     */
    @GetMapping("/reels")
    public Page<PostView> reels(@RequestParam(defaultValue = "recommended") String tab,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(required = false) Long since) {
        long me = CurrentUser.requireId();
        if (!"friends".equals(tab)) return recommendations.reels(me, page, since);

        int p = Math.max(0, page);
        int size = Recommendations.REELS_PAGE;
        List<Long> ids = jdbc.queryForList("""
                select p.id from posts p join users a on a.id = p.author_id
                where p.type = 'REEL' and p.deleted_at is null and p.archived_at is null and a.deleted_at is null and p.created_at <= :now
                  and %s and %s
                  and (p.author_id in (select following_id from follows where follower_id = :me and status = 'ACCEPTED')
                       or exists (select 1 from likes l join follows f on f.following_id = l.user_id
                                  and f.follower_id = :me and f.status = 'ACCEPTED' where l.post_id = p.id))
                order by p.created_at desc, p.id desc
                limit :limit offset :offset
                """.formatted(PostQueries.VISIBLE, RelationSql.postsNotMuted("a.id")),
                new MapSqlParameterSource("me", me).addValue("limit", size + 1).addValue("offset", p * size)
                        .addValue("now", Timestamp.from(Instant.now(clock))), Long.class);
        Integer next = ids.size() > size ? p + 1 : null;
        List<Long> pageIds = ids.size() > size ? ids.subList(0, size) : ids;
        return new Page<>(queries.views(pageIds, me), next, Instant.now(clock).toEpochMilli());
    }
}
