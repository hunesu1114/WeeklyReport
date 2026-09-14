package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /** 프로젝트 하나의 활동. */
    @Query(value = """
            select a from ActivityLog a
            left join fetch a.actor
            where a.project.id = :projectId
            """,
            countQuery = "select count(a) from ActivityLog a where a.project.id = :projectId")
    Page<ActivityLog> findByProject(@Param("projectId") Long projectId, Pageable pageable);

    /** 카드 하나의 활동. 카드 대화상자 아래에 붙는다. */
    @Query(value = """
            select a from ActivityLog a
            left join fetch a.actor
            where a.card.id = :cardId
            """,
            countQuery = "select count(a) from ActivityLog a where a.card.id = :cardId")
    Page<ActivityLog> findByCard(@Param("cardId") Long cardId, Pageable pageable);

    /** 내가 속한 모든 보드의 활동. */
    @Query(value = """
            select a from ActivityLog a
            left join fetch a.actor
            left join fetch a.project
            where exists (select 1 from ProjectMember m
                          where m.project = a.project and m.user.id = :userId)
            """,
            countQuery = """
            select count(a) from ActivityLog a
            where exists (select 1 from ProjectMember m
                          where m.project = a.project and m.user.id = :userId)
            """)
    Page<ActivityLog> findForUser(@Param("userId") Long userId, Pageable pageable);
}
