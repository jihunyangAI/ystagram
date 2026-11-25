package com.yangstagram.ad;

import com.yangstagram.common.error.ApiException;
import com.yangstagram.post.dto.PostDtos.AdInfo;
import com.yangstagram.relation.RelationSql;
import com.yangstagram.seed.Topic;
import com.yangstagram.user.Teens;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 가짜 광고 (기획서 §5 P2, 🟡 단순화): 비즈니스 계정의 게시물을 "광고" 라벨을 붙여 홈 피드에 끼운다.
 * 고르는 규칙: 관심 주제가 맞는 광고 먼저 → 덜 본 광고 먼저 → 목록을 연 시각으로 섞기.
 * 10대 계정에는 관심 주제 타기팅을 하지 않는다. "광고 숨기기"한 광고는 다시 나오지 않는다.
 */
@Service
public class AdService {

    private final NamedParameterJdbcTemplate jdbc;

    public AdService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Picked(long postId, AdInfo info) {
    }

    /**
     * @param topics  보는 사람의 관심 주제 (타기팅용)
     * @param exclude 이미 이 쪽에 있는 게시물 (같은 게시물이 두 번 나오지 않게)
     * @param offset  몇 번째 광고부터 (쪽마다 다른 광고)
     */
    public List<Picked> pick(long me, List<String> topics, Instant since, int count, int offset,
                             Collection<Long> exclude) {
        if (count <= 0) return List.of();
        boolean teen = Boolean.TRUE.equals(jdbc.queryForObject(
                "select " + Teens.sql("u") + " from users u where u.id = :me", Map.of("me", me), Boolean.class));
        List<String> target = teen || topics.isEmpty() ? List.of("-") : topics;
        MapSqlParameterSource p = new MapSqlParameterSource("me", me).addValue("topics", target)
                .addValue("since", Timestamp.from(since)).addValue("salt", String.valueOf(since.toEpochMilli()))
                .addValue("n", count).addValue("off", offset)
                .addValue("exclude", exclude.isEmpty() ? List.of(-1L) : exclude);
        return jdbc.query("""
                select ad.id, ad.post_id, ad.headline, ad.cta, ad.topic, coalesce(ad.topic in (:topics), false) matched
                from ads ad
                join posts p on p.id = ad.post_id
                join users adv on adv.id = ad.advertiser_id
                where ad.active and p.deleted_at is null and p.archived_at is null and adv.deleted_at is null and adv.id <> :me
                  and ad.post_id not in (:exclude)
                  and (ad.ends_at is null or ad.ends_at > :since)
                  -- 팔로우하는 계정의 글은 피드에 그냥 나오므로 광고로 다시 보여 주지 않는다
                  and not exists (select 1 from follows f where f.follower_id = :me and f.following_id = adv.id
                                  and f.status = 'ACCEPTED')
                  and not exists (select 1 from events h where h.user_id = :me and h.type = 'AD_HIDE'
                                  and h.post_id = ad.post_id)
                  and %s
                order by matched desc,
                         (select count(*) from events e where e.user_id = :me and e.post_id = ad.post_id
                          and e.type = 'IMPRESSION' and e.surface = 'AD' and e.created_at < :since),
                         md5(ad.id::text || :salt)
                limit :n offset :off
                """.formatted(RelationSql.notBlocked("adv.id")), p, (rs, i) -> {
            String reason;
            if (rs.getBoolean("matched")) {
                reason = "회원님의 관심사 '" + label(rs.getString("topic")) + "'에 맞춰 보여 주는 광고입니다.";
            } else if (teen) {
                reason = "10대 계정에는 관심사 기반 광고를 보여 주지 않아요. 누구에게나 보이는 광고입니다.";
            } else {
                reason = "광고주가 넓은 대상에게 보여 주도록 설정한 광고입니다.";
            }
            return new Picked(rs.getLong("post_id"), new AdInfo(rs.getLong("id"), rs.getString("headline"),
                    rs.getString("cta"), reason));
        });
    }

    private static String label(String topic) {
        if (topic == null) return "";
        try {
            return Topic.valueOf(topic).label();
        } catch (IllegalArgumentException e) {
            return topic;
        }
    }

    // ------------------------------------------------------------ Lab

    /** Lab 광고 성과: 노출(광고 자리)·도달·클릭·숨기기 */
    public record AdStat(long id, String shortcode, String thumbUrl, String advertiser, String headline, String cta,
                         String topic, boolean active, long impressions, long reach, long clicks, long hides,
                         double ctr) {
    }

    public List<AdStat> stats() {
        return jdbc.query("""
                select ad.id, p.shortcode, m.thumb_url, adv.username, ad.headline, ad.cta, ad.topic, ad.active,
                       coalesce(e.imp, 0) imp, coalesce(e.reach, 0) reach, coalesce(e.clicks, 0) clicks,
                       coalesce(e.hides, 0) hides
                from ads ad
                join posts p on p.id = ad.post_id
                join users adv on adv.id = ad.advertiser_id
                left join post_media m on m.post_id = p.id and m.position = 0
                left join (
                  select post_id,
                         count(*) filter (where type = 'IMPRESSION' and surface = 'AD') imp,
                         count(distinct user_id) filter (where type = 'IMPRESSION' and surface = 'AD') reach,
                         count(*) filter (where type = 'AD_CLICK') clicks,
                         count(*) filter (where type = 'AD_HIDE') hides
                  from events where post_id in (select post_id from ads)
                  group by post_id
                ) e on e.post_id = ad.post_id
                order by imp desc, ad.id
                """, Map.of(), (rs, i) -> {
            long imp = rs.getLong("imp");
            long clicks = rs.getLong("clicks");
            return new AdStat(rs.getLong("id"), rs.getString("shortcode"), rs.getString("thumb_url"),
                    rs.getString("username"), rs.getString("headline"), rs.getString("cta"),
                    label(rs.getString("topic")), rs.getBoolean("active"), imp, rs.getLong("reach"), clicks,
                    rs.getLong("hides"), imp == 0 ? 0 : Math.round(clicks * 10000.0 / imp) / 100.0);
        });
    }

    public void setActive(long id, boolean active) {
        int n = jdbc.update("update ads set active = :a where id = :id", Map.of("a", active, "id", id));
        if (n == 0) throw ApiException.notFound("광고를 찾을 수 없습니다.");
    }

    /** 홈 피드 한 쪽에 광고를 고르게 끼울 자리 (0부터, 앞쪽 3개 뒤부터) */
    public static List<Integer> slots(int size, int count) {
        List<Integer> out = new ArrayList<>();
        if (size < 3 || count <= 0) return out;
        for (int i = 1; i <= count; i++) {
            out.add(Math.max(3, Math.round((float) i * size / (count + 1))) + i - 1);
        }
        return out;
    }
}
