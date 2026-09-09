-- 일반 로그인(아이디/비밀번호 + JWT).

create table app_user (
    id            bigserial primary key,
    username      varchar(50)  not null,
    -- BCrypt 해시. 평문은 어디에도 남기지 않는다.
    password_hash varchar(100) not null,
    display_name  varchar(50)  not null,
    -- ADMIN / USER. 첫 번째로 가입한 계정이 ADMIN 이 된다.
    role          varchar(20)  not null default 'USER',
    enabled       boolean      not null default true,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null
);

-- 대소문자를 구분하지 않고 유일해야 한다. 'Admin' 과 'admin' 이 따로 생기면 안 된다.
create unique index uk_app_user_username on app_user (lower(username));

-- ── 소유자 붙이기 ────────────────────────────────────────────
-- 로그인 이전에 쌓인 데이터가 이미 있으므로 nullable 로 추가한다.
-- 주인이 없는(null) 행은 첫 관리자 계정이 /api/auth/orphans/claim 으로 가져간다.
alter table report add column owner_id bigint references app_user (id) on delete cascade;
alter table project add column owner_id bigint references app_user (id) on delete cascade;

create index idx_report_owner on report (owner_id, report_date desc);
create index idx_project_owner on project (owner_id, sort_order);

-- 프로젝트 이름은 이제 사용자 안에서만 유일하면 된다.
-- 사람이 다르면 같은 이름의 프로젝트를 쓸 수 있어야 한다.
drop index if exists uk_project_name;
create unique index uk_project_owner_name on project (owner_id, lower(name));
