# 주간보고/칸반

기존에 손으로 만들던 주간보고 엑셀을, 웹에서 편하게 입력하고 **같은 모양의 xlsx 로 내려받는** 서비스입니다.
칸반 보드로 주중에 일을 관리하고, 그 카드를 그대로 주간보고에 옮겨 담을 수 있습니다.

Spring Boot 3.4 (Java 21) · Vue 3 · PostgreSQL 16 · Docker Compose · Apache POI

---

## 빠른 시작

```bash
docker compose up -d --build
```

- 화면: <http://localhost:3000>
- API: <http://localhost:8081/api>
- DB: `localhost:5434` (weekly / weekly)

포트를 바꾸려면 `.env.example` 을 `.env` 로 복사해서 값을 수정하세요.

```bash
cp .env.example .env
```

내리기:

```bash
docker compose down          # 컨테이너만
docker compose down -v       # DB 데이터까지
```

---

## 무엇을 할 수 있나

### 입력 화면

- **보고일만 고르면 기간이 자동으로** — 금주 `[보고일-7, 보고일-1]`, 차주 `[보고일, 보고일+6]`.
  공휴일이 낀 주처럼 예외가 있으면 직접 고쳐도 되고, 고친 기간은 보고일을 바꿔도 유지됩니다.
- **직전 보고서의 "차주 진행 예정" 이 새 보고서의 "금주 진행 내용" 으로 자동 이월** —
  매주 같은 내용을 다시 타이핑할 필요가 없습니다. 목록에서 `다음 주 만들기` 로도 같은 일을 합니다.
- **업무상세 전용 에디터**
  - `[진행]` `[완료]` `[예정]` `[이슈]` `[보류]` 태그를 버튼 한 번으로 현재 줄 앞에 붙입니다.
  - `Enter` 를 누르면 앞 줄의 불릿과 들여쓰기를 그대로 이어씁니다. 빈 불릿에서 한 번 더 누르면 정리됩니다.
  - `Tab` / `Shift+Tab` 으로 선택한 줄들을 통째로 들여쓰기 / 내어쓰기 합니다.
  - **엑셀에서 몇 줄이 될지**(약 N줄 / N pt) 를 입력하면서 바로 보여줍니다.
- **항목 정렬** — 드래그, 위/아래 버튼, 복제, 삭제.
- **업무명 자동완성** — 이전에 쓴 업무명을 그대로 제안합니다.
- **실시간 합계** — 근무시간 합계와 제외시간(기준 − 합계)을 항상 아래에 띄우고, 기준을 넘으면 경고합니다.
- **엑셀 미리보기** — 오른쪽에 다운로드될 모양을 그대로 그려줍니다.
- **임시 저장** — 새 보고서는 브라우저에 자동 보관되어, 닫았다 열어도 이어서 씁니다.
- `Ctrl+S` 저장. `엑셀 다운로드` 는 저장 안 된 내용이 있으면 저장 후 내려받습니다.

### 목록 화면

작성자 · 업무명 · 업무상세 전문 검색, 다음 주 보고서 만들기, 엑셀 다운로드, 삭제.

### 칸반 보드

- **프로젝트마다 보드 하나** — 상단 탭으로 오갑니다. 색을 지정하면 카드와 목록에서 함께 보입니다.
- **BACKLOG · TODO · ING · DONE** 네 칸. 카드를 끌어서 칸을 옮기고 같은 칸 안에서 순서도 바꿉니다.
  놓일 자리는 초록 선으로 미리 보여줍니다.
- **카드 한 장** — 제목, 내용, 중요도(낮음/보통/높음/긴급), 생성일, 완료일.
  중요도는 카드 왼쪽 색 띠로 나타나 목록을 훑을 때 바로 걸러집니다.
- **완료일 임박 알림** — 완료일이 **3일 이내**로 남은 카드를 헤더 종 아이콘에 모읍니다.
  이미 지난 카드도 함께 보여줍니다. 놓친 일이 조용히 사라지면 알림의 의미가 없기 때문입니다.
  완료(DONE)된 카드는 제외됩니다.

### 칸반 → 주간보고 연동

보고서 작성 화면의 **엑셀 미리보기 아래**에 `칸반 연동` 패널이 있습니다.
보고서의 **금주 기간 안에 생성일(시작일)이 들어가는** 카드를 프로젝트별로 모아 보여줍니다.

- `넣기` — 카드 한 장을 금주 진행 항목으로
- `전부 넣기` — 그 프로젝트의 카드를 한 번에

보고서의 *업무명*은 프로젝트 단위라, 같은 이름의 항목이 이미 있으면 새로 만들지 않고
그 항목의 업무상세에 줄만 덧붙입니다. 카드 상태는 `[진행] / [완료] / [예정]` 태그로,
내용은 하위 불릿으로 변환됩니다.

### 계정

- **아이디/비밀번호 로그인** — Spring Security + JWT. 토큰은 브라우저에 보관하고
  만료되면 자동으로 로그인 화면으로 돌아갑니다.
- **가장 먼저 가입한 계정이 관리자**가 됩니다. 그 계정만 주인 없는 데이터를 가져올 수 있습니다.
- **데이터는 계정별로 분리됩니다.** 보고서·프로젝트·카드 모두 본인 것만 보입니다.

### 화면

- **라이트 / 다크 모드** — 헤더 오른쪽 버튼으로 전환합니다. 고르기 전에는 OS 설정을 따릅니다.
- 키컬러는 긍정(저장·완료·진행)에 green, 부정(삭제·지연·초과)에 red 를 씁니다.

---

## 개발 모드

DB 만 컨테이너로 띄우고 나머지는 로컬에서 실행합니다.

```bash
docker compose up -d db

cd backend && mvn spring-boot:run          # http://localhost:8080
cd frontend && npm install && npm run dev  # http://localhost:5174
```

Vite dev server 가 `/api` 를 백엔드(기본 `http://localhost:8080`)로 프록시합니다.
백엔드는 컨테이너로 띄우고 화면만 로컬에서 보려면 `VITE_API_TARGET=http://localhost:8081 npm run dev` 로 대상을 바꾸세요.

테스트:

```bash
cd backend && mvn test
```

`DefaultV1ReportRendererTest` 가 생성된 xlsx 를 다시 읽어 레이아웃 · 색 · 열 너비 ·
수식 · 병합을 원본 샘플 기준으로 검증합니다.

---

## API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/reports?query=&page=&size=` | 목록 · 검색 |
| `GET` | `/api/reports/defaults?reportDate=&authorName=` | 새 보고서 초기값 (기간 + 이월 항목) |
| `GET` | `/api/reports/{id}` | 상세 |
| `POST` | `/api/reports` | 생성 |
| `PUT` | `/api/reports/{id}` | 수정 |
| `DELETE` | `/api/reports/{id}` | 삭제 |
| `POST` | `/api/reports/{id}/follow-up` | 차주 예정을 금주로 옮긴 다음 주 보고서 생성 |
| `GET` | `/api/reports/{id}/export?templateKey=` | **xlsx 다운로드** |
| `GET` | `/api/meta/templates` `\|` `/statuses` `\|` `/task-names` `\|` `/authors` | 화면 보조 데이터 |

인증 (`/api/auth` 외의 모든 `/api/**` 는 `Authorization: Bearer <token>` 이 필요합니다):

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/auth/setup-state` | 가입한 계정이 있는지 (공개) |
| `POST` | `/api/auth/register` | 가입. **첫 계정은 ADMIN** (공개) |
| `POST` | `/api/auth/login` | 로그인 → 토큰 (공개) |
| `GET` | `/api/auth/me` | 내 정보 |
| `POST` | `/api/auth/password` | 비밀번호 변경 |
| `GET` | `/api/auth/orphans` | 주인 없는 데이터 건수 |
| `POST` | `/api/auth/orphans/claim` | **주인 없는 데이터를 내 계정으로 귀속** (ADMIN 전용) |

칸반:

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/kanban/projects?activeOnly=` | 프로젝트 목록 (카드 수 · 임박 수 포함) |
| `POST` `\|` `PUT` `\|` `DELETE` | `/api/kanban/projects[/{id}]` | 프로젝트 생성 · 수정 · 삭제 |
| `GET` | `/api/kanban/projects/{id}/board` | 보드 한 판 (칸별 카드) |
| `POST` `\|` `PUT` `\|` `DELETE` | `/api/kanban/cards[/{id}]` | 카드 생성 · 수정 · 삭제 |
| `PUT` | `/api/kanban/cards/{id}/move` | 칸 이동 · 순서 변경 |
| `GET` | `/api/kanban/cards/due-soon?days=3` | 완료일 임박 카드 |
| `GET` | `/api/kanban/cards/started-between?from=&to=&projectId=` | 기간 안에 시작한 카드 (보고서 연동) |

다운로드 파일명은 `주간보고(김현수)_20260515.xlsx` 형식이며,
`Content-Disposition` 에 RFC 5987 로 인코딩되어 내려갑니다.

---

## 엑셀 재현 방식

샘플 파일 3종을 XML 수준까지 분해해서 서식을 맞췄습니다.

- 색은 원본이 테마 색 + tint 로 되어 있는데, 새로 만든 통합 문서에는 테마 파트가 없으므로
  Excel 이 계산하는 것과 같은 RGB(`#1F3864`, `#BDD7EE`, `#808080`)를 직접 지정합니다.
- 글꼴은 맑은 고딕(charset 129). 통합 문서 기본 글꼴도 함께 바꿔서,
  사용자가 빈 칸에 직접 입력해도 Calibri 로 찍히지 않게 했습니다.
- 열 너비 · 용지(A4 세로) · 확대율(85%) · 기본 행 높이(17pt)를 원본과 동일하게 씁니다.
- POI 는 줄바꿈된 셀의 높이를 계산해주지 않으므로 글자 폭으로 추정합니다(한글 2칸, 한 줄 17pt).
  추정 높이가 Excel 상한(409.5pt)을 넘으면 **원본 샘플이 그랬던 것처럼**
  여러 행으로 나눠 B~F 를 세로 병합합니다.

자세한 대응표는 [docs/TEMPLATES.md](docs/TEMPLATES.md) 5절에 있습니다.

---

## 배포

`master` 에 push 하면 GitHub Actions 가 테스트 → 이미지 빌드 → 서버 배포까지 합니다.
운영은 <https://weekly-report-mik.duckdns.org> 이고, 앞단 edge 프록시가 TLS 를 끝내고
도메인을 보고 이 앱으로 넘깁니다. 앱 컨테이너는 공개 포트를 열지 않습니다 —
예외는 postgres 하나이고, 그것도 서버 루프백 전용(조회용)입니다.

서버 최초 설정(인증서 발급 · DB 비밀번호 배치 · GitHub Secrets)과
운영 DB 조회 절차는 [docs/deployment.md](docs/deployment.md) 를 보세요.

---

## 프로젝트 구조

```
.
├─ docker-compose.yml         # 개발용
├─ docker-compose.prod.yml    # 배포용 (CI 가 서버로 보낸다)
├─ .env.example
├─ .github/workflows/deploy.yml
├─ deploy/
│  ├─ edge/weekly-report.conf # edge 저장소 conf.d/ 에 넣을 사이트 설정
│  └─ env.secrets.example     # 서버 ~/weekly-report/.env.secrets 템플릿
├─ docs/
│  ├─ TEMPLATES.md            # 양식 확장 설계 메모
│  └─ deployment.md           # 배포 · 서버 최초 설정
├─ backend/
│  ├─ Dockerfile
│  └─ src/main/
│     ├─ java/com/khs/weeklyreport/
│     │  ├─ domain/           # Report, ReportItem, ReportTemplate,
│     │  │                    # Project, KanbanCard, KanbanStatus, CardPriority
│     │  ├─ repository/
│     │  ├─ service/          # ReportService, ReportExportService,
│     │  │                    # WeekCalculator, KanbanService
│     │  ├─ excel/            # ReportRenderer(SPI), RendererRegistry,
│     │  │                    # DefaultV1ReportRenderer, ExcelStyleKit, RowHeightEstimator
│     │  └─ web/              # 컨트롤러 + DTO + 예외 처리
│     └─ resources/
│        ├─ application.yml
│        └─ db/migration/     # Flyway V1(스키마) · V2(양식 seed) · V3(칸반)
└─ frontend/
   ├─ Dockerfile, nginx.conf
   └─ src/
      ├─ views/               # ReportListView, ReportEditorView, KanbanView
      ├─ components/          # ReportMetaCard, ItemSection, ItemEditor, DetailEditor,
      │                       # ReportPreview, KanbanLinkPanel, KanbanColumn,
      │                       # KanbanCardItem, CardDialog, ProjectDialog,
      │                       # DueSoonBell, BaseModal
      ├─ composables/         # useToast, useTheme (라이트/다크)
      ├─ api/client.js
      ├─ stores/              # meta, kanban
      └─ utils/               # report, kanban
```

---

## 양식 다양화

**MVP 에서는 표준 양식(`DEFAULT_V1`) 하나만 구현했습니다.** 다만 늘리기 쉽도록
`ReportRenderer` SPI + `report_template` 카탈로그 구조를 미리 깔아뒀습니다.
새 양식은 **구현 빈 하나 + DB 행 하나** 로 붙고, 컨트롤러 · 서비스 · 화면은 손대지 않습니다.

무엇을 미리 열어뒀고 무엇이 아직 결정 대기인지는
[docs/TEMPLATES.md](docs/TEMPLATES.md) 에 정리했습니다.

---

## 현재 제약

- 인증 · 권한이 없습니다. 사내 망 등 신뢰된 환경을 가정합니다.
- 행 높이는 추정값입니다. 원본에서 사용자가 손으로 조정했던 높이까지는 재현하지 않습니다
  (내용에 맞춰 자동 계산됩니다).
- 미리보기는 프런트엔드가 HTML 로 흉내 낸 것이라 실제 엑셀과 픽셀 단위로 같지는 않습니다.
- 샘플 파일의 `Sheet2`(작업용 메모 데이터)는 양식의 일부가 아니라고 보고 생성하지 않습니다.
