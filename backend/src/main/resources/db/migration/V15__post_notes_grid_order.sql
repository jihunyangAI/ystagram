-- M6 ⑤: 게시물 위 노트(맞팔로우가 남기는 짧은 글, 3일), 프로필 그리드 재배치
create table post_notes (
    id          bigserial primary key,
    post_id     bigint       not null references posts (id) on delete cascade,
    user_id     bigint       not null references users (id) on delete cascade,
    body        varchar(60)  not null,
    created_at  timestamptz  not null,
    expires_at  timestamptz  not null,
    unique (post_id, user_id)
);
create index post_notes_post_idx on post_notes (post_id, created_at desc);

-- 프로필 그리드 순서: 처음에는 올린 시각, 재배치하면 이웃과 값을 맞바꾼다
alter table posts add column grid_sort timestamptz;
update posts set grid_sort = created_at;
alter table posts alter column grid_sort set not null;
-- 새 글은 올린 시각으로 (게시물을 넣는 곳이 여러 군데라 트리거로)
create function posts_grid_sort_default() returns trigger language plpgsql as $fn$
begin
    if new.grid_sort is null then
        new.grid_sort := new.created_at;
    end if;
    return new;
end
$fn$;
create trigger posts_grid_sort_default before insert on posts
    for each row execute function posts_grid_sort_default();

create index posts_grid_idx on posts (author_id, grid_sort desc, id desc) where deleted_at is null;

analyze posts;
