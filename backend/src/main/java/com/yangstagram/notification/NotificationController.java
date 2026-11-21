package com.yangstagram.notification;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.notification.NotificationService.Activity;
import com.yangstagram.notification.NotificationService.Unread;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** 활동 화면: 묶음 처리된 알림 + 팔로우 요청 요약 */
    @GetMapping
    public Activity list() {
        return service.activity(CurrentUser.requireId());
    }

    /** 배지용 안 읽은 수 */
    @GetMapping("/unread")
    public Unread unread() {
        return service.unread(CurrentUser.requireId());
    }

    /** 활동 화면을 열면 모두 읽음으로 */
    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read() {
        service.markAllRead(CurrentUser.requireId());
    }
}
