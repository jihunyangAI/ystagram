package com.yangstagram.algo;

import com.yangstagram.ad.AdService;
import com.yangstagram.algo.AlgorithmSettings.Ranking;
import com.yangstagram.post.dto.PostDtos;
import com.yangstagram.algo.Ranker.Request;
import com.yangstagram.algo.Ranker.Scored;
import com.yangstagram.common.paging.Slice;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.lab.FeatureFlags;
import com.yangstagram.post.PostQueries;
import com.yangstagram.post.dto.PostDtos.GridItem;
import com.yangstagram.post.dto.PostDtos.PostView;
import com.yangstagram.post.dto.PostDtos.Repost;
import com.yangstagram.relation.RelationSql;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 영역별 추천 (기획서 §9.1): 후보 생성 → 필터 → 점수({@link Ranker}) → 다양성 조정 → 추천 섞기.
 */
@Service
public class Recommendations {

    public static final int FEED_PAGE = 10;
    public static final int EXPLORE_PAGE = 24;
    public static final int REELS_PAGE = 6;

    private static final String FOLLOWEES =
            "(select following_id from follows where follower_id = :me and status = 'ACCEPTED')";

    /** 홈: 팔로우하는 사람·내 최근 3일 글·공동 작업·팔로우하는 사람이 리포스트한 공개 게시물 */
    static final String HOME_FOLLOWING = """
            ((p.author_id = :me and p.created_at > cast(:now as timestamptz) - interval '3 days') or p.author_id in %1$s
             or exists (select 1 from post_collaborators pc where pc.post_id = p.id and pc.status = 'ACCEPTED'
                        and pc.user_id in %1$s)
             or (not a.is_private and exists (select 1 from reposts r where r.post_id = p.id and r.user_id in %1$s)))
            and %2$s and %3$s
            """.formatted(FOLLOWEES, PostQueries.VISIBLE, RelationSql.postsNotMuted("p.author_id"));

    /** 홈 추천: 팔로우하지 않는 공개 계정, 추천 허용 게시물 */
    static final String HOME_SUGGESTED = """
            p.author_id <> :me and not a.is_private and p.recommendable and not p.trial and not p.subscribers_only
            and %4$s
            and p.author_id not in %1$s
            and not exists (select 1 from reposts r where r.post_id = p.id and r.user_id in %1$s)
            -- 광고로 돌고 있는 게시물은 광고 자리에서만 (같은 글이 두 번 보이지 않게)
            and not exists (select 1 from ads ad where ad.post_id = p.id and ad.active)
            and %2$s and %3$s
            """.formatted(FOLLOWEES, RelationSql.notBlocked("a.id"), RelationSql.postsNotMuted("a.id"),
            PostQueries.NOT_SUSPENDED);

    /** 탐색: 팔로우하지 않는 공개 계정의 게시물·릴스 */
    static final String EXPLORE = """
            not a.is_private and a.id <> :me and p.recommendable and not p.subscribers_only
            and a.id not in %s
            and %s and %s
            """.formatted(FOLLOWEES, RelationSql.notBlocked("a.id"), PostQueries.NOT_SUSPENDED);

    /** 릴스 추천 탭: 볼 수 있는 릴스 중 추천 허용이거나 팔로우하는 사람 것 */
    static final String REELS = """
            p.type = 'REEL' and %s and a.id <> :me
            and (p.recommendable or a.id in %s)
            """.formatted(PostQueries.VISIBLE, FOLLOWEES);

    private final com.yangstagram.settings.SettingsService userSettings;
    private final Ranker ranker;
    private final AlgorithmSettings settings;
    private final InterestService interests;
    private final PostQueries queries;
    private final FeatureFlags flags;
    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;
    private final AdService ads;

    public Recommendations(Ranker ranker, AlgorithmSettings settings, InterestService interests, PostQueries queries,
                           FeatureFlags flags, NamedParameterJdbcTemplate jdbc, LabClock clock, AdService ads, com.yangstagram.settings.SettingsService userSettings) {
        this.userSettings = userSettings;
        this.ads = ads;
        this.ranker = ranker;
        this.settings = settings;
        this.interests = interests;
        this.queries = queries;
        this.flags = flags;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * 순서를 매긴 목록 한 쪽.
     *
     * @param since 첫 쪽을 만든 시각(밀리초). 다음 쪽을 부를 때 그대로 돌려주면 순서가 흔들리지 않는다
     */
    public record Page<T>(List<T> items, Integer nextPage, long since) {
    }

    // ------------------------------------------------------------ 탐색·릴스

    public Page<GridItem> explore(long me, int page, Long sinceMs) {
        int p = Math.max(0, page);
        Instant now = Instant.now(clock);
        Instant since = sinceMs == null ? now : Instant.ofEpochMilli(sinceMs);
        Map<Component, Double> w = settings.weights(Surface.EXPLORE);
        List<Scored> scored = ranker.rank(new Request(me, w, interests.effective(me), now, since, ai(EXPLORE) + userSettings.sensitiveFilter(me, "EXPLORE"),
                new MapSqlParameterSource(), EXPLORE_PAGE + 1, p * EXPLORE_PAGE));
        Integer next = scored.size() > EXPLORE_PAGE ? p + 1 : null;
        List<Scored> pageItems = scored.subList(0, Math.min(EXPLORE_PAGE, scored.size()));
        Map<Long, GridItem> grid = gridItems(pageItems.stream().map(Scored::postId).toList(), me);
        List<GridItem> items = new ArrayList<>();
        for (int i = 0; i < pageItems.size(); i++) {
            Scored s = pageItems.get(i);
            GridItem g = grid.get(s.postId());
            if (g == null) continue;
            items.add(g.withRanking(AlgorithmSettings.ranking(Surface.EXPLORE, "SUGGESTED", s, w,
                    p * EXPLORE_PAGE + i + 1, false)));
        }
        return new Page<>(items, next, since.toEpochMilli());
    }

    public Page<PostView> reels(long me, int page, Long sinceMs) {
        int p = Math.max(0, page);
        Instant now = Instant.now(clock);
        Instant since = sinceMs == null ? now : Instant.ofEpochMilli(sinceMs);
        Map<Component, Double> w = settings.weights(Surface.REELS);
        List<Scored> scored = ranker.rank(new Request(me, w, interests.effective(me), now, since, ai(REELS) + userSettings.sensitiveFilter(me, "REELS"),
                new MapSqlParameterSource(), REELS_PAGE + 1, p * REELS_PAGE));
        Integer next = scored.size() > REELS_PAGE ? p + 1 : null;
        List<Scored> pageItems = scored.subList(0, Math.min(REELS_PAGE, scored.size()));
        Set<Long> followed = followed(me);
        List<PostView> views = queries.views(pageItems.stream().map(Scored::postId).toList(), me);
        Map<Long, Scored> byId = pageItems.stream().collect(Collectors.toMap(Scored::postId, s -> s));
        List<PostView> items = new ArrayList<>();
        for (int i = 0; i < views.size(); i++) {
            PostView v = views.get(i);
            Scored s = byId.get(v.id());
            String source = followed.contains(v.author().id()) ? "FOLLOWING" : "SUGGESTED";
            items.add(v.withRanking(AlgorithmSettings.ranking(Surface.REELS, source, s, w, p * REELS_PAGE + i + 1, false)));
        }
        return new Page<>(items, next, since.toEpochMilli());
    }

    // ------------------------------------------------------------ Blend

    /**
     * Blend 한 칸: 릴스 + 누구를 위한 추천인지.
     *
     * @param forUsers 관심 주제가 맞거나 좋아요를 누른 사람 (사용자 이름)
     * @param reason   "OO님의 관심사 '음식'" / "OO님이 좋아한 릴스" 같은 한 줄
     */
    public record BlendItem(PostView post, List<String> forUsers, String reason) {
    }

    /**
     * Blend 릴스: 두 사람의 관심 주제를 합쳐 릴스 점수식으로 줄 세운다. 둘 중 한 명이 좋아요한 릴스도 섞인다.
     * "이미 본 것" 감점은 보는 사람(me) 기준.
     */
    public Page<BlendItem> blendReels(long me, long other, String meName, String otherName, int page, Long sinceMs) {
        int p = Math.max(0, page);
        Instant now = Instant.now(clock);
        Instant since = sinceMs == null ? now : Instant.ofEpochMilli(sinceMs);
        List<String> mine = interests.effective(me);
        List<String> theirs = interests.effective(other);
        List<String> topics = new ArrayList<>(mine);
        theirs.forEach(t -> { if (!topics.contains(t)) topics.add(t); });
        Map<Component, Double> w = settings.weights(Surface.REELS);
        String candidates = ai(REELS) + " and " + RelationSql.notBlocked("a.id").replace(":me", ":other");
        List<Scored> scored = ranker.rank(new Request(me, w, topics, now, since, candidates,
                new MapSqlParameterSource("other", other), REELS_PAGE + 1, p * REELS_PAGE));
        Integer next = scored.size() > REELS_PAGE ? p + 1 : null;
        List<Scored> pageItems = scored.subList(0, Math.min(REELS_PAGE, scored.size()));
        List<Long> ids = pageItems.stream().map(Scored::postId).toList();
        if (ids.isEmpty()) return new Page<>(List.of(), null, since.toEpochMilli());

        Map<Long, String> topicOf = new HashMap<>();
        Map<Long, Set<Long>> likedBy = new HashMap<>();
        jdbc.query("select id, topic from posts where id in (:ids)", Map.of("ids", ids),
                rs -> { topicOf.put(rs.getLong(1), rs.getString(2)); });
        jdbc.query("select post_id, user_id from likes where post_id in (:ids) and user_id in (:me, :other)",
                Map.of("ids", ids, "me", me, "other", other),
                rs -> { likedBy.computeIfAbsent(rs.getLong(1), k -> new java.util.HashSet<>()).add(rs.getLong(2)); });

        Map<Long, Scored> byId = pageItems.stream().collect(Collectors.toMap(Scored::postId, s -> s));
        List<BlendItem> items = new ArrayList<>();
        int rank = p * REELS_PAGE;
        for (PostView v : queries.views(ids, me)) {
            String topic = topicOf.get(v.id());
            Set<Long> liked = likedBy.getOrDefault(v.id(), Set.of());
            List<String> forUsers = new ArrayList<>();
            String reason;
            if (liked.contains(other)) {
                forUsers.add(otherName);
                reason = otherName + "님이 좋아한 릴스";
            } else if (liked.contains(me)) {
                forUsers.add(meName);
                reason = "회원님이 좋아한 릴스";
            } else {
                if (topic != null && mine.contains(topic)) forUsers.add(meName);
                if (topic != null && theirs.contains(topic)) forUsers.add(otherName);
                String label = topicLabel(topic);
                reason = forUsers.size() == 2 ? "두 분 모두 관심 있는 '" + label + "'"
                        : forUsers.contains(otherName) ? otherName + "님의 관심사 '" + label + "'"
                        : forUsers.contains(meName) ? "회원님의 관심사 '" + label + "'"
                        : "두 분이 좋아할 만한 릴스";
            }
            items.add(new BlendItem(v.withRanking(AlgorithmSettings.ranking(Surface.REELS, "BLEND", byId.get(v.id()), w,
                    ++rank, false)), forUsers, reason));
        }
        return new Page<>(items, next, since.toEpochMilli());
    }

    // ------------------------------------------------------------ 홈 피드 (순위)

    /**
     * 홈 피드 (기획서 §6.5): 한 쪽 10개 중 팔로우 게시물 (10 − 추천 수)개 + 추천 게시물.
     * 같은 작성자가 3개 연달아 나오지 않게 섞고, 팔로우 게시물을 다 보면 그 뒤로 추천만 이어진다.
     * 광고는 Lab 플래그 수만큼 고르게 끼운다 (점수식과 따로, 순위에 세지 않음).
     * 커서: "h.{since}.{팔로우 오프셋}.{추천 오프셋}.{광고 오프셋}"
     */
    public Slice<PostView> home(long me, String cursor) {
        Instant now = Instant.now(clock);
        long sinceMs = now.toEpochMilli();
        int fOff = 0;
        int sOff = 0;
        int aOff = 0;
        if (cursor != null && cursor.startsWith("h.")) {
            String[] parts = cursor.split("\\.");
            try {
                sinceMs = Long.parseLong(parts[1]);
                fOff = Math.max(0, Integer.parseInt(parts[2]));
                sOff = Math.max(0, Integer.parseInt(parts[3]));
                if (parts.length > 4) aOff = Math.max(0, Integer.parseInt(parts[4]));
            } catch (RuntimeException ignored) {
                // 잘못된 커서는 처음부터
            }
        }
        Instant since = Instant.ofEpochMilli(sinceMs);
        int k = flags.get().feedSuggestedPerPage();
        int f = FEED_PAGE - k;
        Map<Component, Double> w = settings.weights(Surface.FEED);
        List<String> topics = interests.effective(me);

        List<Scored> following = ranker.rank(new Request(me, w, topics, now, since, HOME_FOLLOWING,
                new MapSqlParameterSource(), f + 1, fOff));
        boolean moreFollowing = following.size() > f;
        if (moreFollowing) following = following.subList(0, f);

        int need = moreFollowing ? k : FEED_PAGE - following.size();
        List<Scored> suggested = need == 0 ? List.of() : ranker.rank(new Request(me, w, topics, now, since,
                ai(HOME_SUGGESTED) + userSettings.sensitiveFilter(me, "FEED"), new MapSqlParameterSource(), need + 1, sOff));
        boolean moreSuggested = suggested.size() > need;
        if (moreSuggested) suggested = suggested.subList(0, need);

        List<Scored> ordered = diversify(following, me);

        // 4개마다 추천 하나씩 끼우고, 팔로우 게시물이 끝났으면 뒤에 몰아서
        List<Scored> merged = new ArrayList<>();
        Set<Long> suggestedIds = suggested.stream().map(Scored::postId).collect(Collectors.toSet());
        LinkedList<Scored> sq = new LinkedList<>(suggested);
        for (int i = 0; i < ordered.size(); i++) {
            merged.add(ordered.get(i));
            if (moreFollowing && (i + 1) % 4 == 0 && !sq.isEmpty()) merged.add(sq.poll());
        }
        Long caughtUpAt = !moreFollowing && !ordered.isEmpty() && !sq.isEmpty() ? sq.peek().postId() : null;
        merged.addAll(sq);

        List<Long> ids = merged.stream().map(Scored::postId).toList();
        Map<Long, Repost> reposts = queries.followeeReposts(me, ids);
        List<PostView> views = queries.views(ids, me, reposts);
        Map<Long, Scored> byId = merged.stream().collect(Collectors.toMap(Scored::postId, s -> s, (a, b) -> a));
        List<PostView> items = new ArrayList<>();
        int rank = fOff + sOff;
        for (PostView v : views) {
            Scored s = byId.get(v.id());
            String source = suggestedIds.contains(v.id()) ? "SUGGESTED" : v.repost() != null ? "REPOST" : "FOLLOWING";
            items.add(v.withRanking(AlgorithmSettings.ranking(Surface.FEED, source, s, w, ++rank,
                    caughtUpAt != null && caughtUpAt == v.id())));
        }

        // 광고 끼우기
        int adCount = flags.get().ads();
        int adsShown = 0;
        if (adCount > 0 && items.size() >= 3) {
            List<AdService.Picked> picked = ads.pick(me, topics, since, adCount, aOff, ids);
            List<PostView> adViews = queries.views(picked.stream().map(AdService.Picked::postId).toList(), me);
            Map<Long, PostDtos.AdInfo> info = picked.stream()
                    .collect(Collectors.toMap(AdService.Picked::postId, AdService.Picked::info));
            List<Integer> slots = AdService.slots(items.size(), adViews.size());
            for (int i = 0; i < adViews.size() && i < slots.size(); i++) {
                PostView ad = adViews.get(i);
                items.add(Math.min(slots.get(i), items.size()), ad.withAd(info.get(ad.id())));
                adsShown++;
            }
        }
        String next = moreFollowing || moreSuggested
                ? "h.%d.%d.%d.%d".formatted(sinceMs, fOff + following.size(), sOff + suggested.size(), aOff + adsShown)
                : null;
        return new Slice<>(items, next);
    }

    /** 같은 작성자가 3개 이상 연달아 나오지 않게, 점수 순서를 최대한 지키며 뒤로 미룬다 */
    private List<Scored> diversify(List<Scored> list, long me) {
        if (list.size() < 3) return list;
        Map<Long, Long> authors = new HashMap<>();
        jdbc.query("select id, author_id from posts where id in (:ids)",
                Map.of("ids", list.stream().map(Scored::postId).toList()), rs -> {
                    authors.put(rs.getLong(1), rs.getLong(2));
                });
        LinkedList<Scored> rest = new LinkedList<>(list);
        List<Scored> out = new ArrayList<>();
        while (!rest.isEmpty()) {
            Scored pick = null;
            for (Scored s : rest) {
                int n = out.size();
                long a = authors.getOrDefault(s.postId(), -1L);
                boolean third = n >= 2 && authors.getOrDefault(out.get(n - 1).postId(), -2L) == a
                        && authors.getOrDefault(out.get(n - 2).postId(), -3L) == a;
                if (!third) {
                    pick = s;
                    break;
                }
            }
            if (pick == null) pick = rest.getFirst();
            rest.remove(pick);
            out.add(pick);
        }
        return out;
    }

    // ------------------------------------------------------------ Lab 미리보기

    /** 미리보기 한 줄: 게시물 요약 + 점수 내역 */
    public record PreviewItem(String shortcode, String thumbUrl, String type, String author, String topic,
                              String captionStart, Ranking ranking) {
    }

    /**
     * 가중치를 저장하지 않고 결과만 본다 (A/B 비교). 홈 피드는 팔로우 게시물과 추천을 섞지 않고 점수 순으로만 보여 준다.
     */
    public List<PreviewItem> preview(Surface surface, long userId, Map<Component, Double> weights, int limit) {
        Instant now = Instant.now(clock);
        String candidates = switch (surface) {
            case FEED -> "((" + HOME_FOLLOWING + ") or (" + ai(HOME_SUGGESTED) + "))";
            case EXPLORE -> ai(EXPLORE);
            case REELS -> ai(REELS);
        };
        List<Scored> scored = ranker.rank(new Request(userId, weights, interests.effective(userId), now, now,
                candidates, new MapSqlParameterSource(), limit, 0));
        if (scored.isEmpty()) return List.of();
        Set<Long> followed = followed(userId);
        Map<Long, PreviewItem> info = new HashMap<>();
        jdbc.query("""
                select p.id, p.shortcode, p.type, p.topic, p.author_id, a.username, left(coalesce(p.caption, ''), 40) cap,
                       m.thumb_url
                from posts p join users a on a.id = p.author_id
                join post_media m on m.post_id = p.id and m.position = 0
                where p.id in (:ids)
                """, Map.of("ids", scored.stream().map(Scored::postId).toList()), rs -> {
            long authorId = rs.getLong("author_id");
            String source = authorId == userId || followed.contains(authorId) ? "FOLLOWING" : "SUGGESTED";
            info.put(rs.getLong("id"), new PreviewItem(rs.getString("shortcode"), rs.getString("thumb_url"),
                    rs.getString("type"), rs.getString("username"), topicLabel(rs.getString("topic")), rs.getString("cap"),
                    new Ranking(surface.name(), source, 0, 0, List.of(), false)));
        });
        List<PreviewItem> out = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            Scored s = scored.get(i);
            PreviewItem base = info.get(s.postId());
            if (base == null) continue;
            out.add(new PreviewItem(base.shortcode(), base.thumbUrl(), base.type(), base.author(), base.topic(),
                    base.captionStart(),
                    AlgorithmSettings.ranking(surface, base.ranking().source(), s, weights, i + 1, false)));
        }
        return out;
    }

    // ------------------------------------------------------------ 보조

    /** AI 생성 프로필 추천 제외 규칙 (Lab 플래그로 끌 수 있음): 팔로우하는 사람 것은 그대로 */
    private String ai(String candidates) {
        if (flags.get().aiInRecs()) return candidates;
        return "(" + candidates + ") and (not a.is_ai_profile or a.id in " + FOLLOWEES + ")";
    }

    private static String topicLabel(String key) {
        if (key == null) return null;
        try {
            return com.yangstagram.seed.Topic.valueOf(key).label();
        } catch (IllegalArgumentException e) {
            return key;
        }
    }

    private Set<Long> followed(long me) {
        return Set.copyOf(jdbc.queryForList(FOLLOWEES.substring(1, FOLLOWEES.length() - 1), Map.of("me", me),
                Long.class));
    }

    private Map<Long, GridItem> gridItems(List<Long> ids, long me) {
        Map<Long, GridItem> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        jdbc.query("""
                select p.id, p.shortcode, p.like_count, p.comment_count, p.hide_like_count, p.author_id,
                       p.type, p.view_count, m.thumb_url,
                       (select count(*) from post_media x where x.post_id = p.id) as media_count
                from posts p
                join post_media m on m.post_id = p.id and m.position = 0
                where p.id in (:ids)
                """, Map.of("ids", ids), rs -> {
            result.put(rs.getLong("id"), PostQueries.gridItem(rs, me));
        });
        return result;
    }
}
