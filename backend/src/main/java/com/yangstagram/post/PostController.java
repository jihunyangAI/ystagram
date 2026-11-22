package com.yangstagram.post;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.comment.CommentService;
import com.yangstagram.common.paging.Slice;
import com.yangstagram.follow.FollowService;
import com.yangstagram.follow.FollowService.UserListItem;
import com.yangstagram.follow.Relationship;
import com.yangstagram.post.PostQueries.PostRow;
import com.yangstagram.post.dto.PostDtos.CommentView;
import com.yangstagram.post.dto.PostDtos.CreateCommentRequest;
import com.yangstagram.post.dto.PostDtos.CreatePostRequest;
import com.yangstagram.post.dto.PostDtos.CreateReelRequest;
import com.yangstagram.post.dto.PostDtos.EditCommentRequest;
import com.yangstagram.post.dto.PostDtos.RepostRequest;
import com.yangstagram.post.dto.PostDtos.RepostResult;
import com.yangstagram.post.dto.PostDtos.SaveResult;
import com.yangstagram.post.dto.PostDtos.LikeResult;
import com.yangstagram.post.dto.PostDtos.PostView;
import com.yangstagram.post.dto.PostDtos.UpdatePostRequest;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class PostController {

    private final PostService postService;
    private final CommentService commentService;
    private final FollowService followService;
    private final NamedParameterJdbcTemplate jdbc;
    private final ScheduledPosts scheduled;

    public PostController(PostService postService, CommentService commentService, FollowService followService,
                          NamedParameterJdbcTemplate jdbc, ScheduledPosts scheduled) {
        this.scheduled = scheduled;
        this.postService = postService;
        this.commentService = commentService;
        this.followService = followService;
        this.jdbc = jdbc;
    }

    /** multipart: files(사진 1~20장, 자르기는 화면에서 끝낸 상태) + data(JSON) */
    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PostView create(@RequestPart(value = "files", required = false) List<MultipartFile> files,
                           @RequestPart("data") CreatePostRequest data) {
        return postService.create(CurrentUser.requireId(), files, data);
    }

    // ------------------------------------------------------------ 예약 게시

    /** 예약 게시: 만들기와 같은 multipart, data.scheduledAt 필수 (10분 뒤 ~ 75일 안) */
    @PostMapping(value = "/posts/scheduled", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduledPosts.ScheduledView schedule(@RequestPart(value = "files", required = false) List<MultipartFile> files,
                                                 @RequestPart("data") CreatePostRequest data) {
        return scheduled.schedule(CurrentUser.requireId(), files, data);
    }

    @GetMapping("/me/scheduled")
    public List<ScheduledPosts.ScheduledView> scheduledList() {
        return scheduled.list(CurrentUser.requireId());
    }

    public record RescheduleRequest(java.time.Instant scheduledAt) {
    }

    @PatchMapping("/me/scheduled/{id}")
    public ScheduledPosts.ScheduledView reschedule(@PathVariable long id, @RequestBody RescheduleRequest req) {
        return scheduled.reschedule(id, CurrentUser.requireId(), req.scheduledAt());
    }

    @DeleteMapping("/me/scheduled/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelScheduled(@PathVariable long id) {
        scheduled.cancel(id, CurrentUser.requireId());
    }

    @PostMapping("/me/scheduled/{id}/publish")
    public PostView publishScheduled(@PathVariable long id) {
        return scheduled.publishNow(id, CurrentUser.requireId());
    }

    /** 시험 릴스를 팔로워에게도 공유 */
    @PostMapping("/posts/{shortcode}/trial/share")
    public PostView shareTrial(@PathVariable String shortcode) {
        return postService.shareTrial(shortcode, CurrentUser.requireId());
    }

    /** 릴스 올리기: video(영상) + poster(첫 화면 JPEG) + data(JSON) */
    @PostMapping(value = "/reels", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PostView createReel(@RequestPart("video") MultipartFile video, @RequestPart("poster") MultipartFile poster,
                               @RequestPart("data") CreateReelRequest data) {
        return postService.createReel(CurrentUser.requireId(), video, poster, data);
    }

    @PostMapping("/posts/{shortcode}/view")
    public Map<String, Integer> view(@PathVariable String shortcode) {
        return Map.of("viewCount", postService.recordView(shortcode, CurrentUser.requireId()));
    }

    /** 공동 작업 초대 수락 / 거절 */
    @PutMapping("/posts/{shortcode}/collab")
    public PostView acceptCollab(@PathVariable String shortcode) {
        return postService.answerCollab(shortcode, CurrentUser.requireId(), true);
    }

    @DeleteMapping("/posts/{shortcode}/collab")
    public PostView declineCollab(@PathVariable String shortcode) {
        return postService.answerCollab(shortcode, CurrentUser.requireId(), false);
    }

    /** 내 태그 삭제 */
    @DeleteMapping("/posts/{shortcode}/tags/me")
    public PostView removeMyTag(@PathVariable String shortcode) {
        return postService.removeMyTag(shortcode, CurrentUser.requireId());
    }

    @GetMapping("/posts/{shortcode}")
    public PostView get(@PathVariable String shortcode) {
        return postService.view(shortcode, CurrentUser.requireId());
    }

    @PatchMapping("/posts/{shortcode}")
    public PostView update(@PathVariable String shortcode, @RequestBody UpdatePostRequest req) {
        return postService.update(shortcode, CurrentUser.requireId(), req);
    }

    @DeleteMapping("/posts/{shortcode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String shortcode) {
        postService.delete(shortcode, CurrentUser.requireId());
    }

    // ------------------------------------------------------------ 좋아요

    @PutMapping("/posts/{shortcode}/like")
    public LikeResult like(@PathVariable String shortcode) {
        return postService.like(shortcode, CurrentUser.requireId());
    }

    @DeleteMapping("/posts/{shortcode}/like")
    public LikeResult unlike(@PathVariable String shortcode) {
        return postService.unlike(shortcode, CurrentUser.requireId());
    }

    /** 이 릴스를 리믹스한 릴스 */
    @GetMapping("/posts/{shortcode}/remixes")
    public List<com.yangstagram.post.dto.PostDtos.GridItem> remixes(@PathVariable String shortcode) {
        return postService.remixes(shortcode, CurrentUser.requireId());
    }

    public record RemixSetting(boolean allow) {
    }

    @PutMapping("/posts/{shortcode}/remix-setting")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remixSetting(@PathVariable String shortcode, @RequestBody RemixSetting body) {
        postService.setAllowRemix(shortcode, CurrentUser.requireId(), body.allow());
    }

    /** 좋아요 누른 사람 목록 (최근 순, 페이지당 30명) */
    @GetMapping("/posts/{shortcode}/likes")
    public List<UserListItem> likers(@PathVariable String shortcode, @RequestParam(defaultValue = "0") int page) {
        long me = CurrentUser.requireId();
        PostRow post = postService.getVisible(shortcode, me);
        List<UserSummary> users = jdbc.query("""
                select %s from likes l join users u on u.id = l.user_id
                where l.post_id = :post and u.deleted_at is null
                order by l.created_at desc, u.id
                limit 30 offset :offset
                """.formatted(UserRows.cols("u")),
                new MapSqlParameterSource("post", post.id()).addValue("offset", Math.max(0, page) * 30),
                (rs, i) -> UserRows.read(rs, "u"));
        Map<Long, Relationship> rel = followService.relationships(me, users.stream().map(UserSummary::id).toList());
        return users.stream().map(u -> new UserListItem(u, rel.get(u.id()))).toList();
    }

    // ------------------------------------------------------------ 댓글

    @GetMapping("/posts/{shortcode}/comments")
    public Slice<CommentView> comments(@PathVariable String shortcode,
                                       @RequestParam(required = false) Long cursor) {
        return commentService.list(shortcode, CurrentUser.requireId(), cursor);
    }

    @PostMapping("/posts/{shortcode}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView comment(@PathVariable String shortcode, @RequestBody CreateCommentRequest req) {
        return commentService.create(shortcode, CurrentUser.requireId(), req.body(), req.parentId());
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable long id) {
        commentService.delete(id, CurrentUser.requireId());
    }

    @PatchMapping("/comments/{id}")
    public CommentView editComment(@PathVariable long id, @RequestBody EditCommentRequest req) {
        return commentService.edit(id, CurrentUser.requireId(), req.body());
    }

    @GetMapping("/comments/{id}/replies")
    public List<CommentView> replies(@PathVariable long id) {
        return commentService.replies(id, CurrentUser.requireId());
    }

    @PutMapping("/comments/{id}/pin")
    public CommentView pin(@PathVariable long id) {
        return commentService.pin(id, CurrentUser.requireId(), true);
    }

    @DeleteMapping("/comments/{id}/pin")
    public CommentView unpin(@PathVariable long id) {
        return commentService.pin(id, CurrentUser.requireId(), false);
    }

    /** 제한한 사람의 댓글 승인 (게시물 주인) */
    @PutMapping("/comments/{id}/approve")
    public CommentView approve(@PathVariable long id) {
        return commentService.approve(id, CurrentUser.requireId());
    }

    @PutMapping("/comments/{id}/like")
    public LikeResult likeComment(@PathVariable long id) {
        return commentService.like(id, CurrentUser.requireId(), true);
    }

    @DeleteMapping("/comments/{id}/like")
    public LikeResult unlikeComment(@PathVariable long id) {
        return commentService.like(id, CurrentUser.requireId(), false);
    }

    // ------------------------------------------------------------ 저장·리포스트

    @PutMapping("/posts/{shortcode}/save")
    public SaveResult save(@PathVariable String shortcode) {
        return postService.save(shortcode, CurrentUser.requireId());
    }

    @DeleteMapping("/posts/{shortcode}/save")
    public SaveResult unsave(@PathVariable String shortcode) {
        return postService.unsave(shortcode, CurrentUser.requireId());
    }

    @PutMapping("/posts/{shortcode}/repost")
    public RepostResult repost(@PathVariable String shortcode, @RequestBody(required = false) RepostRequest req) {
        return postService.repost(shortcode, CurrentUser.requireId(), req == null ? null : req.note());
    }

    @DeleteMapping("/posts/{shortcode}/repost")
    public RepostResult unrepost(@PathVariable String shortcode) {
        return postService.unrepost(shortcode, CurrentUser.requireId());
    }

    // ------------------------------------------------------------ 위치 자동완성

    public record LocationItem(long id, String name, String city, long posts) {
    }

    @GetMapping("/locations")
    public List<LocationItem> locations(@RequestParam(defaultValue = "") String q) {
        String term = q.strip().toLowerCase();
        return jdbc.query("""
                select l.id, l.name, l.city, count(p.id) as posts
                from locations l left join posts p on p.location_id = l.id and p.deleted_at is null
                where :q = '' or lower(l.name) like '%' || :q || '%'
                group by l.id
                order by posts desc, l.name
                limit 10
                """, Map.of("q", term),
                (rs, i) -> new LocationItem(rs.getLong("id"), rs.getString("name"), rs.getString("city"),
                        rs.getLong("posts")));
    }
}
