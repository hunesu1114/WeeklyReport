package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.ProjectMember;
import com.khs.weeklyreport.domain.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    @Query("""
            select m from ProjectMember m
            join fetch m.user u
            where m.project.id = :projectId
            order by m.role asc, u.displayName asc
            """)
    List<ProjectMember> findMembers(@Param("projectId") Long projectId);

    /** 마지막 OWNER 를 내보내거나 강등하지 못하게 막을 때 쓴다. */
    long countByProjectIdAndRole(Long projectId, ProjectRole role);

    /** 보드에 변경이 생겼을 때 알림을 보낼 대상. */
    @Query("select m.user.id from ProjectMember m where m.project.id = :projectId")
    List<Long> findUserIds(@Param("projectId") Long projectId);
}
