package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.KanbanCard;
import com.khs.weeklyreport.domain.Project;
import com.khs.weeklyreport.excel.KanbanBoardWriter;
import com.khs.weeklyreport.repository.KanbanCardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 칸반 보드를 엑셀로 내보낸다. */
@Service
public class KanbanExportService {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KanbanCardRepository cardRepository;
    private final ProjectAccess access;
    private final KanbanBoardWriter writer = new KanbanBoardWriter();

    public KanbanExportService(KanbanCardRepository cardRepository, ProjectAccess access) {
        this.cardRepository = cardRepository;
        this.access = access;
    }

    public record ExportResult(String filename, byte[] content) {
    }

    /**
     * 보드의 모든 카드를 한 장으로 내보낸다.
     * 볼 수 있으면 내보낼 수도 있다 — 읽기 전용 참여자도 받아갈 수 있다.
     */
    @Transactional(readOnly = true)
    public ExportResult exportProject(Long projectId) {
        Project project = access.requireRead(projectId);
        List<KanbanCard> cards = cardRepository.findBoard(projectId);
        LocalDate today = LocalDate.now();

        byte[] content = writer.write(project, cards, today);
        String filename = "%s_칸반_%s.xlsx".formatted(safe(project.getName()), FILE_DATE.format(today));
        return new ExportResult(filename, content);
    }

    /** 파일명에 쓸 수 없는 문자를 걷어낸다. 보드 이름은 사용자가 자유롭게 적는다. */
    private static String safe(String value) {
        if (value == null || value.isBlank()) return "보드";
        return value.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
