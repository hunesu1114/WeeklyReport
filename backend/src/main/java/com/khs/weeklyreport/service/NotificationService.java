package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.Notification;
import com.khs.weeklyreport.domain.NotificationType;
import com.khs.weeklyreport.domain.ProjectInvitation;
import com.khs.weeklyreport.realtime.RealtimeEvent;
import com.khs.weeklyreport.realtime.RealtimePublisher;
import com.khs.weeklyreport.repository.NotificationRepository;
import com.khs.weeklyreport.repository.ProjectInvitationRepository;
import com.khs.weeklyreport.security.CurrentUser;
import com.khs.weeklyreport.web.dto.TeamDtos;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ProjectInvitationRepository invitationRepository;
    private final CurrentUser currentUser;
    private final RealtimePublisher realtime;

    public NotificationService(NotificationRepository notificationRepository,
                               ProjectInvitationRepository invitationRepository,
                               CurrentUser currentUser,
                               RealtimePublisher realtime) {
        this.notificationRepository = notificationRepository;
        this.invitationRepository = invitationRepository;
        this.currentUser = currentUser;
        this.realtime = realtime;
    }

    // ── 보내기 ───────────────────────────────────────────────

    public void send(AppUser recipient, NotificationType type, String title, String message, String link) {
        notificationRepository.save(Notification.to(recipient, type, title, message, link));
        realtime.toUser(recipient.getId(), RealtimeEvent.inbox());
    }

    public void sendInvite(ProjectInvitation invitation) {
        Notification notification = Notification.to(
                invitation.getInvitee(),
                NotificationType.PROJECT_INVITE,
                "%s 님이 '%s' 보드에 초대했습니다".formatted(
                        invitation.getInviter().getDisplayName(), invitation.getProject().getName()),
                invitation.getRole() == com.khs.weeklyreport.domain.ProjectRole.VIEWER
                        ? "읽기 전용으로 참여하게 됩니다."
                        : "카드를 함께 만들고 옮길 수 있습니다.",
                null);
        notification.setInvitation(invitation);
        notificationRepository.save(notification);
        realtime.toUser(invitation.getInvitee().getId(), RealtimeEvent.inbox());
    }

    /** 초대를 거둬들이면 그 알림도 의미가 없다. */
    public void dropInviteNotifications(Long invitationId, Long recipientId) {
        notificationRepository.deleteByInvitation(invitationId);
        realtime.toUser(recipientId, RealtimeEvent.inbox());
    }

    // ── 읽기 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TeamDtos.InboxView inbox(int limit) {
        Long userId = currentUser.requireId();
        List<TeamDtos.NotificationView> items =
                notificationRepository.findInbox(userId, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)))
                        .stream()
                        .map(TeamDtos.NotificationView::of)
                        .toList();
        return new TeamDtos.InboxView(
                items,
                notificationRepository.countByRecipientIdAndReadAtIsNull(userId),
                invitationRepository.findPendingFor(userId).size());
    }

    @Transactional
    public void markAllRead() {
        notificationRepository.markAllRead(currentUser.requireId(), Instant.now());
    }

    @Transactional
    public void markRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("알림을 찾을 수 없습니다. id=" + id));
        if (!notification.getRecipient().getId().equals(currentUser.requireId())) {
            // 남의 알림은 없는 것으로 친다
            throw new NotFoundException("알림을 찾을 수 없습니다. id=" + id);
        }
        if (notification.isUnread()) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
    }
}
