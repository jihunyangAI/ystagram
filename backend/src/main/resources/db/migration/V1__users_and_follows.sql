-- M0: 사용자와 팔로우 관계

create table users (
    id               bigserial    primary key,
    username         varchar(30)  not null unique,
    email            varchar(255) unique,
    password_hash    varchar(100) not null,
    full_name        varchar(64)  not null,
    bio              varchar(150),
    avatar_url       varchar(500),          -- 직접 올린 프로필 사진 (없으면 avatar_seed로 일러스트 생성)
    avatar_seed      varchar(64)  not null, -- 일러스트 아바타(DiceBear) 생성용 값
    pronouns         varchar(30),
    account_type     varchar(20)  not null default 'PERSONAL', -- PERSONAL | CREATOR | BUSINESS
    category         varchar(64),
    is_private       boolean      not null default false,
    is_verified      boolean      not null default false,
    is_ai_profile    boolean      not null default false,
    birth_date       date         not null,
    is_fake          boolean      not null default false,      -- 시드로 만든 가짜 계정
    interests        varchar(200),                              -- 관심 주제 (쉼표 구분, 추천·시드용)
    followers_count  integer      not null default 0,
    following_count  integer      not null default 0,
    posts_count      integer      not null default 0,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    deleted_at       timestamptz
);

create table follows (
    id            bigserial   primary key,
    follower_id   bigint      not null references users (id) on delete cascade, -- 팔로우하는 사람
    following_id  bigint      not null references users (id) on delete cascade, -- 팔로우 대상
    status        varchar(20) not null default 'ACCEPTED',                     -- ACCEPTED | PENDING
    is_favorite   boolean     not null default false,
    created_at    timestamptz not null,
    constraint uq_follows unique (follower_id, following_id),
    constraint ck_follows_not_self check (follower_id <> following_id)
);

create index ix_follows_following on follows (following_id, status);
