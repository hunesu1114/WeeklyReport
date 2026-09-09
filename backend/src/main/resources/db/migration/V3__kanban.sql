-- 칸반 보드. 프로젝트별로 하나씩 갖는다.

create table project (
    id          bigserial primary key,
    name        varchar(100) not null,
    description varchar(500),
    -- 보드/카드에 쓰는 강조색. 비워두면 화면이 기본색을 쓴다.
    color       varchar(20),
    active      boolean     not null default true,
    sort_order  integer     not null default 0,
    created_at  timestamptz not null,
    updated_at  timestamptz not null
);

create unique index uk_project_name on project (name);
create index idx_project_active on project (active, sort_order);

create table kanban_card (
    id         bigserial primary key,
    project_id bigint       not null references project (id) on delete cascade,

    -- BACKLOG / TODO / ING / DONE
    status     varchar(20)  not null,
    title      varchar(200) not null,
    content    text,
    -- LOW / NORMAL / HIGH / URGENT
    priority   varchar(20)  not null default 'NORMAL',

    -- 화면에는 '생성일'로 보이지만 실제로는 일을 시작한 날이다.
    -- 주간보고의 '금주 기간'과 겹치는 카드를 찾을 때 이 값을 쓴다.
    start_date date,
    -- 목표 완료일. 임박 알림의 기준.
    due_date   date,

    -- 같은 칸 안에서의 표시 순서(0-based)
    sort_order integer      not null default 0,
    created_at timestamptz  not null,
    updated_at timestamptz  not null
);

create index idx_kanban_board on kanban_card (project_id, status, sort_order);
-- 보고서 연동: 기간으로 훑는다
create index idx_kanban_start_date on kanban_card (start_date);
-- 임박 알림: 완료일이 가까운 것부터
create index idx_kanban_due_date on kanban_card (due_date);

-- 처음 들어왔을 때 빈 화면을 보지 않도록 보드 하나를 만들어 둔다. 이름은 바꿔도 된다.
insert into project (name, description, color, active, sort_order, created_at, updated_at)
values ('기본 프로젝트', '처음 만들어진 칸반 보드입니다. 이름과 설명은 언제든 바꿀 수 있습니다.',
        '#22c55e', true, 0, now(), now());
