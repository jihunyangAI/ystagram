package com.yangstagram.channel;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.media.MediaStorage;
import com.yangstagram.media.StoredImage;
import com.yangstagram.realtime.Realtime;
import com.yangstagram.relation.RelationSql;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

/**
 * 브로드캐스트 채널 (기획서 §5 P3): 크리에이터가 여러 팔로워에게 한 방향으로 보내는 채팅방.
 * 주인만 글·사진·투표를 올리고, 참여자는 이모지 반응과 투표만 한다. 사람당 채널 하나.
 * 새 글은 참여자에게 실시간으로 간다("channel:new").
 */
@Service
public class ChannelService {

    public static final int MAX_NAME = 60;
    public static final int MAX_POLL_OPTIONS = 4;
    public static final int PAGE = 30;

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;
    private final Realtime realtime;
    private final MediaStorage storage;
    private final JsonMapper json;

    public ChannelService(NamedParameterJdbcTemplate jdbc, LabClock clock, Realtime realtime, MediaStorage storage,
                          JsonMapper json) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.realtime = realtime;
        this.storage = storage;
        this.json = json;
    }

    /** 받은 메시지함·프로필에서 보는 채널 요약 */
    public record ChannelSummary(long id, String name, UserSummary owner, long memberCount, boolean joined,
                                 boolean mine, String lastText, Instant lastAt, int unread) {
    }

    public record Reaction(String emoji, long count, boolean mine) {
    }

    /**
     * @param poll 투표 (일반 글이면 null)
     */
    public record ChannelMessage(long id, String body, String mediaUrl, Integer mediaWidth, Integer mediaHeight,
                                 Poll poll, List<Reaction> reactions, Instant createdAt) {
    }

    /** @param myVote 내가 고른 보기 (안 했으면 null). 결과는 투표한 뒤나 주인에게만 보인다 */
    public record Poll(List<String> options, List<Long> votes, long total, Integer myVote) {
    }

    public record ChannelView(ChannelSummary channel, List<ChannelMessage> messages, Long nextBefore) {
    }

    // ------------------------------------------------------------ 채널

    @Transactional
    public ChannelSummary create(long me, String rawName) {
        String name = rawName == null ? "" : rawName.strip();
        if (name.isEmpty()) throw ApiException.badRequest("EMPTY_NAME", "채널 이름을 입력해 주세요.");
        if (name.length() > MAX_NAME) throw ApiException.badRequest("NAME_TOO_LONG", "채널 이름은 60자까지입니다.");
        Integer existing = jdbc.queryForObject("select count(*) from channels where owner_id = :me", Map.of("me", me),
                Integer.class);
        if (existing != null && existing > 0) throw ApiException.conflict("CHANNEL_EXISTS", "채널은 한 개만 만들 수 있습니다.");
        Timestamp now = Timestamp.from(Instant.now(clock));
        Long id = jdbc.queryForObject("""
                insert into channels (owner_id, name, created_at) values (:me, :name, :now) returning id
                """, new MapSqlParameterSource("me", me).addValue("name", name).addValue("now", now), Long.class);
        jdbc.update("insert into channel_members (channel_id, user_id, joined_at) values (:c, :me, :now)",
                new MapSqlParameterSource("c", id).addValue("me", me).addValue("now", now));
        return summary(id, me);
    }

    @Transactional
    public void delete(long channelId, long me) {
        requireOwner(channelId, me);
        jdbc.update("delete from channels where id = :c", Map.of("c", channelId));
    }

    /** 내가 참여한 채널 (받은 메시지함 "채널" 탭) */
    public List<ChannelSummary> mine(long me) {
        return jdbc.query(SUMMARY + """
                 where exists (select 1 from channel_members m where m.channel_id = c.id and m.user_id = :me)
                 order by coalesce(last.created_at, c.created_at) desc
                """, Map.of("me", me), (rs, i) -> readSummary(rs, me));
    }

    /** 프로필의 채널 (없으면 null) */
    public ChannelSummary ofUser(String username, long me) {
        return jdbc.query(SUMMARY + " where o.username = :u and " + RelationSql.notBlocked("o.id"),
                Map.of("u", username, "me", me), (rs, i) -> readSummary(rs, me)).stream().findFirst().orElse(null);
    }

    private ChannelSummary summary(long id, long me) {
        return jdbc.query(SUMMARY + " where c.id = :c", Map.of("c", id, "me", me), (rs, i) -> readSummary(rs, me))
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("채널을 찾을 수 없습니다."));
    }

    private static final String SUMMARY = """
            select c.id, c.name, c.owner_id, %s,
                   (select count(*) from channel_members m where m.channel_id = c.id) members,
                   exists (select 1 from channel_members m where m.channel_id = c.id and m.user_id = :me) joined,
                   last.body last_body, last.poll_options is not null last_poll, last.media_url last_media,
                   last.created_at last_at,
                   (select count(*) from channel_messages x where x.channel_id = c.id and x.id > coalesce(
                      (select m.last_read_id from channel_members m where m.channel_id = c.id and m.user_id = :me), 0)
                      and c.owner_id <> :me) unread
            from channels c
            join users o on o.id = c.owner_id and o.deleted_at is null
            left join lateral (select * from channel_messages x where x.channel_id = c.id order by x.id desc limit 1) last
              on true
            """.formatted(UserRows.cols("o"));

    private static ChannelSummary readSummary(java.sql.ResultSet rs, long me) throws java.sql.SQLException {
        String last = rs.getBoolean("last_poll") ? "📊 " + nz(rs.getString("last_body"))
                : rs.getString("last_body") != null ? rs.getString("last_body")
                : rs.getString("last_media") != null ? "사진을 보냈습니다" : null;
        Timestamp at = rs.getTimestamp("last_at");
        return new ChannelSummary(rs.getLong("id"), rs.getString("name"), UserRows.read(rs, "o"), rs.getLong("members"),
                rs.getBoolean("joined"), rs.getLong("owner_id") == me, last, at == null ? null : at.toInstant(),
                rs.getInt("unread"));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @Transactional
    public ChannelSummary join(long channelId, long me) {
        summary(channelId, me);
        jdbc.update("""
                insert into channel_members (channel_id, user_id, joined_at) values (:c, :me, :now) on conflict do nothing
                """, new MapSqlParameterSource("c", channelId).addValue("me", me)
                .addValue("now", Timestamp.from(Instant.now(clock))));
        return summary(channelId, me);
    }

    @Transactional
    public void leave(long channelId, long me) {
        Long owner = ownerOf(channelId);
        if (owner == me) throw ApiException.badRequest("OWNER_CANNOT_LEAVE", "내 채널은 나갈 수 없습니다. 삭제는 채널 메뉴에서 해 주세요.");
        jdbc.update("delete from channel_members where channel_id = :c and user_id = :me", Map.of("c", channelId, "me", me));
    }

    // ------------------------------------------------------------ 메시지

    /** 채널 열기: 최근 글 30개(오래된 순), 열면 읽음 처리. 참여하지 않아도 미리 볼 수 있다 */
    @Transactional
    public ChannelView view(long channelId, long me, Long before) {
        ChannelSummary summary = summary(channelId, me);
        MapSqlParameterSource p = new MapSqlParameterSource("c", channelId).addValue("me", me)
                .addValue("limit", PAGE + 1).addValue("before", before == null ? Long.MAX_VALUE : before);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, body, media_url, media_width, media_height, poll_options::text poll, created_at
                from channel_messages where channel_id = :c and id < :before order by id desc limit :limit
                """, p);
        Long next = null;
        if (rows.size() > PAGE) {
            rows = rows.subList(0, PAGE);
            next = ((Number) rows.getLast().get("id")).longValue();
        }
        List<ChannelMessage> messages = new ArrayList<>(messagesOf(rows, me, summary.mine()));
        java.util.Collections.reverse(messages);
        if (summary.joined() && !messages.isEmpty()) {
            jdbc.update("""
                    update channel_members set last_read_id = greatest(last_read_id, :id) where channel_id = :c and user_id = :me
                    """, p.addValue("id", messages.getLast().id()));
        }
        return new ChannelView(summary(channelId, me), messages, next);
    }

    private List<ChannelMessage> messagesOf(List<Map<String, Object>> rows, long me, boolean owner) {
        if (rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream().map(r -> ((Number) r.get("id")).longValue()).toList();
        Map<Long, List<Reaction>> reactions = new HashMap<>();
        jdbc.query("""
                select message_id, emoji, count(*) n, bool_or(user_id = :me) mine from channel_reactions
                where message_id in (:ids) group by message_id, emoji order by n desc
                """, Map.of("ids", ids, "me", me), rs -> {
            reactions.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                    .add(new Reaction(rs.getString(2), rs.getLong(3), rs.getBoolean(4)));
        });
        Map<Long, Map<Integer, Long>> votes = new HashMap<>();
        Map<Long, Integer> myVotes = new HashMap<>();
        jdbc.query("""
                select message_id, option_index, count(*) n, bool_or(user_id = :me) mine from channel_poll_votes
                where message_id in (:ids) group by message_id, option_index
                """, Map.of("ids", ids, "me", me), rs -> {
            votes.computeIfAbsent(rs.getLong(1), k -> new HashMap<>()).put(rs.getInt(2), rs.getLong(3));
            if (rs.getBoolean(4)) myVotes.put(rs.getLong(1), rs.getInt(2));
        });
        List<ChannelMessage> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long id = ((Number) r.get("id")).longValue();
            Poll poll = null;
            if (r.get("poll") != null) {
                List<String> options = List.of(json.readValue((String) r.get("poll"), String[].class));
                Integer mine = myVotes.get(id);
                Map<Integer, Long> counts = votes.getOrDefault(id, Map.of());
                boolean show = mine != null || owner;
                List<Long> v = new ArrayList<>();
                long total = 0;
                for (int i = 0; i < options.size(); i++) {
                    long n = counts.getOrDefault(i, 0L);
                    total += n;
                    v.add(show ? n : null);
                }
                poll = new Poll(options, v, total, mine);
            }
            out.add(new ChannelMessage(id, (String) r.get("body"), (String) r.get("media_url"),
                    (Integer) r.get("media_width"), (Integer) r.get("media_height"), poll,
                    reactions.getOrDefault(id, List.of()), ((Timestamp) r.get("created_at")).toInstant()));
        }
        return out;
    }

    /** 주인만: 글 (+투표 보기 2~4개) */
    @Transactional
    public ChannelMessage post(long channelId, long me, String rawBody, List<String> pollOptions) {
        requireOwner(channelId, me);
        String body = rawBody == null || rawBody.isBlank() ? null : rawBody.strip();
        if (body != null && body.length() > 1000) throw ApiException.badRequest("MESSAGE_TOO_LONG", "1000자까지 쓸 수 있습니다.");
        String poll = null;
        if (pollOptions != null && !pollOptions.isEmpty()) {
            List<String> opts = pollOptions.stream().filter(o -> o != null && !o.isBlank()).map(String::strip)
                    .map(o -> o.length() > 40 ? o.substring(0, 40) : o).toList();
            if (opts.size() < 2 || opts.size() > MAX_POLL_OPTIONS) {
                throw ApiException.badRequest("BAD_POLL", "투표 보기는 2~4개입니다.");
            }
            if (body == null) throw ApiException.badRequest("EMPTY_QUESTION", "투표 질문을 입력해 주세요.");
            poll = json.writeValueAsString(opts);
        }
        if (body == null) throw ApiException.badRequest("EMPTY_MESSAGE", "내용을 입력해 주세요.");
        return insert(channelId, body, null, poll);
    }

    @Transactional
    public ChannelMessage postImage(long channelId, long me, MultipartFile file) {
        requireOwner(channelId, me);
        return insert(channelId, null, storage.saveImage(file), null);
    }

    private ChannelMessage insert(long channelId, String body, StoredImage img, String poll) {
        Long id = jdbc.queryForObject("""
                insert into channel_messages (channel_id, body, media_url, media_width, media_height, poll_options, created_at)
                values (:c, :body, :url, :w, :h, cast(:poll as jsonb), :now) returning id
                """, new MapSqlParameterSource("c", channelId).addValue("body", body)
                .addValue("url", img == null ? null : img.url()).addValue("w", img == null ? null : img.width())
                .addValue("h", img == null ? null : img.height()).addValue("poll", poll)
                .addValue("now", Timestamp.from(Instant.now(clock))), Long.class);
        long owner = ownerOf(channelId);
        jdbc.update("update channel_members set last_read_id = :id where channel_id = :c and user_id = :o",
                Map.of("id", id, "c", channelId, "o", owner));
        ChannelMessage message = one(id, owner, true);
        List<Long> members = jdbc.queryForList("select user_id from channel_members where channel_id = :c and user_id <> :o",
                Map.of("c", channelId, "o", owner), Long.class);
        realtime.send(members, "channel:new", Map.of("channelId", channelId, "messageId", id));
        return message;
    }

    private ChannelMessage one(long messageId, long me, boolean owner) {
        return messagesOf(jdbc.queryForList("""
                select id, body, media_url, media_width, media_height, poll_options::text poll, created_at
                from channel_messages where id = :id
                """, Map.of("id", messageId)), me, owner).getFirst();
    }

    @Transactional
    public void deleteMessage(long messageId, long me) {
        long channelId = channelOf(messageId);
        requireOwner(channelId, me);
        jdbc.update("delete from channel_messages where id = :id", Map.of("id", messageId));
    }

    /** 참여자 반응 (emoji null이면 취소) */
    @Transactional
    public ChannelMessage react(long messageId, long me, String emoji) {
        long channelId = channelOf(messageId);
        requireMember(channelId, me);
        if (emoji == null || emoji.isBlank()) {
            jdbc.update("delete from channel_reactions where message_id = :m and user_id = :me", Map.of("m", messageId, "me", me));
        } else {
            if (emoji.length() > 16) throw ApiException.badRequest("BAD_EMOJI", "이모지가 너무 깁니다.");
            jdbc.update("""
                    insert into channel_reactions (message_id, user_id, emoji) values (:m, :me, :e)
                    on conflict (message_id, user_id) do update set emoji = excluded.emoji
                    """, Map.of("m", messageId, "me", me, "e", emoji));
        }
        return one(messageId, me, ownerOf(channelId) == me);
    }

    /** 투표 (한 번만, 바꿀 수 없음 — 인스타그램 채널 투표와 같다) */
    @Transactional
    public ChannelMessage vote(long messageId, long me, int option) {
        long channelId = channelOf(messageId);
        requireMember(channelId, me);
        String poll = jdbc.queryForObject("select poll_options::text from channel_messages where id = :id",
                Map.of("id", messageId), String.class);
        if (poll == null) throw ApiException.badRequest("NOT_POLL", "투표가 아닙니다.");
        int size = json.readValue(poll, String[].class).length;
        if (option < 0 || option >= size) throw ApiException.badRequest("BAD_OPTION", "보기를 골라 주세요.");
        jdbc.update("""
                insert into channel_poll_votes (message_id, user_id, option_index) values (:m, :me, :o) on conflict do nothing
                """, Map.of("m", messageId, "me", me, "o", option));
        return one(messageId, me, ownerOf(channelId) == me);
    }

    // ------------------------------------------------------------ 도우미

    private Long ownerOf(long channelId) {
        return jdbc.queryForList("select owner_id from channels where id = :c", Map.of("c", channelId), Long.class)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("채널을 찾을 수 없습니다."));
    }

    private long channelOf(long messageId) {
        return jdbc.queryForList("select channel_id from channel_messages where id = :m", Map.of("m", messageId), Long.class)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("메시지를 찾을 수 없습니다."));
    }

    private void requireOwner(long channelId, long me) {
        if (ownerOf(channelId) != me) throw ApiException.forbidden("NOT_OWNER", "채널 주인만 글을 올릴 수 있습니다.");
    }

    private void requireMember(long channelId, long me) {
        Integer n = jdbc.queryForObject("select count(*) from channel_members where channel_id = :c and user_id = :me",
                Map.of("c", channelId, "me", me), Integer.class);
        if (n == null || n == 0) throw ApiException.forbidden("NOT_MEMBER", "채널에 참여해야 반응할 수 있습니다.");
    }
}
