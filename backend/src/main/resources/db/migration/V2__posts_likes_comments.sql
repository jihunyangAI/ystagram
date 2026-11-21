-- M1: 게시물·미디어·해시태그·위치·좋아요·댓글

alter table users add column website varchar(200);

create table locations (
    id          bigserial primary key,
    name        varchar(100) not null unique,
    city        varchar(60),
    created_at  timestamptz  not null
);

create table posts (
    id                 bigserial primary key,
    shortcode          varchar(11)  not null unique,
    author_id          bigint       not null references users (id) on delete cascade,
    type               varchar(20)  not null default 'PHOTO',   -- PHOTO, CAROUSEL (REEL은 M3)
    caption            varchar(2200),
    location_id        bigint       references locations (id) on delete set null,
    hide_like_count    boolean      not null default false,
    comments_disabled  boolean      not null default false,
    like_count         integer      not null default 0,
    comment_count      integer      not null default 0,
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null,
    deleted_at         timestamptz
);
create index posts_author_created_idx on posts (author_id, created_at desc, id desc) where deleted_at is null;
create index posts_created_idx on posts (created_at desc, id desc) where deleted_at is null;

create table post_media (
    id         bigserial primary key,
    post_id    bigint       not null references posts (id) on delete cascade,
    position   smallint     not null,
    url        varchar(300) not null,
    thumb_url  varchar(300) not null,
    width      integer      not null,
    height     integer      not null,
    alt_text   varchar(200),
    unique (post_id, position)
);

create table hashtags (
    id          bigserial primary key,
    name        varchar(100) not null unique,   -- '#' 없이 소문자
    post_count  integer      not null default 0
);

create table post_hashtags (
    post_id     bigint not null references posts (id) on delete cascade,
    hashtag_id  bigint not null references hashtags (id) on delete cascade,
    primary key (post_id, hashtag_id)
);
create index post_hashtags_tag_idx on post_hashtags (hashtag_id);

create table likes (
    user_id     bigint      not null references users (id) on delete cascade,
    post_id     bigint      not null references posts (id) on delete cascade,
    created_at  timestamptz not null,
    primary key (user_id, post_id)
);
create index likes_post_idx on likes (post_id, created_at desc);

create table comments (
    id          bigserial primary key,
    post_id     bigint        not null references posts (id) on delete cascade,
    author_id   bigint        not null references users (id) on delete cascade,
    parent_id   bigint        references comments (id) on delete cascade,  -- 답글 (화면은 M2)
    body        varchar(2200) not null,
    created_at  timestamptz   not null
);
create index comments_post_idx on comments (post_id, created_at, id);
