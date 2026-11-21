package com.yangstagram.follow;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.follow.FollowService.UserListItem;
import com.yangstagram.user.User;
import com.yangstagram.user.UserService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 비공개 계정 운영: 팔로우 요청 승인·거절, 공개 범위 변경, 팔로워 삭제 */
@RestController
@RequestMapping("/api/me")
public class FollowRequestController {

    private final FollowService followService;
    private final UserService userService;

    public FollowRequestController(FollowService followService, UserService userService) {
        this.followService = followService;
        this.userService = userService;
    }

    public record PrivacyForm(boolean isPrivate) {
    }

    @GetMapping("/follow-requests")
    public List<UserListItem> requests(@RequestParam(defaultValue = "0") int page) {
        return followService.requests(CurrentUser.requireId(), Math.max(0, page), 30);
    }

    /** 승인 */
    @PutMapping("/follow-requests/{username}")
    public Map<String, Object> accept(@PathVariable String username) {
        long me = CurrentUser.requireId();
        User requester = userService.getByUsername(username);
        followService.accept(me, requester.getId());
        // 승인한 뒤 화면에 "맞팔로우" 버튼을 보여 주기 위해 내 → 상대 관계를 돌려준다
        return Map.of("relationship", followService.relationship(me, requester.getId()));
    }

    /** 거절 */
    @DeleteMapping("/follow-requests/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable String username) {
        followService.decline(CurrentUser.requireId(), userService.getByUsername(username).getId());
    }

    @DeleteMapping("/followers/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFollower(@PathVariable String username) {
        followService.removeFollower(CurrentUser.requireId(), userService.getByUsername(username).getId());
    }

    @GetMapping("/privacy")
    public PrivacyForm privacy() {
        return new PrivacyForm(userService.getActive(CurrentUser.requireId()).isPrivateAccount());
    }

    /** 공개로 바꾸면 대기 중인 요청이 모두 승인된다 */
    @PutMapping("/privacy")
    public PrivacyForm setPrivacy(@RequestBody PrivacyForm form) {
        followService.setPrivate(CurrentUser.requireId(), form.isPrivate());
        return form;
    }
}
