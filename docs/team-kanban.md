# 팀 칸반 전환 설계

칸반을 **여러 사람이 같은 보드를 쓰는** 형태로 올리기 위한 설계. 아직 구현 전이다.

---

## 1. 지금 구조와 한계

로그인을 붙이면서 모든 데이터를 사람에게 묶었다.

```
app_user ──1:N──> project ──1:N──> kanban_card
              (owner_id)
```

- `KanbanService` 는 모든 조회에 `owner_id = 현재 사용자` 를 건다.
- `loadProject()` 는 남의 보드를 찾을 때 404 로 답한다.
- 프로젝트 이름은 `(owner_id, lower(name))` 로 유일하다.

**한계는 하나다. 보드를 남에게 보여줄 방법이 없다.** 소유자 한 명만 존재하므로
"같이 본다"를 표현할 자리가 아예 없다.

그 외에 팀으로 가면 새로 필요해지는 것:

| 없는 것 | 왜 필요한가 |
|---|---|
| 담당자 | 팀 보드에서 "이 일은 누구 것인가"가 카드의 핵심 정보다 |
| 동시 편집 처리 | 두 사람이 같은 카드를 옮기면 지금은 순서가 깨진다 (6장) |
| 변경 알림 | 남이 옮긴 것을 내 화면이 모른다 |

---

## 2. 핵심 결정: Team 엔티티를 두지 않는다

두 가지 길이 있다.

**A. 프로젝트 멤버십** — 프로젝트마다 참여자 명단을 둔다.

```
project ──1:N──> project_member ──N:1──> app_user
```

**B. 팀 엔티티** — 팀을 만들고 프로젝트가 팀에 속한다.

```
team ──1:N──> team_member
  └──1:N──> project
```

**A 를 고른다.** "팀단위 프로젝트 칸반"에서 실제로 필요한 건 *이 보드를 누가 볼 수
있는가* 하나다. B 는 `team → project → card` 3단이 되어 권한 판정이 한 단계 깊어지고,
팀이 하나뿐인 조직에서는 순수한 오버헤드다.

> **B 가 필요해지는 시점**: 프로젝트가 10개를 넘고 같은 명단을 반복해서 넣게 될 때.
> 그때 `team` 테이블과 `project.team_id` 를 추가하고, `project_member` 를
> "팀 기본 명단에서 상속 + 프로젝트별 예외" 로 바꾸면 된다.
> 지금 구조가 그 확장을 막지 않는다 — `project_member` 가 그대로 예외 테이블이 된다.

---

## 3. 데이터 모델

### 3.1 새 테이블: project_member

```sql
create table project_member (
    id         bigserial primary key,
    project_id bigint      not null references project (id) on delete cascade,
    user_id    bigint      not null references app_user (id) on delete cascade,
    -- OWNER / MEMBER / VIEWER
    role       varchar(20) not null default 'MEMBER',
    joined_at  timestamptz not null
);

-- 한 사람이 같은 보드에 두 번 들어갈 수 없다
create unique index uk_project_member on project_member (project_id, user_id);
-- "내가 속한 보드" 조회가 가장 잦다
create index idx_project_member_user on project_member (user_id, project_id);
```

### 3.2 카드에 담당자

```sql
alter table kanban_card
    add column assignee_id bigint references app_user (id) on delete set null;

create index idx_kanban_assignee on kanban_card (assignee_id, due_date);
```

`on delete set null` 인 이유: 사람이 지워져도 카드는 남아야 한다. 담당자만 비고
화면에는 "담당 없음" 으로 보인다.

### 3.3 카드에 버전 (동시 편집 감지)

```sql
alter table kanban_card add column version bigint not null default 0;
```

JPA `@Version`. 두 사람이 같은 카드를 열어 저장하면 뒤가 조용히 이기는 것을 막는다
(6.2 참고).

### 3.4 project.owner_id 의 의미 축소

지우지 않고 **"만든 사람" 기록**으로만 남긴다. 권한 판정은 전부 `project_member` 가 한다.

```sql
-- 프로젝트 이름 유일 제약은 없앤다.
-- 팀 보드에서 이름 중복은 막을 이유가 없고, 오히려 마찰만 된다.
-- 중복은 화면에서 "같은 이름의 보드가 있습니다" 로 알려주기만 한다.
drop index if exists uk_project_owner_name;
```

### 3.5 마이그레이션 (V5)

기존 개인 보드를 **그 사람이 OWNER 인 1인 팀**으로 바꾼다. 데이터 손실 없음.

```sql
insert into project_member (project_id, user_id, role, joined_at)
select p.id, p.owner_id, 'OWNER', now()
from project p
where p.owner_id is not null
on conflict do nothing;
```

> `owner_id` 가 아직 null 인 보드(로그인 이전 데이터)는 그대로 둔다.
> 기존 `/api/auth/orphans/claim` 이 owner 를 채운 뒤, 같은 트랜잭션에서
> `project_member` 도 함께 넣도록 `AuthService.claimOrphans()` 를 고친다.

---

## 4. 역할과 권한

앱 전역 역할(`app_user.role` = ADMIN/USER)과 **분리한다.** 전역 ADMIN 은 주인 없는
데이터 회수 전용이고, 보드 권한은 프로젝트마다 따로 정한다.

| 할 수 있는 일 | OWNER | MEMBER | VIEWER |
|---|:---:|:---:|:---:|
| 보드 보기 | ● | ● | ● |
| 카드 만들기 · 고치기 · 옮기기 | ● | ● | |
| 카드 지우기 | ● | ● | |
| 담당자 지정 | ● | ● | |
| 멤버 추가 · 역할 변경 · 내보내기 | ● | | |
| 보드 설정(이름/색/활성) | ● | | |
| 보드 삭제 | ● | | |
| 보드 나가기 | ●※ | ● | ● |

※ 마지막 OWNER 는 나갈 수 없다. 주인 없는 보드가 생기면 아무도 멤버를 못 넣는다.
나가려면 먼저 다른 사람을 OWNER 로 올려야 한다.

**MEMBER 가 남의 카드도 고칠 수 있게 한다.** 팀 보드에서 "내 카드만 수정" 은 실무에서
곧바로 막힌다 — 담당자가 휴가면 아무도 못 옮긴다. 대신 누가 바꿨는지 남긴다(9장).

---

## 5. 권한 판정을 한 곳으로

지금은 `KanbanService` 안에 `owner_id` 비교가 흩어져 있다. 멤버십으로 바뀌면
판정이 더 복잡해지므로 한 곳에 모은다.

```java
@Component
public class ProjectAccess {

    /** 읽기 권한. 없으면 404 — '권한 없음'이라 답하면 그 보드의 존재가 새어 나간다. */
    public Project requireRead(Long projectId) { ... }

    /** 카드를 건드릴 권한. VIEWER 면 403. */
    public Project requireWrite(Long projectId) { ... }

    /** 멤버·설정·삭제. OWNER 만. */
    public Project requireOwner(Long projectId) { ... }

    public ProjectRole roleOf(Long projectId, Long userId) { ... }
}
```

- **읽을 수 없는 보드 → 404**, **읽을 수는 있으나 쓸 수 없음 → 403**.
  이 구분을 지켜야 보드 목록에 없는 id 를 찍어보는 것으로 존재 여부를 알아낼 수 없다.
- 조회 쿼리는 `owner.id = :userId` 를 전부
  `exists (select 1 from ProjectMember m where m.project = p and m.user.id = :userId)` 로 바꾼다.

---

## 6. 동시 편집 — 여기가 진짜 문제다

팀으로 가면서 새로 생기는 유일한 **정합성** 위험이다. 나머지는 권한 배선일 뿐이다.

### 6.1 카드 순서 경합

지금 `moveCard()` 는 칸 전체를 읽어 `0..n` 으로 다시 번호를 매기고 통째로 저장한다.

```java
List<KanbanCard> target = cardsIn(projectId, to);   // ① 스냅샷을 읽고
target.add(position, card);                          // ② 메모리에서 끼워넣고
renumber(target);                                    // ③ 전부 다시 저장
```

두 사람이 같은 보드에서 동시에 옮기면 **둘 다 ① 을 같은 상태로 읽는다.** 나중에
커밋한 쪽이 앞사람의 이동을 덮어쓴다. 카드가 사라지진 않지만 순서가 뒤죽박죽이 되고,
사용자 눈에는 "방금 옮긴 게 제자리로 돌아갔다"로 보인다.

**세 가지 선택지**

| 방법 | 구현 비용 | 동시성 | 비고 |
|---|---|---|---|
| A. 보드 행 비관적 잠금 | 3줄 | 보드 단위 직렬화 | 이동이 짧아 대기 거의 없음 |
| B. `@Version` 낙관적 잠금 + 재시도 | 중간 | 충돌 시 실패 | 드래그가 튕기면 UX 최악 |
| C. LexoRank(문자열 랭크) | 큼 | 경합 거의 없음 | 재번호 자체가 사라짐 |

**A 를 고른다.** 이동 트랜잭션은 수 ms 이고, 한 보드를 동시에 드래그하는 사람은 많아야
두셋이다. 락 대기가 체감되지 않는다.

```java
// 이동은 보드 단위로 줄을 세운다. 같은 보드를 동시에 건드리는 것만 막고
// 다른 보드는 서로 영향이 없다.
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from Project p where p.id = :id")
Optional<Project> findByIdForUpdate(@Param("id") Long id);
```

`moveCard()` / `deleteCard()` 맨 앞에서 이 잠금을 잡는다. 둘 다 재번호를 매기는
메서드다.

> **C 로 가야 할 때**: 한 보드에 카드가 수백 장이 되어 재번호 비용이 눈에 띄거나,
> 오프라인 편집을 지원하게 될 때. 그때 `sort_order integer` 를 `rank varchar` 로
> 바꾸고 두 카드 사이 값을 계산하는 방식으로 옮기면 된다. 잠금은 그대로 둬도 된다.

### 6.2 카드 내용 경합

두 사람이 같은 카드를 열어두고 각자 저장하면 뒤가 이긴다. 앞사람이 쓴 내용은
알림도 없이 사라진다.

`@Version` 으로 잡는다.

```java
@Version
@Column(name = "version", nullable = false)
private long version;
```

- `CardRequest` 에 `version` 을 받는다.
- 다르면 `OptimisticLockException` → **409 Conflict** + 현재 서버 상태를 함께 내려준다.
- 화면: "다른 사람이 먼저 수정했습니다" 와 함께 **[내 내용 유지] / [새로 받기]** 를 준다.
  그냥 덮어쓰거나 그냥 버리게 하면 둘 다 화가 난다.

순서 이동(`moveCard`)에는 버전을 요구하지 않는다. 드래그는 원자적 조작이고,
6.1 의 잠금으로 이미 직렬화된다.

---

## 7. 남의 변경을 내 화면에 반영하기

| 방법 | 서버 비용 | 지연 | 구현 |
|---|---|---|---|
| 폴링 (10~15초) | 낮음 | 최대 15초 | 매우 쉬움 |
| SSE | 낮음 | 즉시 | 쉬움 (단방향) |
| WebSocket | 중간 | 즉시 | 양방향까지 필요할 때 |

**단계적으로 간다.**

- **Phase 1 — 폴링.** 보드 화면에서 15초마다, 그리고 **탭이 다시 활성화될 때**
  (`visibilitychange`) 새로 읽는다. 자리를 비웠다 돌아오는 순간이 가장 중요하다.
  잠금(6.1)과 합쳐지면 이것만으로도 실무에서 거의 불편하지 않다.
- **Phase 2 — SSE.** 변경은 여전히 REST 로 하고, 서버는 "보드 N 이 바뀌었다"는
  신호만 보낸다. 화면은 그 신호를 받으면 보드를 다시 읽는다.
  변경 내용을 실어 보내지 않으므로 이벤트 설계가 단순하고, 놓쳐도 다음 신호에 복구된다.

```
GET /api/kanban/projects/{id}/stream   (text/event-stream)
  event: board-changed
  data: {"projectId":3,"by":"hskim","at":"..."}
```

> **배포 주의**: SSE 는 프록시가 버퍼링하면 죽는다. 앱 nginx 의 `/api/` 는 이미
> `proxy_buffering off` 라 괜찮지만, **edge 의 `weekly-report.conf` 에도
> `proxy_buffering off` 와 넉넉한 `proxy_read_timeout` 이 필요**하다.
> 지금 edge 는 `proxy_read_timeout 120s` 라 2분마다 끊긴다 — heartbeat(15초 주석 이벤트)를
> 보내거나 타임아웃을 늘려야 한다.

---

## 8. 알림과 주간보고 연동의 변화

### 8.1 임박 알림

지금은 "내 소유 카드" 전부다. 팀에서는 **내가 담당한 카드**만 기본으로 한다.
팀 전체 임박 카드가 모두에게 울리면 아무도 안 본다.

```
GET /api/kanban/cards/due-soon            -> 내가 담당인 것 (기본)
GET /api/kanban/cards/due-soon?scope=team -> 내가 속한 보드 전체
```

담당자가 없는 임박 카드는 헤더 종에 넣지 않는다. 대신 **보드 상단에
"담당 없는 임박 N건"** 으로 보여준다. 아무도 책임지지 않는 일이 조용히 지나가는 것을
막으면서, 모두를 시끄럽게 하지도 않는다.

### 8.2 주간보고 연동

**이게 팀 전환에서 가장 조용히 깨지는 부분이다.** 지금 `칸반 연동` 패널은
"금주 기간에 시작한 카드"를 전부 가져온다. 보드가 팀 것이 되면 **남의 카드가
내 주간보고에 쏟아진다.**

```
GET /api/kanban/cards/started-between?from=&to=&assignee=me   (기본값)
```

- 기본은 **내가 담당한 카드**.
- 패널에 `내 카드 / 팀 전체` 토글을 둔다. 팀 리더가 팀 주간보고를 쓸 때 쓴다.
- 담당자가 없는 카드는 "내 카드" 에 포함하지 않는다. 토글로만 보인다.

### 8.3 보고서는 개인 것으로 둔다

**보고서에는 멤버십을 붙이지 않는다.** 주간보고는 개인 산출물이고, 팀 공유는
별개의 요구사항(결재선·열람권)이다. 지금 섞으면 둘 다 어중간해진다.
칸반만 팀으로 올리고 보고서는 그대로 둔다.

---

## 9. 활동 기록 (선택)

팀 보드에서 가장 자주 나오는 질문은 "이거 누가 옮겼어?" 다.

```sql
create table card_activity (
    id         bigserial primary key,
    card_id    bigint      not null references kanban_card (id) on delete cascade,
    actor_id   bigint      references app_user (id) on delete set null,
    -- CREATED / MOVED / ASSIGNED / EDITED / DELETED
    type       varchar(20) not null,
    detail     jsonb,
    created_at timestamptz not null
);

create index idx_card_activity_card on card_activity (card_id, created_at desc);
```

카드 대화상자 아래에 최근 10건을 보여준다. Phase 3.

---

## 10. API 변경 요약

**새로 생기는 것**

| 메서드 | 경로 | 권한 |
|---|---|---|
| `GET` | `/api/kanban/projects/{id}/members` | 읽기 |
| `PATCH` | `/api/kanban/projects/{id}/members/{userId}` (역할 변경) | OWNER |
| `DELETE` | `/api/kanban/projects/{id}/members/{userId}` | OWNER (본인이면 나가기) |
| `POST` | `/api/kanban/projects/{id}/invitations` | OWNER |
| `GET` | `/api/kanban/projects/{id}/invitations` (대기 중) | 읽기 |
| `DELETE` | `/api/kanban/projects/{id}/invitations/{id}` (취소) | OWNER |
| `GET` | `/api/kanban/invitations` (내가 받은) | 로그인 |
| `POST` | `/api/kanban/invitations/{id}/accept` · `/decline` | 받은 본인 |
| `GET` | `/api/meta/users?query=` | 로그인 (초대할 사람 자동완성) |
| `GET` | `/api/kanban/notifications`, `/notifications/{id}/read`, `/read-all` | 로그인 |
| `GET` | `/api/kanban/projects/{id}/activities`, `.../cards/{cardId}/activities`, `/api/kanban/activities` | 읽기 |
| `GET` | `/api/kanban/projects/{id}/export` (엑셀) | 읽기 |
| `WS` | `/ws/kanban` | 첫 메시지로 인증, 보드는 멤버만 구독 |

> 멤버 직접 추가(`POST .../members`)는 넣지 않았다. 초대를 보내고 상대가 수락해야
> 참여자가 된다(11장). 실시간도 SSE 대신 WebSocket 으로 갔다(7장).

**바뀌는 것**

- `GET /api/kanban/projects` — 소유 → **내가 멤버인 보드**. 응답에 `myRole` 추가.
- `POST|PUT /api/kanban/cards` — `assigneeId`, `version` 추가
- `PUT /api/kanban/cards/{id}` — 409 응답 추가
- `GET /api/kanban/cards/due-soon` — `scope` 추가, 기본 = 내 담당
- `GET /api/kanban/cards/started-between` — `assignee` 추가, 기본 = `me`
- 보드/카드 응답 — `assignee{id,displayName}`, `version`

> `/api/meta/users` 는 **username 과 displayName 만** 내려준다.
> 멤버를 붙이려면 남의 계정을 찾아야 하지만, 그 이상은 알 필요가 없다.

---

## 11. 멤버 초대 방식

**초대 → 알림 → 수락**으로 간다. 초안은 "아이디로 직접 추가"였으나, 초대는 상대
화면에 보드를 하나 더 붙이는 일이라 본인이 받아들이는 편이 맞다.

화면: 참여자 → 아이디·이름 검색(자동완성) → 역할 고르기 → 초대하기.
받은 사람의 알림함에 뜨고, 거기서 수락하면 참여자가 된다. 보낸 초대는 OWNER 가
목록에서 보고 취소할 수 있다.

초대 링크·코드·메일은 조직 밖 사람과 협업할 때 필요해지는 것이라 지금은 넣지 않는다.

---

## 12. 화면에서 달라지는 것

- **보드 탭** — 내가 멤버인 보드. 내 역할 배지(VIEWER 면 눈에 띄게).
- **카드** — 담당자 아바타(표시 이름 첫 글자). 담당 없으면 회색 점선 원.
- **카드 대화상자** — 담당자 고르기(멤버 목록에서), 저장 충돌 시 안내.
- **VIEWER** — `draggable=false`, 추가/수정 버튼 감춤. 막힌 버튼을 눌러보고
  거절당하는 것보다 처음부터 안 보이는 게 낫다.
- **보드 헤더** — 멤버 아바타 줄, "담당 없는 임박 N건".
- **칸반 연동 패널(보고서)** — `내 카드 / 팀 전체` 토글.

---

## 13. 구현 순서

**Phase 1 — 같이 보고 같이 쓴다 (핵심)**
1. `V5__team_kanban.sql` — `project_member`, `assignee_id`, `version`, 기존 owner 승격
2. `ProjectAccess` 로 권한 판정 집중, 모든 쿼리 `owner_id` → 멤버십
3. 멤버 API + `/api/meta/users`
4. 보드 잠금(6.1) — **멤버 API보다 먼저 넣어도 된다. 지금도 있는 버그다.**
5. 담당자 지정, 카드 버전 충돌(409)
6. 화면: 멤버 관리, 담당자, VIEWER 처리
7. 폴링 + 탭 복귀 시 새로고침

**Phase 2 — 남의 변경이 바로 보인다**
8. SSE + edge nginx 버퍼링/타임아웃 조정
9. 알림 범위 정리(내 담당 / 담당 없는 임박)

**Phase 3 — 누가 언제 했는지**
10. `card_activity` 와 카드별 히스토리

Phase 1 만으로도 "여러 명이 같은 보드를 쓴다"는 목적은 달성된다.
2·3 은 사람이 늘어난 뒤에 판단해도 늦지 않다.

---

## 14. 하지 않기로 한 것

| 안 하는 것 | 이유 |
|---|---|
| Team 엔티티 | 프로젝트가 10개 넘게 같은 명단을 쓸 때 도입 (2장) |
| 보고서 팀 공유 | 주간보고는 개인 산출물. 결재·열람권은 별개 요구사항 |
| 초대 링크·메일 | 조직 밖 협업이 생길 때 |
| 카드 댓글 | 활동 기록이 먼저다. 댓글은 그 다음에 판단 |
| 실시간 커서·공동 편집 | 칸반 규모에서 비용 대비 얻는 게 적다 |
| LexoRank | 보드당 카드가 수백 장이 되면 (6.1) |

---

## 15. 미리 짚어둘 함정

- **404 vs 403** — 못 보는 보드는 404, 볼 수는 있지만 못 고치면 403. 섞으면
  보드 존재 여부가 새어 나간다.
- **마지막 OWNER** — 나가기·역할 강등 둘 다 막아야 한다. 주인 없는 보드는
  아무도 되살릴 수 없다.
- **멤버를 뺐을 때 그 사람 담당 카드** — 자동으로 비우지 말고 그대로 두되,
  내보내기 대화상자에 "담당 중인 카드 N장이 담당 없음이 됩니다" 를 먼저 보여준다.
- **`claimOrphans`** — owner 를 채울 때 `project_member` 도 같이 넣어야 한다.
  안 넣으면 회수는 됐는데 보드가 목록에 안 보인다.
- **SSE 와 edge 타임아웃** — `proxy_read_timeout 120s` 에 걸려 2분마다 끊긴다.
  heartbeat 를 보내거나 타임아웃을 늘린다.
- **N+1** — 보드 조회에서 카드마다 담당자를 따로 읽지 않도록 `join fetch` 를 건다.
  `ProjectView` 의 카드 수 집계도 지금 프로젝트마다 전건 조회라, 멤버가 늘면
  먼저 아파진다. 집계 쿼리로 바꾼다.
