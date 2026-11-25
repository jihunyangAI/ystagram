package com.yangstagram.seed;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M4-② 가짜 데이터: AI 생성 프로필·AI 라벨, 가짜 광고(+광고 이벤트), 노트, 시험 릴스.
 * 10대 계정은 사용자 생성 단계(생년월일)에서 이미 섞여 있다.
 */
final class SeedFeatures {

    record Result(int aiProfiles, int aiPosts, int ads, int notes, int trialReels) {
    }

    private final JdbcTemplate jdbc;
    private final Random rnd;
    private final Instant now;

    SeedFeatures(JdbcTemplate jdbc, Random rnd, Instant now) {
        this.jdbc = jdbc;
        this.rnd = rnd;
        this.now = now;
    }

    Result generate() {
        jdbc.queryForList("select setseed(?)", rnd.nextDouble() * 2 - 1);
        Timestamp ts = Timestamp.from(now);

        // AI 생성 프로필: 일반 개인 계정 중 약 2% (최소 2명). 소개에 표시하고 게시물에 AI 라벨
        int users = jdbc.queryForObject("select count(*) from users", Integer.class);
        int aiProfiles = jdbc.update("""
                update users set is_ai_profile = true, is_private = false,
                       bio = left('🤖 AI로 만든 가상 인물 · ' || coalesce(bio, ''), 150)
                where id in (select id from users where account_type = 'PERSONAL' and not is_verified
                             and birth_date < current_date - interval '18 years'
                             order by random() limit ?)
                """, Math.max(2, users / 50));
        int aiPosts = jdbc.update("""
                update posts set ai_labeled = true
                where author_id in (select id from users where is_ai_profile) or random() < 0.02
                """);

        // 가짜 광고: 비즈니스 계정마다 좋아요가 많은 게시물 1~2개
        int ads = jdbc.update("""
                insert into ads (post_id, advertiser_id, headline, cta, topic, created_at)
                select x.id, x.author_id,
                       left(x.full_name || ' — ' || (array['지금 만나 보세요','이번 주 특가','새로 나왔어요',
                            '첫 방문 10% 할인','오늘만 무료 배송','예약 받는 중'])[1 + abs(hashtext(x.id::text)) % 6], 60),
                       (array['더 알아보기','지금 구매하기','예약하기','쇼핑하기','메시지 보내기'])[1 + abs(hashtext(x.id::text || 'cta')) % 5],
                       x.topic, ?
                from (
                  select p.id, p.author_id, p.topic, u.full_name,
                         row_number() over (partition by p.author_id order by p.like_count desc, p.id) rn
                  from posts p join users u on u.id = p.author_id
                  where u.account_type = 'BUSINESS' and p.deleted_at is null and p.type <> 'REEL'
                ) x
                where x.rn <= 1 + (x.author_id % 2)
                """, ts);

        // 광고 노출·클릭·숨기기 기록 (Lab 광고 성과용): 광고마다 노출 40~240, 클릭 약 1~4%, 숨기기 약 0.5%
        jdbc.update("""
                insert into events (user_id, type, post_id, surface, created_at)
                select 1 + floor(random() * ?)::bigint, 'IMPRESSION', ad.post_id, 'AD',
                       cast(? as timestamptz) - random() * interval '14 days'
                from ads ad cross join lateral generate_series(1, 40 + abs(hashtext(ad.id::text || 'n')) % 200) g
                """, users, ts);
        // 노출 id의 해시로 고른다 (DB 난수 대신: 같은 시드값이면 같은 결과)
        jdbc.update("""
                insert into events (user_id, type, post_id, surface, created_at)
                select e.user_id, case when abs(hashtext(e.id::text || 'h')) % 100 < 15 then 'AD_HIDE' else 'AD_CLICK' end,
                       e.post_id, 'AD',
                       e.created_at + interval '3 seconds'
                from events e join ads ad on ad.post_id = e.post_id
                where e.type = 'IMPRESSION' and e.surface = 'AD'
                  and abs(hashtext(e.id::text)) % 1000 < 10 + ((ad.id * 7) % 4) * 10
                """);

        // 노트: 눈팅족이 아닌 사람 중 약 30%, 지난 20시간 안에 남김
        List<String> texts = NOTE_TEXTS;
        int notes = 0;
        List<Long> noteUsers = jdbc.queryForList("""
                select id from users where posts_count > 0 and random() < 0.3 order by id
                """, Long.class);
        for (Long uid : noteUsers) {
            Instant created = now.minusSeconds(rnd.nextInt(20 * 3600));
            notes += jdbc.update("""
                    insert into notes (user_id, body, audience, created_at, expires_at)
                    values (?, ?, ?, ?, ?) on conflict do nothing
                    """, uid, texts.get(rnd.nextInt(texts.size())), rnd.nextDouble() < 0.15 ? "CLOSE_FRIENDS" : "MUTUAL",
                    Timestamp.from(created), Timestamp.from(created.plusSeconds(24 * 3600)));
        }

        // 시험 릴스: 최근 3일 안에 올린 릴스 중 일부
        int trial = jdbc.update("""
                update posts set trial = true
                where id in (select id from posts where type = 'REEL' and deleted_at is null
                             and created_at > cast(? as timestamptz) - interval '3 days' order by random() limit 8)
                """, ts);
        return new Result(aiProfiles, aiPosts, ads, notes, trial);
    }

    private static final List<String> NOTE_TEXTS = List.of(
            "오늘 점심 뭐 먹지 🤔", "주말에 한강 갈 사람?", "이 노래 무한 반복 중 🎧", "카페 추천 받아요 ☕",
            "드디어 금요일!!", "운동 3일째 💪", "비 오니까 파전 생각남", "새 책 읽기 시작 📚", "여행 가고 싶다 ✈️",
            "고양이가 키보드 위에 누움", "오늘도 야근…", "마라탕 vs 떡볶이", "졸려요 😴", "공연 티켓 성공!!",
            "다이어트는 내일부터", "산책하기 좋은 날 🌿", "누가 맛집 좀 알려 줘요", "감기 조심하세요 🤧",
            "넷플릭스 뭐 볼까", "코딩하다 밤샘 💻", "🎂 생일 축하해 준 사람들 고마워요", "새 프로필 사진 어때요?",
            "이사 끝!! 🛋️", "출근길 지하철 지옥", "오늘 하늘 진짜 예쁘다");
}
