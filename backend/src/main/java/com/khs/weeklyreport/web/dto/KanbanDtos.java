package com.khs.weeklyreport.web.dto;

import com.khs.weeklyreport.domain.CardPriority;
import com.khs.weeklyreport.domain.KanbanCard;
import com.khs.weeklyreport.domain.KanbanStatus;
import com.khs.weeklyreport.domain.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class KanbanDtos {

    private KanbanDtos() {
    }

    // ── 프로젝트 ──────────────────────────────────────────────

    public record ProjectRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Size(max = 20) String color,
            Boolean active,
            Integer sortOrder
    ) {
    }

    public record ProjectView(
            Long id,
            String name,
            String description,
            String color,
            boolean active,
            int sortOrder,
            long cardCount,
            long openCount,
            long dueSoonCount,
            Instant updatedAt
    ) {
        public static ProjectView of(Project project, long cardCount, long openCount, long dueSoonCount) {
            return new ProjectView(project.getId(), project.getName(), project.getDescription(),
                    project.getColor(), project.isActive(), project.getSortOrder(),
                    cardCount, openCount, dueSoonCount, project.getUpdatedAt());
        }
    }

    // ── 카드 ─────────────────────────────────────────────────

    public record CardRequest(
            @NotNull Long projectId,
            KanbanStatus status,
            @NotBlank @Size(max = 200) String title,
            String content,
            CardPriority priority,
            LocalDate startDate,
            LocalDate dueDate
    ) {
    }

    public record CardView(
            Long id,
            Long projectId,
            String projectName,
            String projectColor,
            KanbanStatus status,
            String title,
            String content,
            CardPriority priority,
            LocalDate startDate,
            LocalDate dueDate,
            int sortOrder,
            /** 완료일까지 남은 날. 없으면 null, 음수면 이미 지났다. */
            Long daysUntilDue,
            /** DONE 이 아니고 완료일이 임박(기본 3일 이내)했는지 */
            boolean dueSoon,
            Instant updatedAt
    ) {
        public static CardView of(KanbanCard card, LocalDate today, int dueSoonDays) {
            Project project = card.getProject();
            return new CardView(
                    card.getId(),
                    project.getId(),
                    project.getName(),
                    project.getColor(),
                    card.getStatus(),
                    card.getTitle(),
                    card.getContent(),
                    card.getPriority(),
                    card.getStartDate(),
                    card.getDueDate(),
                    card.getSortOrder(),
                    card.daysUntilDue(today),
                    card.isDueSoon(today, dueSoonDays),
                    card.getUpdatedAt());
        }
    }

    /** 카드를 다른 칸으로 옮기거나 같은 칸 안에서 순서를 바꿀 때. */
    public record MoveRequest(
            @NotNull KanbanStatus status,
            /** 옮겨갈 칸에서의 위치(0-based). 비우면 맨 아래로 간다. */
            Integer sortOrder
    ) {
    }

    /** 보드 한 판. 칸 이름 -> 그 칸의 카드들. */
    public record BoardView(
            ProjectView project,
            Map<KanbanStatus, List<CardView>> columns
    ) {
    }
}
