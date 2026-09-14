package com.khs.weeklyreport.web.dto;

import com.khs.weeklyreport.domain.*;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** 멤버 · 초대 · 알림 · 활동 기록 DTO. */
public final class TeamDtos {

    private TeamDtos() {
    }

    // ── 사용자 ───────────────────────────────────────────────

    /**
     * 남의 계정을 가리킬 때 쓰는 최소 정보. 그 이상은 알 필요가 없다.
     * 아바타는 바이트가 아니라 "있는지"와 "몇 번째 버전인지"만 준다 —
     * 이미지는 별도 주소로 받아간다.
     */
    public record UserBrief(Long id, String username, String displayName,
                            boolean hasAvatar, long avatarVersion) {
        public static UserBrief of(AppUser user) {
            return user == null ? null
                    : new UserBrief(user.getId(), user.getUsername(), user.getDisplayName(),
                            user.hasAvatar(), user.getAvatarVersion());
        }
    }

    // ── 멤버 ─────────────────────────────────────────────────

    public record MemberView(
            Long userId,
            String username,
            String displayName,
            boolean hasAvatar,
            long avatarVersion,
            ProjectRole role,
            Instant joinedAt
    ) {
        public static MemberView of(ProjectMember member) {
            AppUser user = member.getUser();
            return new MemberView(user.getId(), user.getUsername(), user.getDisplayName(),
                    user.hasAvatar(), user.getAvatarVersion(),
                    member.getRole(), member.getJoinedAt());
        }
    }

    public record RoleRequest(@NotNull ProjectRole role) {
    }

    // ── 초대 ─────────────────────────────────────────────────

    public record InviteRequest(
            @NotNull Long userId,
            /** 비우면 MEMBER(공동 수정)로 초대한다. */
            ProjectRole role
    ) {
        public ProjectRole roleOrDefault() {
            return role == null ? ProjectRole.MEMBER : role;
        }
    }

    public record InvitationView(
            Long id,
            Long projectId,
            String projectName,
            String projectColor,
            UserBrief inviter,
            UserBrief invitee,
            ProjectRole role,
            InvitationStatus status,
            Instant createdAt
    ) {
        public static InvitationView of(ProjectInvitation invitation) {
            Project project = invitation.getProject();
            return new InvitationView(
                    invitation.getId(),
                    project.getId(),
                    project.getName(),
                    project.getColor(),
                    UserBrief.of(invitation.getInviter()),
                    UserBrief.of(invitation.getInvitee()),
                    invitation.getRole(),
                    invitation.getStatus(),
                    invitation.getCreatedAt());
        }
    }

    // ── 알림 ─────────────────────────────────────────────────

    public record NotificationView(
            Long id,
            NotificationType type,
            String title,
            String message,
            String link,
            /** 초대 알림이면 그 초대. 수락/거절 버튼이 쓴다. 이미 답한 초대면 비어 있다. */
            InvitationView invitation,
            boolean read,
            Instant createdAt
    ) {
        public static NotificationView of(Notification notification) {
            ProjectInvitation invitation = notification.getInvitation();
            return new NotificationView(
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getLink(),
                    invitation == null ? null : InvitationView.of(invitation),
                    !notification.isUnread(),
                    notification.getCreatedAt());
        }
    }

    public record InboxView(
            java.util.List<NotificationView> items,
            long unreadCount,
            /** 답을 기다리는 초대 수. 배지에 따로 쓴다. */
            long pendingInviteCount
    ) {
    }

    // ── 활동 기록 ────────────────────────────────────────────

    public record ActivityView(
            Long id,
            Long projectId,
            String projectName,
            Long cardId,
            UserBrief actor,
            ActivityType type,
            String cardTitle,
            KanbanStatus fromStatus,
            KanbanStatus toStatus,
            String targetName,
            String detail,
            Instant createdAt
    ) {
        public static ActivityView of(ActivityLog log) {
            Project project = log.getProject();
            return new ActivityView(
                    log.getId(),
                    project.getId(),
                    project.getName(),
                    log.getCard() == null ? null : log.getCard().getId(),
                    UserBrief.of(log.getActor()),
                    log.getType(),
                    log.getCardTitle(),
                    log.getFromStatus(),
                    log.getToStatus(),
                    log.getTargetName(),
                    log.getDetail(),
                    log.getCreatedAt());
        }
    }
}
