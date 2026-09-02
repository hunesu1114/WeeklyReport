-- MVP 기본 양식. 새 양식을 추가할 때는
--   1) ReportRenderer 구현 빈을 하나 만들고
--   2) 여기에 행을 추가한다 (renderer_bean = 빈 이름)
-- 나머지 API/화면은 수정할 필요가 없다.
insert into report_template (template_key, name, description, renderer_bean, filename_pattern, active, sort_order)
values ('DEFAULT_V1',
        '기본 주간보고 (표준)',
        '금주 진행 내용 / 근무시간·제외시간 / 비고 / 차주 진행 예정으로 구성된 표준 양식',
        'defaultV1ReportRenderer',
        '주간보고({author})_{date:yyyyMMdd}.xlsx',
        true,
        0);
