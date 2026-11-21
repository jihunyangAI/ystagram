-- M2: 답글·댓글 수정/고정/좋아요, 저장·컬렉션, 리포스트, 알림, 검색 기록, DM, 스토리

alter table users add column last_active_at timestamptz;

alter table posts add column save_count   integer not null default 0;
alter table posts add column repost_count integer not null default 0;

-- 댓글 ------------------------------------------------------------------

alter table comments add column edited_at  timestamptz;
alter table comments add column pinned_at  timestamptz;           -- 게시물 주인이 고정 (최대 3개)
alter table comments add column like_count integer not null default 0;
create index comments_parent_idx on comments (parent_id, id) where parent_id is not null;

create table comment_likes (
    comment_id  bigint      not null references comments (id) on delete cascade,
    user_id     bigint      not null references users (id) on delete cascade,
    created_at  timestamptz not null,
    primary key (comment_id, user_id)
);

-- 저장·컬렉션 -----------------------------------------------------------

create table saved_posts (
    user_id     bigint      not null references users (id) on delete cascade,
    post_id     bigint      not null references posts (id) on delete cascade,
    created_at  timestamptz not null,
    primary key (user_id, post_id)
);
create index saved_posts_user_idx on saved_posts (user_id, created_at desc);

create table collections (
    id          bigserial    primary key,
    user_id     bigint       not null references users (id) on delete cascade,
    name        varchar(100) not null,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null
);
create index collections_user_idx on collections (user_id, updated_at desc);

-- 컬렉션에 넣으려면 먼저 저장되어 있어야 한다 (저장을 취소하면 모든 컬렉션에서 빠진다)
create table collection_posts (
    collection_id  bigint      not null references collections (id) on delete cascade,
    post_id        bigint      not null references posts (id) on delete cascade,
    created_at     timestamptz not null,
    primary key (collection_id, post_id)
);

-- 리포스트 --------------------------------------------------------------

create table reposts (
    user_id     bigint      not null references users (id) on delete cascade,
    post_id     bigint      not null references posts (id) on delete cascade,
    note        varchar(60),                                  -- 리포스트에 남기는 짧은 메모
    created_at  timestamptz not null,
    primary key (user_id, post_id)
);
create index reposts_user_idx on reposts (user_id, created_at desc);
create index reposts_created_idx on reposts (created_at desc);

-- 스토리 ----------------------------------------------------------------

create table stories (
    id          bigserial    primary key,
    author_id   bigint       not null references users (id) on delete cascade,
    media_url   varchar(300) not null,
    width       integer      not null,
    height      integer      not null,
    alt_text    varchar(200),
    created_at  timestamptz  not null,
    expires_at  timestamptz  not null,                         -- 24시간 뒤 (지나면 아카이브에만 남음)
    deleted_at  timestamptz
);
create index stories_author_idx on stories (author_id, created_at) where deleted_at is null;
create index stories_expires_idx on stories (expires_at) where deleted_at is null;

create table story_views (
    story_id    bigint      not null references stories (id) on delete cascade,
    viewer_id   bigint      not null references users (id) on delete cascade,
    liked       boolean     not null default false,
    created_at  timestamptz not null,
    primary key (story_id, viewer_id)
);
create index story_views_viewer_idx on story_views (viewer_id);

-- 알림 ------------------------------------------------------------------

create table notifications (
    id          bigserial   primary key,
    user_id     bigint      not null references users (id) on delete cascade,     -- 받는 사람
    actor_id    bigint      not null references users (id) on delete cascade,     -- 행동한 사람
    type        varchar(30) not null,  -- LIKE, COMMENT, REPLY, COMMENT_LIKE, MENTION, FOLLOW, FOLLOW_REQUEST, FOLLOW_ACCEPTED, REPOST, STORY_LIKE
    post_id     bigint      references posts (id) on delete cascade,
    comment_id  bigint      references comments (id) on delete cascade,
    story_id    bigint      references stories (id) on delete cascade,
    body        varchar(200),                                                     -- 댓글 미리보기 등
    created_at  timestamptz not null,
    read_at     timestamptz
);
create index notifications_user_idx on notifications (user_id, created_at desc);

-- 검색 기록 -------------------------------------------------------------

create table search_history (
    user_id     bigint       not null references users (id) on delete cascade,
    kind        varchar(10)  not null,   -- USER, TAG, PLACE, QUERY
    term        varchar(100) not null,   -- 사용자 이름 / 태그 이름 / 장소 id / 검색어
    created_at  timestamptz  not null,
    primary key (user_id, kind, term)
);

-- DM --------------------------------------------------------------------

create table conversations (
    id               bigserial    primary key,
    is_group         boolean      not null default false,
    title            varchar(100),
    direct_key       varchar(41)  unique,                       -- 1:1 대화방: "작은id:큰id"
    created_by       bigint       references users (id) on delete set null,
    created_at       timestamptz  not null,
    last_message_at  timestamptz  not null
);

create table conversation_members (
    conversation_id       bigint      not null references conversations (id) on delete cascade,
    user_id               bigint      not null references users (id) on delete cascade,
    folder                varchar(10) not null default 'PRIMARY',  -- PRIMARY, GENERAL, REQUEST
    last_read_message_id  bigint      not null default 0,
    joined_at             timestamptz not null,
    primary key (conversation_id, user_id)
);
create index conversation_members_user_idx on conversation_members (user_id);

create table messages (
    id               bigserial     primary key,
    conversation_id  bigint        not null references conversations (id) on delete cascade,
    sender_id        bigint        not null references users (id) on delete cascade,
    kind             varchar(20)   not null default 'TEXT',     -- TEXT, IMAGE, POST, STORY_REPLY
    body             varchar(1000),
    media_url        varchar(300),
    media_width      integer,
    media_height     integer,
    post_id          bigint        references posts (id) on delete set null,
    story_id         bigint        references stories (id) on delete set null,
    reply_to_id      bigint        references messages (id) on delete set null,
    created_at       timestamptz   not null,
    edited_at        timestamptz,
    unsent_at        timestamptz
);
create index messages_conversation_idx on messages (conversation_id, id desc);

create table message_reactions (
    message_id  bigint      not null references messages (id) on delete cascade,
    user_id     bigint      not null references users (id) on delete cascade,
    emoji       varchar(16) not null,
    created_at  timestamptz not null,
    primary key (message_id, user_id)
);
