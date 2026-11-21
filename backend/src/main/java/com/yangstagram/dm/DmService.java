package com.yangstagram.dm;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.dm.DmDtos.ConversationView;
import com.yangstagram.dm.DmDtos.InboxItem;
import com.yangstagram.dm.DmDtos.LastMessage;
import com.yangstagram.dm.DmDtos.Media;
import com.yangstagram.dm.DmDtos.Member;
import com.yangstagram.dm.DmDtos.MessagePage;
import com.yangstagram.dm.DmDtos.MessageView;
import com.yangstagram.dm.DmDtos.Reaction;
import com.yangstagram.dm.DmDtos.ReadEvent;
import com.yangstagram.dm.DmDtos.ReplyTo;
import com.yangstagram.dm.DmDtos.SharedPost;
import com.yangstagram.dm.DmDtos.SharedStory;
import com.yangstagram.dm.DmDtos.TypingEvent;
import com.yangstagram.dm.DmDtos.Unread;
import com.yangstagram.dm.DmDtos.UnsendEvent;
import com.yangstagram.media.MediaStorage;
import com.yangstagram.media.StoredImage;
import com.yangstagram.post.PostQueries;
import com.yangstagram.realtime.Realtime;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * DM (분석 문서 §15, 기획서 §6.6).
 * <ul>
 *   <li>받은 메시지함: 내가 팔로우하는 사람이 보낸 대화 → 기본, 그 외 → 요청. 요청에 답장하거나 수락하면 기본으로</li>
 *   <li>메시지 수정 15분, 전송 취소, 반응(이모지 1개), 답장, 사진, 게시물 공유, 스토리 답장</li>
 *   <li>모든 변경은 대화 참여자 전원에게 실시간 이벤트로 보낸다 (보낸 사람의 다른 탭 포함)</li>
 * </ul>
 */
@Service
public class DmService {

    public static final int MAX_BODY = 1000;
    public static final int MAX_GROUP = 32;
    private static final int PAGE = 30;
    private static final Set<String> FOLDERS = Set.of("PRIMARY", "GENERAL", "REQUEST");

    private final NamedParameterJdbcTemplate jdbc;
    private final Realtime realtime;
    private final MediaStorage storage;
    private final LabClock clock;

    public DmService(NamedParameterJdbcTemplate jdbc, Realtime realtime, MediaStorage storage, LabClock clock) {
        this.jdbc = jdbc;
        this.realtime = realtime;
        this.storage = storage;
        this.clock = clock;
    }

    // ------------------------------------------------------------ 받은 메시지함

    public List<InboxItem> inbox(long me, String folder) {
        String f = folder == null ? "PRIMARY" : folder.toUpperCase();
        if (!FOLDERS.contains(f)) throw ApiException.badRequest("BAD_FOLDER", "알 수 없는 메시지함입니다.");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select c.id, c.is_group, c.title, cm.folder, cm.last_read_message_id,
                       lm.id as msg_id, lm.sender_id, lm.kind, lm.body, lm.created_at as msg_at, lm.unsent_at
                from conversation_members cm
                join conversations c on c.id = cm.conversation_id
                left join lateral (
                  select m.* from messages m where m.conversation_id = c.id order by m.id desc limit 1
                ) lm on true
                where cm.user_id = :me and cm.folder = :folder and lm.id is not null
                order by c.last_message_at desc, c.id desc
                limit 100
                """, Map.of("me", me, "folder", f));
        Map<Long, List<UserSummary>> members = otherMembers(me, rows.stream().map(r -> toLong(r.get("id"))).toList());
        Map<Long, Instant> lastActive = new HashMap<>();
        Set<Long> userIds = new LinkedHashSet<>();
        members.values().forEach(list -> list.forEach(u -> userIds.add(u.id())));
        if (!userIds.isEmpty()) {
            jdbc.query("select id, last_active_at from users where id in (:ids)", Map.of("ids", userIds), rs -> {
                Timestamp t = rs.getTimestamp(2);
                if (t != null) lastActive.put(rs.getLong(1), t.toInstant());
            });
        }

        List<InboxItem> items = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long id = toLong(r.get("id"));
            boolean group = (Boolean) r.get("is_group");
            List<UserSummary> others = members.getOrDefault(id, List.of());
            long msgId = toLong(r.get("msg_id"));
            long sender = toLong(r.get("sender_id"));
            String text = r.get("unsent_at") != null ? null : (String) r.get("body");
            LastMessage last = new LastMessage(msgId, sender, (String) r.get("kind"), text,
                    ((Timestamp) r.get("msg_at")).toInstant());
            boolean unread = sender != me && msgId > toLong(r.get("last_read_message_id"));
            UserSummary single = !group && !others.isEmpty() ? others.getFirst() : null;
            items.add(new InboxItem(id, group, (String) r.get("title"), others, (String) r.get("folder"), last, unread,
                    single != null && realtime.isOnline(single.id()), single == null ? null : lastActive.get(single.id())));
        }
        return items;
    }

    /** 안 읽은 대화 수 (메시지함별) */
    public Unread unread(long me) {
        Map<String, Long> counts = new HashMap<>();
        jdbc.query("""
                select cm.folder, count(*) from conversation_members cm
                where cm.user_id = :me and exists (
                  select 1 from messages m where m.conversation_id = cm.conversation_id
                  and m.id > cm.last_read_message_id and m.sender_id <> :me)
                group by cm.folder
                """, Map.of("me", me), rs -> {
            counts.put(rs.getString(1), rs.getLong(2));
        });
        return new Unread(counts.getOrDefault("PRIMARY", 0L), counts.getOrDefault("GENERAL", 0L),
                counts.getOrDefault("REQUEST", 0L));
    }

    // ------------------------------------------------------------ 대화방

    /** 1명이면 1:1 대화방(있으면 기존 방), 2명 이상이면 그룹 */
    @Transactional
    public ConversationView open(long me, List<String> usernames, String title) {
        Set<String> names = new LinkedHashSet<>();
        if (usernames != null) usernames.forEach(n -> names.add(n.strip().toLowerCase()));
        names.remove(usernameOf(me));
        if (names.isEmpty()) throw ApiException.badRequest("NO_RECIPIENT", "받는 사람을 선택해 주세요.");
        if (names.size() + 1 > MAX_GROUP) {
            throw ApiException.badRequest("GROUP_TOO_LARGE", "그룹 대화는 최대 " + MAX_GROUP + "명입니다.");
        }
        List<Long> ids = jdbc.queryForList(
                "select id from users where username in (:names) and deleted_at is null",
                new MapSqlParameterSource("names", names), Long.class);
        if (ids.size() != names.size()) {
            throw ApiException.notFound("받는 사람을 찾을 수 없습니다.");
        }
        Timestamp now = Timestamp.from(Instant.now(clock));
        long id;
        if (ids.size() == 1) {
            long other = ids.getFirst();
            String key = Math.min(me, other) + ":" + Math.max(me, other);
            id = jdbc.queryForObject("""
                    insert into conversations (is_group, direct_key, created_by, created_at, last_message_at)
                    values (false, :key, :me, :now, :now)
                    on conflict (direct_key) do update set direct_key = excluded.direct_key
                    returning id
                    """, new MapSqlParameterSource("key", key).addValue("me", me).addValue("now", now), Long.class);
            addMember(id, me, "PRIMARY", now);
            addMember(id, other, folderFor(other, me), now);
        } else {
            String t = title == null || title.isBlank() ? null : title.strip();
            if (t != null && t.length() > 100) t = t.substring(0, 100);
            id = jdbc.queryForObject("""
                    insert into conversations (is_group, title, created_by, created_at, last_message_at)
                    values (true, :title, :me, :now, :now) returning id
                    """, new MapSqlParameterSource("title", t).addValue("me", me).addValue("now", now), Long.class);
            addMember(id, me, "PRIMARY", now);
            for (Long other : ids) addMember(id, other, folderFor(other, me), now);
        }
        return conversation(id, me);
    }

    private String usernameOf(long id) {
        return jdbc.queryForObject("select username from users where id = :id", Map.of("id", id), String.class);
    }

    /** 받는 사람이 보낸 사람을 팔로우하면 기본, 아니면 요청함 */
    private String folderFor(long recipient, long sender) {
        Integer n = jdbc.queryForObject("""
                select count(*) from follows where follower_id = :r and following_id = :s and status = 'ACCEPTED'
                """, Map.of("r", recipient, "s", sender), Integer.class);
        return n != null && n > 0 ? "PRIMARY" : "REQUEST";
    }

    private void addMember(long conversationId, long userId, String folder, Timestamp now) {
        jdbc.update("""
                insert into conversation_members (conversation_id, user_id, folder, joined_at)
                values (:c, :u, :f, :now) on conflict do nothing
                """, new MapSqlParameterSource("c", conversationId).addValue("u", userId).addValue("f", folder)
                .addValue("now", now));
    }

    public ConversationView conversation(long id, long me) {
        Map<String, Object> mine = membership(id, me);
        Map<String, Object> c = jdbc.queryForMap("select is_group, title from conversations where id = :id",
                Map.of("id", id));
        List<Member> members = jdbc.query("""
                select %s, cm.last_read_message_id, u.last_active_at
                from conversation_members cm join users u on u.id = cm.user_id
                where cm.conversation_id = :id and cm.user_id <> :me
                order by cm.joined_at, u.id
                """.formatted(UserRows.cols("u")), Map.of("id", id, "me", me), (rs, i) -> {
            UserSummary u = UserRows.read(rs, "u");
            Timestamp t = rs.getTimestamp("last_active_at");
            return new Member(u, rs.getLong("last_read_message_id"), realtime.isOnline(u.id()),
                    t == null ? null : t.toInstant());
        });
        return new ConversationView(id, (Boolean) c.get("is_group"), (String) c.get("title"), members,
                (String) mine.get("folder"), toLong(mine.get("last_read_message_id")));
    }

    /** 요청 수락 → 기본 메시지함으로 */
    @Transactional
    public ConversationView accept(long id, long me) {
        membership(id, me);
        jdbc.update("update conversation_members set folder = 'PRIMARY' where conversation_id = :c and user_id = :me",
                Map.of("c", id, "me", me));
        return conversation(id, me);
    }

    /** 내 메시지함에서 대화방 삭제 (상대에게는 남아 있다) */
    @Transactional
    public void leave(long id, long me) {
        membership(id, me);
        jdbc.update("delete from conversation_members where conversation_id = :c and user_id = :me",
                Map.of("c", id, "me", me));
    }

    // ------------------------------------------------------------ 메시지

    private static final String MESSAGE_SELECT = """
            select m.*, %s,
                   p.shortcode as p_shortcode, p.caption as p_caption, p.deleted_at as p_deleted,
                   (select x.thumb_url from post_media x where x.post_id = p.id and x.position = 0) as p_thumb,
                   %s,
                   case when p.id is null then false else %s end as p_visible,
                   s.media_url as s_url, s.expires_at as s_expires, s.deleted_at as s_deleted, s.author_id as s_author,
                   su.username as s_username, su.full_name as s_full_name, su.avatar_url as s_avatar_url,
                   su.avatar_seed as s_avatar_seed, su.is_verified as s_is_verified, su.is_private as s_is_private,
                   su.id as s_id,
                   r.id as r_id, r.body as r_body, r.kind as r_kind, r.unsent_at as r_unsent, ru.username as r_username
            from messages m
            join users u on u.id = m.sender_id
            left join posts p on p.id = m.post_id
            left join users a on a.id = p.author_id
            left join stories s on s.id = m.story_id
            left join users su on su.id = s.author_id
            left join messages r on r.id = m.reply_to_id
            left join users ru on ru.id = r.sender_id
            """.formatted(UserRows.cols("u"), UserRows.cols("a"), PostQueries.VISIBLE);

    /** 최근 메시지부터 30개씩 (화면에는 오래된 순으로 뒤집어 보여 준다) */
    public MessagePage messages(long conversationId, long me, Long before) {
        membership(conversationId, me);
        MapSqlParameterSource p = new MapSqlParameterSource("c", conversationId).addValue("me", me)
                .addValue("before", before == null ? Long.MAX_VALUE : before).addValue("limit", PAGE + 1);
        List<MessageView> items = readMessages(MESSAGE_SELECT + """
                where m.conversation_id = :c and m.id < :before
                order by m.id desc limit :limit
                """, p, me);
        Long next = null;
        if (items.size() > PAGE) {
            items = items.subList(0, PAGE);
            next = items.getLast().id();
        }
        return new MessagePage(items, next);
    }

    private MessageView message(long id, long me) {
        return readMessages(MESSAGE_SELECT + " where m.id = :id",
                new MapSqlParameterSource("id", id).addValue("me", me), me).getFirst();
    }

    private List<MessageView> readMessages(String sql, MapSqlParameterSource p, long me) {
        Instant now = Instant.now(clock);
        List<MessageView> list = jdbc.query(sql, p, (rs, i) -> readMessage(rs, me, now));
        if (list.isEmpty()) return list;
        Map<Long, List<Reaction>> reactions = new HashMap<>();
        jdbc.query("select message_id, user_id, emoji from message_reactions where message_id in (:ids) "
                        + "order by created_at",
                Map.of("ids", list.stream().map(MessageView::id).toList()), rs -> {
                    reactions.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                            .add(new Reaction(rs.getLong(2), rs.getString(3)));
                });
        return list.stream().map(m -> new MessageView(m.id(), m.conversationId(), m.sender(), m.kind(), m.body(),
                m.media(), m.post(), m.story(), m.replyTo(), m.createdAt(), m.edited(), m.unsent(),
                reactions.getOrDefault(m.id(), List.of()), m.canEdit())).toList();
    }

    private MessageView readMessage(ResultSet rs, long me, Instant now) throws SQLException {
        UserSummary sender = UserRows.read(rs, "u");
        boolean unsent = rs.getTimestamp("unsent_at") != null;
        Instant createdAt = rs.getTimestamp("created_at").toInstant();

        Media media = null;
        if (!unsent && rs.getString("media_url") != null) {
            media = new Media(rs.getString("media_url"), rs.getInt("media_width"), rs.getInt("media_height"));
        }
        SharedPost post = null;
        if (!unsent && rs.getObject("post_id") != null) {
            boolean available = rs.getBoolean("p_visible") && rs.getTimestamp("p_deleted") == null;
            post = available
                    ? new SharedPost(rs.getString("p_shortcode"), rs.getString("p_thumb"), UserRows.read(rs, "a"),
                    rs.getString("p_caption"), true)
                    : new SharedPost(null, null, null, null, false);
        }
        SharedStory story = null;
        if (!unsent && rs.getObject("story_id") != null) {
            boolean expired = rs.getTimestamp("s_deleted") != null || !rs.getTimestamp("s_expires").toInstant().isAfter(now);
            long author = rs.getLong("s_author");
            boolean canSee = !expired || author == me || sender.id() == me;
            story = new SharedStory(rs.getLong("story_id"), canSee ? rs.getString("s_url") : null,
                    UserRows.read(rs, "s"), expired);
        }
        ReplyTo replyTo = null;
        if (rs.getObject("r_id") != null) {
            String text = rs.getTimestamp("r_unsent") != null ? null
                    : rs.getString("r_body") != null ? rs.getString("r_body") : kindLabel(rs.getString("r_kind"));
            replyTo = new ReplyTo(rs.getLong("r_id"), rs.getString("r_username"), text);
        }
        boolean mine = sender.id() == me;
        return new MessageView(rs.getLong("id"), rs.getLong("conversation_id"), sender, rs.getString("kind"),
                unsent ? null : rs.getString("body"), media, post, story, replyTo, createdAt,
                rs.getTimestamp("edited_at") != null, unsent, List.of(),
                mine && !unsent && "TEXT".equals(rs.getString("kind"))
                        && Duration.between(createdAt, now).compareTo(PostQueries.EDIT_WINDOW) <= 0);
    }

    private static String kindLabel(String kind) {
        return switch (kind) {
            case "IMAGE" -> "사진";
            case "POST" -> "게시물";
            case "STORY_REPLY" -> "스토리 답장";
            default -> "";
        };
    }

    @Transactional
    public MessageView send(long conversationId, long me, String rawBody, Long replyToId) {
        String body = validBody(rawBody, true);
        return insert(conversationId, me, "TEXT", body, null, null, null, replyToId);
    }

    @Transactional
    public MessageView sendImage(long conversationId, long me, MultipartFile file) {
        membership(conversationId, me);
        StoredImage img = storage.saveImage(file);
        return insert(conversationId, me, "IMAGE", null, img, null, null, null);
    }

    /** 게시물 공유: 받는 사람마다 1:1 대화방에 게시물 메시지(+선택 메모)를 보낸다 */
    @Transactional
    public List<Long> share(long me, String shortcode, List<String> usernames, String rawBody) {
        Long postId = jdbc.query("""
                select p.id from posts p join users a on a.id = p.author_id
                where p.shortcode = :code and p.deleted_at is null and %s
                """.formatted(PostQueries.VISIBLE), Map.of("code", shortcode, "me", me),
                (rs, i) -> rs.getLong(1)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("게시물을 찾을 수 없습니다."));
        String body = validBody(rawBody, false);
        if (usernames == null || usernames.isEmpty()) {
            throw ApiException.badRequest("NO_RECIPIENT", "받는 사람을 선택해 주세요.");
        }
        List<Long> conversations = new ArrayList<>();
        for (String username : usernames.stream().limit(MAX_GROUP).toList()) {
            ConversationView c = open(me, List.of(username), null);
            insert(c.id(), me, "POST", null, null, postId, null, null);
            if (body != null) insert(c.id(), me, "TEXT", body, null, null, null, null);
            conversations.add(c.id());
        }
        return conversations;
    }

    /** 스토리 답장: 스토리 주인과의 1:1 대화방으로 */
    @Transactional
    public MessageView storyReply(long me, long storyId, String authorUsername, String rawBody) {
        String body = validBody(rawBody, true);
        ConversationView c = open(me, List.of(authorUsername), null);
        return insert(c.id(), me, "STORY_REPLY", body, null, null, storyId, null);
    }

    private MessageView insert(long conversationId, long me, String kind, String body, StoredImage img, Long postId,
                               Long storyId, Long replyToId) {
        membership(conversationId, me);
        if (replyToId != null) {
            Integer n = jdbc.queryForObject("select count(*) from messages where id = :r and conversation_id = :c",
                    Map.of("r", replyToId, "c", conversationId), Integer.class);
            if (n == null || n == 0) throw ApiException.badRequest("BAD_REPLY", "답장할 메시지를 찾을 수 없습니다.");
        }
        Timestamp now = Timestamp.from(Instant.now(clock));
        Long id = jdbc.queryForObject("""
                insert into messages (conversation_id, sender_id, kind, body, media_url, media_width, media_height,
                                      post_id, story_id, reply_to_id, created_at)
                values (:c, :me, :kind, :body, :url, :w, :h, :post, :story, :reply, :now) returning id
                """, new MapSqlParameterSource("c", conversationId).addValue("me", me).addValue("kind", kind)
                .addValue("body", body).addValue("url", img == null ? null : img.url())
                .addValue("w", img == null ? null : img.width()).addValue("h", img == null ? null : img.height())
                .addValue("post", postId).addValue("story", storyId).addValue("reply", replyToId)
                .addValue("now", now), Long.class);
        jdbc.update("update conversations set last_message_at = :now where id = :c",
                new MapSqlParameterSource("now", now).addValue("c", conversationId));
        // 내가 보낸 메시지까지는 읽은 것으로, 요청함에서 답장하면 수락한 것으로
        jdbc.update("""
                update conversation_members set last_read_message_id = :id,
                       folder = case when folder = 'REQUEST' then 'PRIMARY' else folder end
                where conversation_id = :c and user_id = :me
                """, Map.of("id", id, "c", conversationId, "me", me));
        // 대화방을 지웠던 상대도 새 메시지를 받으면 다시 보이도록 (1:1)
        restoreDirectMembers(conversationId, me, now);
        MessageView view = message(id, me);
        realtime.send(memberIds(conversationId), "message:new", view);
        return view;
    }

    private void restoreDirectMembers(long conversationId, long me, Timestamp now) {
        String key = jdbc.queryForObject("select direct_key from conversations where id = :c",
                Map.of("c", conversationId), String.class);
        if (key == null) return;
        for (String part : key.split(":")) {
            long uid = Long.parseLong(part);
            if (uid != me) addMember(conversationId, uid, folderFor(uid, me), now);
        }
    }

    /** 15분 안에 텍스트 메시지 수정 */
    @Transactional
    public MessageView edit(long messageId, long me, String rawBody) {
        Map<String, Object> m = own(messageId, me);
        Instant now = Instant.now(clock);
        Instant createdAt = ((Timestamp) m.get("created_at")).toInstant();
        if (!"TEXT".equals(m.get("kind")) || m.get("unsent_at") != null) {
            throw ApiException.badRequest("CANNOT_EDIT", "이 메시지는 수정할 수 없습니다.");
        }
        if (Duration.between(createdAt, now).compareTo(PostQueries.EDIT_WINDOW) > 0) {
            throw ApiException.forbidden("EDIT_WINDOW_EXPIRED", "메시지는 보낸 후 15분 안에만 수정할 수 있습니다.");
        }
        jdbc.update("update messages set body = :body, edited_at = :now where id = :id",
                new MapSqlParameterSource("body", validBody(rawBody, true)).addValue("now", Timestamp.from(now))
                        .addValue("id", messageId));
        MessageView view = message(messageId, me);
        realtime.send(memberIds(view.conversationId()), "message:edit", view);
        return view;
    }

    /** 전송 취소: 모두에게서 내용이 사라진다 */
    @Transactional
    public void unsend(long messageId, long me) {
        Map<String, Object> m = own(messageId, me);
        long conversationId = toLong(m.get("conversation_id"));
        jdbc.update("update messages set unsent_at = :now, body = null where id = :id",
                Map.of("now", Timestamp.from(Instant.now(clock)), "id", messageId));
        jdbc.update("delete from message_reactions where message_id = :id", Map.of("id", messageId));
        realtime.send(memberIds(conversationId), "message:unsend", new UnsendEvent(conversationId, messageId));
    }

    /** 반응: 한 사람당 이모지 1개 (다시 누르면 바뀜), emoji가 null이면 취소 */
    @Transactional
    public MessageView react(long messageId, long me, String emoji) {
        Map<String, Object> m = jdbc.queryForList("select conversation_id, unsent_at from messages where id = :id",
                Map.of("id", messageId)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("메시지를 찾을 수 없습니다."));
        long conversationId = toLong(m.get("conversation_id"));
        membership(conversationId, me);
        if (emoji == null) {
            jdbc.update("delete from message_reactions where message_id = :id and user_id = :me",
                    Map.of("id", messageId, "me", me));
        } else {
            String e = emoji.strip();
            if (e.isEmpty() || e.length() > 16 || m.get("unsent_at") != null) {
                throw ApiException.badRequest("BAD_REACTION", "반응을 남길 수 없습니다.");
            }
            jdbc.update("""
                    insert into message_reactions (message_id, user_id, emoji, created_at) values (:id, :me, :e, :now)
                    on conflict (message_id, user_id) do update set emoji = excluded.emoji, created_at = excluded.created_at
                    """, new MapSqlParameterSource("id", messageId).addValue("me", me).addValue("e", e)
                    .addValue("now", Timestamp.from(Instant.now(clock))));
        }
        MessageView view = message(messageId, me);
        realtime.send(memberIds(conversationId), "message:edit", view);
        return view;
    }

    /** 여기까지 읽음 → 상대 화면에 "읽음" 표시 */
    @Transactional
    public void markRead(long conversationId, long me, long messageId) {
        membership(conversationId, me);
        int updated = jdbc.update("""
                update conversation_members set last_read_message_id = :m
                where conversation_id = :c and user_id = :me and last_read_message_id < :m
                  and :m <= (select coalesce(max(id), 0) from messages where conversation_id = :c)
                """, Map.of("m", messageId, "c", conversationId, "me", me));
        if (updated > 0) {
            realtime.send(memberIds(conversationId), "message:read", new ReadEvent(conversationId, me, messageId));
        }
    }

    /** 입력 중 표시: 다른 참여자에게만 */
    public void typing(long conversationId, long me) {
        List<Long> members = memberIds(conversationId);
        if (!members.contains(me)) return;
        UserSummary user = jdbc.queryForObject("select %s from users u where u.id = :id".formatted(UserRows.cols("u")),
                Map.of("id", me), (rs, i) -> UserRows.read(rs, "u"));
        realtime.send(members.stream().filter(id -> id != me).toList(), "typing",
                new TypingEvent(conversationId, user));
    }

    // ------------------------------------------------------------ 도우미

    private Map<String, Object> membership(long conversationId, long me) {
        return jdbc.queryForList("""
                select folder, last_read_message_id from conversation_members
                where conversation_id = :c and user_id = :me
                """, Map.of("c", conversationId, "me", me)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("대화방을 찾을 수 없습니다."));
    }

    private Map<String, Object> own(long messageId, long me) {
        Map<String, Object> m = jdbc.queryForList(
                "select conversation_id, sender_id, kind, created_at, unsent_at from messages where id = :id",
                Map.of("id", messageId)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("메시지를 찾을 수 없습니다."));
        if (toLong(m.get("sender_id")) != me) {
            throw ApiException.forbidden("NOT_ALLOWED", "내가 보낸 메시지만 바꿀 수 있습니다.");
        }
        return m;
    }

    private List<Long> memberIds(long conversationId) {
        return jdbc.queryForList("select user_id from conversation_members where conversation_id = :c",
                Map.of("c", conversationId), Long.class);
    }

    private Map<Long, List<UserSummary>> otherMembers(long me, List<Long> conversationIds) {
        Map<Long, List<UserSummary>> result = new HashMap<>();
        if (conversationIds.isEmpty()) return result;
        jdbc.query("""
                select cm.conversation_id, %s from conversation_members cm join users u on u.id = cm.user_id
                where cm.conversation_id in (:ids) and cm.user_id <> :me
                order by cm.conversation_id, cm.joined_at, u.id
                """.formatted(UserRows.cols("u")),
                new MapSqlParameterSource("ids", conversationIds).addValue("me", me), rs -> {
                    result.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(UserRows.read(rs, "u"));
                });
        return result;
    }

    private static String validBody(String raw, boolean required) {
        String body = raw == null ? "" : raw.strip();
        if (body.isEmpty()) {
            if (required) throw ApiException.badRequest("EMPTY_MESSAGE", "메시지를 입력해 주세요.");
            return null;
        }
        if (body.length() > MAX_BODY) {
            throw ApiException.badRequest("MESSAGE_TOO_LONG", "메시지는 " + MAX_BODY + "자까지 보낼 수 있습니다.");
        }
        return body;
    }

    private static long toLong(Object o) {
        return ((Number) o).longValue();
    }
}
