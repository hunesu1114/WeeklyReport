package com.khs.weeklyreport.excel;

import com.khs.weeklyreport.domain.Report;
import com.khs.weeklyreport.domain.ReportItem;
import com.khs.weeklyreport.domain.ReportSection;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultV1ReportRendererTest {

    private final DefaultV1ReportRenderer renderer = new DefaultV1ReportRenderer();

    @Test
    void 샘플과_동일한_레이아웃으로_그린다() throws Exception {
        Report report = sampleReport();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(renderer.render(report)))) {
            XSSFSheet sheet = wb.getSheetAt(0);

            assertThat(wb.getSheetName(0)).isEqualTo("Sheet1");
            assertThat(text(sheet, 1, 1)).isEqualTo("주간보고 - 김현수");
            assertThat(text(sheet, 3, 1)).isEqualTo("금주 진행 내용 ( 2026. 05. 08  ~ 2026. 05. 15 )");
            assertThat(List.of(text(sheet, 4, 1), text(sheet, 4, 2), text(sheet, 4, 3),
                    text(sheet, 4, 4), text(sheet, 4, 5)))
                    .containsExactly("NO", "업무명", "업무상세", "진행상태", "소요시간(H)");

            // 금주 항목 3건 (5, 6, 7행 인덱스)
            assertThat(sheet.getRow(5).getCell(1).getNumericCellValue()).isEqualTo(1d);
            assertThat(text(sheet, 5, 2)).isEqualTo("포탈 테스트 및 개선 건의");
            assertThat(text(sheet, 5, 4)).isEqualTo("진행");
            assertThat(sheet.getRow(5).getCell(5).getNumericCellValue()).isEqualTo(2d);
            assertThat(text(sheet, 7, 2)).isEqualTo("기타");

            // 합계 / 제외시간
            assertThat(text(sheet, 8, 4)).isEqualTo("근무시간");
            assertThat(sheet.getRow(8).getCell(5).getCellFormula()).isEqualTo("SUM(F6:F8)");
            assertThat(text(sheet, 9, 4)).isEqualTo("제외시간");
            assertThat(sheet.getRow(9).getCell(5).getCellFormula()).isEqualTo("40-F9");

            // 비고 -> 빈 줄 -> 차주 구획
            assertThat(text(sheet, 11, 1)).isEqualTo("비고");
            assertThat(text(sheet, 11, 2)).isEqualTo("5/1, 5/5 공휴일로 인해 24H 근무");
            assertThat(text(sheet, 13, 1)).isEqualTo("차주 진행 예정 ( 2026. 05. 18  ~ 2026. 05. 22 )");
            assertThat(text(sheet, 14, 3)).isEqualTo("업무상세");
            assertThat(text(sheet, 15, 2)).isEqualTo("포탈 v3, v2 테스트");
            assertThat(text(sheet, 15, 4)).isEqualTo("예정");
        }
    }

    @Test
    void 차주_항목의_소요시간_칸은_회색으로_막는다() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(renderer.render(sampleReport())))) {
            XSSFCell blocked = wb.getSheetAt(0).getRow(15).getCell(5);
            assertThat(blocked.getCellType()).isEqualTo(CellType.BLANK);
            assertThat(blocked.getCellStyle().getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(hex(blocked)).isEqualTo("808080");
        }
    }

    @Test
    void 구획_제목과_표머리글은_원본_테마색을_그대로_쓴다() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(renderer.render(sampleReport())))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            assertThat(hex(sheet.getRow(3).getCell(1))).isEqualTo("1F3864");
            assertThat(hex(sheet.getRow(4).getCell(1))).isEqualTo("BDD7EE");
            assertThat(sheet.getRow(3).getCell(1).getCellStyle().getFont().getFontName())
                    .isEqualTo("맑은 고딕");
        }
    }

    @Test
    void 열_너비와_인쇄설정이_원본과_같다() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(renderer.render(sampleReport())))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getColumnWidth(1)).isEqualTo(2069);
            assertThat(sheet.getColumnWidth(2)).isEqualTo(6549);
            assertThat(sheet.getColumnWidth(3)).isEqualTo(21696);
            assertThat(sheet.getColumnWidth(4)).isEqualTo(2709);
            assertThat(sheet.getColumnWidth(5)).isEqualTo(3349);
            assertThat(sheet.getPrintSetup().getPaperSize()).isEqualTo((short) 9);
            assertThat(sheet.getPrintSetup().getLandscape()).isFalse();
        }
    }

    @Test
    void 아주_긴_업무상세는_여러_행으로_나눠_병합한다() throws Exception {
        Report report = sampleReport();
        StringBuilder longDetail = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            longDetail.append("- 아주 긴 업무 상세 라인 ").append(i).append("\n");
        }
        report.itemsOf(ReportSection.THIS_WEEK).get(0).setDetail(longDetail.toString());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(renderer.render(report)))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            List<CellRangeAddress> vertical = sheet.getMergedRegions().stream()
                    .filter(r -> r.getFirstRow() == 5 && r.getLastRow() > 5)
                    .toList();
            assertThat(vertical).hasSize(5); // B~F 각각 세로 병합
            assertThat(sheet.getRow(5).getHeightInPoints())
                    .isLessThanOrEqualTo((float) RowHeightEstimator.MAX_ROW_HEIGHT);
        }
    }

    @Test
    void 금주_항목이_없으면_합계는_0_으로_쓴다() throws Exception {
        Report report = sampleReport();
        report.getItems().removeIf(it -> it.getSection() == ReportSection.THIS_WEEK);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(renderer.render(report)))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(5).getCell(5).getNumericCellValue()).isEqualTo(0d);
        }
    }

    private static String text(XSSFSheet sheet, int row, int col) {
        return sheet.getRow(row).getCell(col).getStringCellValue();
    }

    private static String hex(XSSFCell cell) {
        return HexFormat.of().formatHex(cell.getCellStyle().getFillForegroundColorColor().getRGB())
                .toUpperCase();
    }

    private Report sampleReport() {
        Report report = new Report();
        report.setReportDate(LocalDate.of(2026, 5, 15));
        report.setAuthorName("김현수");
        report.setThisWeekStart(LocalDate.of(2026, 5, 8));
        report.setThisWeekEnd(LocalDate.of(2026, 5, 15));
        report.setNextWeekStart(LocalDate.of(2026, 5, 18));
        report.setNextWeekEnd(LocalDate.of(2026, 5, 22));
        report.setBaseHours(new BigDecimal("40"));
        report.setNote("5/1, 5/5 공휴일로 인해 24H 근무");
        report.setTemplateKey(DefaultV1ReportRenderer.TEMPLATE_KEY);

        List<ReportItem> items = new ArrayList<>();
        items.add(item(ReportSection.THIS_WEEK, 0, "포탈 테스트 및 개선 건의",
                "\n[진행] 포탈 디자인 변경 후 기능 및 디자인 테스트/검수 진행\n- Total : 101건 / Closed : 80건\n",
                "진행", new BigDecimal("2")));
        items.add(item(ReportSection.THIS_WEEK, 1, "포탈 v3",
                "\n[진행] 근태관리 구독센터 적용\n- 최종 완료 결과 보고\n", "진행", new BigDecimal("6")));
        items.add(item(ReportSection.THIS_WEEK, 2, "기타", "", "", null));
        items.add(item(ReportSection.NEXT_WEEK, 0, "포탈 v3, v2 테스트",
                "42dot 마이그레이션", "예정", null));
        report.replaceItems(items);
        return report;
    }

    private ReportItem item(ReportSection section, int order, String name, String detail,
                            String status, BigDecimal hours) {
        ReportItem item = new ReportItem();
        item.setSection(section);
        item.setSortOrder(order);
        item.setTaskName(name);
        item.setDetail(detail);
        item.setStatus(status);
        item.setHours(hours);
        return item;
    }
}
