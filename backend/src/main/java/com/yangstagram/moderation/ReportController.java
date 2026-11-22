package com.yangstagram.moderation;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.moderation.ModerationService.MyReport;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 신고 (사용자). 처리는 Lab 모더레이션 큐(LabModerationController) */
@RestController
@RequestMapping("/api")
public class ReportController {

    private final ModerationService moderation;

    public ReportController(ModerationService moderation) {
        this.moderation = moderation;
    }

    /** 신고 사유 목록 (화면 순서대로) */
    @GetMapping("/reports/reasons")
    public Map<String, String> reasons() {
        return ModerationService.REASONS;
    }

    /**
     * @param targetType POST / COMMENT / USER
     * @param target     게시물 shortcode / 댓글 id / 사용자 이름
     */
    public record ReportRequest(String targetType, String target, String reason, String detail) {
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@RequestBody ReportRequest req) {
        moderation.report(CurrentUser.requireId(), req.targetType(), req.target(), req.reason(), req.detail());
    }

    @GetMapping("/me/reports")
    public List<MyReport> mine() {
        return moderation.mine(CurrentUser.requireId());
    }
}
