package com.yangstagram.map;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.common.error.ApiException;
import com.yangstagram.common.time.LabClock;
import com.yangstagram.post.PostQueries;
import com.yangstagram.relation.RelationSql;
import com.yangstagram.user.dto.UserRows;
import com.yangstagram.user.dto.UserSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지도 (기획서 §5 P3, 🟡 가짜 좌표 + OpenStreetMap): 친구들이 공유한 마지막 위치 + 게시물이 올라온 장소.
 * 위치는 실제 GPS가 아니라 화면에서 지도를 눌러 정한다. 공유 대상: 서로 팔로우하는 사람 또는 친한 친구.
 * 마지막 공유 후 3일이 지나면 지도에서 사라진다.
 */
@RestController
@RequestMapping("/api/map")
public class MapController {

    private static final Set<String> SHARING = Set.of("OFF", "MUTUAL", "CLOSE_FRIENDS");

    private final NamedParameterJdbcTemplate jdbc;
    private final LabClock clock;

    public MapController(NamedParameterJdbcTemplate jdbc, LabClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record MyLocation(double lat, double lng, String placeName, String sharing, Instant updatedAt) {
    }

    public record FriendLocation(UserSummary user, double lat, double lng, String placeName, Instant updatedAt) {
    }

    /** 장소 핀: 볼 수 있는 게시물 수와 최근 사진 */
    public record PlacePin(long id, String name, double lat, double lng, long postCount, String thumbUrl) {
    }

    public record MapView(MyLocation me, List<FriendLocation> friends, List<PlacePin> places) {
    }

    @GetMapping
    public MapView map() {
        long me = CurrentUser.requireId();
        MapSqlParameterSource p = new MapSqlParameterSource("me", me)
                .addValue("now", Timestamp.from(Instant.now(clock)));
        MyLocation mine = jdbc.query("select * from user_locations where user_id = :me", p, (rs, i) ->
                new MyLocation(rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("place_name"),
                        rs.getString("sharing"), rs.getTimestamp("updated_at").toInstant())).stream().findFirst().orElse(null);

        List<FriendLocation> friends = jdbc.query("""
                select l.*, %s from user_locations l join users u on u.id = l.user_id
                where l.user_id <> :me and u.deleted_at is null and l.sharing <> 'OFF'
                  and l.updated_at > cast(:now as timestamptz) - interval '3 days' and %s
                  and case l.sharing
                        when 'CLOSE_FRIENDS' then exists (select 1 from close_friends cf
                                                          where cf.user_id = l.user_id and cf.friend_id = :me)
                        else exists (select 1 from follows a where a.follower_id = :me and a.following_id = l.user_id
                                     and a.status = 'ACCEPTED')
                         and exists (select 1 from follows b where b.follower_id = l.user_id and b.following_id = :me
                                     and b.status = 'ACCEPTED')
                      end
                order by l.updated_at desc limit 200
                """.formatted(UserRows.cols("u"), RelationSql.notBlocked("u.id")), p, (rs, i) ->
                new FriendLocation(UserRows.read(rs, "u"), rs.getDouble("lat"), rs.getDouble("lng"),
                        rs.getString("place_name"), rs.getTimestamp("updated_at").toInstant()));

        List<PlacePin> places = jdbc.query("""
                select l.id, l.name, l.lat, l.lng, count(*) n,
                       (array_agg(m.thumb_url order by p.created_at desc))[1] thumb
                from locations l
                join posts p on p.location_id = l.id and p.deleted_at is null
                join users a on a.id = p.author_id and a.deleted_at is null
                join post_media m on m.post_id = p.id and m.position = 0
                where l.lat is not null and %s
                group by l.id order by n desc limit 100
                """.formatted(PostQueries.VISIBLE), p, (rs, i) -> new PlacePin(rs.getLong("id"), rs.getString("name"),
                rs.getDouble("lat"), rs.getDouble("lng"), rs.getLong("n"), rs.getString("thumb")));
        return new MapView(mine, friends, places);
    }

    public record UpdateRequest(Double lat, Double lng, String placeName, String sharing) {
    }

    /** 내 위치(지도에서 고른 곳)와 공유 대상 */
    @PutMapping("/me")
    public MyLocation update(@RequestBody UpdateRequest req) {
        long me = CurrentUser.requireId();
        String sharing = req.sharing() == null ? "MUTUAL" : req.sharing();
        if (!SHARING.contains(sharing)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_SHARING", "공유 대상은 OFF, MUTUAL, CLOSE_FRIENDS입니다.");
        }
        if (req.lat() == null || req.lng() == null || Math.abs(req.lat()) > 90 || Math.abs(req.lng()) > 180) {
            throw ApiException.badRequest("BAD_POSITION", "지도에서 위치를 골라 주세요.");
        }
        String place = req.placeName() == null || req.placeName().isBlank() ? null : req.placeName().strip();
        if (place != null && place.length() > 100) place = place.substring(0, 100);
        jdbc.update("""
                insert into user_locations (user_id, lat, lng, place_name, sharing, updated_at)
                values (:me, :lat, :lng, :place, :sharing, :now)
                on conflict (user_id) do update set lat = excluded.lat, lng = excluded.lng,
                  place_name = excluded.place_name, sharing = excluded.sharing, updated_at = excluded.updated_at
                """, new MapSqlParameterSource("me", me).addValue("lat", req.lat()).addValue("lng", req.lng())
                .addValue("place", place).addValue("sharing", sharing)
                .addValue("now", Timestamp.from(Instant.now(clock))));
        return map().me();
    }

    /** 위치 공유 끄기 (기록도 지운다) */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop() {
        jdbc.update("delete from user_locations where user_id = :me", Map.of("me", CurrentUser.requireId()));
    }
}
