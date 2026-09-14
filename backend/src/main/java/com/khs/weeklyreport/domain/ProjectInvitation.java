package com.khs.weeklyreport.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 보드 초대. 보낸 뒤 상대가 수락해야 멤버가 된다.
 *
 * <p>거절·취소된 것도 기록으로 남긴다. 같은 사람에게 다시 보낼 수 있어야 하므로
 * 유일 제약은 PENDING 인 건에만 걸려 있다(V5 참고).
 */
@Entity
@Table(name = "project_invitation")
@Getter
@Setter
public class ProjectInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inviter_id", nullable = false)
    private AppUser inviter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitee_id", nullable = false)
    private AppUser invitee;

    /** 수락하면 이 역할로 들어간다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ProjectRole role = ProjectRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "responded_at")
    private Instant respondedAt;

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    public void respond(InvitationStatus next) {
        this.status = next;
        this.respondedAt = Instant.now();
    }
}
