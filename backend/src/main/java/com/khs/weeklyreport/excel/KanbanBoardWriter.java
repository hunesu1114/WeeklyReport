package com.khs.weeklyreport.excel;

import com.khs.weeklyreport.domain.CardPriority;
import com.khs.weeklyreport.domain.KanbanCard;
import com.khs.weeklyreport.domain.KanbanStatus;
import com.khs.weeklyreport.domain.Project;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 칸반 보드 한 판을 엑셀 한 장으로 옮긴다.
 *
 * <p>주간보고 양식과 달리 정해진 서식이 없다. 목적은 보드를 통째로 들고 나가
 * 엑셀에서 거르고 정렬하는 것이므로, 한 카드가 한 행인 단순한 표로 만들고
 * 머리글에 필터를 걸어둔다.
 */
public class KanbanBoardWriter {

    private static final String[] HEADERS = {
            "No", "상태", "제목", "중요도", "담당자", "시작일", "완료일", "남은 기간", "내용", "최근 수정"
    };

    /** 열 너비(문자 수 * 256). 제목과 내용만 넓게 준다. */
    private static final int[] WIDTHS = {
            6, 10, 40, 8, 14, 12, 12, 12, 60, 18
    };

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public byte[] write(Project project, List<KanbanCard> cards, LocalDate today) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Styles styles = new Styles(workbook);
            XSSFSheet sheet = workbook.createSheet("칸반");

            int rowIndex = 0;
            rowIndex = writeTitle(sheet, styles, project, cards, today, rowIndex);
            int headerRow = rowIndex;
            rowIndex = writeHeader(sheet, styles, rowIndex);
            writeCards(sheet, styles, cards, today, rowIndex);

            for (int i = 0; i < WIDTHS.length; i++) {
                sheet.setColumnWidth(i, WIDTHS[i] * 256);
            }
            // 머리글을 고정해 두어야 아래로 내려도 어느 열인지 알 수 있다
            sheet.createFreezePane(0, headerRow + 1);
            if (!cards.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(
                        headerRow, headerRow + cards.size(), 0, HEADERS.length - 1));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("칸반 엑셀을 만들지 못했습니다.", e);
        }
    }

    private int writeTitle(XSSFSheet sheet, Styles styles, Project project, List<KanbanCard> cards,
                           LocalDate today, int rowIndex) {
        Row title = sheet.createRow(rowIndex);
        title.setHeightInPoints(26);
        title.createCell(0).setCellValue(project.getName() + " 칸반 보드");
        title.getCell(0).setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, HEADERS.length - 1));
        rowIndex++;

        long done = cards.stream().filter(c -> c.getStatus() == KanbanStatus.DONE).count();
        String summary = "받은 날짜 %s · 전체 %d장 · 진행할 일 %d장 · 완료 %d장"
                .formatted(DATE.format(today), cards.size(), cards.size() - done, done);

        Row meta = sheet.createRow(rowIndex);
        meta.createCell(0).setCellValue(summary);
        meta.getCell(0).setCellStyle(styles.meta);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, HEADERS.length - 1));
        rowIndex++;

        // 제목과 표 사이 한 줄
        sheet.createRow(rowIndex);
        return rowIndex + 1;
    }

    private int writeHeader(XSSFSheet sheet, Styles styles, int rowIndex) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(20);
        for (int i = 0; i < HEADERS.length; i++) {
            row.createCell(i).setCellValue(HEADERS[i]);
            row.getCell(i).setCellStyle(styles.header);
        }
        return rowIndex + 1;
    }

    private void writeCards(XSSFSheet sheet, Styles styles, List<KanbanCard> cards,
                            LocalDate today, int startRow) {
        int rowIndex = startRow;
        int no = 1;

        for (KanbanCard card : cards) {
            Row row = sheet.createRow(rowIndex++);
            boolean done = card.getStatus() == KanbanStatus.DONE;

            put(row, 0, String.valueOf(no++), styles.center);
            put(row, 1, card.getStatus().name(), styles.center);
            put(row, 2, card.getTitle(), styles.text);
            put(row, 3, label(card.getPriority()), styles.center);
            put(row, 4, card.getAssignee() == null ? "-" : card.getAssignee().getDisplayName(),
                    styles.center);
            put(row, 5, card.getStartDate() == null ? "" : DATE.format(card.getStartDate()),
                    styles.center);
            put(row, 6, card.getDueDate() == null ? "" : DATE.format(card.getDueDate()),
                    styles.center);

            // 완료한 카드에 "3일 지남"이 남아 있으면 읽는 사람이 오해한다
            Long days = done ? null : card.daysUntilDue(today);
            put(row, 7, remaining(days), days != null && days <= 0 ? styles.overdue : styles.center);

            put(row, 8, card.getContent() == null ? "" : card.getContent(), styles.text);
            put(row, 9, card.getUpdatedAt() == null ? ""
                    : STAMP.format(card.getUpdatedAt().atZone(ZoneId.systemDefault())), styles.center);
        }
    }

    private static void put(Row row, int column, String value, CellStyle style) {
        row.createCell(column).setCellValue(value);
        row.getCell(column).setCellStyle(style);
    }

    private static String remaining(Long days) {
        if (days == null) return "";
        if (days < 0) return Math.abs(days) + "일 지남";
        if (days == 0) return "오늘까지";
        return days + "일 남음";
    }

    private static String label(CardPriority priority) {
        if (priority == null) return "보통";
        return switch (priority) {
            case LOW -> "낮음";
            case NORMAL -> "보통";
            case HIGH -> "높음";
            case URGENT -> "긴급";
        };
    }

    /** 이 표에만 쓰는 스타일. 주간보고 양식과 서식을 맞출 이유가 없어 따로 둔다. */
    private static final class Styles {
        final CellStyle title;
        final CellStyle meta;
        final CellStyle header;
        final CellStyle center;
        final CellStyle text;
        final CellStyle overdue;

        Styles(XSSFWorkbook workbook) {
            XSSFFont normal = workbook.getFontAt(0);
            normal.setFontName(ExcelStyleKit.FONT_NAME);
            normal.setFontHeightInPoints((short) 11);
            normal.setCharSet(129);

            this.title = plain(workbook, font(workbook, 14, true, null),
                    null, HorizontalAlignment.LEFT, false);
            this.meta = plain(workbook, font(workbook, 10, false, rgb(0x60, 0x60, 0x60)),
                    null, HorizontalAlignment.LEFT, false);
            this.header = bordered(workbook, font(workbook, 11, true, null),
                    ExcelStyleKit.TABLE_HEADER_BG, HorizontalAlignment.CENTER, false);
            this.center = bordered(workbook, font(workbook, 11, false, null),
                    null, HorizontalAlignment.CENTER, false);
            this.text = bordered(workbook, font(workbook, 11, false, null),
                    null, HorizontalAlignment.LEFT, true);
            this.overdue = bordered(workbook, font(workbook, 11, true, rgb(0xC0, 0x00, 0x00)),
                    null, HorizontalAlignment.CENTER, false);
        }

        private static XSSFFont font(XSSFWorkbook workbook, int points, boolean bold, byte[] color) {
            XSSFFont f = workbook.createFont();
            f.setFontName(ExcelStyleKit.FONT_NAME);
            f.setFontHeightInPoints((short) points);
            f.setBold(bold);
            f.setCharSet(129);
            if (color != null) {
                f.setColor(new XSSFColor(color, null));
            }
            return f;
        }

        private static CellStyle plain(XSSFWorkbook workbook, XSSFFont font, byte[] background,
                                       HorizontalAlignment horizontal, boolean wrap) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setAlignment(horizontal);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(wrap);
            if (background != null) {
                style.setFillForegroundColor(new XSSFColor(background, null));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return style;
        }

        private static CellStyle bordered(XSSFWorkbook workbook, XSSFFont font, byte[] background,
                                          HorizontalAlignment horizontal, boolean wrap) {
            XSSFCellStyle style = (XSSFCellStyle) plain(workbook, font, background, horizontal, wrap);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }

        private static byte[] rgb(int r, int g, int b) {
            return new byte[]{(byte) r, (byte) g, (byte) b};
        }
    }
}
