package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            select n from Notification n
            left join fetch n.invitation i
            left join fetch i.project
            where n.recipient.id = :userId
            order by n.createdAt desc
            """)
    List<Notification> findInbox(@Param("userId") Long userId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.recipient.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    /** 초대가 취소되면 그 초대를 가리키던 알림도 의미가 없어진다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.invitation.id = :invitationId")
    int deleteByInvitation(@Param("invitationId") Long invitationId);
}
