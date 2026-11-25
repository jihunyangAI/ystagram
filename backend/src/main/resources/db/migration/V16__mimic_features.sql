-- M6 ⑥: 릴스 자르기·자막(메타데이터로, 다시 인코딩하지 않음), 스토리 효과(부메랑), 홍보하기(가짜 광고 캠페인), 라이브 게스트(흉내)
alter table post_media add column trim_start_ms integer;
alter table post_media add column trim_end_ms integer;
alter table post_media add column overlays jsonb; -- [{text, startMs, endMs, y}]

alter table stories add column effect varchar(20); -- BOOMERANG

alter table ads add column promoted boolean not null default false; -- 사용자가 "홍보하기"로 만든 광고
alter table ads add column daily_budget integer;
alter table ads add column ends_at timestamptz;

alter table lives add column guest_id bigint references users (id) on delete set null;
alter table lives add column guest_status varchar(10); -- INVITED / JOINED
