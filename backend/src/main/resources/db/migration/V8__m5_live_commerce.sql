-- M5-②: 라이브, 음성·영상 통화, 리믹스, 쇼핑, 구독·선물 (결제는 모두 가짜)

-- 라이브 방송
create table lives (
    id            bigserial primary key,
    host_id       bigint       not null references users (id) on delete cascade,
    title         varchar(100),
    kind          varchar(10)  not null default 'CAMERA', -- CAMERA(브라우저 카메라, WebRTC) | VIDEO(가짜: 영상 반복 재생)
    video_url     varchar(500),
    poster_url    varchar(500),
    status        varchar(10)  not null default 'LIVE',   -- LIVE | ENDED
    started_at    timestamptz  not null,
    ended_at      timestamptz,
    peak_viewers  integer      not null default 0,
    total_viewers integer      not null default 0,
    heart_count   integer      not null default 0,
    comment_count integer      not null default 0,
    badge_count   integer      not null default 0
);
create index lives_status_idx on lives (status, started_at desc);
create unique index lives_one_per_host on lives (host_id) where status = 'LIVE';

create table live_comments (
    id         bigserial primary key,
    live_id    bigint       not null references lives (id) on delete cascade,
    user_id    bigint       not null references users (id) on delete cascade,
    kind       varchar(10)  not null default 'COMMENT', -- COMMENT | JOIN | BADGE
    body       varchar(300),
    badge      integer,                                  -- 배지 단계 1~3 (BADGE)
    created_at timestamptz  not null
);
create index live_comments_live_idx on live_comments (live_id, id);

create table live_viewers (
    live_id   bigint      not null references lives (id) on delete cascade,
    user_id   bigint      not null references users (id) on delete cascade,
    joined_at timestamptz not null,
    primary key (live_id, user_id)
);

-- 1:1 통화 (WebRTC, 신호만 서버를 거친다)
create table calls (
    id              bigserial primary key,
    conversation_id bigint      not null references conversations (id) on delete cascade,
    caller_id       bigint      not null references users (id) on delete cascade,
    callee_id       bigint      not null references users (id) on delete cascade,
    video           boolean     not null,
    status          varchar(10) not null, -- RINGING | ACTIVE | ENDED | MISSED | DECLINED | CANCELED
    created_at      timestamptz not null,
    answered_at     timestamptz,
    ended_at        timestamptz
);
create index calls_open_idx on calls (status) where status in ('RINGING', 'ACTIVE');
alter table messages add column call_id bigint references calls (id) on delete set null;

-- 리믹스 (원본 릴스와 나란히)
alter table posts add column remix_of_id      bigint references posts (id) on delete set null;
alter table posts add column allow_remix      boolean not null default true;
-- 구독자 전용 게시물
alter table posts add column subscribers_only boolean not null default false;
create index posts_remix_idx on posts (remix_of_id) where remix_of_id is not null;

-- 쇼핑: 제품, 게시물의 제품 태그, 위시리스트, 가짜 주문
create table products (
    id          bigserial primary key,
    seller_id   bigint       not null references users (id) on delete cascade,
    name        varchar(100) not null,
    price       integer      not null,  -- 원
    description varchar(1000),
    image_url   varchar(500) not null,
    thumb_url   varchar(500) not null,
    created_at  timestamptz  not null,
    deleted_at  timestamptz
);
create index products_seller_idx on products (seller_id) where deleted_at is null;

create table post_products (
    post_id     bigint           not null references posts (id) on delete cascade,
    product_id  bigint           not null references products (id) on delete cascade,
    media_index integer          not null default 0,
    x           double precision not null default 0.5,
    y           double precision not null default 0.5,
    primary key (post_id, product_id)
);
create index post_products_product_idx on post_products (product_id);

create table product_wishlist (
    user_id    bigint      not null references users (id) on delete cascade,
    product_id bigint      not null references products (id) on delete cascade,
    created_at timestamptz not null,
    primary key (user_id, product_id)
);

create table orders (
    id         bigserial primary key,
    buyer_id   bigint      not null references users (id) on delete cascade,
    product_id bigint      not null references products (id) on delete cascade,
    quantity   integer     not null,
    amount     integer     not null,
    created_at timestamptz not null
);
create index orders_buyer_idx on orders (buyer_id, created_at desc);

-- 구독 (크리에이터가 월 가격을 정하면 받을 수 있다)
alter table users add column subscription_price integer;
create table subscriptions (
    subscriber_id bigint      not null references users (id) on delete cascade,
    creator_id    bigint      not null references users (id) on delete cascade,
    price         integer     not null,
    status        varchar(10) not null default 'ACTIVE', -- ACTIVE | CANCELED
    started_at    timestamptz not null,
    renews_at     timestamptz not null,
    canceled_at   timestamptz,
    primary key (subscriber_id, creator_id)
);
create index subscriptions_creator_idx on subscriptions (creator_id) where status = 'ACTIVE';

-- 선물(릴스)·배지(라이브): 별 1개 = 110원 (가짜)
create table gifts (
    id           bigserial primary key,
    sender_id    bigint      not null references users (id) on delete cascade,
    recipient_id bigint      not null references users (id) on delete cascade,
    post_id      bigint references posts (id) on delete set null,
    live_id      bigint references lives (id) on delete set null,
    kind         varchar(10) not null, -- GIFT | BADGE
    stars        integer     not null,
    amount       integer     not null,
    created_at   timestamptz not null
);
create index gifts_recipient_idx on gifts (recipient_id, created_at desc);
