package com.yangstagram.post.dto;

import com.yangstagram.algo.AlgorithmSettings.Ranking;
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
                            Integer durationMs, Integer trimStartMs, Integer trimEndMs, List<Overlay> overlays) {
    }

    /** 릴스 자막·글자: startMs~endMs 동안 화면 세로 위치 y(0~1)에 보인다 */
    public record Overlay(String text, int startMs, int endMs, double y) {
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
                              int likeCount, boolean likedByMe, int replyCount, boolean restricted,
                              boolean subscriber) {
    }

    /**
     * 피드 카드·상세 화면용 게시물.
     *
     * @param likeCount       좋아요 수 숨김이 켜져 있고 본인 글이 아니면 null
     * @param likedBy         "OOO님 외 여러 명이 좋아합니다"에 쓸, 내가 팔로우하는 사람 중 한 명 (없으면 null)
     * @param previewComments 피드 카드 아래에 보여 줄 최근 댓글 최대 2개
     * @param repost          피드에서 팔로우하는 사람의 리포스트로 들어온 경우 (아니면 null)
     * @param authorStory     작성자 프로필 사진 둘레의 스토리 링
     * @param aiLabeled       "AI 정보" 라벨
     * @param trial           시험 릴스 (팔로워에게 안 보임, 작성자에게만 표시)
     * @param ad              광고로 들어온 경우 (아니면 null)
     */
    public record PostView(long id, String shortcode, UserSummary author, Relationship authorRelationship,
                           String caption, LocationView location, List<MediaView> media,
                           Integer likeCount, int commentCount, boolean likedByMe, UserSummary likedBy,
                           boolean hideLikeCount, boolean commentsDisabled, Instant createdAt, boolean mine,
                           List<CommentView> previewComments, boolean savedByMe, boolean repostedByMe,
                           int repostCount, Repost repost, Ring authorStory, String type, int viewCount,
                           List<TagView> tags, List<UserSummary> collaborators, boolean collabInvite,
                           boolean aiLabeled, boolean trial, AdInfo ad, Ranking ranking, Extras extras) {

        /** 추천 영역에서 줄 세운 이유를 붙인 복사본 */
        public PostView withRanking(Ranking r) {
            return new PostView(id, shortcode, author, authorRelationship, caption, location, media, likeCount,
                    commentCount, likedByMe, likedBy, hideLikeCount, commentsDisabled, createdAt, mine,
                    previewComments, savedByMe, repostedByMe, repostCount, repost, authorStory, type, viewCount, tags,
                    collaborators, collabInvite, aiLabeled, trial, ad, r, extras);
        }

        /** 광고로 끼워 넣을 때 */
        public PostView withAd(AdInfo a) {
            return new PostView(id, shortcode, author, authorRelationship, caption, location, media, likeCount,
                    commentCount, likedByMe, likedBy, hideLikeCount, commentsDisabled, createdAt, mine,
                    previewComments, savedByMe, repostedByMe, repostCount, repost, authorStory, type, viewCount, tags,
                    collaborators, collabInvite, aiLabeled, trial, a, ranking, extras);
        }
    }

    /**
     * M5-② 항목: 리믹스, 제품 태그, 구독자 전용, 받은 선물.
     *
     * @param remixOf   이 릴스가 리믹스한 원본 (아니면 null)
     * @param giftStars 받은 별 (작성자 본인에게만, 아니면 null)
     */
    public record Extras(RemixRef remixOf, int remixCount, boolean allowRemix, List<ProductTag> products,
                         boolean subscribersOnly, Long giftStars, boolean archived, boolean pinned,
                         com.yangstagram.audio.AudioService.AudioRef audio, boolean canComment,
                         List<PostNoteView> notes, boolean canNote) {
        public static final Extras NONE = new Extras(null, 0, true, List.of(), false, null, false, false, null, true,
                List.of(), false);
    }

    /** 게시물 위 노트: 맞팔로우가 남긴 짧은 글 (3일) */
    public record PostNoteView(long id, UserSummary user, String body, Instant createdAt, boolean mine) {
    }

    /** @param available 원본이 지워졌거나 볼 수 없으면 false (영상 주소 없음) */
    public record RemixRef(String shortcode, UserSummary author, String videoUrl, String thumbUrl, Integer durationMs,
                           boolean available) {
    }

    public record ProductTag(long id, String name, int price, String thumbUrl, int mediaIndex, double x, double y) {
    }

    /**
     * 가짜 광고 정보.
     *
     * @param reason "이 광고가 표시되는 이유" 한 줄
     */
    public record AdInfo(long id, String headline, String cta, String reason) {
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
                           String type, int viewCount, Ranking ranking, boolean pinned) {

        public GridItem withRanking(Ranking r) {
            return new GridItem(shortcode, thumbUrl, mediaCount, likeCount, commentCount, type, viewCount, r, pinned);
        }

        public GridItem pinnedCopy() {
            return new GridItem(shortcode, thumbUrl, mediaCount, likeCount, commentCount, type, viewCount, ranking, true);
        }
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
                                    List<String> collaborators, String filter, Boolean aiLabel,
                                    Instant scheduledAt, Boolean subscribersOnly, Long audioId) {

        public boolean ai() {
            return Boolean.TRUE.equals(aiLabel);
        }

        public boolean hideLikes() {
            return Boolean.TRUE.equals(hideLikeCount);
        }

        public boolean noComments() {
            return Boolean.TRUE.equals(commentsDisabled);
        }
    }

    /**
     * 릴스 만들기 (영상은 video, 첫 화면 사진은 poster 파트).
     *
     * @param trial 시험 릴스: 팔로워가 아닌 사람에게만 보여 주고 24시간 뒤 성과를 본다
     */
    public record CreateReelRequest(String caption, Long durationMs, Integer trimStartMs, Integer trimEndMs,
                                    List<Overlay> overlays, Boolean hideLikeCount, Boolean commentsDisabled,
                                    Boolean aiLabel, Boolean trial, String remixOf, Boolean allowRemix,
                                    Boolean subscribersOnly, Long audioId) {
    }

    /** 게시 후 수정: 캡션·위치·고급 설정 */
    public record UpdatePostRequest(String caption, String location, Boolean hideLikeCount, Boolean commentsDisabled,
                                    Boolean aiLabel) {
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
