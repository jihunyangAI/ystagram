-- M5-①: 신고·모더레이션, 계정 제재, 2단계 인증, 지도(위치 공유), 브로드캐스트 채널, Blend

-- 신고 ----------------------------------------------------------------------
create table reports (
    id           bigserial    primary key,
    reporter_id  bigint       not null references users (id) on delete cascade,
    target_type  varchar(10)  not null,             -- POST, COMMENT, USER
    target_id    bigint       not null,             -- posts.id / comments.id / users.id
    target_user_id bigint     references users (id) on delete cascade, -- 신고당한 콘텐츠의 주인
    reason       varchar(20)  not null,             -- SPAM, NUDITY, HATE, VIOLENCE, BULLYING, FALSE_INFO, SCAM, IP, SELF_HARM, OTHER
    detail       varchar(300),
    status       varchar(10)  not null default 'OPEN', -- OPEN, REMOVED(콘텐츠 삭제), DISMISSED(문제없음)
    action       varchar(20),                       -- REMOVE, DISMISS, SUSPEND_7D, SUSPEND_30D, BAN
    created_at   timestamptz  not null,
    resolved_at  timestamptz,
    unique (reporter_id, target_type, target_id)    -- 같은 사람이 같은 대상을 두 번 신고하지 않는다
);
create index reports_open_idx on reports (status, target_type, target_id);
create index reports_reporter_idx on reports (reporter_id, created_at desc);

-- 계정 제재: 정지 기간 동안 로그인 불가·콘텐츠 숨김. 영구 정지는 먼 미래 시각
alter table users add column suspended_until timestamptz;
alter table users add column strike_count    integer not null default 0;

-- 2단계 인증 (TOTP, RFC 6238) --------------------------------------------------
alter table users add column totp_secret   varchar(64);   -- Base32, 켜기 전 확인 단계에서도 저장
alter table users add column totp_enabled  boolean not null default false;
create table backup_codes (
    user_id    bigint      not null references users (id) on delete cascade,
    code_hash  varchar(100) not null,
    used_at    timestamptz,
    primary key (user_id, code_hash)
);

-- 지도 ----------------------------------------------------------------------
alter table locations add column lat double precision;
alter table locations add column lng double precision;

-- 친구 지도: 마지막으로 공유한 위치 (가짜 좌표, 화면에서 지도를 눌러 정함)
create table user_locations (
    user_id     bigint           primary key references users (id) on delete cascade,
    lat         double precision not null,
    lng         double precision not null,
    place_name  varchar(100),
    sharing     varchar(20)      not null default 'OFF', -- OFF, MUTUAL(서로 팔로우), CLOSE_FRIENDS
    updated_at  timestamptz      not null
);

-- 브로드캐스트 채널 ----------------------------------------------------------
create table channels (
    id          bigserial    primary key,
    owner_id    bigint       not null references users (id) on delete cascade,
    name        varchar(60)  not null,
    created_at  timestamptz  not null
);
create index channels_owner_idx on channels (owner_id);

create table channel_members (
    channel_id    bigint      not null references channels (id) on delete cascade,
    user_id       bigint      not null references users (id) on delete cascade,
    last_read_id  bigint      not null default 0,
    joined_at     timestamptz not null,
    primary key (channel_id, user_id)
);
create index channel_members_user_idx on channel_members (user_id);

create table channel_messages (
    id            bigserial     primary key,
    channel_id    bigint        not null references channels (id) on delete cascade,
    body          varchar(1000),
    media_url     varchar(300),
    media_width   integer,
    media_height  integer,
    poll_options  jsonb,        -- 투표: ["보기1", "보기2", ...] (없으면 일반 메시지)
    created_at    timestamptz   not null
);
create index channel_messages_idx on channel_messages (channel_id, id desc);

create table channel_reactions (
    message_id  bigint      not null references channel_messages (id) on delete cascade,
    user_id     bigint      not null references users (id) on delete cascade,
    emoji       varchar(16) not null,
    primary key (message_id, user_id)
);

create table channel_poll_votes (
    message_id  bigint   not null references channel_messages (id) on delete cascade,
    user_id     bigint   not null references users (id) on delete cascade,
    option_index integer not null,
    primary key (message_id, user_id)
);

-- Blend: 1:1 대화방의 두 사람이 함께 보는 릴스 피드 ---------------------------
create table blends (
    id               bigserial   primary key,
    conversation_id  bigint      not null unique references conversations (id) on delete cascade,
    inviter_id       bigint      not null references users (id) on delete cascade,
    invitee_id       bigint      not null references users (id) on delete cascade,
    status           varchar(10) not null default 'PENDING', -- PENDING, ACTIVE
    created_at       timestamptz not null,
    accepted_at      timestamptz
);
alter table messages add column blend_id bigint references blends (id) on delete set null;
