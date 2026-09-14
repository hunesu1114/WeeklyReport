package com.khs.weeklyreport.excel;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.CardPriority;
import com.khs.weeklyreport.domain.KanbanCard;
import com.khs.weeklyreport.domain.KanbanStatus;
import com.khs.weeklyreport.domain.Project;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KanbanBoardWriterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);

    /** 표가 시작하는 행. 제목 · 요약 · 빈 줄 다음이다. */
    private static final int HEADER_ROW = 3;

    @Test
    void 카드가_한_장씩_행이_된다() throws IOException {
        byte[] bytes = new KanbanBoardWriter().write(
                project("결제 개편"),
                List.of(
                        card(KanbanStatus.TODO, "결제 모듈 정리", CardPriority.HIGH,
                                TODAY, TODAY.plusDays(2), user("박보람")),
                        card(KanbanStatus.DONE, "스펙 확정", CardPriority.NORMAL,
                                TODAY.minusDays(5), TODAY.minusDays(1), null)),
                TODAY);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("결제 개편 칸반 보드");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .contains("전체 2장", "진행할 일 1장", "완료 1장");
            assertThat(sheet.getRow(HEADER_ROW).getCell(1).getStringCellValue()).isEqualTo("상태");

            Row first = sheet.getRow(HEADER_ROW + 1);
            assertThat(first.getCell(0).getStringCellValue()).isEqualTo("1");
            assertThat(first.getCell(1).getStringCellValue()).isEqualTo("TODO");
            assertThat(first.getCell(2).getStringCellValue()).isEqualTo("결제 모듈 정리");
            assertThat(first.getCell(3).getStringCellValue()).isEqualTo("높음");
            assertThat(first.getCell(4).getStringCellValue()).isEqualTo("박보람");
            assertThat(first.getCell(6).getStringCellValue()).isEqualTo("2026-09-16");
            assertThat(first.getCell(7).getStringCellValue()).isEqualTo("2일 남음");

            Row second = sheet.getRow(HEADER_ROW + 2);
            assertThat(second.getCell(4).getStringCellValue()).isEqualTo("-");
            // 완료한 카드에 "1일 지남"이 남아 있으면 읽는 사람이 오해한다
            assertThat(second.getCell(7).getStringCellValue()).isEmpty();
        }
    }

    @Test
    void 카드가_없어도_파일은_만들어진다() throws IOException {
        byte[] bytes = new KanbanBoardWriter().write(project("빈 보드"), List.of(), TODAY);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(HEADER_ROW).getCell(0).getStringCellValue()).isEqualTo("No");
            assertThat(sheet.getRow(HEADER_ROW + 1)).isNull();
        }
    }

    private static Project project(String name) {
        Project project = new Project();
        project.setName(name);
        return project;
    }

    private static AppUser user(String displayName) {
        AppUser user = new AppUser();
        user.setDisplayName(displayName);
        return user;
    }

    private static KanbanCard card(KanbanStatus status, String title, CardPriority priority,
                                   LocalDate start, LocalDate due, AppUser assignee) {
        KanbanCard card = new KanbanCard();
        card.setStatus(status);
        card.setTitle(title);
        card.setPriority(priority);
        card.setStartDate(start);
        card.setDueDate(due);
        card.setAssignee(assignee);
        return card;
    }
}
