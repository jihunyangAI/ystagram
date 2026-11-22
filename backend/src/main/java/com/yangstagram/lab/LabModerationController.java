package com.yangstagram.lab;

import com.yangstagram.moderation.ModerationService;
import com.yangstagram.moderation.ModerationService.QueueItem;
import com.yangstagram.moderation.ModerationService.Stats;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lab 신고·모더레이션 큐 (L10): 대상별로 묶은 신고를 보고 처리한다 */
@RestController
@RequestMapping("/api/lab/moderation")
@ConditionalOnProperty(prefix = "yangstagram.lab", name = "enabled", havingValue = "true")
public class LabModerationController {

    private final ModerationService moderation;

    public LabModerationController(ModerationService moderation) {
        this.moderation = moderation;
    }

    /** @param status OPEN(처리 대기) / RESOLVED(처리됨) */
    @GetMapping
    public List<QueueItem> queue(@RequestParam(defaultValue = "OPEN") String status) {
        return moderation.queue(status);
    }

    @GetMapping("/stats")
    public Stats stats() {
        return moderation.stats();
    }

    /** @param action REMOVE, DISMISS, SUSPEND_7D, SUSPEND_30D, BAN, UNSUSPEND */
    public record ActionRequest(String action) {
    }

    @PostMapping("/{type}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void act(@PathVariable String type, @PathVariable long id, @RequestBody ActionRequest req) {
        moderation.act(type, id, req.action());
    }
}
