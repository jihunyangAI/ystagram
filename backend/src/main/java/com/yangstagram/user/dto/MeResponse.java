package com.yangstagram.user.dto;

import com.yangstagram.user.AccountType;
import com.yangstagram.user.Teens;
import com.yangstagram.user.User;
import java.time.LocalDate;

/**
 * 로그인한 본인 정보.
 *
 * @param teen 10대 계정 (만 18세 미만, 기본 보호 규칙 적용)
 */
public record MeResponse(UserSummary user, String email, AccountType accountType, boolean fake, boolean teen) {

    public static MeResponse from(User u) {
        return new MeResponse(UserSummary.from(u), u.getEmail(), u.getAccountType(), u.isFake(),
                Teens.isTeen(u.getBirthDate(), LocalDate.now()));
    }
}
