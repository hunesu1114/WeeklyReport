-- 메모 휴지통.
--
-- 지운 메모를 바로 없애지 않고 지운 시각만 적어 둔다. 잘못 눌러도 되살릴 수 있고,
-- 30일이 지나면 그 사람이 메모 화면을 열 때 정리한다(MemoService.workspace).
-- 지운 시각이 비어 있으면 살아 있는 메모다.
alter table memo add column deleted_at timestamptz;

-- 휴지통은 몇 장 안 된다. 지운 행만 담는다.
create index idx_memo_trash on memo (owner_id, deleted_at) where deleted_at is not null;
