package com.khs.weeklyreport.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 보고일 기준으로 금주/차주 구간을 계산한다.
 *
 * <p>기존 샘플의 규칙: 보고일 D 에 대해 금주 = [D-7, D-1], 차주 = [D, D+6].
 * (예: 2026-05-08 보고 → 금주 05.01~05.07, 차주 05.08~05.14)
 * 공휴일이나 보고 일정 변경으로 어긋나는 주가 있으므로 화면에서 직접 수정할 수 있게 둔다.
 */
@Component
public class WeekCalculator {

    public LocalDate thisWeekStart(LocalDate reportDate) {
        return reportDate.minusDays(7);
    }

    public LocalDate thisWeekEnd(LocalDate reportDate) {
        return reportDate.minusDays(1);
    }

    public LocalDate nextWeekStart(LocalDate reportDate) {
        return reportDate;
    }

    public LocalDate nextWeekEnd(LocalDate reportDate) {
        return reportDate.plusDays(6);
    }
}
