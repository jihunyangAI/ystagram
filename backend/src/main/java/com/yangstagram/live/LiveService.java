package com.yangstagram.live;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.realtime.Realtime;
import com.yangstagram.relation.RelationSql;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 라이브 방송 (기획서 §4.7, 🟡).
 * <ul>
 *   <li>CAMERA: 브라우저 카메라. 영상은 진행자 브라우저에서 시청자 브라우저로 WebRTC로 바로 간다 (서버는 신호만 전달)</li>
 *   <li>VIDEO: 가짜 라이브. 가짜 사용자가 미리 만든 릴스 영상을 반복 재생한다 ({@link LiveSimulator})</li>
 * </ul>
 * 댓글·하트·배지·입장은 진행자와 시청자 모두에게 실시간으로 간다("live:*"). 지금 보고 있는 사람은 메모리에 둔다.
 */
@Service
public class LiveService {

    public static final int MAX_TITLE = 100;
    public static final int MAX_COMMENT = 300;
    /** 배지 단계별 가격 (원, 가짜 결제) */
    public static final int[] BADGE_PRICES = {0, 1_100, 2_200, 6_600};

    private final NamedParameterJdbcTemplate jdbc;
    private final Realtime realtime;

    /** 라이브 id → 지금 보고 있는 실제 사용자 */
    private final Map<Long, Set<Long>> watching = new ConcurrentHashMap<>();
    /** 라이브 id → 가짜 시청자 수 (시뮬레이터가 늘리고 줄인다) */
    private final Map<Long, Integer> fakeAudience = new ConcurrentHashMap<>();
    /** 진행자 연결이 끊긴 채로 지난 점검 횟수 */
    private final Map<Long, Integer> hostMisses = new ConcurrentHashMap<>();

    public LiveService(NamedParameterJdbcTemplate jdbc, Realtime realtime) {
        this.jdbc = jdbc;
        this.realtime = realtime;
    }

    public record LiveView(long id, UserSummary host, String title, String kind, String videoUrl, String posterUrl,
                           String status, Instant startedAt, Instant endedAt, int viewers, int peakViewers,
                           int totalViewers, int heartCount, int commentCount, int badgeCount, boolean mine,
                           UserSummary guest, String guestStatus) {
    }

    /** @param badge 배지 단계 (BADGE일 때 1~3) */
    public record LiveComment(long id, long liveId, UserSummary user, String kind, String body, Integer badge,
                              Instant createdAt) {
    }

    public record Joined(LiveView live, List<LiveComment> comments) {
    }

    public record HeartEvent(long liveId, long userId, int count) {
    }

    public record ViewersEvent(long liveId, int viewers) {
    }

    // ------------------------------------------------------------ 시작·끝

    @Transactional
    public LiveView start(long me, String rawTitle) {
        String title = rawTitle == null ? null : rawTitle.strip();
        if (title != null && title.isEmpty()) title = null;
        if (title != null && title.length() > MAX_TITLE) {
            throw ApiException.badRequest("TITLE_TOO_LONG", "제목은 100자까지입니다.");
        }
        if (current(me) != null) throw ApiException.conflict("ALREADY_LIVE", "이미 라이브 방송 중입니다.");
        long id = insert(me, title, "CAMERA", null, null);
        return view(id, me);
    }

    /** 가짜 라이브 시작 (시뮬레이터) */
    @Transactional
    public long startFake(long hostId, String title, String videoUrl, String posterUrl) {
        return insert(hostId, title, "VIDEO", videoUrl, posterUrl);
    }

    private long insert(long host, String title, String kind, String videoUrl, String posterUrl) {
        Long id = jdbc.queryForObject("""
                insert into lives (host_id, title, kind, video_url, poster_url, started_at)
                values (:host, :title, :kind, :video, :poster, :now) returning id
                """, new MapSqlParameterSource("host", host).addValue("title", title).addValue("kind", kind)
                .addValue("video", videoUrl).addValue("poster", posterUrl)
                .addValue("now", Timestamp.from(Instant.now())), Long.class);
        watching.put(id, ConcurrentHashMap.newKeySet());
        fakeAudience.put(id, 0);
        return id;
    }

    /** 방송 끝: 진행자만. 시청자에게 "live:end"를 보낸다 */
    @Transactional
    public LiveView end(long id, long me) {
        Map<String, Object> row = row(id);
        if (((Number) row.get("host_id")).longValue() != me) {
            throw ApiException.forbidden("NOT_HOST", "진행자만 방송을 끝낼 수 있습니다.");
        }
        finish(id);
        return view(id, me);
    }

    void finish(long id) {
        int updated = jdbc.update("update lives set status = 'ENDED', ended_at = :now where id = :id and status = 'LIVE'",
                new MapSqlParameterSource("now", Timestamp.from(Instant.now())).addValue("id", id));
        if (updated == 0) return;
        Set<Long> audience = audience(id);
        watching.remove(id);
        fakeAudience.remove(id);
        hostMisses.remove(id);
        realtime.send(audience, "live:end", Map.of("liveId", id));
    }

    // ------------------------------------------------------------ 보기

    /** 지금 방송 중인 라이브: 내가 팔로우하는 사람 먼저, 그다음 공개 계정의 인기 라이브 (최대 12개) */
    public List<LiveView> active(long me) {
        List<Long> ids = jdbc.queryForList("""
                select l.id from lives l join users a on a.id = l.host_id
                where l.status = 'LIVE' and a.deleted_at is null
                  and (a.suspended_until is null or a.suspended_until <= current_timestamp)
                  and (a.id = :me or not a.is_private or exists (select 1 from follows f where f.follower_id = :me
                       and f.following_id = a.id and f.status = 'ACCEPTED'))
                  and %s
                order by (a.id = :me) desc,
                         exists (select 1 from follows f where f.follower_id = :me and f.following_id = a.id
                                 and f.status = 'ACCEPTED') desc,
                         a.followers_count desc
                limit 12
                """.formatted(RelationSql.notBlocked("a.id")), Map.of("me", me), Long.class);
        List<LiveView> list = new ArrayList<>();
        for (Long id : ids) list.add(view(id, me));
        return list;
    }

    /** 이 사람이 지금 하고 있는 라이브 id (없으면 null) */
    public Long current(long hostId) {
        List<Long> ids = jdbc.queryForList("select id from lives where host_id = :h and status = 'LIVE'",
                Map.of("h", hostId), Long.class);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public LiveView get(long id, long me) {
        requireCanSee(id, me);
        return view(id, me);
    }

    /** 지난 라이브 기록 (내 것) */
    public List<LiveView> history(long me) {
        List<Long> ids = jdbc.queryForList("""
                select id from lives where host_id = :me order by started_at desc limit 30
                """, Map.of("me", me), Long.class);
        return ids.stream().map(id -> view(id, me)).toList();
    }

    LiveView view(long id, long me) {
        List<LiveView> list = jdbc.query("""
                select l.*, %s, %s from lives l join users u on u.id = l.host_id left join users g on g.id = l.guest_id
                where l.id = :id
                """.formatted(UserRows.cols("u"), UserRows.cols("g")), Map.of("id", id), (rs, i) -> {
            UserSummary host = UserRows.read(rs, "u");
            UserSummary guest = rs.getObject("guest_id") == null ? null : UserRows.read(rs, "g");
            Timestamp ended = rs.getTimestamp("ended_at");
            boolean live = "LIVE".equals(rs.getString("status"));
            return new LiveView(rs.getLong("id"), host, rs.getString("title"), rs.getString("kind"),
                    rs.getString("video_url"), rs.getString("poster_url"), rs.getString("status"),
                    rs.getTimestamp("started_at").toInstant(), ended == null ? null : ended.toInstant(),
                    live ? viewerCount(id) : 0, rs.getInt("peak_viewers"), rs.getInt("total_viewers"),
                    rs.getInt("heart_count"), rs.getInt("comment_count"), rs.getInt("badge_count"), host.id() == me,
                    guest, rs.getString("guest_status"));
        });
        if (list.isEmpty()) throw ApiException.notFound("라이브를 찾을 수 없습니다.");
        return list.getFirst();
    }

    // ------------------------------------------------------------ 게스트 (⚪ 흉내: 화면에 게스트 자리만, 영상 연결은 없음)

    public record GuestEvent(long liveId, Long guestId, String status) {
    }

    /** 진행자가 시청 중인 사람을 게스트로 초대 */
    @Transactional
    public LiveView inviteGuest(long id, long me, String username) {
        Map<String, Object> l = row(id);
        if (((Number) l.get("host_id")).longValue() != me) throw ApiException.forbidden("NOT_HOST", "진행자만 초대할 수 있습니다.");
        if (!"LIVE".equals(l.get("status"))) throw ApiException.badRequest("ENDED", "끝난 라이브입니다.");
        List<Long> ids = jdbc.queryForList("""
                select u.id from users u join live_viewers v on v.user_id = u.id and v.live_id = :id
                where u.username = :name
                """, Map.of("id", id, "name", username), Long.class);
        if (ids.isEmpty()) throw ApiException.badRequest("NOT_WATCHING", "지금 보고 있는 사람만 초대할 수 있습니다.");
        jdbc.update("update lives set guest_id = :g, guest_status = 'INVITED' where id = :id",
                Map.of("g", ids.getFirst(), "id", id));
        realtime.send(ids.getFirst(), "live:guest-invite", new GuestEvent(id, ids.getFirst(), "INVITED"));
        broadcast(id, "live:guest", new GuestEvent(id, ids.getFirst(), "INVITED"));
        return view(id, me);
    }

    /** 초대받은 사람: 수락(JOINED) 또는 거절, 진행자·게스트 누구나 내보내기(remove) */
    @Transactional
    public LiveView answerGuest(long id, long me, String action) {
        Map<String, Object> l = row(id);
        Long guest = l.get("guest_id") == null ? null : ((Number) l.get("guest_id")).longValue();
        long host = ((Number) l.get("host_id")).longValue();
        switch (action) {
            case "accept" -> {
                if (guest == null || guest != me || !"INVITED".equals(l.get("guest_status"))) {
                    throw ApiException.badRequest("NOT_INVITED", "초대받지 않았습니다.");
                }
                jdbc.update("update lives set guest_status = 'JOINED' where id = :id", Map.of("id", id));
            }
            case "remove" -> {
                if (guest == null || (me != host && me != guest)) {
                    throw ApiException.forbidden("NOT_ALLOWED", "진행자나 게스트만 내보낼 수 있습니다.");
                }
                jdbc.update("update lives set guest_id = null, guest_status = null where id = :id", Map.of("id", id));
            }
            default -> throw ApiException.badRequest("BAD_ACTION", "알 수 없는 요청입니다.");
        }
        LiveView v = view(id, me);
        broadcast(id, "live:guest", new GuestEvent(id, v.guest() == null ? null : v.guest().id(), v.guestStatus()));
        return v;
    }

    private Map<String, Object> row(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select l.host_id, l.status, l.kind, l.guest_id, l.guest_status, a.is_private from lives l join users a on a.id = l.host_id
                where l.id = :id and a.deleted_at is null
                """, Map.of("id", id));
        if (rows.isEmpty()) throw ApiException.notFound("라이브를 찾을 수 없습니다.");
        return rows.getFirst();
    }

    private Map<String, Object> requireCanSee(long id, long me) {
        Map<String, Object> row = row(id);
        long host = ((Number) row.get("host_id")).longValue();
        if (host == me) return row;
        Integer ok = jdbc.queryForObject("""
                select count(*) from users a where a.id = :host
                  and (a.suspended_until is null or a.suspended_until <= current_timestamp)
                  and (not a.is_private or exists (select 1 from follows f where f.follower_id = :me
                       and f.following_id = a.id and f.status = 'ACCEPTED'))
                  and %s
                """.formatted(RelationSql.notBlocked("a.id")), Map.of("host", host, "me", me), Integer.class);
        if (ok == null || ok == 0) throw ApiException.notFound("라이브를 찾을 수 없습니다.");
        return row;
    }

    private Map<String, Object> requireLive(long id, long me) {
        Map<String, Object> row = requireCanSee(id, me);
        if (!"LIVE".equals(row.get("status"))) throw ApiException.conflict("LIVE_ENDED", "라이브 방송이 끝났습니다.");
        return row;
    }

    // ------------------------------------------------------------ 시청

    /** 입장: 시청자로 넣고 최근 댓글 50개를 준다. 진행자에게는 "live:join" */
    @Transactional
    public Joined join(long id, long me) {
        Map<String, Object> row = requireLive(id, me);
        long host = ((Number) row.get("host_id")).longValue();
        if (host != me) {
            Set<Long> set = watching.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
            boolean added = set.add(me);
            int first = jdbc.update("""
                    insert into live_viewers (live_id, user_id, joined_at) values (:id, :me, :now) on conflict do nothing
                    """, new MapSqlParameterSource("id", id).addValue("me", me)
                    .addValue("now", Timestamp.from(Instant.now())));
            int viewers = viewerCount(id);
            jdbc.update("""
                    update lives set total_viewers = total_viewers + :first, peak_viewers = greatest(peak_viewers, :v)
                    where id = :id
                    """, Map.of("first", first, "v", viewers, "id", id));
            if (added) {
                LiveComment joined = insertComment(id, me, "JOIN", null, null);
                broadcast(id, "live:comment", joined);
                broadcast(id, "live:viewers", new ViewersEvent(id, viewers));
            }
        }
        return new Joined(view(id, me), recentComments(id));
    }

    public void leave(long id, long me) {
        Set<Long> set = watching.get(id);
        if (set != null && set.remove(me)) {
            broadcast(id, "live:viewers", new ViewersEvent(id, viewerCount(id)));
        }
    }

    @Transactional
    public LiveComment comment(long id, long me, String rawBody) {
        requireLive(id, me);
        String body = rawBody == null ? "" : rawBody.strip();
        if (body.isEmpty()) throw ApiException.badRequest("EMPTY_COMMENT", "댓글을 입력해 주세요.");
        if (body.length() > MAX_COMMENT) throw ApiException.badRequest("COMMENT_TOO_LONG", "댓글은 300자까지입니다.");
        LiveComment c = insertComment(id, me, "COMMENT", body, null);
        broadcast(id, "live:comment", c);
        return c;
    }

    /** 하트: 한 번에 최대 10개 (화면이 모아서 보낸다) */
    @Transactional
    public int heart(long id, long me, int count) {
        requireLive(id, me);
        int n = Math.clamp(count, 1, 10);
        Integer total = jdbc.queryForObject("update lives set heart_count = heart_count + :n where id = :id returning heart_count",
                Map.of("n", n, "id", id), Integer.class);
        broadcast(id, "live:heart", new HeartEvent(id, me, n));
        return total == null ? 0 : total;
    }

    /** 배지 구입 (가짜 결제): 진행자에게 선물로 기록하고, 댓글 창에 강조해서 보인다 */
    @Transactional
    public LiveComment badge(long id, long me, int tier) {
        Map<String, Object> row = requireLive(id, me);
        long host = ((Number) row.get("host_id")).longValue();
        if (host == me) throw ApiException.badRequest("OWN_LIVE", "내 라이브에는 배지를 보낼 수 없습니다.");
        if (tier < 1 || tier > 3) throw ApiException.badRequest("BAD_BADGE", "배지는 1~3단계입니다.");
        jdbc.update("""
                insert into gifts (sender_id, recipient_id, live_id, kind, stars, amount, created_at)
                values (:me, :host, :id, 'BADGE', :tier, :amount, :now)
                """, new MapSqlParameterSource("me", me).addValue("host", host).addValue("id", id)
                .addValue("tier", tier).addValue("amount", BADGE_PRICES[tier])
                .addValue("now", Timestamp.from(Instant.now())));
        jdbc.update("update lives set badge_count = badge_count + 1 where id = :id", Map.of("id", id));
        LiveComment c = insertComment(id, me, "BADGE", "배지 " + "❤️".repeat(tier) + "를 보냈어요", tier);
        broadcast(id, "live:comment", c);
        return c;
    }

    /** 가짜 시청자 댓글 (시뮬레이터). 시청자 수 집계에는 넣지 않는다 */
    @Transactional
    public void botComment(long id, long userId, String body) {
        LiveComment c = insertComment(id, userId, body == null ? "JOIN" : "COMMENT", body, null);
        broadcast(id, "live:comment", c);
    }

    void botHearts(long id, long userId, int n) {
        jdbc.update("update lives set heart_count = heart_count + :n where id = :id", Map.of("n", n, "id", id));
        broadcast(id, "live:heart", new HeartEvent(id, userId, n));
    }

    private LiveComment insertComment(long liveId, long userId, String kind, String body, Integer badge) {
        Long cid = jdbc.queryForObject("""
                insert into live_comments (live_id, user_id, kind, body, badge, created_at)
                values (:l, :u, :kind, :body, :badge, :now) returning id
                """, new MapSqlParameterSource("l", liveId).addValue("u", userId).addValue("kind", kind)
                .addValue("body", body).addValue("badge", badge)
                .addValue("now", Timestamp.from(Instant.now())), Long.class);
        if (!"JOIN".equals(kind)) {
            jdbc.update("update lives set comment_count = comment_count + 1 where id = :id", Map.of("id", liveId));
        }
        return comments("where c.id = :cid", new MapSqlParameterSource("cid", cid)).getFirst();
    }

    private List<LiveComment> recentComments(long liveId) {
        List<LiveComment> list = new ArrayList<>(comments("where c.live_id = :l order by c.id desc limit 50",
                new MapSqlParameterSource("l", liveId)));
        java.util.Collections.reverse(list);
        return list;
    }

    private List<LiveComment> comments(String where, MapSqlParameterSource p) {
        return jdbc.query("select c.*, %s from live_comments c join users u on u.id = c.user_id %s"
                .formatted(UserRows.cols("u"), where), p, (rs, i) -> {
            Timestamp at = rs.getTimestamp("created_at");
            return new LiveComment(rs.getLong("id"), rs.getLong("live_id"), UserRows.read(rs, "u"),
                    rs.getString("kind"), rs.getString("body"), (Integer) rs.getObject("badge"), at.toInstant());
        });
    }

    // ------------------------------------------------------------ 시청자·신호

    public int viewerCount(long id) {
        Set<Long> set = watching.get(id);
        return (set == null ? 0 : set.size()) + fakeAudience.getOrDefault(id, 0);
    }

    int realViewers(long id) {
        Set<Long> set = watching.get(id);
        return set == null ? 0 : set.size();
    }

    void setFakeAudience(long id, int n) {
        if (!watching.containsKey(id)) return;
        int before = viewerCount(id);
        fakeAudience.put(id, Math.max(0, n));
        int after = viewerCount(id);
        if (before != after) {
            jdbc.update("update lives set peak_viewers = greatest(peak_viewers, :v) where id = :id",
                    Map.of("v", after, "id", id));
            broadcast(id, "live:viewers", new ViewersEvent(id, after));
        }
    }

    int fakeAudience(long id) {
        return fakeAudience.getOrDefault(id, 0);
    }

    /** WebRTC 신호를 주고받아도 되는 사이인가: 한쪽이 진행자, 다른 쪽이 지금 보고 있는 시청자 */
    public boolean canSignal(long liveId, long from, long to) {
        List<Long> hosts = jdbc.queryForList("select host_id from lives where id = :id and status = 'LIVE' and kind = 'CAMERA'",
                Map.of("id", liveId), Long.class);
        if (hosts.isEmpty()) return false;
        long host = hosts.getFirst();
        Set<Long> set = watching.getOrDefault(liveId, Set.of());
        return (from == host && set.contains(to)) || (to == host && set.contains(from));
    }

    /** 진행자 + 지금 보는 사람 */
    private Set<Long> audience(long id) {
        Set<Long> all = new HashSet<>(watching.getOrDefault(id, Set.of()));
        jdbc.queryForList("select host_id from lives where id = :id", Map.of("id", id), Long.class).forEach(all::add);
        return all;
    }

    private void broadcast(long id, String type, Object data) {
        realtime.send(audience(id), type, data);
    }

    /** 방송 중인 라이브 id들 (시뮬레이터용) */
    List<Map<String, Object>> liveRows() {
        return jdbc.queryForList("""
                select l.id, l.host_id, l.kind, l.started_at, a.followers_count, a.is_fake
                from lives l join users a on a.id = l.host_id where l.status = 'LIVE'
                """, Map.of());
    }

    /**
     * 10초마다: 브라우저 카메라 라이브의 진행자 연결이 두 번 연속 끊겨 있으면 방송을 끝내고,
     * 연결이 끊긴 시청자는 빼고, DB에서 없어진(가짜 데이터를 다시 만든 등) 라이브는 메모리에서 지운다.
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    void sweep() {
        Set<Long> alive = new HashSet<>();
        for (Map<String, Object> r : liveRows()) {
            long id = ((Number) r.get("id")).longValue();
            alive.add(id);
            watching.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
            Set<Long> set = watching.get(id);
            if (set.removeIf(u -> !realtime.isOnline(u))) {
                broadcast(id, "live:viewers", new ViewersEvent(id, viewerCount(id)));
            }
            if ("CAMERA".equals(r.get("kind"))) {
                long host = ((Number) r.get("host_id")).longValue();
                if (realtime.isOnline(host)) {
                    hostMisses.remove(id);
                } else if (hostMisses.merge(id, 1, Integer::sum) >= 2) {
                    finish(id);
                }
            }
        }
        watching.keySet().removeIf(id -> !alive.contains(id));
        fakeAudience.keySet().removeIf(id -> !alive.contains(id));
    }
}
