package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.KanbanCard;
import com.khs.weeklyreport.domain.KanbanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface KanbanCardRepository extends JpaRepository<KanbanCard, Long> {

    /** 보드 조회. 담당자를 같이 읽어 카드마다 따로 읽지 않게 한다. */
    @Query("""
            select c from KanbanCard c
            left join fetch c.assignee
            where c.project.id = :projectId
            order by c.status asc, c.sortOrder asc, c.id asc
            """)
    List<KanbanCard> findBoard(@Param("projectId") Long projectId);

    @Query("""
            select c from KanbanCard c
            join fetch c.project
            left join fetch c.assignee
            where c.id = :id
            """)
    Optional<KanbanCard> findWithProject(@Param("id") Long id);

    /** 순서를 다시 매길 때만 쓴다. 보드 잠금 안에서 호출된다. */
    List<KanbanCard> findByProjectIdAndStatusOrderBySortOrderAscIdAsc(Long projectId, KanbanStatus status);

    /**
     * 완료일이 임박한 내 담당 카드. DONE 은 빼고 이미 지난 것도 함께 준다.
     * 놓친 일이 목록에서 조용히 빠지면 알림의 의미가 없다.
     */
    @Query("""
            select c from KanbanCard c
            join fetch c.project p
            left join fetch c.assignee
            where c.assignee.id = :userId
              and c.dueDate is not null
              and c.status <> com.khs.weeklyreport.domain.KanbanStatus.DONE
              and c.dueDate <= :until
            order by c.dueDate asc, c.id asc
            """)
    List<KanbanCard> findDueForAssignee(@Param("until") LocalDate until, @Param("userId") Long userId);

    /** 내가 속한 보드 전체의 임박 카드. 담당자 없는 것도 포함한다. */
    @Query("""
            select c from KanbanCard c
            join fetch c.project p
            left join fetch c.assignee
            where c.dueDate is not null
              and c.status <> com.khs.weeklyreport.domain.KanbanStatus.DONE
              and c.dueDate <= :until
              and exists (select 1 from ProjectMember m
                          where m.project = p and m.user.id = :userId)
            order by c.dueDate asc, c.id asc
            """)
    List<KanbanCard> findDueInMyProjects(@Param("until") LocalDate until, @Param("userId") Long userId);

    /** 담당자가 없는 임박 카드. 보드 상단 안내에 쓴다. */
    @Query("""
            select count(c) from KanbanCard c
            where c.project.id = :projectId
              and c.assignee is null
              and c.dueDate is not null
              and c.status <> com.khs.weeklyreport.domain.KanbanStatus.DONE
              and c.dueDate <= :until
            """)
    long countUnassignedDue(@Param("projectId") Long projectId, @Param("until") LocalDate until);

    // ── 주간보고 연동 ────────────────────────────────────────
    // 프로젝트/담당자 필터 조합마다 메서드를 나눈다. 한 쿼리에서 :param is null 로
    // 분기하면 PostgreSQL 이 null 파라미터의 타입을 추론하지 못해 실패한다.

    @Query("""
            select c from KanbanCard c
            join fetch c.project p
            left join fetch c.assignee
            where c.startDate between :from and :to
              and c.assignee.id = :userId
            order by p.sortOrder asc, p.name asc, c.startDate asc, c.id asc
            """)
    List<KanbanCard> findStartedByAssignee(@Param("from") LocalDate from,
                                           @Param("to") LocalDate to,
                                           @Param("userId") Long userId);

    @Query("""
            select c from KanbanCard c
            join fetch c.project p
            left join fetch c.assignee
            where c.startDate between :from and :to
              and exists (select 1 from ProjectMember m
                          where m.project = p and m.user.id = :userId)
            order by p.sortOrder asc, p.name asc, c.startDate asc, c.id asc
            """)
    List<KanbanCard> findStartedInMyProjects(@Param("from") LocalDate from,
                                             @Param("to") LocalDate to,
                                             @Param("userId") Long userId);

    @Query("""
            select c from KanbanCard c
            join fetch c.project p
            left join fetch c.assignee
            where c.startDate between :from and :to
              and p.id = :projectId
            order by c.startDate asc, c.id asc
            """)
    List<KanbanCard> findStartedInProject(@Param("from") LocalDate from,
                                          @Param("to") LocalDate to,
                                          @Param("projectId") Long projectId);

    /** 같은 칸의 마지막 순번. 새 카드는 맨 아래에 붙인다. */
    @Query("""
            select coalesce(max(c.sortOrder), -1) from KanbanCard c
            where c.project.id = :projectId and c.status = :status
            """)
    int findMaxSortOrder(@Param("projectId") Long projectId, @Param("status") KanbanStatus status);

    // ── 보드 목록의 집계 ─────────────────────────────────────
    // 프로젝트마다 카드를 전건 읽어 세던 것을 집계 쿼리로 바꾼다.

    @Query("""
            select c.project.id, count(c),
                   sum(case when c.status <> com.khs.weeklyreport.domain.KanbanStatus.DONE then 1 else 0 end),
                   sum(case when c.dueDate is not null
                             and c.status <> com.khs.weeklyreport.domain.KanbanStatus.DONE
                             and c.dueDate <= :until then 1 else 0 end)
            from KanbanCard c
            where c.project.id in :projectIds
            group by c.project.id
            """)
    List<Object[]> summarize(@Param("projectIds") List<Long> projectIds, @Param("until") LocalDate until);

    // ── 참여자 정리 ──────────────────────────────────────────

    /** 보드 안에서 사람마다 담당 중인 카드 수. 내보내기 전에 무엇이 사라지는지 알린다. */
    @Query("""
            select c.assignee.id, count(c) from KanbanCard c
            where c.project.id = :projectId and c.assignee is not null
            group by c.assignee.id
            """)
    List<Object[]> countAssignedPerMember(@Param("projectId") Long projectId);

    /**
     * 보드를 떠난 사람의 담당을 비운다.
     *
     * <p>그대로 두면 명단에 없는 사람이 카드에 남는다. 보기에도 이상하지만,
     * 그 카드를 열어 제목만 고치려 해도 담당자가 참여자가 아니라며 거절당해
     * 손댈 수 없는 카드가 된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update KanbanCard c set c.assignee = null "
            + "where c.project.id = :projectId and c.assignee.id = :userId")
    int clearAssignee(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
