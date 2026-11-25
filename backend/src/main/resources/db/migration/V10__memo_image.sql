-- 메모 그림.
--
-- 본문은 지금처럼 글자만 둔다. 그림 자리에는 ![설명](img:아이디) 한 줄이 들어가고,
-- 그림 바이트는 여기에 따로 둔다. 본문에 base64 로 넣으면 1.2초마다 도는 자동 저장이
-- 매번 수 MB 를 보내고, 목록과 검색도 그만큼 무거워진다.
--
-- 프로필 사진(V6)과 같은 이유로 DB 에 둔다. 컨테이너를 갈아끼우는 배포라 디스크에 두려면
-- 볼륨과 백업 경로가 따로 필요하고, DB 에 두면 지금 쓰는 pg_dumpall 백업에 그대로 실린다.

create table memo_image (
    -- 브라우저가 미리 만든 10자 아이디. 붙여넣는 순간 최종 토큰을 본문에 넣고
    -- 올리기는 뒤에서 한다 — 그래야 Ctrl+Z 한 번에 토큰이 통째로 사라진다.
    id            varchar(16) primary key,
    owner_id      bigint not null references app_user (id) on delete cascade,
    -- 처음 올린 메모. 메모당 장 수를 셀 때 쓴다. 메모가 영구 삭제되면 비운다.
    memo_id       bigint references memo (id) on delete set null,
    content_type  varchar(50) not null,
    byte_size     int not null,
    width         int not null,
    height        int not null,
    original_name varchar(200),
    created_at    timestamptz not null,
    /*
     * 어느 메모에서도 가리키지 않게 된 시각. 비어 있으면 어딘가에서 쓰고 있다.
     * 이 시각에서 하루가 지나면 지운다. 지운 토큰을 Ctrl+Z 로 되살려도
     * 그림이 남아 있게 하루를 기다린다.
     */
    orphaned_at   timestamptz
);

create index idx_memo_image_owner on memo_image (owner_id);
create index idx_memo_image_memo on memo_image (memo_id);
-- 정리할 것만 찾는다.
create index idx_memo_image_orphaned on memo_image (owner_id, orphaned_at) where orphaned_at is not null;

-- 바이트는 옆 테이블로 뺀다. 그림 목록을 읽을 때 MB 단위 데이터가 딸려 오지 않게.
create table memo_image_blob (
    image_id varchar(16) primary key references memo_image (id) on delete cascade,
    bytes    bytea not null,
    -- 긴 변 320px 섬네일. 그림 줄과 크게 보기의 아래 줄이 쓴다.
    thumb    bytea not null
);

-- 어느 메모가 어느 그림을 가리키는지. 저장할 때마다 본문의 토큰으로 다시 맞춘다.
-- 같은 그림을 다른 메모에 붙여넣어도 된다 — 줄이 하나 더 생길 뿐이다.
create table memo_image_ref (
    memo_id  bigint not null references memo (id) on delete cascade,
    image_id varchar(16) not null references memo_image (id) on delete cascade,
    primary key (memo_id, image_id)
);

create index idx_memo_image_ref_image on memo_image_ref (image_id);
