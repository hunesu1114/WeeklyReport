-- 메모장.
--
-- 칸반과 달리 개인 것이다. 보고서처럼 owner_id 로만 가른다 — 남의 메모는
-- 목록에도 뜨지 않는다. 나중에 공유가 필요해지면 그때 참여자 명단을 붙이면 된다.

create table memo_folder (
    id         bigserial primary key,
    owner_id   bigint not null references app_user (id) on delete cascade,
    -- 비어 있으면 최상위. 스스로를 가리켜 트리를 만든다.
    parent_id  bigint references memo_folder (id) on delete cascade,
    name       varchar(100) not null,
    sort_order int not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_memo_folder_owner on memo_folder (owner_id);
create index idx_memo_folder_parent on memo_folder (parent_id);

create table memo (
    id         bigserial primary key,
    owner_id   bigint not null references app_user (id) on delete cascade,
    /*
     * 폴더를 지워도 메모는 남는다(set null -> 최상위로 내려온다).
     * 폴더 정리하다 글이 통째로 사라지는 것보다, 최상위에 나와 있는 편이 낫다.
     */
    folder_id  bigint references memo_folder (id) on delete set null,

    title      varchar(200) not null,
    content    text not null default '',

    /*
     * 글꼴은 메모마다 따로 둔다. 메모장처럼 글 전체에 하나가 걸리는 방식이라
     * 내용은 순수한 텍스트로 남고, txt 로 내보내도 잃는 것이 없다.
     */
    font_family varchar(60),
    font_size   int,
    font_color  varchar(20),
    word_wrap   boolean not null default true,

    sort_order int not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_memo_owner on memo (owner_id);
create index idx_memo_folder on memo (folder_id);
