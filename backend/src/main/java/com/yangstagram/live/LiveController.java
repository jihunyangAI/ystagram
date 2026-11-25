package com.yangstagram.live;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.live.LiveService.Joined;
import com.yangstagram.live.LiveService.LiveComment;
import com.yangstagram.live.LiveService.LiveView;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 라이브 방송 API. 영상은 WebRTC(브라우저끼리)로 가고, 신호는 {@code /api/rtc/signal}로 주고받는다. */
@RestController
@RequestMapping("/api/lives")
public class LiveController {

    private final LiveService lives;

    public LiveController(LiveService lives) {
        this.lives = lives;
    }

    public record StartRequest(String title) {
    }

    public record CommentRequest(String body) {
    }

    public record HeartRequest(Integer count) {
    }

    public record BadgeRequest(int tier) {
    }

    /** 지금 방송 중 (스토리 줄 맨 앞에 보인다) */
    @GetMapping
    public List<LiveView> active() {
        return lives.active(CurrentUser.requireId());
    }

    @GetMapping("/mine")
    public List<LiveView> mine() {
        return lives.history(CurrentUser.requireId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LiveView start(@RequestBody StartRequest body) {
        return lives.start(CurrentUser.requireId(), body.title());
    }

    @GetMapping("/{id}")
    public LiveView get(@PathVariable long id) {
        return lives.get(id, CurrentUser.requireId());
    }

    @PostMapping("/{id}/end")
    public LiveView end(@PathVariable long id) {
        return lives.end(id, CurrentUser.requireId());
    }

    @PostMapping("/{id}/join")
    public Joined join(@PathVariable long id) {
        return lives.join(id, CurrentUser.requireId());
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable long id) {
        lives.leave(id, CurrentUser.requireId());
    }

    @PostMapping("/{id}/comments")
    public LiveComment comment(@PathVariable long id, @RequestBody CommentRequest body) {
        return lives.comment(id, CurrentUser.requireId(), body.body());
    }

    @PostMapping("/{id}/hearts")
    public Map<String, Integer> heart(@PathVariable long id, @RequestBody(required = false) HeartRequest body) {
        int n = body == null || body.count() == null ? 1 : body.count();
        return Map.of("heartCount", lives.heart(id, CurrentUser.requireId(), n));
    }

    /** 배지 사기 (가짜 결제) */
    @PostMapping("/{id}/badges")
    public LiveComment badge(@PathVariable long id, @RequestBody BadgeRequest body) {
        return lives.badge(id, CurrentUser.requireId(), body.tier());
    }

    public record GuestRequest(String username) {
    }

    /** 게스트 초대 (진행자, 지금 보고 있는 사람) */
    @PostMapping("/{id}/guest")
    public LiveService.LiveView inviteGuest(@PathVariable long id, @RequestBody GuestRequest body) {
        return lives.inviteGuest(id, CurrentUser.requireId(), body.username());
    }

    /** 초대 수락(accept) 또는 내보내기(remove) */
    @PostMapping("/{id}/guest/{action}")
    public LiveService.LiveView answerGuest(@PathVariable long id, @PathVariable String action) {
        return lives.answerGuest(id, CurrentUser.requireId(), action);
    }
}
