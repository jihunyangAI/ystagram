package com.yangstagram.feed;

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
 * 홈 피드. MVP는 "팔로잉" 모드(시간순)만 — 추천 섞기(§6.5)는 M4 알고리즘 단계에서.
 */
@RestController
@RequestMapping("/api")
public class FeedController {

    private static final int PAGE = 10;

    private final PostQueries queries;
    private final FollowService followService;

    public FeedController(PostQueries queries, FollowService followService) {
        this.queries = queries;
        this.followService = followService;
    }

    @GetMapping("/feed")
    public Slice<PostView> feed(@RequestParam(required = false) String cursor) {
        return queries.feed(CurrentUser.requireId(), Cursor.decode(cursor), PAGE);
    }

    /** 홈 오른쪽·피드 끝의 "회원님을 위한 추천" */
    @GetMapping("/suggestions/users")
    public List<SuggestedUser> suggestions(@RequestParam(defaultValue = "5") int limit) {
        return followService.suggestions(CurrentUser.requireId(), Math.clamp(limit, 1, 30));
    }
}
