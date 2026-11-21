package com.yangstagram.relation;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.follow.FollowService.UserListItem;
import com.yangstagram.relation.RelationService.Candidate;
import com.yangstagram.relation.RelationService.Flags;
import com.yangstagram.user.UserService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 차단·제한·숨기기·친한 친구·즐겨찾기. 바뀐 뒤의 내 설정(Flags)을 돌려준다 */
@RestController
public class RelationController {

    private final RelationService relations;
    private final UserService userService;

    public RelationController(RelationService relations, UserService userService) {
        this.relations = relations;
        this.userService = userService;
    }

    public record MuteForm(boolean posts, boolean stories) {
    }

    private long target(String username) {
        return userService.getByUsername(username).getId();
    }

    @PutMapping("/api/users/{username}/block")
    public Flags block(@PathVariable String username) {
        long me = CurrentUser.requireId();
        long t = target(username);
        relations.block(me, t);
        return relations.flags(me, t);
    }

    @DeleteMapping("/api/users/{username}/block")
    public Flags unblock(@PathVariable String username) {
        long me = CurrentUser.requireId();
        long t = target(username);
        relations.unblock(me, t);
        return relations.flags(me, t);
    }

    @PutMapping("/api/users/{username}/restrict")
    public Flags restrict(@PathVariable String username) {
        return toggle(username, Op.RESTRICT, true);
    }

    @DeleteMapping("/api/users/{username}/restrict")
    public Flags unrestrict(@PathVariable String username) {
        return toggle(username, Op.RESTRICT, false);
    }

    @PutMapping("/api/users/{username}/mute")
    public Flags mute(@PathVariable String username, @RequestBody MuteForm form) {
        long me = CurrentUser.requireId();
        long t = target(username);
        relations.mute(me, t, form.posts(), form.stories());
        return relations.flags(me, t);
    }

    @PutMapping("/api/users/{username}/close-friend")
    public Flags addCloseFriend(@PathVariable String username) {
        return toggle(username, Op.CLOSE_FRIEND, true);
    }

    @DeleteMapping("/api/users/{username}/close-friend")
    public Flags removeCloseFriend(@PathVariable String username) {
        return toggle(username, Op.CLOSE_FRIEND, false);
    }

    @PutMapping("/api/users/{username}/favorite")
    public Flags addFavorite(@PathVariable String username) {
        return toggle(username, Op.FAVORITE, true);
    }

    @DeleteMapping("/api/users/{username}/favorite")
    public Flags removeFavorite(@PathVariable String username) {
        return toggle(username, Op.FAVORITE, false);
    }

    private enum Op { RESTRICT, CLOSE_FRIEND, FAVORITE }

    private Flags toggle(String username, Op op, boolean on) {
        long me = CurrentUser.requireId();
        long t = target(username);
        switch (op) {
            case RESTRICT -> relations.restrict(me, t, on);
            case CLOSE_FRIEND -> relations.closeFriend(me, t, on);
            case FAVORITE -> relations.favorite(me, t, on);
        }
        return relations.flags(me, t);
    }

    /** 설정 화면 목록: blocked, restricted, muted, close-friends, favorites */
    @GetMapping("/api/me/relations/{kind}")
    public List<UserListItem> list(@PathVariable String kind) {
        return relations.list(CurrentUser.requireId(), kind);
    }

    /** 친한 친구·즐겨찾기 고르기 화면 */
    @GetMapping("/api/me/relations/{kind}/candidates")
    public List<Candidate> candidates(@PathVariable String kind) {
        return relations.candidates(CurrentUser.requireId(), kind);
    }
}
