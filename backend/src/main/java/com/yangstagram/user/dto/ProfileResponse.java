package com.yangstagram.user.dto;

import com.yangstagram.follow.Relationship;
import com.yangstagram.user.AccountType;
import com.yangstagram.user.User;

/**
 * 프로필 화면 헤더 정보.
 *
 * @param relationship   보는 사람 → 이 사용자 관계 (버튼 문구)
 * @param followsMe      이 사용자가 나를 팔로우 중인가 ("맞팔로우" 버튼)
 * @param canViewContent 게시물·팔로워 목록을 볼 수 있는가 (비공개 계정 잠금)
 */
public record ProfileResponse(
        UserSummary user,
        String bio,
        String pronouns,
        String website,
        AccountType accountType,
        String category,
        boolean aiProfile,
        int followersCount,
        int followingCount,
        int postsCount,
        boolean me,
        Relationship relationship,
        boolean followsMe,
        boolean canViewContent) {

    public static ProfileResponse of(User u, Relationship relationship, boolean followsMe, boolean canViewContent) {
        return new ProfileResponse(UserSummary.from(u), u.getBio(), u.getPronouns(), u.getWebsite(),
                u.getAccountType(), u.getCategory(), u.isAiProfile(), u.getFollowersCount(),
                u.getFollowingCount(), u.getPostsCount(), relationship == Relationship.SELF, relationship,
                followsMe, canViewContent);
    }
}
