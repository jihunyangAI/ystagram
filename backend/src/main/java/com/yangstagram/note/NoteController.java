package com.yangstagram.note;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.dm.DmDtos.MessageView;
import com.yangstagram.dm.DmService;
import com.yangstagram.relation.RelationSql;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 노트 (기획서 §5 P2): DM 받은 편지함 위쪽에 보이는 60자 짧은 글. 24시간 뒤 사라지고 사람당 하나.
 * 보이는 사람: 서로 팔로우하는 사람(MUTUAL) 또는 친한 친구(CLOSE_FRIENDS). 답장하면 DM으로 간다.
 */
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    public static final int MAX_LENGTH = 60;
    public static final Duration LIFETIME = Duration.ofHours(24);
    private static final Set<String> AUDIENCES = Set.of("MUTUAL", "CLOSE_FRIENDS");

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;
    private final DmService dm;

    public NoteController(NamedParameterJdbcTemplate jdbc, LabClock clock, DmService dm) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.dm = dm;
    }

    public record NoteView(UserSummary user, String body, String audience, Instant createdAt, Instant expiresAt,
                           boolean mine) {
    }

    public record Notes(NoteView mine, List<NoteView> others) {
    }

    public record NoteRequest(String body, String audience) {
    }

    public record ReplyRequest(String body) {
    }

    private static final String SELECT = """
            select n.body, n.audience, n.created_at, n.expires_at, %s
            from notes n join users u on u.id = n.user_id
            """.formatted(UserRows.cols("u"));

    @GetMapping
    public Notes list() {
        long me = CurrentUser.requireId();
        MapSqlParameterSource p = params(me);
        NoteView mine = jdbc.query(SELECT + " where n.user_id = :me and n.expires_at > :now", p,
                (rs, i) -> read(rs, true)).stream().findFirst().orElse(null);
        List<NoteView> others = jdbc.query(SELECT + """
                 where n.user_id <> :me and n.expires_at > :now and u.deleted_at is null and %s
                   and case n.audience
                         when 'CLOSE_FRIENDS' then exists (select 1 from close_friends cf
                                                           where cf.user_id = n.user_id and cf.friend_id = :me)
                         else exists (select 1 from follows a where a.follower_id = :me and a.following_id = n.user_id
                                      and a.status = 'ACCEPTED')
                          and exists (select 1 from follows b where b.follower_id = n.user_id and b.following_id = :me
                                      and b.status = 'ACCEPTED')
                       end
                 order by n.created_at desc
                 limit 30
                """.formatted(RelationSql.notBlocked("n.user_id")), p, (rs, i) -> read(rs, false));
        return new Notes(mine, others);
    }

    /** 노트 남기기 (있으면 바꾼다, 24시간 새로 시작) */
    @PutMapping
    public NoteView put(@RequestBody NoteRequest req) {
        long me = CurrentUser.requireId();
        String body = req.body() == null ? "" : req.body().strip();
        if (body.isEmpty()) throw ApiException.badRequest("EMPTY_NOTE", "노트 내용을 입력해 주세요.");
        if (body.codePointCount(0, body.length()) > MAX_LENGTH) {
            throw ApiException.badRequest("NOTE_TOO_LONG", "노트는 " + MAX_LENGTH + "자까지 쓸 수 있습니다.");
        }
        String audience = req.audience() == null ? "MUTUAL" : req.audience();
        if (!AUDIENCES.contains(audience)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_AUDIENCE", "공개 대상은 MUTUAL 또는 CLOSE_FRIENDS입니다.");
        }
        Instant now = Instant.now(clock);
        jdbc.update("""
                insert into notes (user_id, body, audience, created_at, expires_at) values (:me, :b, :a, :now, :exp)
                on conflict (user_id) do update set body = excluded.body, audience = excluded.audience,
                  created_at = excluded.created_at, expires_at = excluded.expires_at
                """, params(me).addValue("b", body).addValue("a", audience)
                .addValue("exp", Timestamp.from(now.plus(LIFETIME))));
        return list().mine();
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete() {
        jdbc.update("delete from notes where user_id = :me", Map.of("me", CurrentUser.requireId()));
    }

    /** 노트에 답장: 노트 주인에게 DM (원래 노트 문구가 함께 보인다) */
    @PostMapping("/{username}/reply")
    @Transactional
    public MessageView reply(@PathVariable String username, @RequestBody ReplyRequest req) {
        long me = CurrentUser.requireId();
        String note = list().others().stream().filter(n -> n.user().username().equals(username)).findFirst()
                .map(NoteView::body)
                .orElseThrow(() -> ApiException.notFound("노트를 찾을 수 없습니다. 이미 사라졌을 수 있어요."));
        return dm.noteReply(me, username, note, req.body());
    }

    private MapSqlParameterSource params(long me) {
        return new MapSqlParameterSource("me", me).addValue("now", Timestamp.from(Instant.now(clock)));
    }

    private static NoteView read(java.sql.ResultSet rs, boolean mine) throws java.sql.SQLException {
        return new NoteView(UserRows.read(rs, "u"), rs.getString("body"), rs.getString("audience"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("expires_at").toInstant(), mine);
    }
}
