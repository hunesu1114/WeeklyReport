package com.khs.weeklyreport.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "kanban_card")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class KanbanCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** 이 일을 맡은 사람. 비어 있으면 담당 없음. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KanbanStatus status = KanbanStatus.BACKLOG;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private CardPriority priority = CardPriority.NORMAL;

    /**
     * 화면에는 '생성일'로 보이지만 실제로는 일을 시작한 날이다.
     * 주간보고의 '금주 기간'과 겹치는 카드를 찾을 때 이 값을 쓴다.
     */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** 목표 완료일. 임박 알림의 기준. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** 같은 칸 안에서의 표시 순서(0-based). */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * 두 사람이 같은 카드를 열어 저장했을 때 뒤가 조용히 이기는 것을 막는다.
     * 저장 요청이 들고 온 값과 다르면 409 로 돌려보낸다.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 완료일까지 남은 날. 완료일이 없으면 null.
     * 0 이면 오늘이 완료일이고, 음수면 이미 지났다.
     */
    public Long daysUntilDue(LocalDate today) {
        return dueDate == null ? null : ChronoUnit.DAYS.between(today, dueDate);
    }

    /**
     * DONE 이 아니면서 완료일이 {@code days} 일 이내로 남은 카드인지.
     * 이미 지난 것(음수)도 임박으로 친다. 놓친 일이 조용히 사라지면 안 된다.
     */
    public boolean isDueSoon(LocalDate today, int days) {
        Long remaining = daysUntilDue(today);
        return remaining != null && status != KanbanStatus.DONE && remaining <= days;
    }
}
