package com.yangstagram.feed;

import com.yangstagram.algo.Recommendations;
import com.yangstagram.auth.CurrentUser;
import com.yangstagram.common.paging.Cursor;
import com.yangstagram.common.paging.Slice;
import com.yangstagram.follow.FollowService;
import com.yangstagram.follow.FollowService.SuggestedUser;
import com.yangstagram.post.PostQueries;
import com.yangstagram.post.dto.PostDtos.PostView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홈 피드.
 * home(기본): 점수 순 + 추천 섞기 (기획서 §6.5) / following: 팔로우하는 사람 글 최신순 / favorites: 즐겨찾기만 최신순.
 */
@RestController
@RequestMapping("/api")
public class FeedController {

    private static final int PAGE = 10;

    private final PostQueries queries;
    private final FollowService followService;
    private final Recommendations recommendations;

    public FeedController(PostQueries queries, FollowService followService, Recommendations recommendations) {
        this.queries = queries;
        this.followService = followService;
        this.recommendations = recommendations;
    }

    @GetMapping("/feed")
    public Slice<PostView> feed(@RequestParam(required = false) String cursor,
                                @RequestParam(defaultValue = "home") String mode) {
        long me = CurrentUser.requireId();
        if ("home".equals(mode)) return recommendations.home(me, cursor);
        return queries.feed(me, Cursor.decode(cursor), PAGE, "favorites".equals(mode));
    }

    /** 홈 오른쪽·피드 끝의 "회원님을 위한 추천" */
    @GetMapping("/suggestions/users")
    public List<SuggestedUser> suggestions(@RequestParam(defaultValue = "5") int limit) {
        return followService.suggestions(CurrentUser.requireId(), Math.clamp(limit, 1, 30));
    }
}
