package com.yangstagram.dm;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.common.error.ApiException;
import com.yangstagram.dm.CallService.CallView;
import com.yangstagram.live.LiveService;
import com.yangstagram.realtime.Realtime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통화 API와 WebRTC 신호 전달.
 * 신호는 REST로 받아(CSRF 보호 유지) 상대에게 실시간 이벤트 "rtc:signal"로 넘긴다.
 * 통화는 두 사람 사이에서만, 라이브는 진행자와 지금 보고 있는 시청자 사이에서만 주고받을 수 있다.
 */
@RestController
@RequestMapping("/api")
public class CallController {

    private final CallService calls;
    private final LiveService lives;
    private final Realtime realtime;

    public CallController(CallService calls, LiveService lives, Realtime realtime) {
        this.calls = calls;
        this.lives = lives;
        this.realtime = realtime;
    }

    public record StartRequest(Boolean video) {
    }

    /**
     * @param scope "call" 또는 "live"
     * @param id    통화 id 또는 라이브 id
     * @param to    받을 사람 id
     * @param data  화면이 정한 내용 (예: {type: "offer", sdp})
     */
    public record Signal(String scope, long id, long to, Object data) {
    }

    public record SignalEvent(String scope, long id, long from, Object data) {
    }

    @PostMapping("/direct/conversations/{conversationId}/calls")
    @ResponseStatus(HttpStatus.CREATED)
    public CallView start(@PathVariable long conversationId, @RequestBody StartRequest body) {
        return calls.start(conversationId, CurrentUser.requireId(), Boolean.TRUE.equals(body.video()));
    }

    @GetMapping("/calls/{id}")
    public CallView get(@PathVariable long id) {
        return calls.get(id, CurrentUser.requireId());
    }

    @PostMapping("/calls/{id}/accept")
    public CallView accept(@PathVariable long id) {
        return calls.accept(id, CurrentUser.requireId());
    }

    @PostMapping("/calls/{id}/decline")
    public CallView decline(@PathVariable long id) {
        return calls.decline(id, CurrentUser.requireId());
    }

    @PostMapping("/calls/{id}/end")
    public CallView end(@PathVariable long id) {
        return calls.hangUp(id, CurrentUser.requireId());
    }

    @PostMapping("/rtc/signal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void signal(@RequestBody Signal body) {
        long me = CurrentUser.requireId();
        boolean ok = switch (body.scope() == null ? "" : body.scope()) {
            case "call" -> calls.canSignal(body.id(), me, body.to());
            case "live" -> lives.canSignal(body.id(), me, body.to());
            default -> false;
        };
        if (!ok) throw ApiException.forbidden("NO_SESSION", "연결할 수 없는 상대입니다.");
        realtime.send(body.to(), "rtc:signal", new SignalEvent(body.scope(), body.id(), me, body.data()));
    }

    /** 화면이 쓰는 기본값 (통화 30초 대기 등) */
    @GetMapping("/calls/config")
    public Map<String, Object> config() {
        return Map.of("ringTimeoutSec", CallService.RING_TIMEOUT.toSeconds());
    }
}
