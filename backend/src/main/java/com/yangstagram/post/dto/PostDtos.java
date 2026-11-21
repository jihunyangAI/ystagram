package com.yangstagram.post.dto;

import com.yangstagram.follow.Relationship;
import com.yangstagram.user.dto.UserSummary;
import java.time.Instant;
import java.util.List;

/** 게시물 관련 응답·요청 모음 */
public final class PostDtos {

    private PostDtos() {
    }

    public record MediaView(String url, String thumbUrl, int width, int height, String altText) {
    }

    public record LocationView(long id, String name) {
    }

    public record CommentView(long id, UserSummary author, String body, Instant createdAt, Long parentId,
                              boolean canDelete) {
    }

    /**
     * 피드 카드·상세 화면용 게시물.
     *
     * @param likeCount       좋아요 수 숨김이 켜져 있고 본인 글이 아니면 null
     * @param likedBy         "OOO님 외 여러 명이 좋아합니다"에 쓸, 내가 팔로우하는 사람 중 한 명 (없으면 null)
     * @param previewComments 피드 카드 아래에 보여 줄 최근 댓글 최대 2개
     */
    public record PostView(long id, String shortcode, UserSummary author, Relationship authorRelationship,
                           String caption, LocationView location, List<MediaView> media,
                           Integer likeCount, int commentCount, boolean likedByMe, UserSummary likedBy,
                           boolean hideLikeCount, boolean commentsDisabled, Instant createdAt, boolean mine,
                           List<CommentView> previewComments) {
    }

    /** 프로필 그리드 한 칸 */
    public record GridItem(String shortcode, String thumbUrl, int mediaCount, Integer likeCount, int commentCount) {
    }

    /** 게시물 만들기 (사진 파일은 multipart의 files 파트로 따로 받는다). 빠진 항목은 false */
    public record CreatePostRequest(String caption, String location, List<String> altTexts,
                                    Boolean hideLikeCount, Boolean commentsDisabled) {

        public boolean hideLikes() {
            return Boolean.TRUE.equals(hideLikeCount);
        }

        public boolean noComments() {
            return Boolean.TRUE.equals(commentsDisabled);
        }
    }

    /** 게시 후 수정: 캡션·위치·고급 설정 */
    public record UpdatePostRequest(String caption, String location, Boolean hideLikeCount, Boolean commentsDisabled) {
    }

    public record CreateCommentRequest(String body, Long parentId) {
    }

    public record LikeResult(boolean liked, int likeCount) {
    }
}
