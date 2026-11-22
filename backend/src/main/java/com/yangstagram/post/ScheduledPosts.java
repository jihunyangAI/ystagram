package com.yangstagram.post;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.media.StoredImage;
import com.yangstagram.post.dto.PostDtos.CreatePostRequest;
import com.yangstagram.post.dto.PostDtos.PostView;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

/**
 * 예약 게시 (기획서 §5 P2): 사진은 예약할 때 저장해 두고, 시각이 되면 실제 게시물로 만든다.
 * 시각은 Lab 가상 시계를 따르므로 시간 가속으로 바로 확인할 수 있다.
 */
@Service
public class ScheduledPosts {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPosts.class);

    public static final Duration MIN_AHEAD = Duration.ofMinutes(10);
    public static final Duration MAX_AHEAD = Duration.ofDays(75);
    public static final int MAX_PENDING = 25;

    private final NamedParameterJdbcTemplate jdbc;
    private final PostService posts;
    private final LabClock clock;
    private final JsonMapper json;

    public ScheduledPosts(NamedParameterJdbcTemplate jdbc, PostService posts, LabClock clock, JsonMapper json) {
        this.jdbc = jdbc;
        this.posts = posts;
        this.clock = clock;
        this.json = json;
    }

    /** 저장해 두는 내용: 만들기 요청(예약 시각 제외) + 저장한 사진 */
    record Payload(CreatePostRequest request, List<StoredImage> images) {
    }

    /**
     * 예약한 게시물 한 건.
     *
     * @param status PENDING(대기) / PUBLISHED(게시됨, shortcode 있음) / FAILED
     */
    public record ScheduledView(long id, String thumbUrl, String captionStart, int mediaCount, Instant scheduledAt,
                                Instant createdAt, String status, String shortcode, String failure) {
    }

    public ScheduledView schedule(long authorId, List<MultipartFile> files, CreatePostRequest req) {
        Instant now = Instant.now(clock);
        Instant at = req.scheduledAt();
        if (at == null) throw ApiException.badRequest("NO_TIME", "예약 시각을 골라 주세요.");
        if (at.isBefore(now.plus(MIN_AHEAD))) {
            throw ApiException.badRequest("TOO_SOON", "예약은 지금부터 10분 뒤 이후로만 할 수 있습니다.");
        }
        if (at.isAfter(now.plus(MAX_AHEAD))) {
            throw ApiException.badRequest("TOO_LATE", "예약은 75일 안으로만 할 수 있습니다.");
        }
        Integer pending = jdbc.queryForObject("""
                select count(*) from scheduled_posts where author_id = :a and published_at is null and failure is null
                """, Map.of("a", authorId), Integer.class);
        if (pending != null && pending >= MAX_PENDING) {
            throw ApiException.badRequest("TOO_MANY_SCHEDULED", "예약 게시물은 " + MAX_PENDING + "개까지 둘 수 있습니다.");
        }
        List<StoredImage> images = posts.validateAndStore(files, req);
        CreatePostRequest stored = new CreatePostRequest(req.caption(), req.location(), req.altTexts(),
                req.hideLikeCount(), req.commentsDisabled(), req.tags(), req.collaborators(), req.filter(),
                req.aiLabel(), null, req.subscribersOnly());
        String caption = req.caption() == null ? null : req.caption().strip();
        Long id = jdbc.queryForObject("""
                insert into scheduled_posts (author_id, payload, thumb_url, caption_start, media_count, scheduled_at,
                                             created_at)
                values (:a, cast(:payload as jsonb), :thumb, :cap, :n, :at, :now) returning id
                """, new MapSqlParameterSource("a", authorId)
                .addValue("payload", json.writeValueAsString(new Payload(stored, images)))
                .addValue("thumb", images.getFirst().thumbUrl())
                .addValue("cap", caption == null || caption.isEmpty() ? null
                        : caption.substring(0, Math.min(60, caption.length())))
                .addValue("n", images.size()).addValue("at", Timestamp.from(at))
                .addValue("now", Timestamp.from(now)), Long.class);
        return get(id, authorId);
    }

    public List<ScheduledView> list(long authorId) {
        return jdbc.query(SELECT + """
                 where s.author_id = :a and (s.published_at is null
                       or s.published_at > cast(:now as timestamptz) - interval '7 days')
                 order by s.published_at is not null, s.scheduled_at
                """, new MapSqlParameterSource("a", authorId).addValue("now", Timestamp.from(Instant.now(clock))),
                (rs, i) -> read(rs));
    }

    private static final String SELECT = """
            select s.id, s.thumb_url, s.caption_start, s.media_count, s.scheduled_at, s.created_at, s.published_at,
                   s.failure, p.shortcode
            from scheduled_posts s left join posts p on p.id = s.post_id and p.deleted_at is null and p.archived_at is null
            """;

    private static ScheduledView read(java.sql.ResultSet rs) throws java.sql.SQLException {
        String status = rs.getString("failure") != null ? "FAILED"
                : rs.getTimestamp("published_at") != null ? "PUBLISHED" : "PENDING";
        return new ScheduledView(rs.getLong("id"), rs.getString("thumb_url"), rs.getString("caption_start"),
                rs.getInt("media_count"), rs.getTimestamp("scheduled_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(), status, rs.getString("shortcode"),
                rs.getString("failure"));
    }

    private ScheduledView get(long id, long authorId) {
        return jdbc.query(SELECT + " where s.id = :id and s.author_id = :a", Map.of("id", id, "a", authorId),
                (rs, i) -> read(rs)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("예약한 게시물을 찾을 수 없습니다."));
    }

    /** 시각 바꾸기 */
    public ScheduledView reschedule(long id, long authorId, Instant at) {
        Instant now = Instant.now(clock);
        if (at == null || at.isBefore(now.plus(MIN_AHEAD)) || at.isAfter(now.plus(MAX_AHEAD))) {
            throw ApiException.badRequest("BAD_TIME", "예약 시각은 10분 뒤부터 75일 안으로 골라 주세요.");
        }
        int n = jdbc.update("""
                update scheduled_posts set scheduled_at = :at
                where id = :id and author_id = :a and published_at is null and failure is null
                """, new MapSqlParameterSource("id", id).addValue("a", authorId).addValue("at", Timestamp.from(at)));
        if (n == 0) throw ApiException.notFound("대기 중인 예약 게시물을 찾을 수 없습니다.");
        return get(id, authorId);
    }

    public void cancel(long id, long authorId) {
        int n = jdbc.update("delete from scheduled_posts where id = :id and author_id = :a and published_at is null",
                Map.of("id", id, "a", authorId));
        if (n == 0) throw ApiException.notFound("대기 중인 예약 게시물을 찾을 수 없습니다.");
    }

    /** 지금 바로 게시 */
    public PostView publishNow(long id, long authorId) {
        get(id, authorId);
        PostView view = publishOne(id);
        if (view == null) throw ApiException.notFound("대기 중인 예약 게시물을 찾을 수 없습니다.");
        return view;
    }

    /** 시각이 된 예약 게시물을 게시한다 (가상 시계 기준, 5초마다) */
    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void publishDue() {
        List<Long> due = jdbc.queryForList("""
                select id from scheduled_posts
                where published_at is null and failure is null and scheduled_at <= :now
                order by scheduled_at limit 50
                """, Map.of("now", Timestamp.from(Instant.now(clock))), Long.class);
        for (Long id : due) {
            try {
                publishOne(id);
            } catch (RuntimeException e) {
                log.warn("예약 게시 실패 id={}: {}", id, e.getMessage());
            }
        }
    }

    /** 한 건 게시. 이미 게시됐으면 null. 동시에 두 번 게시되지 않게 행을 잠근다 */
    private PostView publishOne(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                update scheduled_posts set published_at = :now
                where id = :id and published_at is null and failure is null
                returning author_id, payload::text as payload
                """, new MapSqlParameterSource("id", id).addValue("now", Timestamp.from(Instant.now(clock))));
        if (rows.isEmpty()) return null;
        long authorId = ((Number) rows.getFirst().get("author_id")).longValue();
        Payload payload = json.readValue((String) rows.getFirst().get("payload"), Payload.class);
        Integer active = jdbc.queryForObject("select count(*) from users where id = :a and deleted_at is null",
                Map.of("a", authorId), Integer.class);
        if (active == null || active == 0) {
            jdbc.update("update scheduled_posts set failure = '계정이 삭제되어 게시하지 않았습니다.', published_at = null "
                    + "where id = :id", Map.of("id", id));
            return null;
        }
        PostView view;
        try {
            view = posts.publish(authorId, payload.images(), payload.request());
        } catch (RuntimeException e) {
            String reason = e instanceof ApiException ? e.getMessage() : "게시하지 못했습니다.";
            jdbc.update("update scheduled_posts set failure = :f, published_at = null where id = :id",
                    Map.of("f", reason.length() > 200 ? reason.substring(0, 200) : reason, "id", id));
            throw e;
        }
        jdbc.update("update scheduled_posts set post_id = :p where id = :id", Map.of("p", view.id(), "id", id));
        log.info("예약 게시: id={} → {}", id, view.shortcode());
        return view;
    }
}
