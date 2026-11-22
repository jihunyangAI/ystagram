-- M6 ③: 가짜 음원 목록(소리는 화면이 곡마다 정해진 값으로 멜로디를 합성), 원본 오디오, 게시물·스토리 음악
create table audio_tracks (
    id              bigserial primary key,
    title           varchar(80)  not null,
    artist          varchar(60)  not null,
    genre           varchar(20),
    bpm             integer      not null,
    seed            integer      not null,
    duration_ms     integer      not null,
    -- 원본 오디오: 이 릴스에서 처음 나온 소리 (음원 목록 곡은 null)
    origin_post_id  bigint references posts (id) on delete set null,
    origin_user_id  bigint references users (id) on delete set null,
    created_at      timestamptz  not null
);
create index audio_tracks_title_idx on audio_tracks (lower(title));

alter table posts add column audio_id bigint references audio_tracks (id) on delete set null;
alter table stories add column audio_id bigint references audio_tracks (id) on delete set null;
create index posts_audio_idx on posts (audio_id, created_at desc) where audio_id is not null;

analyze posts;
analyze stories;
