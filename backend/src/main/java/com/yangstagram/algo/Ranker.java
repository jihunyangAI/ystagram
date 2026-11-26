package com.yangstagram.algo;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 점수식으로 후보 게시물을 줄 세운다 (기획서 §9.1의 "점수 계산" 단계).
 * 후보 조건(누구의 어떤 게시물인지)은 부르는 쪽이 SQL로 주고, 여기서는 구성 요소 값을 계산해 가중합으로 정렬한다.
 * 구성 요소 값도 함께 돌려줘서 "이 게시물이 보이는 이유"와 Lab 점수 내역에 쓴다.
 */
@Service
public class Ranker {

    private final NamedParameterJdbcTemplate jdbc;

    public Ranker(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param candidates 후보 조건 SQL (posts p, users a 기준). 이 SQL이 쓰는 파라미터는 params에 넣는다
     * @param since      이미 본 횟수는 이 시각 이전 노출만 센다 (목록을 넘기는 동안 순서가 흔들리지 않게)
     */
    public record Request(long me, Map<Component, Double> weights, List<String> topics, Instant now, Instant since,
                          String candidates, MapSqlParameterSource params, int limit, int offset) {
    }

    public record Scored(long postId, double score, Map<Component, Double> values) {
    }

    public List<Scored> rank(Request r) {
        List<Component> comps = new ArrayList<>(r.weights().keySet());
        String cols = comps.stream().map(c -> c.sql() + " as " + c.column()).collect(Collectors.joining(",\n       "));
        String score = comps.stream()
                .map(c -> String.format(Locale.ROOT, "%s%.4f * %s", c.sign() < 0 ? "-" : "+", r.weights().get(c),
                        c.column()))
                .collect(Collectors.joining(" "));

        // 완료율·공유 수는 후보 게시물마다 색인(events_post_idx)으로 센다.
        // events 전체를 게시물별로 모은 뒤 붙이면 이벤트가 늘수록 느려진다 (large 릴스 쿼리 0.2초 → 0.08초)
        StringBuilder joins = new StringBuilder();
        if (comps.contains(Component.COMPLETION)) {
            joins.append("""
                    left join lateral (select avg(least(e.value_ms::float / nullif(m.duration_ms, 0), 1)) r
                               from events e join post_media m on m.post_id = e.post_id and m.position = 0
                               where e.post_id = p.id and e.type = 'VIEW_TIME') cr on true
                    """);
        }
        if (comps.contains(Component.SHARES)) {
            joins.append("left join lateral (select count(*) n from events where post_id = p.id and type = 'SHARE') sh "
                    + "on true\n");
        }

        String sql = """
                with aff as (
                  select uid, count(*) n from (
                    select p2.author_id uid from likes l join posts p2 on p2.id = l.post_id
                    where l.user_id = :me and l.created_at > :aff_since
                    union all
                    select p2.author_id from comments c join posts p2 on p2.id = c.post_id
                    where c.author_id = :me and c.created_at > :aff_since
                    union all
                    select cm.user_id from messages m
                    join conversation_members cm on cm.conversation_id = m.conversation_id and cm.user_id <> :me
                    where m.sender_id = :me and m.created_at > :aff_since
                    union all
                    select target_user_id from events
                    where user_id = :me and type = 'PROFILE_VISIT' and created_at > :aff_since
                  ) x where uid <> :me group by uid
                ), sn as (
                  select post_id, count(*) n from (
                    select post_id from events where user_id = :me and type = 'IMPRESSION' and created_at < :since
                    union all
                    select post_id from post_views where user_id = :me and created_at < :since
                  ) y group by post_id
                )
                select * from (
                  select p.id, %s
                  from posts p
                  join users a on a.id = p.author_id
                  left join aff on aff.uid = p.author_id
                  left join sn on sn.post_id = p.id
                  %s
                  where p.deleted_at is null and p.archived_at is null and a.deleted_at is null and p.created_at <= :now
                    and not exists (select 1 from events ni where ni.user_id = :me and ni.post_id = p.id
                                    and ni.type = 'NOT_INTERESTED')
                    and (%s)
                ) ranked
                order by (0 %s) desc, id desc
                limit :limit offset :offset
                """.formatted(cols, joins, r.candidates(), score);

        MapSqlParameterSource p = r.params()
                .addValue("me", r.me())
                .addValue("now", Timestamp.from(r.now()))
                .addValue("since", Timestamp.from(r.since() != null ? r.since() : r.now()))
                .addValue("aff_since", Timestamp.from(r.now().minus(Duration.ofDays(30))))
                .addValue("topics", r.topics().isEmpty() ? List.of("-") : r.topics())
                .addValue("limit", r.limit())
                .addValue("offset", r.offset());

        return jdbc.query(sql, p, (rs, i) -> {
            Map<Component, Double> values = new EnumMap<>(Component.class);
            double total = 0;
            for (Component c : comps) {
                double v = rs.getDouble(c.column());
                values.put(c, v);
                total += c.sign() * r.weights().get(c) * v;
            }
            return new Scored(rs.getLong("id"), total, values);
        });
    }
}
