package com.khs.weeklyreport.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 보드에서 일어난 일 한 줄.
 *
 * <p>카드 제목과 대상자 이름을 그대로 박아둔다. 카드나 사람이 지워져도
 * "무엇을 옮겼는지" 읽혀야 기록으로서 쓸모가 있다.
 */
@Entity
@Table(name = "activity_log")
@Getter
@Setter
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** 카드와 무관한 활동(멤버 추가 등)이면 비어 있다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private KanbanCard card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private AppUser actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ActivityType type;

    @Column(name = "card_title", length = 200)
    private String cardTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private KanbanStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20)
    private KanbanStatus toStatus;

    @Column(name = "target_name", length = 50)
    private String targetName;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
