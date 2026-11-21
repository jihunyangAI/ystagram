-- M4 연구 도구 ①: 이벤트 수집, 게시물 주제, 회원님의 알고리즘(관심 주제), Lab 설정(가중치·기능 플래그)

-- 게시물 주제 (탐색·추천의 관심 일치 계산용). 시드는 직접 지정, 직접 올린 글은 작성자의 첫 관심 주제
alter table posts add column topic varchar(20);
update posts p set topic = nullif(split_part(coalesce(u.interests, ''), ',', 1), '')
from users u where u.id = p.author_id;
create index posts_topic_idx on posts (topic) where deleted_at is null;

-- 행동 이벤트 (기획서 §9.3). 화면에서 보내는 것(노출·시청 시간·프로필 방문·건너뛰기·관심 없음)과
-- 서버가 직접 남기는 것(좋아요·댓글·저장·공유·팔로우)을 한 테이블에 쌓는다
create table events (
    id              bigserial   primary key,
    user_id         bigint      not null references users (id) on delete cascade,
    type            varchar(30) not null,   -- IMPRESSION, VIEW_TIME, PROFILE_VISIT, SKIP, NOT_INTERESTED, LIKE, COMMENT, SAVE, SHARE, FOLLOW
    post_id         bigint      references posts (id) on delete cascade,
    target_user_id  bigint      references users (id) on delete cascade,
    surface         varchar(20),            -- HOME, EXPLORE, REELS, PROFILE, POST, SEARCH, SIM(시뮬레이터)
    value_ms        integer,                -- VIEW_TIME의 시청 시간
    created_at      timestamptz not null
);
create index events_user_idx on events (user_id, created_at desc);
create index events_post_idx on events (post_id, type) where post_id is not null;
create index events_target_idx on events (target_user_id, created_at) where target_user_id is not null;
create index events_created_idx on events (created_at desc, id desc);

-- 회원님의 알고리즘: 관심 주제를 직접 더하거나(ADD) 빼기(REMOVE)
create table user_topic_prefs (
    user_id     bigint      not null references users (id) on delete cascade,
    topic       varchar(20) not null,
    mode        varchar(10) not null,   -- ADD | REMOVE
    created_at  timestamptz not null,
    primary key (user_id, topic)
);

-- Lab 설정: 알고리즘 가중치(algo.feed 등), 기능 플래그(flags)
create table lab_settings (
    key         varchar(50) primary key,
    value       jsonb       not null,
    updated_at  timestamptz not null
);
