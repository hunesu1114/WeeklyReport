package com.khs.weeklyreport.domain;

/**
 * 보고서 안에서 항목이 속하는 구획.
 * 템플릿이 늘어나더라도 "이번 주 / 다음 주"라는 축은 공통이므로 도메인 레벨에 둔다.
 */
public enum ReportSection {
    /** 금주 진행 내용 */
    THIS_WEEK,
    /** 차주 진행 예정 */
    NEXT_WEEK
}
