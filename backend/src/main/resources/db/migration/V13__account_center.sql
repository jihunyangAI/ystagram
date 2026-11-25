-- M6 ④-a: 가입 인증 코드·비밀번호 재설정 코드, 로그인 기록(원격 로그아웃), 계정 비활성화·삭제(30일 유예)

-- 이메일로 "보내는" 6자리 코드 (실제 발송 없이 Lab 화면에 표시)
create table auth_codes (
    id          bigserial primary key,
    purpose     varchar(10)  not null, -- SIGNUP / RESET
    target      varchar(255) not null, -- 이메일(가입) 또는 사용자 id(재설정)
    code        varchar(6)   not null,
    created_at  timestamptz  not null,
    expires_at  timestamptz  not null,
    used_at     timestamptz,
    attempts    integer      not null default 0
);
create index auth_codes_target_idx on auth_codes (purpose, target, created_at desc);

-- 로그인 한 번 = 한 줄. revoked_at이 차면 그 브라우저의 로그인이 끊긴다
create table login_sessions (
    id            bigserial primary key,
    user_id       bigint       not null references users (id) on delete cascade,
    method        varchar(10)  not null, -- PASSWORD / SIGNUP / TWO_FACTOR / LAB / ADD / RESET
    user_agent    varchar(300),
    ip            varchar(45),
    created_at    timestamptz  not null,
    last_seen_at  timestamptz  not null,
    revoked_at    timestamptz
);
create index login_sessions_user_idx on login_sessions (user_id, last_seen_at desc);

-- 비활성화(다시 로그인하면 복구)·삭제 요청(30일 안에 로그인하면 취소, 지나면 영구 삭제).
-- 둘 다 deleted_at도 채워서 기존 "탈퇴한 사람은 안 보이게" 조건을 그대로 쓴다
alter table users add column deactivated_at timestamptz;
alter table users add column delete_requested_at timestamptz;

analyze users;
