package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.InvitationStatus;
import com.khs.weeklyreport.domain.ProjectInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Long> {

    Optional<ProjectInvitation> findByProjectIdAndInviteeIdAndStatus(
            Long projectId, Long inviteeId, InvitationStatus status);

    /** 보드 설정 화면의 "보낸 초대" 목록. */
    @Query("""
            select i from ProjectInvitation i
            join fetch i.invitee
            join fetch i.inviter
            where i.project.id = :projectId and i.status = :status
            order by i.createdAt desc
            """)
    List<ProjectInvitation> findByProjectAndStatus(@Param("projectId") Long projectId,
                                                   @Param("status") InvitationStatus status);

    @Query("""
            select i from ProjectInvitation i
            join fetch i.project
            join fetch i.inviter
            where i.invitee.id = :userId and i.status = com.khs.weeklyreport.domain.InvitationStatus.PENDING
            order by i.createdAt desc
            """)
    List<ProjectInvitation> findPendingFor(@Param("userId") Long userId);
}
