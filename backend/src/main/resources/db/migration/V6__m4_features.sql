-- M4-②: 예약 게시·예약 메시지, 노트, 시험 릴스, AI 라벨, 가짜 광고

-- 게시물 ------------------------------------------------------------------
alter table posts add column ai_labeled boolean not null default false;  -- "AI 정보" 라벨
alter table posts add column trial      boolean not null default false;  -- 시험 릴스: 팔로워에게는 안 보임
alter table posts add column trial_shared_at timestamptz;                -- 시험 릴스를 팔로워에게 공유한 시각

-- 예약 게시: 사진은 예약할 때 미리 저장해 두고, 시각이 되면 실제 게시물로 만든다
create table scheduled_posts (
    id            bigserial    primary key,
    author_id     bigint       not null references users (id) on delete cascade,
    payload       jsonb        not null,          -- 만들기 요청(캡션·위치·설정) + 저장한 사진 정보
    thumb_url     varchar(300) not null,
    caption_start varchar(60),
    media_count   integer      not null,
    scheduled_at  timestamptz  not null,
    created_at    timestamptz  not null,
    post_id       bigint       references posts (id) on delete set null, -- 게시된 뒤의 게시물
    published_at  timestamptz,
    failure       varchar(200)                     -- 게시 실패 이유 (예: 계정 삭제)
);
create index scheduled_posts_due_idx on scheduled_posts (scheduled_at) where published_at is null and failure is null;
create index scheduled_posts_author_idx on scheduled_posts (author_id, scheduled_at);

-- 예약 메시지 (최대 29일 후, 보내면 지운다)
create table scheduled_messages (
    id               bigserial     primary key,
    conversation_id  bigint        not null references conversations (id) on delete cascade,
    sender_id        bigint        not null references users (id) on delete cascade,
    body             varchar(1000) not null,
    scheduled_at     timestamptz   not null,
    created_at       timestamptz   not null
);
create index scheduled_messages_due_idx on scheduled_messages (scheduled_at);
create index scheduled_messages_conv_idx on scheduled_messages (conversation_id, sender_id);

-- 노트: DM 받은 편지함 위쪽의 60자 짧은 글 (24시간, 사람당 하나)
create table notes (
    user_id     bigint      primary key references users (id) on delete cascade,
    body        varchar(60) not null,
    audience    varchar(20) not null default 'MUTUAL', -- MUTUAL(서로 팔로우), CLOSE_FRIENDS
    created_at  timestamptz not null,
    expires_at  timestamptz not null
);

-- 노트에 답장하면 원래 노트 문구를 메시지에 같이 남긴다
alter table messages add column quote varchar(100);

-- 가짜 광고: 비즈니스 계정의 게시물 하나를 광고로 내보낸다
create table ads (
    id             bigserial   primary key,
    post_id        bigint      not null unique references posts (id) on delete cascade,
    advertiser_id  bigint      not null references users (id) on delete cascade,
    headline       varchar(60) not null,
    cta            varchar(20) not null,   -- 더 알아보기, 지금 구매하기, 예약하기 ...
    topic          varchar(30),            -- 관심 주제 타기팅 (10대에게는 쓰지 않음)
    active         boolean     not null default true,
    created_at     timestamptz not null
);
