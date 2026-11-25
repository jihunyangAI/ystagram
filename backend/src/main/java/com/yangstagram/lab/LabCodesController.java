package com.yangstagram.lab;

import com.yangstagram.account.AccountService;
import com.yangstagram.account.AccountService.LabCode;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lab: "이메일로 보낸" 가입·비밀번호 재설정 코드 (실제 메일 발송 없이 여기서 확인) */
@RestController
@ConditionalOnProperty(prefix = "yangstagram.lab", name = "enabled", havingValue = "true")
public class LabCodesController {

    private final AccountService accounts;

    public LabCodesController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/api/lab/codes")
    public List<LabCode> codes() {
        return accounts.recentCodes();
    }
}
