-- M6 ②: DM 음성 메시지, 메시지 고정·채팅방 고정, 1회 보기 미디어, 사라지는 메시지(배니시 모드)
alter table messages add column pinned_at timestamptz;
alter table messages add column view_once boolean not null default false;
alter table messages add column vanish boolean not null default false;
alter table messages add column media_duration_ms integer;

-- 1회 보기 미디어를 연 사람
create table message_opens (
    message_id bigint      not null references messages (id) on delete cascade,
    user_id    bigint      not null references users (id) on delete cascade,
    opened_at  timestamptz not null,
    primary key (message_id, user_id)
);

-- 배니시 모드(1:1): 켜져 있는 동안 보낸 메시지는 끄면 사라진다
alter table conversations add column vanish_mode boolean not null default false;
-- 채팅방 고정(내 받은 메시지함 맨 위, 최대 3개)
alter table conversation_members add column pinned_at timestamptz;
