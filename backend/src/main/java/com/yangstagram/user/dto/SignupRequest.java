package com.yangstagram.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 64, message = "이름은 64자 이하여야 합니다.")
        String fullName,

        @NotBlank(message = "사용자 이름을 입력해 주세요.")
        String username,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 6, max = 100, message = "비밀번호는 6자 이상이어야 합니다.")
        String password,

        @NotNull(message = "생년월일을 입력해 주세요.")
        LocalDate birthDate,

        /** 이메일로 받은 6자리 인증 코드 (POST /api/auth/signup/code) */
        String code) {
}
