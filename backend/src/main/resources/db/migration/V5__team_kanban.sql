-- 칸반을 팀 단위로. 프로젝트마다 참여자 명단을 둔다.
-- 설계 배경은 docs/team-kanban.md 참고.

-- ── 참여자 ────────────────────────────────────────────────
create table project_member (
    id         bigserial   primary key,
    project_id bigint      not null references project (id) on delete cascade,
    user_id    bigint      not null references app_user (id) on delete cascade,
    -- OWNER(관리) / MEMBER(공동 수정) / VIEWER(읽기만)
    role       varchar(20) not null default 'MEMBER',
    joined_at  timestamptz not null
);

create unique index uk_project_member on project_member (project_id, user_id);
-- "내가 속한 보드" 조회가 가장 잦다
create index idx_project_member_user on project_member (user_id, project_id);

-- 기존 개인 보드를 그 사람이 OWNER 인 1인 팀으로 바꾼다. 데이터 손실 없음.
-- owner_id 가 아직 비어 있는 보드(로그인 도입 이전 데이터)는 그대로 둔다.
-- 나중에 /api/auth/orphans/claim 이 owner 를 채울 때 멤버도 함께 넣는다.
insert into project_member (project_id, user_id, role, joined_at)
select p.id, p.owner_id, 'OWNER', now()
from project p
where p.owner_id is not null;

-- 프로젝트 이름 유일 제약을 없앤다.
-- 팀 보드에서 이름 중복을 막을 이유가 없고 마찰만 된다. 화면에서 알려주기만 한다.
drop index if exists uk_project_owner_name;

-- ── 카드: 담당자와 버전 ───────────────────────────────────
-- 사람이 지워져도 카드는 남아야 한다. 담당자만 비운다.
alter table kanban_card
    add column assignee_id bigint references app_user (id) on delete set null;

-- 두 사람이 같은 카드를 열어 저장했을 때 뒤가 조용히 이기는 것을 막는다
alter table kanban_card
    add column version bigint not null default 0;

create index idx_kanban_assignee on kanban_card (assignee_id, due_date);

-- ── 초대 ──────────────────────────────────────────────────
create table project_invitation (
    id           bigserial   primary key,
    project_id   bigint      not null references project (id) on delete cascade,
    inviter_id   bigint      not null references app_user (id) on delete cascade,
    invitee_id   bigint      not null references app_user (id) on delete cascade,
    -- 수락하면 이 역할로 들어간다
    role         varchar(20) not null default 'MEMBER',
    -- PENDING / ACCEPTED / DECLINED / CANCELED
    status       varchar(20) not null default 'PENDING',
    created_at   timestamptz not null,
    responded_at timestamptz
);

-- 같은 사람에게 대기 중인 초대가 둘 이상 쌓이지 않게 한다.
-- 거절/취소된 건은 기록으로 남아야 하므로 PENDING 만 막는다.
create unique index uk_invitation_pending
    on project_invitation (project_id, invitee_id)
    where status = 'PENDING';

create index idx_invitation_invitee on project_invitation (invitee_id, status, created_at desc);

-- ── 알림함 ────────────────────────────────────────────────
create table notification (
    id            bigserial    primary key,
    recipient_id  bigint       not null references app_user (id) on delete cascade,
    -- PROJECT_INVITE / INVITE_ACCEPTED / INVITE_DECLINED / CARD_ASSIGNED / MEMBER_REMOVED
    type          varchar(30)  not null,
    title         varchar(200) not null,
    message       varchar(500),
    -- 눌렀을 때 갈 화면 (예: /kanban/3)
    link          varchar(200),
    -- 초대 알림이면 그 초대를 가리킨다. 수락/거절 버튼이 여기를 쓴다.
    invitation_id bigint       references project_invitation (id) on delete cascade,
    read_at       timestamptz,
    created_at    timestamptz  not null
);

create index idx_notification_recipient on notification (recipient_id, created_at desc);
-- 안 읽은 것 개수를 헤더에서 계속 센다
create index idx_notification_unread on notification (recipient_id) where read_at is null;

-- ── 활동 기록 ─────────────────────────────────────────────
-- 카드별 / 프로젝트별 / 내 전체 로 조회한다.
create table activity_log (
    id           bigserial   primary key,
    project_id   bigint      not null references project (id) on delete cascade,
    -- 카드와 무관한 활동(멤버 추가 등)이면 비어 있다.
    -- 카드를 지워도 기록은 남아야 하므로 on delete set null.
    card_id      bigint      references kanban_card (id) on delete set null,
    -- 사람이 지워져도 기록은 남는다
    actor_id     bigint      references app_user (id) on delete set null,

    -- CARD_CREATED / CARD_UPDATED / CARD_MOVED / CARD_ASSIGNED / CARD_DELETED
    -- PROJECT_CREATED / PROJECT_UPDATED
    -- MEMBER_INVITED / MEMBER_JOINED / MEMBER_LEFT / MEMBER_REMOVED / MEMBER_ROLE_CHANGED
    type         varchar(30) not null,

    -- 카드가 지워져도 "무엇을" 옮겼는지 읽히도록 제목을 박아둔다
    card_title   varchar(200),
    from_status  varchar(20),
    to_status    varchar(20),
    -- 멤버 관련 활동의 대상자
    target_name  varchar(50),
    -- 그 밖에 한 줄로 남길 것
    detail       varchar(500),

    created_at   timestamptz not null
);

create index idx_activity_project on activity_log (project_id, created_at desc);
create index idx_activity_card on activity_log (card_id, created_at desc);
