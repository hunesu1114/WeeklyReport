package com.khs.weeklyreport.excel;

import com.khs.weeklyreport.domain.Report;
import com.khs.weeklyreport.domain.ReportItem;
import com.khs.weeklyreport.domain.ReportSection;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 기존에 쓰던 표준 주간보고 양식(DEFAULT_V1).
 *
 * <pre>
 *   B2:C2   주간보고 - 홍길동
 *   (빈 줄)
 *   B4:F4   금주 진행 내용 ( 2026. 05. 08  ~ 2026. 05. 15 )
 *   B5:F5   NO | 업무명 | 업무상세 | 진행상태 | 소요시간(H)
 *   ...     항목들
 *   E:F     근무시간 = SUM(...)
 *   E:F     제외시간 = 기준시간 - 근무시간
 *   (빈 줄)
 *   B:F     비고 | (내용)
 *   (빈 줄)
 *   B:F     차주 진행 예정 ( 2026. 05. 18  ~ 2026. 05. 22 )
 *   B:F     NO | 업무명 |   | 진행상태 | 소요시간(H)
 *   ...     항목들 (소요시간 칸은 회색으로 막아둔다)
 * </pre>
 */
@Component("defaultV1ReportRenderer")
public class DefaultV1ReportRenderer implements ReportRenderer {

    public static final String TEMPLATE_KEY = "DEFAULT_V1";

    private static final DateTimeFormatter RANGE_FORMAT = DateTimeFormatter.ofPattern("yyyy. MM. dd");

    /** B ~ F 열 (0-based). A 열은 여백으로 비워둔다. */
    private static final int COL_NO = 1;
    private static final int COL_TASK = 2;
    private static final int COL_DETAIL = 3;
    private static final int COL_STATUS = 4;
    private static final int COL_HOURS = 5;

    /** 샘플 파일에서 그대로 가져온 열 너비(문자 수). */
    private static final double[] COLUMN_WIDTHS = {8.08203125, 25.58203125, 84.75, 10.58203125, 13.08203125};

    private static final double TITLE_ROW_HEIGHT = 24.0;
    /** 제목 아래 여백 줄만 높이를 지정한다. 나머지 여백 줄은 기본 높이(17pt). */
    private static final double TITLE_SPACER_ROW_HEIGHT = 24.0;
    private static final double SECTION_ROW_HEIGHT = 17.5;
    private static final double TABLE_HEADER_ROW_HEIGHT = 17.15;
    private static final double WORK_HOURS_ROW_HEIGHT = 42.75;
    private static final double NOTE_ROW_MIN_HEIGHT = 59.25;
    private static final double ITEM_ROW_MIN_HEIGHT = 30.0;

    @Override
    public String templateKey() {
        return TEMPLATE_KEY;
    }

    @Override
    public byte[] render(Report report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet("Sheet1");
            ExcelStyleKit styles = new ExcelStyleKit(workbook);
            prepareSheet(sheet);

            int rowIndex = 1; // Excel 2행부터 시작

            rowIndex = writeTitle(sheet, styles, report, rowIndex);
            rowIndex = writeSpacer(sheet, rowIndex, TITLE_SPACER_ROW_HEIGHT);

            rowIndex = writeSectionHeader(sheet, styles, rowIndex,
                    "금주 진행 내용 " + range(report.getThisWeekStart(), report.getThisWeekEnd()));
            rowIndex = writeTableHeader(sheet, styles, rowIndex, "업무상세");

            int firstItemRow = rowIndex;
            ItemsWritten thisWeek = writeItems(sheet, styles,
                    report.itemsOf(ReportSection.THIS_WEEK), rowIndex, true);
            rowIndex = thisWeek.nextRow();

            rowIndex = writeSummary(sheet, styles, report, rowIndex, firstItemRow, thisWeek.lastItemTopRow());
            rowIndex = writeSpacer(sheet, rowIndex, null);
            rowIndex = writeNote(sheet, styles, report, rowIndex);
            rowIndex = writeSpacer(sheet, rowIndex, null);

            rowIndex = writeSectionHeader(sheet, styles, rowIndex,
                    "차주 진행 예정 " + range(report.getNextWeekStart(), report.getNextWeekEnd()));
            rowIndex = writeTableHeader(sheet, styles, rowIndex, " ");
            writeItems(sheet, styles, report.itemsOf(ReportSection.NEXT_WEEK), rowIndex, false);

            sheet.setForceFormulaRecalculation(true);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("주간보고 엑셀 생성에 실패했습니다.", e);
        }
    }

    private void prepareSheet(XSSFSheet sheet) {
        sheet.setDefaultRowHeightInPoints(17f);
        for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
            sheet.setColumnWidth(COL_NO + i, (int) Math.round(COLUMN_WIDTHS[i] * 256));
        }
        sheet.setZoom(85);
        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        printSetup.setLandscape(false);
    }

    private int writeTitle(XSSFSheet sheet, ExcelStyleKit styles, Report report, int rowIndex) {
        Row row = newRow(sheet, rowIndex, TITLE_ROW_HEIGHT);
        cell(row, COL_NO, styles.title).setCellValue(report.resolvedTitle());
        cell(row, COL_TASK, styles.title);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, COL_NO, COL_TASK));
        return rowIndex + 1;
    }

    private int writeSpacer(XSSFSheet sheet, int rowIndex, Double heightPoints) {
        newRow(sheet, rowIndex, heightPoints);
        return rowIndex + 1;
    }

    private int writeSectionHeader(XSSFSheet sheet, ExcelStyleKit styles, int rowIndex, String text) {
        Row row = newRow(sheet, rowIndex, SECTION_ROW_HEIGHT);
        cell(row, COL_NO, styles.sectionHeader).setCellValue(text);
        for (int col = COL_TASK; col <= COL_HOURS; col++) {
            cell(row, col, styles.sectionHeader);
        }
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, COL_NO, COL_HOURS));
        return rowIndex + 1;
    }

    private int writeTableHeader(XSSFSheet sheet, ExcelStyleKit styles, int rowIndex, String detailHeader) {
        Row row = newRow(sheet, rowIndex, TABLE_HEADER_ROW_HEIGHT);
        cell(row, COL_NO, styles.tableHeader).setCellValue("NO");
        cell(row, COL_TASK, styles.tableHeader).setCellValue("업무명");
        cell(row, COL_DETAIL, styles.tableHeader).setCellValue(detailHeader);
        cell(row, COL_STATUS, styles.tableHeader).setCellValue("진행상태");
        cell(row, COL_HOURS, styles.tableHeader).setCellValue("소요시간(H)");
        return rowIndex + 1;
    }

    /**
     * @param nextRow        다음에 쓸 행 인덱스
     * @param lastItemTopRow 마지막 항목이 시작한 행. 병합된 꼬리 행은 비어 있으므로
     *                       합계 수식 범위는 여기까지만 잡는다.
     */
    private record ItemsWritten(int nextRow, int lastItemTopRow) {
    }

    /**
     * 항목을 그린다. 추정 높이가 Excel 상한(409.5pt)을 넘으면 원본 샘플처럼
     * 여러 행으로 나눠 세로 병합한다.
     */
    private ItemsWritten writeItems(XSSFSheet sheet, ExcelStyleKit styles, List<ReportItem> items,
                                    int startRow, boolean withHours) {
        int rowIndex = startRow;
        int lastItemTopRow = startRow - 1;
        int no = 1;
        for (ReportItem item : items) {
            double height = estimateHeight(item);
            int span = RowHeightEstimator.rowSpanFor(height);
            double perRow = height / span;

            for (int i = 0; i < span; i++) {
                Row row = newRow(sheet, rowIndex + i, perRow);
                cell(row, COL_NO, styles.no);
                cell(row, COL_TASK, styles.taskName);
                cell(row, COL_DETAIL, styles.detail);
                cell(row, COL_STATUS, styles.status);
                cell(row, COL_HOURS, withHours ? styles.hours : styles.blocked);
            }

            Row top = sheet.getRow(rowIndex);
            top.getCell(COL_NO).setCellValue(no);
            top.getCell(COL_TASK).setCellValue(nullToEmpty(item.getTaskName()));
            top.getCell(COL_DETAIL).setCellValue(nullToEmpty(item.getDetail()));
            top.getCell(COL_STATUS).setCellValue(nullToEmpty(item.getStatus()));
            if (withHours && item.getHours() != null) {
                top.getCell(COL_HOURS).setCellValue(item.getHours().doubleValue());
            }

            if (span > 1) {
                for (int col = COL_NO; col <= COL_HOURS; col++) {
                    sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex + span - 1, col, col));
                }
            }
            lastItemTopRow = rowIndex;
            rowIndex += span;
            no++;
        }
        return new ItemsWritten(rowIndex, lastItemTopRow);
    }

    private double estimateHeight(ReportItem item) {
        int lines = Math.max(
                RowHeightEstimator.lineCount(item.getDetail(), COLUMN_WIDTHS[COL_DETAIL - COL_NO]),
                RowHeightEstimator.lineCount(item.getTaskName(), COLUMN_WIDTHS[COL_TASK - COL_NO]));
        return Math.max(ITEM_ROW_MIN_HEIGHT, RowHeightEstimator.heightFor(lines));
    }

    private int writeSummary(XSSFSheet sheet, ExcelStyleKit styles, Report report,
                             int rowIndex, int firstItemRow, int lastItemTopRow) {
        Row workRow = newRow(sheet, rowIndex, WORK_HOURS_ROW_HEIGHT);
        cell(workRow, COL_STATUS, styles.summaryLabel).setCellValue("근무시간");
        Cell workValue = cell(workRow, COL_HOURS, styles.summaryValue);
        if (lastItemTopRow >= firstItemRow) {
            workValue.setCellFormula("SUM(F%d:F%d)".formatted(firstItemRow + 1, lastItemTopRow + 1));
        } else {
            workValue.setCellValue(0);
        }

        Row excludeRow = newRow(sheet, rowIndex + 1, null);
        cell(excludeRow, COL_STATUS, styles.summaryLabel).setCellValue("제외시간");
        cell(excludeRow, COL_HOURS, styles.summaryValue)
                .setCellFormula("%s-F%d".formatted(plainNumber(report.getBaseHours()), rowIndex + 1));

        return rowIndex + 2;
    }

    private int writeNote(XSSFSheet sheet, ExcelStyleKit styles, Report report, int rowIndex) {
        String note = nullToEmpty(report.getNote());
        int lines = RowHeightEstimator.lineCount(note,
                COLUMN_WIDTHS[COL_TASK - COL_NO] + COLUMN_WIDTHS[COL_DETAIL - COL_NO]
                        + COLUMN_WIDTHS[COL_STATUS - COL_NO] + COLUMN_WIDTHS[COL_HOURS - COL_NO]);
        double height = Math.max(NOTE_ROW_MIN_HEIGHT, RowHeightEstimator.heightFor(lines));

        Row row = newRow(sheet, rowIndex, height);
        cell(row, COL_NO, styles.noteLabel).setCellValue("비고");
        cell(row, COL_TASK, styles.noteBody).setCellValue(note);
        for (int col = COL_DETAIL; col <= COL_HOURS; col++) {
            cell(row, col, styles.noteBody);
        }
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, COL_TASK, COL_HOURS));
        return rowIndex + 1;
    }

    private Row newRow(XSSFSheet sheet, int rowIndex, Double heightPoints) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        if (heightPoints != null) {
            row.setHeightInPoints((float) Math.min(heightPoints, RowHeightEstimator.MAX_ROW_HEIGHT));
        }
        return row;
    }

    private Cell cell(Row row, int column, CellStyle style) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            cell = row.createCell(column);
        }
        cell.setCellStyle(style);
        return cell;
    }

    private static String range(LocalDate start, LocalDate end) {
        return "( %s  ~ %s )".formatted(RANGE_FORMAT.format(start), RANGE_FORMAT.format(end));
    }

    private static String plainNumber(BigDecimal value) {
        BigDecimal target = value == null ? BigDecimal.ZERO : value;
        return target.stripTrailingZeros().toPlainString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
