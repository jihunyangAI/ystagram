-- 게시물 보관(나만 보기)과 프로필 고정(최대 3개)
alter table posts add column archived_at timestamptz;
alter table posts add column pinned_at timestamptz;
create index posts_pinned_idx on posts (author_id, pinned_at desc) where pinned_at is not null;
create index posts_deleted_idx on posts (author_id, deleted_at desc) where deleted_at is not null;
