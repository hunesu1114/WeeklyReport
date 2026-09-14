package com.khs.weeklyreport.web.dto;

import com.khs.weeklyreport.domain.CardPriority;
import com.khs.weeklyreport.domain.KanbanCard;
import com.khs.weeklyreport.domain.KanbanStatus;
import com.khs.weeklyreport.domain.Project;
import com.khs.weeklyreport.domain.ProjectRole;
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
            /** 이 보드에서 내 역할. 화면이 버튼을 감출지 판단한다. */
            ProjectRole myRole,
            Instant updatedAt
    ) {
        public static ProjectView of(Project project, long cardCount, long openCount,
                                     long dueSoonCount, ProjectRole myRole) {
            return new ProjectView(project.getId(), project.getName(), project.getDescription(),
                    project.getColor(), project.isActive(), project.getSortOrder(),
                    cardCount, openCount, dueSoonCount, myRole, project.getUpdatedAt());
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
            LocalDate dueDate,
            /** 담당자. 비우면 담당 없음. 그 보드의 참여자여야 한다. */
            Long assigneeId,
            /**
             * 화면이 이 카드를 읽었을 때의 버전. 그 사이 남이 고쳤으면 409 로 돌려보낸다.
             * 새 카드이거나 충돌 검사가 필요 없으면 비워 보낸다.
             */
            Long version
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
            TeamDtos.UserBrief assignee,
            long version,
            /** 완료일까지 남은 날. 없으면 null, 음수면 이미 지났다. */
            Long daysUntilDue,
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
                    TeamDtos.UserBrief.of(card.getAssignee()),
                    card.getVersion(),
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
            Map<KanbanStatus, List<CardView>> columns,
            /**
             * 담당자 없이 완료일이 임박한 카드 수.
             * 아무도 책임지지 않는 일이 조용히 지나가지 않도록 보드 위에 띄운다.
             */
            long unassignedDueCount
    ) {
    }

    /** 저장 충돌(409) 응답. 서버의 현재 값을 함께 준다. */
    public record ConflictView(
            String message,
            CardView current
    ) {
    }
}
