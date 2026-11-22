package com.yangstagram.dm;

import com.yangstagram.algo.Recommendations;
import com.yangstagram.algo.Recommendations.BlendItem;
import com.yangstagram.algo.Recommendations.Page;
import com.yangstagram.auth.CurrentUser;
import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Blend (기획서 §5 P3, 🟡): 1:1 대화방에서 초대 → 상대가 수락하면 두 사람의 관심사를 합친 릴스 피드.
 * 릴스마다 누구를 위한 추천인지 보여 준다. 대화방마다 하나, 둘 중 누구든 그만두면 사라진다.
 */
@RestController
@RequestMapping("/api")
public class BlendController {

    private final NamedParameterJdbcTemplate jdbc;
    private final DmService dm;
    private final Recommendations recommendations;
    private final LabClock clock;

    public BlendController(NamedParameterJdbcTemplate jdbc, DmService dm, Recommendations recommendations,
                           LabClock clock) {
        this.jdbc = jdbc;
        this.dm = dm;
        this.recommendations = recommendations;
        this.clock = clock;
    }

    public record BlendView(long id, long conversationId, String status, UserSummary inviter, UserSummary invitee,
                            boolean invitedMe, Instant createdAt) {
    }

    /** 대화방의 Blend (없으면 204) */
    @GetMapping("/direct/conversations/{id}/blend")
    public org.springframework.http.ResponseEntity<BlendView> ofConversation(@PathVariable long id) {
        long me = CurrentUser.requireId();
        requireMember(id, me);
        return jdbc.queryForList("select id from blends where conversation_id = :c", Map.of("c", id), Long.class)
                .stream().findFirst().map(b -> org.springframework.http.ResponseEntity.ok(get(b, me)))
                .orElse(org.springframework.http.ResponseEntity.noContent().build());
    }

    /** 초대 (1:1 대화방만). 이미 있으면 그대로 */
    @PostMapping("/direct/conversations/{id}/blend")
    @Transactional
    public BlendView invite(@PathVariable long id) {
        long me = CurrentUser.requireId();
        requireMember(id, me);
        Map<String, Object> c = jdbc.queryForMap("select is_group, direct_key from conversations where id = :c",
                Map.of("c", id));
        if (Boolean.TRUE.equals(c.get("is_group")) || c.get("direct_key") == null) {
            throw ApiException.badRequest("GROUP_BLEND", "Blend는 1:1 대화에서만 만들 수 있습니다.");
        }
        Long existing = jdbc.queryForList("select id from blends where conversation_id = :c", Map.of("c", id), Long.class)
                .stream().findFirst().orElse(null);
        if (existing != null) return get(existing, me);
        long other = 0;
        for (String part : ((String) c.get("direct_key")).split(":")) {
            if (Long.parseLong(part) != me) other = Long.parseLong(part);
        }
        Long blendId = jdbc.queryForObject("""
                insert into blends (conversation_id, inviter_id, invitee_id, created_at) values (:c, :me, :o, :now)
                returning id
                """, new MapSqlParameterSource("c", id).addValue("me", me).addValue("o", other)
                .addValue("now", Timestamp.from(Instant.now(clock))), Long.class);
        dm.blendInvite(id, me, blendId); // 대화방에 초대 메시지 (차단 등은 여기서 막힌다)
        return get(blendId, me);
    }

    /** 초대받은 사람이 참여 */
    @PostMapping("/blends/{id}/accept")
    @Transactional
    public BlendView accept(@PathVariable long id) {
        long me = CurrentUser.requireId();
        int n = jdbc.update("""
                update blends set status = 'ACTIVE', accepted_at = :now where id = :id and invitee_id = :me
                """, new MapSqlParameterSource("id", id).addValue("me", me)
                .addValue("now", Timestamp.from(Instant.now(clock))));
        if (n == 0) throw ApiException.notFound("Blend 초대를 찾을 수 없습니다.");
        return get(id, me);
    }

    /** 그만두기 (둘 중 누구든, Blend가 사라진다) */
    @DeleteMapping("/blends/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable long id) {
        long me = CurrentUser.requireId();
        int n = jdbc.update("delete from blends where id = :id and (inviter_id = :me or invitee_id = :me)",
                Map.of("id", id, "me", me));
        if (n == 0) throw ApiException.notFound("Blend를 찾을 수 없습니다.");
    }

    @GetMapping("/blends/{id}")
    public BlendView view(@PathVariable long id) {
        return get(id, CurrentUser.requireId());
    }

    @GetMapping("/blends/{id}/reels")
    public Page<BlendItem> reels(@PathVariable long id, @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(required = false) Long since) {
        long me = CurrentUser.requireId();
        BlendView b = get(id, me);
        if (!"ACTIVE".equals(b.status())) throw ApiException.badRequest("NOT_ACTIVE", "상대가 아직 Blend에 참여하지 않았습니다.");
        UserSummary other = b.inviter().id() == me ? b.invitee() : b.inviter();
        UserSummary self = b.inviter().id() == me ? b.inviter() : b.invitee();
        return recommendations.blendReels(me, other.id(), self.username(), other.username(), page, since);
    }

    private BlendView get(long id, long me) {
        List<BlendView> list = jdbc.query("""
                select b.*, %s, %s from blends b
                join users i on i.id = b.inviter_id join users v on v.id = b.invitee_id
                where b.id = :id and (b.inviter_id = :me or b.invitee_id = :me)
                """.formatted(UserRows.cols("i"), UserRows.cols("v")), Map.of("id", id, "me", me), (rs, n) ->
                new BlendView(rs.getLong("id"), rs.getLong("conversation_id"), rs.getString("status"),
                        UserRows.read(rs, "i"), UserRows.read(rs, "v"), rs.getLong("invitee_id") == me,
                        rs.getTimestamp("created_at").toInstant()));
        if (list.isEmpty()) throw ApiException.notFound("Blend를 찾을 수 없습니다.");
        return list.getFirst();
    }

    private void requireMember(long conversationId, long me) {
        Integer n = jdbc.queryForObject("""
                select count(*) from conversation_members where conversation_id = :c and user_id = :me
                """, Map.of("c", conversationId, "me", me), Integer.class);
        if (n == null || n == 0) throw ApiException.notFound("대화방을 찾을 수 없습니다.");
    }
}
