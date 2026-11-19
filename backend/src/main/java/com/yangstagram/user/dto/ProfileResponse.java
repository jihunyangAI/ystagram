package com.yangstagram.user.dto;

import com.yangstagram.user.AccountType;
import com.yangstagram.user.User;

/** 프로필 화면 헤더 정보. */
public record ProfileResponse(
        UserSummary user,
        String bio,
        String pronouns,
        AccountType accountType,
        String category,
        boolean aiProfile,
        int followersCount,
        int followingCount,
        int postsCount,
        boolean me) {

    public static ProfileResponse of(User u, boolean me) {
        return new ProfileResponse(UserSummary.from(u), u.getBio(), u.getPronouns(), u.getAccountType(),
                u.getCategory(), u.isAiProfile(), u.getFollowersCount(), u.getFollowingCount(),
                u.getPostsCount(), me);
    }
}
