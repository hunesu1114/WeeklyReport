package com.khs.weeklyreport.excel;

/**
 * Excel 의 행 높이 자동 맞춤 결과를 근사한다.
 *
 * <p>POI 는 줄바꿈된 셀의 높이를 계산해주지 않는다. 높이를 지정하지 않으면
 * 뷰어에 따라 기본 높이로 눌려 보이므로, 원본 샘플과 같은 모양이 나오도록
 * 글자 폭 기준으로 직접 추정해서 넣는다.
 */
public final class RowHeightEstimator {

    /** 맑은 고딕 11pt 한 줄 높이(pt). 샘플 파일의 자동 맞춤 값(5줄 = 85pt)에서 역산. */
    public static final double LINE_HEIGHT = 17.0;
    /** Excel 이 허용하는 행 높이 상한. */
    public static final double MAX_ROW_HEIGHT = 409.5;

    private RowHeightEstimator() {
    }

    /** 열 너비(문자 수) 안에서 텍스트가 차지하는 줄 수. */
    public static int lineCount(String text, double columnWidthChars) {
        if (text == null || text.isEmpty()) {
            return 1;
        }
        double usable = Math.max(1.0, columnWidthChars - 1.0);
        int total = 0;
        for (String line : text.split("\n", -1)) {
            double width = displayWidth(line);
            total += Math.max(1, (int) Math.ceil(width / usable));
        }
        return Math.max(1, total);
    }

    /** 한글/한자/가나는 두 칸, 나머지는 한 칸으로 센다. */
    public static double displayWidth(String line) {
        double width = 0;
        for (int i = 0; i < line.length(); i++) {
            width += isWide(line.charAt(i)) ? 2.0 : 1.0;
        }
        return width;
    }

    private static boolean isWide(char c) {
        return (c >= 0x1100 && c <= 0x115F)
                || (c >= 0x2E80 && c <= 0xA4CF)
                || (c >= 0xAC00 && c <= 0xD7A3)
                || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0xFE30 && c <= 0xFE6F)
                || (c >= 0xFF00 && c <= 0xFF60)
                || (c >= 0xFFE0 && c <= 0xFFE6);
    }

    public static double heightFor(int lines) {
        return Math.max(LINE_HEIGHT, lines * LINE_HEIGHT);
    }

    /** 상한(409.5pt)을 넘으면 몇 개의 행으로 나눠 병합해야 하는지. */
    public static int rowSpanFor(double height) {
        return Math.max(1, (int) Math.ceil(height / MAX_ROW_HEIGHT));
    }
}
