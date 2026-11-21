package com.yangstagram.notification;

/** 알림 종류. FOLLOW_REQUEST는 목록 대신 맨 위 "팔로우 요청" 줄에 모아 보여 준다. */
public enum NotificationType {
    LIKE,
    COMMENT,
    REPLY,
    COMMENT_LIKE,
    MENTION,
    FOLLOW,
    FOLLOW_REQUEST,
    FOLLOW_ACCEPTED,
    REPOST,
    STORY_LIKE,
    TAG,
    COLLAB_INVITE
}
