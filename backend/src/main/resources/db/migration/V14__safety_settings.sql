-- M6 ④-b: 숨겨진 단어·댓글 허용 범위·알림 설정(종류별·일시 중지)·민감 콘텐츠·사용 시간 알림·수면 모드, 임시 저장
create table user_settings (
    user_id            bigint primary key references users (id) on delete cascade,
    hide_offensive     boolean       not null default false, -- 기본 비속어 목록이 들어간 댓글 숨기기
    hidden_words       varchar(1000),                         -- 내가 정한 단어 (쉼표로 구분)
    comment_audience   varchar(20)   not null default 'EVERYONE', -- EVERYONE / FOLLOWING / FOLLOWERS / BOTH / OFF
    notif_off          varchar(200)  not null default '',     -- 끈 알림 묶음 (쉼표로 구분)
    notif_paused_until timestamptz,
    sensitive_level    varchar(10)   not null default 'STANDARD', -- LESS / STANDARD / MORE
    daily_limit_min    integer,
    sleep_on           boolean       not null default false,
    sleep_start        smallint      not null default 23,
    sleep_end          smallint      not null default 7,
    updated_at         timestamptz   not null
);

-- 숨긴 댓글: 쓴 사람만 보이고, 게시물 주인은 "숨겨진 댓글"에서 따로 본다
alter table comments add column hidden boolean not null default false;

-- 민감할 수 있는 콘텐츠 (연구용 표시: 추천에서 걸러지는지 실험)
alter table posts add column sensitive boolean not null default false;
update posts set sensitive = true where id % 29 = 0;

-- 임시 저장: 예약 게시물 표를 같이 쓴다 (시각 없음)
alter table scheduled_posts alter column scheduled_at drop not null;
alter table scheduled_posts add column draft boolean not null default false;

analyze posts;
analyze comments;
