-- 마이페이지: 프로필 사진.
--
-- 파일 시스템 대신 DB 에 둔다. 배포는 컨테이너를 갈아끼우는 방식이라
-- 디스크에 두려면 볼륨과 백업 경로가 따로 필요한데, 아바타 한 장은
-- 수십 KB 라 DB 백업에 얹는 편이 운영이 단순하다.
--
-- 다만 app_user 에 바이트를 같이 두지는 않는다. 로그인한 사람은 요청마다
-- app_user 를 읽는데, 그때마다 사진까지 딸려 오면 매 요청이 수십 KB 씩 무거워진다.
-- 바이트만 옆 테이블로 빼고, app_user 에는 "있는지"와 "몇 번째인지"만 남긴다.
alter table app_user add column avatar_type varchar(50);

-- 사진이 바뀌면 URL 은 그대로여도 브라우저가 다시 받아야 한다.
-- 이 값을 쿼리스트링에 붙여 캐시를 끊는다.
alter table app_user add column avatar_version bigint not null default 0;

create table user_avatar (
    user_id    bigint primary key references app_user (id) on delete cascade,
    bytes      bytea not null,
    updated_at timestamptz not null
);
