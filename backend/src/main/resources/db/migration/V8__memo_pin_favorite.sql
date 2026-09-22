-- 메모 고정과 즐겨찾기.
--
-- 둘을 나눠 두는 이유가 있다. 고정은 "이 폴더를 열면 이게 맨 위에 있어야 한다"는
-- 자리 이야기라 폴더를 벗어나면 뜻을 잃고, 즐겨찾기는 "폴더가 어디든 늘 꺼내 본다"는
-- 이야기라 폴더와 상관이 없다. 한 깃발로 둘 다 하려 들면 둘 중 하나는 어색해진다.

-- 메모는 한 폴더에만 들어 있으므로, 이 깃발 하나가 곧 '그 폴더에서의 고정'이 된다.
-- 메모를 다른 폴더로 옮기면 고정도 따라간다.
alter table memo add column pinned boolean not null default false;
alter table memo add column favorite boolean not null default false;

-- 폴더를 열 때 고정된 것부터 찾는다.
create index idx_memo_folder_pinned on memo (owner_id, folder_id, pinned);

-- 즐겨찾기는 전체에서 몇 개 안 된다. 표시된 행만 담는다.
create index idx_memo_favorite on memo (owner_id) where favorite;
