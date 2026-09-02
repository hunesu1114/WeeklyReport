# 주간보고 양식 확장 설계 메모

MVP 에서는 기존에 쓰던 표준 양식(`DEFAULT_V1`) 하나만 구현했다.
이 문서는 **앞으로 양식이 여러 개가 될 때를 위해 지금 무엇을 미리 열어뒀는지**,
그리고 **실제로 늘릴 때 어디를 손대야 하는지**를 정리한 것이다. (구현은 하지 않았다.)

---

## 1. 지금 구조

양식 선택은 "DB 카탈로그 + 렌더러 SPI" 두 조각으로 되어 있다.

```
report.template_key ──┐
                      ├─→ report_template (카탈로그: 이름/설명/파일명 패턴/활성 여부)
                      │        └─ renderer_bean ─→ ReportRenderer 구현 빈
                      └─→ GET /api/reports/{id}/export?templateKey=...
```

| 조각 | 파일 | 역할 |
| --- | --- | --- |
| SPI | `excel/ReportRenderer.java` | `templateKey()` + `render(Report) → byte[]` 딱 두 개 |
| 배선 | `excel/RendererRegistry.java` | `report_template.renderer_bean` 이름으로 스프링 빈을 찾아 연결 |
| 카탈로그 | `report_template` 테이블 | 화면 셀렉트 박스와 파일명 패턴의 원천 |
| 구현체 | `excel/DefaultV1ReportRenderer.java` | 표준 양식 한 종 |
| 재사용 부품 | `excel/ExcelStyleKit.java`, `excel/RowHeightEstimator.java` | 색/글꼴/테두리, 행 높이 추정 |

핵심은 **DB 행 하나가 곧 배선**이라는 점이다. 양식을 늘려도
컨트롤러 · 서비스 · 프런트엔드는 건드리지 않는다.

---

## 2. 새 양식 추가 절차 (현재 구조에서 가능한 범위)

1. `ReportRenderer` 구현 빈을 하나 만든다.

   ```java
   @Component("compactV1ReportRenderer")
   public class CompactV1ReportRenderer implements ReportRenderer {
       public String templateKey() { return "COMPACT_V1"; }
       public byte[] render(Report report) { /* POI 로 시트 구성 */ }
   }
   ```

   `ExcelStyleKit` 과 `RowHeightEstimator` 를 그대로 쓰면 실제 코드는 150줄 안팎이다.

2. Flyway 마이그레이션에 카탈로그 행을 추가한다.

   ```sql
   insert into report_template
       (template_key, name, description, renderer_bean, filename_pattern, active, sort_order)
   values ('COMPACT_V1', '간이 주간보고', '한 장짜리 요약 양식',
           'compactV1ReportRenderer', '주간보고_간이({author})_{date:yyyyMMdd}.xlsx', true, 1);
   ```

3. 끝. 화면의 "다운로드 양식" 셀렉트에 자동으로 나타나고,
   `GET /api/meta/templates` 도 자동으로 새 항목을 내려준다.

파일명 패턴은 `{author}`, `{title}`, `{date:패턴}` 치환자를 지원한다
(`ReportExportService.resolveFilename`).

---

## 3. 지금 이미 양식 중립적인 것

- **도메인 모델**: `Report` + `ReportItem(section, sortOrder, taskName, detail, status, hours)`.
  "이번 주 / 다음 주 × 업무 목록" 이라는 축은 어떤 양식이든 공통이라고 보고 이 형태로 고정했다.
- **주차 계산**: `WeekCalculator` 가 보고일 → 구간을 계산하고, 화면에서 언제든 수동 조정할 수 있다.
- **합계 규칙**: `base_hours` 를 컬럼으로 빼서 "제외시간 = 기준 − 합계" 를 양식이 아니라 데이터로 갖고 있다.
- **파일명**: 렌더러가 아니라 카탈로그(`filename_pattern`)가 결정한다.
- **API**: `export?templateKey=` 로 저장된 양식과 다른 양식으로도 즉시 뽑을 수 있다.
  같은 데이터를 여러 양식으로 내보내는 시나리오가 이미 열려 있다.

---

## 4. 아직 열려 있지 않은 것 (양식이 늘어날 때 결정할 일)

### 4.1 양식마다 필드가 다를 때

지금 `Report` 는 표준 양식에 맞춘 고정 스키마다. 예를 들어 어떤 양식이
"이슈/리스크" 구획이나 "협업 부서" 컬럼을 요구하면 선택지는 두 가지다.

- **A. `report.extra jsonb` 컬럼 추가** — 양식별 추가 필드를 자유 형식으로 담는다.
  마이그레이션이 가볍고 빠르지만, 검증과 화면 구성이 약해진다.
- **B. 구획을 1급 개념으로 승격** — `ReportSection` enum 을 `report_section` 테이블로 바꾸고
  항목이 임의의 구획에 속하게 한다. 유연하지만 MVP 대비 복잡도가 크게 는다.

> 권장: 양식이 2~3종이 될 때까지는 **A**. 서로 다른 구획 구성이 3종 이상 나오면 그때 **B** 로 간다.

### 4.2 화면(입력 UI)의 양식 대응

지금 프런트엔드는 표준 양식 모양을 그대로 가정한다
(`금주 / 비고 / 차주` 3블록, `ReportPreview.vue` 도 하드코딩).

양식별로 입력 화면이 달라져야 하면 `GET /api/meta/templates` 응답에
**폼 스키마(구획 목록, 컬럼 사용 여부, 라벨)** 를 실어 내리고
화면이 그걸 보고 그리는 방식이 자연스럽다. 현재 `TemplateOption` DTO 에
필드를 더하는 것으로 시작할 수 있다.

### 4.3 사용자가 직접 올리는 양식

"xlsx 를 올리면 그 양식으로 뽑아준다" 를 하려면
placeholder 치환형 렌더러(`TemplateFileReportRenderer`)가 하나 더 필요하다.
다만 이 양식은 **항목 수에 따라 행이 늘어나는 구조**라 단순 치환으로는 부족하고,
행 삽입 + 스타일 복제 + 병합 영역 이동까지 다뤄야 한다.
`renderer_bean` 을 공용 파일 기반 렌더러 하나로 두고
`report_template` 에 업로드된 파일 참조를 추가하는 형태가 될 것이다.

### 4.4 기타

- **양식 버전 관리**: `DEFAULT_V1` → `DEFAULT_V2` 로 올릴 때 과거 보고서는
  옛 양식으로 계속 뽑혀야 한다. `active=false` 로 내려도 이미 저장된
  `template_key` 는 유효해야 하므로, `export` 는 비활성 양식도 허용할지 결정이 필요하다.
  (지금은 비활성 양식이면 거부한다.)
- **미리보기**: 현재 미리보기는 프런트엔드가 HTML 로 흉내 낸 것이다.
  양식이 늘면 렌더링 로직이 두 곳에 생기므로, 서버가 미리보기용 HTML/JSON 을
  내려주는 쪽으로 옮기는 편이 안전하다.
- **권한**: 지금은 인증이 없다. 여러 사람이 쓰면 작성자별 조회 제한이 필요하다.

---

## 5. 표준 양식 재현 근거

`DEFAULT_V1` 은 샘플 파일 3종
(`주간보고(김현수)_20260501/0508/0515.xlsx`)을 분해해서 값을 맞췄다.

| 항목 | 값 | 비고 |
| --- | --- | --- |
| 구획 제목 배경 | `#1F3864` | 원본은 테마 accent1 + tint −50% |
| 표 머리글 배경 | `#BDD7EE` | 원본은 테마 accent5 + tint +60% |
| 입력 불가 칸 | `#808080` | 원본은 text1 + tint +50% |
| 글꼴 | 맑은 고딕 (charset 129) | 통합 문서 기본 글꼴도 함께 교체 |
| 열 너비 (B~F) | 8.08 / 25.58 / 84.75 / 10.58 / 13.08 | 문자 수 기준 |
| 용지 | A4 세로, 확대 85% | |
| 근무시간 | `SUM(F{첫 항목}:F{마지막 항목})` | 병합된 꼬리 행은 범위에서 뺀다 |
| 제외시간 | `{기준시간}-F{근무시간 행}` | |

행 높이는 POI 가 계산해주지 않아 `RowHeightEstimator` 가 글자 폭으로 추정한다
(한글 2칸, 맑은 고딕 11pt 한 줄 = 17pt). 추정 높이가 Excel 상한인 409.5pt 를
넘으면 원본 샘플처럼 여러 행으로 나눠 세로 병합한다.
