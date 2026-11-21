package com.yangstagram.post.dto;

import com.yangstagram.follow.Relationship;
import com.yangstagram.story.StoryRings.Ring;
import com.yangstagram.user.dto.UserSummary;
import java.time.Instant;
import java.util.List;

/** 게시물 관련 응답·요청 모음 */
public final class PostDtos {

    private PostDtos() {
    }

    /**
     * 사진 또는 영상 한 장.
     *
     * @param type       IMAGE / VIDEO (VIDEO면 thumbUrl이 첫 화면 사진)
     * @param durationMs 영상 길이 (사진이면 null)
     */
    public record MediaView(String url, String thumbUrl, int width, int height, String altText, String type,
                            Integer durationMs) {
    }

    /** 사진 속 사람 태그 (x·y는 사진 대비 0~1) */
    public record TagView(UserSummary user, int mediaIndex, double x, double y) {
    }

    public record TagInput(String username, Integer mediaIndex, Double x, Double y) {
    }

    public record LocationView(long id, String name) {
    }

    /**
     * 댓글 한 개.
     *
     * @param canEdit    내 댓글이고 쓴 지 15분 안
     * @param canPin     게시물 주인이 보는 최상위 댓글
     * @param replyCount 답글 수 (최상위 댓글만, 답글이면 0)
     * @param restricted 제한한 사람의 댓글 (쓴 사람과 게시물 주인에게만 보임, 주인이 승인하면 공개)
     */
    public record CommentView(long id, UserSummary author, String body, Instant createdAt, Long parentId,
                              boolean canDelete, boolean canEdit, boolean canPin, boolean edited, boolean pinned,
                              int likeCount, boolean likedByMe, int replyCount, boolean restricted) {
    }

    /**
     * 피드 카드·상세 화면용 게시물.
     *
     * @param likeCount       좋아요 수 숨김이 켜져 있고 본인 글이 아니면 null
     * @param likedBy         "OOO님 외 여러 명이 좋아합니다"에 쓸, 내가 팔로우하는 사람 중 한 명 (없으면 null)
     * @param previewComments 피드 카드 아래에 보여 줄 최근 댓글 최대 2개
     * @param repost          피드에서 팔로우하는 사람의 리포스트로 들어온 경우 (아니면 null)
     * @param authorStory     작성자 프로필 사진 둘레의 스토리 링
     */
    public record PostView(long id, String shortcode, UserSummary author, Relationship authorRelationship,
                           String caption, LocationView location, List<MediaView> media,
                           Integer likeCount, int commentCount, boolean likedByMe, UserSummary likedBy,
                           boolean hideLikeCount, boolean commentsDisabled, Instant createdAt, boolean mine,
                           List<CommentView> previewComments, boolean savedByMe, boolean repostedByMe,
                           int repostCount, Repost repost, Ring authorStory, String type, int viewCount,
                           List<TagView> tags, List<UserSummary> collaborators, boolean collabInvite) {
    }

    /** 피드에서 "OOO님이 리포스트함"으로 보일 때 */
    public record Repost(UserSummary by, String note, Instant createdAt) {
    }

    /**
     * 프로필·탐색 그리드 한 칸.
     *
     * @param type      PHOTO / CAROUSEL / REEL
     * @param viewCount 릴스 조회수 (릴스 탭에 표시)
     */
    public record GridItem(String shortcode, String thumbUrl, int mediaCount, Integer likeCount, int commentCount,
                           String type, int viewCount) {
    }

    /**
     * 게시물 만들기 (사진 파일은 multipart의 files 파트로 따로 받는다). 빠진 항목은 false/없음.
     *
     * @param tags          사람 태그 (최대 20명)
     * @param collaborators 공동 작업자 사용자 이름 (최대 5명, 수락하면 양쪽 프로필·팔로워 피드에 보임)
     * @param filter        적용한 필터 이름 (사진은 화면에서 이미 입혀서 보낸다, 기록용)
     */
    public record CreatePostRequest(String caption, String location, List<String> altTexts,
                                    Boolean hideLikeCount, Boolean commentsDisabled, List<TagInput> tags,
                                    List<String> collaborators, String filter) {

        public boolean hideLikes() {
            return Boolean.TRUE.equals(hideLikeCount);
        }

        public boolean noComments() {
            return Boolean.TRUE.equals(commentsDisabled);
        }
    }

    /** 릴스 만들기 (영상은 video, 첫 화면 사진은 poster 파트) */
    public record CreateReelRequest(String caption, Long durationMs, Boolean hideLikeCount, Boolean commentsDisabled) {
    }

    /** 게시 후 수정: 캡션·위치·고급 설정 */
    public record UpdatePostRequest(String caption, String location, Boolean hideLikeCount, Boolean commentsDisabled) {
    }

    public record CreateCommentRequest(String body, Long parentId) {
    }

    public record EditCommentRequest(String body) {
    }

    public record LikeResult(boolean liked, int likeCount) {
    }

    public record RepostRequest(String note) {
    }

    public record RepostResult(boolean reposted, int repostCount) {
    }

    public record SaveResult(boolean saved) {
    }
}
