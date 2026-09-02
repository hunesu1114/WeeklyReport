package com.khs.weeklyreport.excel;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 샘플 주간보고 파일에서 추출한 서식을 그대로 재현하는 스타일 모음.
 *
 * <p>원본은 테마 색 + tint 로 지정되어 있으나(accent1 -50%, accent5 +60%, text1 +50%),
 * 새로 만든 통합 문서에는 테마 파트가 없으므로 Excel 이 계산하는 것과 동일한
 * RGB 값을 직접 지정한다.
 */
public class ExcelStyleKit {

    /** 원본 accent1(4472C4) tint -50% : 구획 제목 배경 */
    public static final byte[] SECTION_HEADER_BG = rgb(0x1F, 0x38, 0x64);
    /** 원본 accent5(5B9BD5) tint +60% : 표 머리글 배경 */
    public static final byte[] TABLE_HEADER_BG = rgb(0xBD, 0xD7, 0xEE);
    /** 원본 text1(000000) tint +50% : 입력하지 않는 칸 */
    public static final byte[] BLOCKED_BG = rgb(0x80, 0x80, 0x80);

    public static final String FONT_NAME = "맑은 고딕";

    private final XSSFWorkbook workbook;
    private final short textFormat;

    public final CellStyle title;
    public final CellStyle sectionHeader;
    public final CellStyle tableHeader;
    public final CellStyle no;
    public final CellStyle taskName;
    public final CellStyle detail;
    public final CellStyle status;
    public final CellStyle hours;
    public final CellStyle summaryLabel;
    public final CellStyle summaryValue;
    public final CellStyle noteLabel;
    public final CellStyle noteBody;
    public final CellStyle blocked;

    public ExcelStyleKit(XSSFWorkbook workbook) {
        this.workbook = workbook;
        this.textFormat = workbook.createDataFormat().getFormat("@");
        applyDefaultFont();

        this.title = build(font(14, false, null), null, HorizontalAlignment.CENTER, false, false);
        this.sectionHeader = build(font(12, true, rgb(0xFF, 0xFF, 0xFF)), SECTION_HEADER_BG,
                HorizontalAlignment.CENTER, false, false);
        this.tableHeader = build(font(11, true, null), TABLE_HEADER_BG,
                HorizontalAlignment.CENTER, false, false);
        this.no = build(font(11, false, null), null, HorizontalAlignment.CENTER, false, false);
        this.taskName = build(font(11, false, null), null, HorizontalAlignment.LEFT, true, false);
        this.detail = build(font(11, true, null), null, HorizontalAlignment.LEFT, true, true);
        this.status = build(font(11, false, null), null, HorizontalAlignment.CENTER, false, false);
        this.hours = build(font(11, false, null), null, HorizontalAlignment.CENTER, false, false);
        this.summaryLabel = build(font(11, true, null), TABLE_HEADER_BG,
                HorizontalAlignment.CENTER, false, false);
        this.summaryValue = build(font(11, false, null), null, HorizontalAlignment.CENTER, false, false);
        this.noteLabel = build(font(11, true, null), TABLE_HEADER_BG,
                HorizontalAlignment.CENTER, false, false);
        this.noteBody = build(font(11, false, null), null, HorizontalAlignment.LEFT, true, true);
        this.blocked = build(font(11, false, null), BLOCKED_BG, HorizontalAlignment.CENTER, false, false);
    }

    /**
     * 통합 문서의 Normal 스타일 글꼴. 이걸 바꿔두지 않으면 사용자가 빈 칸에
     * 직접 입력할 때 Calibri 로 찍힌다.
     */
    private void applyDefaultFont() {
        XSSFFont normal = workbook.getFontAt(0);
        normal.setFontName(FONT_NAME);
        normal.setFontHeightInPoints((short) 11);
        normal.setCharSet(129);
    }

    private XSSFFont font(int points, boolean bold, byte[] color) {
        XSSFFont f = workbook.createFont();
        f.setFontName(FONT_NAME);
        f.setFontHeightInPoints((short) points);
        f.setBold(bold);
        f.setCharSet(129); // Hangul
        if (color != null) {
            f.setColor(new XSSFColor(color, null));
        }
        return f;
    }

    private CellStyle build(XSSFFont font, byte[] background, HorizontalAlignment horizontal,
                            boolean wrap, boolean asText) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(horizontal);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(wrap);
        if (asText) {
            style.setDataFormat(textFormat);
        }
        if (background != null) {
            style.setFillForegroundColor(new XSSFColor(background, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
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
