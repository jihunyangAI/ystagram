package com.yangstagram.seed;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M6 ③ 가짜 음원 목록 + 원본 오디오.
 * 곡 이름·가수는 지어낸 것이고 소리 파일은 없다 (화면이 seed·bpm으로 멜로디를 합성).
 * 릴스 일부는 목록의 곡을 쓰고(인기 곡이 더 자주), 나머지는 자기 "원본 오디오", 리믹스는 원본 릴스의 소리를 그대로 쓴다.
 */
final class SeedMusic {

    private static final String[][] LIBRARY = {
            {"한강 산책", "새벽세시", "인디"}, {"Summer Rewind", "Lumen Park", "팝"}, {"우리의 여름밤", "모래알", "발라드"},
            {"Coffee & Rain", "Mellow Fox", "로파이"}, {"달리는 버스", "주말여행", "록"}, {"Neon Seoul", "KITE", "신스팝"},
            {"오늘은 맑음", "햇살반", "어쿠스틱"}, {"Midnight Drive", "Polar Lights", "시티팝"}, {"작은 방", "소란한오후", "인디"},
            {"Golden Hour", "Ivy & June", "팝"}, {"구름 위를 걷다", "하늘색", "발라드"}, {"Late Night Study", "Lo-Fi Owl", "로파이"},
            {"첫눈", "겨울나무", "발라드"}, {"Weekend Mood", "Sunny Days", "댄스"}, {"바다 냄새", "파도소리", "어쿠스틱"},
            {"Run It Back", "Velvet Kid", "힙합"}, {"고양이 낮잠", "말랑", "로파이"}, {"Paper Planes", "The Airlines", "인디"},
            {"퇴근길", "직장인밴드", "록"}, {"Dancing Queen Bee", "Honey Jar", "댄스"}, {"봄비", "연두", "발라드"},
            {"Starlight Train", "Nova", "신스팝"}, {"동네 한 바퀴", "골목길", "어쿠스틱"}, {"Bounce", "DJ Mango", "댄스"},
            {"새벽 감성", "미드나잇", "R&B"}, {"Slow Sunday", "Cotton", "R&B"}, {"여행 가방", "출발선", "팝"},
            {"Pixel Heart", "8bit Club", "일렉트로닉"}, {"한여름의 꿈", "청량", "팝"}, {"Rooftop", "Skyline", "힙합"},
    };

    private final JdbcTemplate jdbc;
    private final Random rnd;
    private final Instant now;

    SeedMusic(JdbcTemplate jdbc, Random rnd, Instant now) {
        this.jdbc = jdbc;
        this.rnd = rnd;
        this.now = now;
    }

    /** 만든 곡 수(목록 + 원본 오디오) */
    int generate() {
        List<Long> library = new ArrayList<>();
        for (String[] t : LIBRARY) {
            int bpm = 70 + rnd.nextInt(70);
            int seed = rnd.nextInt(100_000);
            int duration = (15 + rnd.nextInt(46)) * 1000;
            library.add(jdbc.queryForObject("""
                    insert into audio_tracks (title, artist, genre, bpm, seed, duration_ms, created_at)
                    values (?, ?, ?, ?, ?, ?, ?) returning id
                    """, Long.class, t[0], t[1], t[2], bpm, seed, duration,
                    Timestamp.from(now.minus(Duration.ofDays(200 + rnd.nextInt(400))))));
        }

        // 릴스 55%는 목록의 곡 (앞쪽 곡일수록 인기)
        List<Long> reels = jdbc.queryForList(
                "select id from posts where type = 'REEL' and remix_of_id is null order by id", Long.class);
        List<Object[]> picks = new ArrayList<>();
        for (Long id : reels) {
            if (rnd.nextDouble() < 0.55) picks.add(new Object[] {popular(library), id});
        }
        // 사진 게시물 15%, 스토리 20%에도 음악
        for (Long id : jdbc.queryForList("select id from posts where type <> 'REEL' order by id", Long.class)) {
            if (rnd.nextDouble() < 0.15) picks.add(new Object[] {popular(library), id});
        }
        jdbc.batchUpdate("update posts set audio_id = ? where id = ?", picks);
        List<Object[]> storyPicks = new ArrayList<>();
        for (Long id : jdbc.queryForList("select id from stories order by id", Long.class)) {
            if (rnd.nextDouble() < 0.2) storyPicks.add(new Object[] {popular(library), id});
        }
        jdbc.batchUpdate("update stories set audio_id = ? where id = ?", storyPicks);

        // 나머지 릴스는 자기 원본 오디오
        int originals = jdbc.update("""
                insert into audio_tracks (title, artist, bpm, seed, duration_ms, origin_post_id, origin_user_id, created_at)
                select '원본 오디오', u.username, 80 + (p.id * 37 % 60)::int, (p.id * 2654435761 % 100000)::int,
                       least(coalesce(m.duration_ms, 15000), 60000), p.id, u.id, p.created_at
                from posts p join users u on u.id = p.author_id
                left join post_media m on m.post_id = p.id and m.position = 0
                where p.type = 'REEL' and p.remix_of_id is null and p.audio_id is null
                """);
        jdbc.update("""
                update posts p set audio_id = t.id from audio_tracks t
                where t.origin_post_id = p.id and p.audio_id is null
                """);
        // 리믹스는 원본 릴스의 소리 (리믹스의 리믹스도 있어서 더 바뀌지 않을 때까지)
        for (int i = 0; i < 10; i++) {
            int n = jdbc.update("""
                    update posts r set audio_id = o.audio_id from posts o
                    where r.remix_of_id = o.id and r.audio_id is null and o.audio_id is not null
                    """);
            if (n == 0) break;
        }
        // 그래도 남은 릴스(원본이 지워진 리믹스 등)는 자기 원본 오디오
        originals += jdbc.update("""
                insert into audio_tracks (title, artist, bpm, seed, duration_ms, origin_post_id, origin_user_id, created_at)
                select '원본 오디오', u.username, 80 + (p.id * 37 % 60)::int, (p.id * 2654435761 % 100000)::int,
                       least(coalesce(m.duration_ms, 15000), 60000), p.id, u.id, p.created_at
                from posts p join users u on u.id = p.author_id
                left join post_media m on m.post_id = p.id and m.position = 0
                where p.type = 'REEL' and p.audio_id is null
                """);
        jdbc.update("""
                update posts p set audio_id = t.id from audio_tracks t
                where t.origin_post_id = p.id and p.audio_id is null
                """);
        return library.size() + originals;
    }

    private long popular(List<Long> library) {
        double x = rnd.nextDouble();
        return library.get((int) (x * x * library.size()));
    }
}
