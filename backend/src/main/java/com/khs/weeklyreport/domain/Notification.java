package com.khs.weeklyreport.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 사용자 알림함의 한 줄. */
@Entity
@Table(name = "notification")
@Getter
@Setter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private AppUser recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", length = 500)
    private String message;

    /** 눌렀을 때 갈 화면. 예: /kanban/3 */
    @Column(name = "link", length = 200)
    private String link;

    /** 초대 알림이면 그 초대. 수락/거절 버튼이 이걸 쓴다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitation_id")
    private ProjectInvitation invitation;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isUnread() {
        return readAt == null;
    }

    public static Notification to(AppUser recipient, NotificationType type, String title,
                                  String message, String link) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setLink(link);
        notification.setCreatedAt(Instant.now());
        return notification;
    }
}
