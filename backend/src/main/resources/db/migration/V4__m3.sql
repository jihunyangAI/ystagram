-- M3: 차단·제한·숨기기·친한 친구, 스토리 공개 범위·스티커·하이라이트, 사람 태그·협업, 릴스(영상)·조회

-- 관계 제어 ---------------------------------------------------------------

create table blocks (
    blocker_id  bigint      not null references users (id) on delete cascade,
    blocked_id  bigint      not null references users (id) on delete cascade,
    created_at  timestamptz not null,
    primary key (blocker_id, blocked_id),
    constraint ck_blocks_not_self check (blocker_id <> blocked_id)
);
create index blocks_blocked_idx on blocks (blocked_id);

-- 제한: 상대의 댓글은 상대에게만 보이고(내가 승인하면 공개), DM은 요청함으로, 내 읽음·활동 상태를 숨긴다
create table restricts (
    user_id        bigint      not null references users (id) on delete cascade,
    restricted_id  bigint      not null references users (id) on delete cascade,
    created_at     timestamptz not null,
    primary key (user_id, restricted_id)
);

-- 숨기기(뮤트): 팔로우는 유지하고 내 피드·스토리 트레이에서만 빼기
create table mutes (
    user_id     bigint      not null references users (id) on delete cascade,
    muted_id    bigint      not null references users (id) on delete cascade,
    posts       boolean     not null default false,
    stories     boolean     not null default false,
    created_at  timestamptz not null,
    primary key (user_id, muted_id)
);

create table close_friends (
    user_id     bigint      not null references users (id) on delete cascade,
    friend_id   bigint      not null references users (id) on delete cascade,
    created_at  timestamptz not null,
    primary key (user_id, friend_id)
);

alter table comments add column restricted boolean not null default false; -- 제한된 사람의 댓글 (승인 전)

-- 스토리 ----------------------------------------------------------------

alter table stories add column audience varchar(20) not null default 'PUBLIC'; -- PUBLIC, CLOSE_FRIENDS
alter table stories add column stickers jsonb;                                  -- [{id, type, x, y, ...}]

create table story_sticker_responses (
    story_id    bigint       not null references stories (id) on delete cascade,
    sticker_id  varchar(20)  not null,
    user_id     bigint       not null references users (id) on delete cascade,
    value       varchar(500) not null,  -- 설문: 선택지 번호, 퀴즈: 선택지 번호, 슬라이더: 0~100, 질문: 답변 글
    created_at  timestamptz  not null,
    primary key (story_id, sticker_id, user_id)
);

create table highlights (
    id              bigserial   primary key,
    user_id         bigint      not null references users (id) on delete cascade,
    title           varchar(30) not null,
    cover_story_id  bigint      references stories (id) on delete set null,
    position        integer     not null default 0,
    created_at      timestamptz not null,
    updated_at      timestamptz not null
);
create index highlights_user_idx on highlights (user_id, position);

create table highlight_items (
    highlight_id  bigint      not null references highlights (id) on delete cascade,
    story_id      bigint      not null references stories (id) on delete cascade,
    added_at      timestamptz not null,
    primary key (highlight_id, story_id)
);

-- 게시물: 사람 태그·협업·릴스 ---------------------------------------------

create table post_tags (
    post_id      bigint   not null references posts (id) on delete cascade,
    user_id      bigint   not null references users (id) on delete cascade,
    media_index  smallint not null default 0,
    x            real     not null,   -- 사진 안 위치 0~1
    y            real     not null,
    primary key (post_id, user_id)
);
create index post_tags_user_idx on post_tags (user_id);

create table post_collaborators (
    post_id     bigint      not null references posts (id) on delete cascade,
    user_id     bigint      not null references users (id) on delete cascade,
    status      varchar(10) not null default 'PENDING', -- PENDING, ACCEPTED
    created_at  timestamptz not null,
    primary key (post_id, user_id)
);
create index post_collaborators_user_idx on post_collaborators (user_id, status);

alter table posts add column view_count    integer not null default 0;
alter table posts add column recommendable boolean not null default true;  -- 3분 넘는 릴스는 비팔로워 추천 제외
alter table posts add column filter        varchar(20);                    -- 적용한 필터 이름 (기록용)

alter table post_media add column media_type  varchar(10) not null default 'IMAGE'; -- IMAGE, VIDEO
alter table post_media add column duration_ms integer;

create index posts_reels_idx on posts (created_at desc) where type = 'REEL' and deleted_at is null;

-- 본 기록 (릴스 추천에서 이미 본 것 미루기, 조회수)
create table post_views (
    user_id     bigint      not null references users (id) on delete cascade,
    post_id     bigint      not null references posts (id) on delete cascade,
    created_at  timestamptz not null,
    primary key (user_id, post_id)
);
