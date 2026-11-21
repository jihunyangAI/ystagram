package com.yangstagram.follow;

/** 보는 사람 → 상대 관계. 버튼 문구를 정할 때 쓴다. */
public enum Relationship {
    /** 본인 */
    SELF,
    /** 팔로우하지 않음 → "팔로우" (상대가 나를 팔로우하면 화면에서 "맞팔로우") */
    NONE,
    /** 팔로우 중 → "팔로잉" */
    FOLLOWING,
    /** 비공개 계정에 요청을 보내고 승인 대기 → "요청됨" */
    REQUESTED;

    static Relationship of(FollowStatus status) {
        if (status == null) return NONE;
        return status == FollowStatus.ACCEPTED ? FOLLOWING : REQUESTED;
    }
}
