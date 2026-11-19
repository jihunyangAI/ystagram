package com.yangstagram.user.dto;

import com.yangstagram.user.User;

/** 목록·카드 등 여러 곳에서 쓰는 사용자 요약 정보. */
public record UserSummary(
        Long id,
        String username,
        String fullName,
        String avatarUrl,
        String avatarSeed,
        boolean verified,
        boolean isPrivate) {

    public static UserSummary from(User u) {
        return new UserSummary(u.getId(), u.getUsername(), u.getFullName(), u.getAvatarUrl(),
                u.getAvatarSeed(), u.isVerified(), u.isPrivateAccount());
    }
}
