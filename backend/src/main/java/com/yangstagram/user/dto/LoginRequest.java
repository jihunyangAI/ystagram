package com.yangstagram.user.dto;

import jakarta.validation.constraints.NotBlank;

/** login에는 사용자 이름 또는 이메일을 넣는다. */
public record LoginRequest(
        @NotBlank(message = "사용자 이름 또는 이메일을 입력해 주세요.") String login,
        @NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
}
