package com.yangstagram.user;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.common.paging.Cursor;
import com.yangstagram.common.paging.Slice;
import com.yangstagram.follow.FollowService;
import com.yangstagram.follow.FollowService.UserListItem;
import com.yangstagram.follow.FollowStatus;
import com.yangstagram.follow.Relationship;
import com.yangstagram.post.PostQueries;
import com.yangstagram.post.dto.PostDtos.GridItem;
import com.yangstagram.common.error.ApiException;
import com.yangstagram.relation.RelationService;
import com.yangstagram.story.StoryRings;
import com.yangstagram.user.dto.ProfileResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final int GRID_PAGE = 12;
    private static final int LIST_PAGE = 30;

    private final UserService userService;
    private final FollowService followService;
    private final PostQueries postQueries;
    private final StoryRings storyRings;
    private final RelationService relations;

    public UserController(UserService userService, FollowService followService, PostQueries postQueries,
                          StoryRings storyRings, RelationService relations) {
        this.relations = relations;
        this.userService = userService;
        this.followService = followService;
        this.postQueries = postQueries;
        this.storyRings = storyRings;
    }

    @GetMapping("/{username}")
    public ProfileResponse profile(@PathVariable String username) {
        long me = CurrentUser.requireId();
        User user = userService.getByUsername(username);
        // 나를 차단한 사람은 "찾을 수 없음"으로 보인다
        if (relations.blockedBy(me, user.getId())) {
            throw ApiException.notFound("죄송합니다. 페이지를 사용할 수 없습니다.");
        }
        return profileOf(me, user);
    }

    private ProfileResponse profileOf(long me, User user) {
        Relationship rel = followService.relationship(me, user.getId());
        boolean followsMe = me != user.getId() && followService.status(user.getId(), me) == FollowStatus.ACCEPTED;
        boolean canView = followService.canViewContent(me, user.getId(), user.isPrivateAccount());
        return ProfileResponse.of(user, rel, followsMe, canView, storyRings.ring(me, user.getId()),
                relations.flags(me, user.getId()));
    }

    /** 프로필 그리드 (3:4, 한 번에 12칸 = 4줄) */
    @GetMapping("/{username}/posts")
    public Slice<GridItem> posts(@PathVariable String username, @RequestParam(required = false) String cursor) {
        long me = CurrentUser.requireId();
        User user = userService.getByUsername(username);
        followService.requireCanView(me, user.getId(), user.isPrivateAccount());
        return postQueries.grid(user.getId(), me, Cursor.decode(cursor), GRID_PAGE);
    }

    /** 프로필 "릴스" 탭 */
    @GetMapping("/{username}/reels")
    public Slice<GridItem> reels(@PathVariable String username, @RequestParam(required = false) String cursor) {
        long me = CurrentUser.requireId();
        User user = userService.getByUsername(username);
        followService.requireCanView(me, user.getId(), user.isPrivateAccount());
        return postQueries.reelGrid(user.getId(), me, Cursor.decode(cursor), GRID_PAGE);
    }

    /** 프로필 "태그됨" 탭 */
    @GetMapping("/{username}/tagged")
    public Slice<GridItem> tagged(@PathVariable String username, @RequestParam(required = false) String cursor) {
        long me = CurrentUser.requireId();
        User user = userService.getByUsername(username);
        followService.requireCanView(me, user.getId(), user.isPrivateAccount());
        return postQueries.taggedGrid(user.getId(), me, Cursor.decode(cursor), GRID_PAGE);
    }

    /** 프로필 "리포스트" 탭 */
    @GetMapping("/{username}/reposts")
    public Slice<GridItem> reposts(@PathVariable String username, @RequestParam(required = false) String cursor) {
        long me = CurrentUser.requireId();
        User user = userService.getByUsername(username);
        followService.requireCanView(me, user.getId(), user.isPrivateAccount());
        return postQueries.repostGrid(user.getId(), me, Cursor.decode(cursor), GRID_PAGE);
    }

    @GetMapping("/{username}/followers")
    public List<UserListItem> followers(@PathVariable String username, @RequestParam(defaultValue = "0") int page) {
        long me = CurrentUser.requireId();
        User user = userService.getByUsername(username);
        followService.requireCanView(me, user.getId(), user.isPrivateAccount());
        return followService.followers(me, user.getId(), Math.max(0, page), LIST_PAGE);
    }

    @GetMapping("/{username}/following")
    public List<UserListItem> following(@PathVariable String username, @RequestParam(defaultValue = "0") int page) {
        long me = CurrentUser.requireId();
        User user = userService.getByUsername(username);
        followService.requireCanView(me, user.getId(), user.isPrivateAccount());
        return followService.following(me, user.getId(), Math.max(0, page), LIST_PAGE);
    }

    /** 팔로우 (비공개 계정이면 요청). 바뀐 프로필 헤더를 돌려준다. */
    @PutMapping("/{username}/follow")
    public Map<String, Object> follow(@PathVariable String username) {
        long me = CurrentUser.requireId();
        User user = userService.getByUsername(username);
        Relationship rel = followService.follow(me, user.getId(), user.isPrivateAccount());
        return Map.of("relationship", rel);
    }

    /** 언팔로우 또는 요청 취소 */
    @DeleteMapping("/{username}/follow")
    public Map<String, Object> unfollow(@PathVariable String username) {
        long me = CurrentUser.requireId();
        User user = userService.getByUsername(username);
        return Map.of("relationship", followService.unfollow(me, user.getId()));
    }
}
