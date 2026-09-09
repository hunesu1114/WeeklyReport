package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.KanbanCard;
import com.khs.weeklyreport.domain.KanbanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface KanbanCardRepository extends JpaRepository<KanbanCard, Long> {

    List<KanbanCard> findByProjectIdOrderByStatusAscSortOrderAscIdAsc(Long projectId);

    /**
     * 완료일이 임박한 카드. DONE 은 제외하고, 이미 지난 것도 함께 준다.
     * 놓친 일이 목록에서 조용히 빠지면 알림의 의미가 없다.
     *
     * <p>정렬은 완료일까지만 하고 중요도는 서비스에서 정렬한다.
     * 중요도는 문자열로 저장되어 있어 DB 정렬은 알파벳순(HIGH, LOW, NORMAL, URGENT)이 된다.
     */
    @Query("""
            select c from KanbanCard c
            join fetch c.project p
            where c.dueDate is not null
              and c.status <> com.khs.weeklyreport.domain.KanbanStatus.DONE
              and c.dueDate <= :until
            order by c.dueDate asc, c.id asc
            """)
    List<KanbanCard> findDueUntil(@Param("until") LocalDate until);

    /**
     * 시작일이 주어진 기간 안에 있는 카드. 주간보고의 '금주 기간' 연동에 쓴다.
     *
     * <p>프로젝트 필터가 필요할 때를 위해 메서드를 따로 둔다. 하나의 쿼리에서
     * {@code :projectId is null} 로 분기하면 PostgreSQL 이 null 파라미터의 타입을
     * 추론하지 못해 실패한다.
     */
    @Query("""
            select c from KanbanCard c
            join fetch c.project p
            where c.startDate is not null
              and c.startDate between :from and :to
            order by p.sortOrder asc, p.name asc, c.startDate asc, c.id asc
            """)
    List<KanbanCard> findStartedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select c from KanbanCard c
            join fetch c.project p
            where c.startDate is not null
              and c.startDate between :from and :to
              and p.id = :projectId
            order by c.startDate asc, c.id asc
            """)
    List<KanbanCard> findStartedBetweenInProject(@Param("from") LocalDate from,
                                                 @Param("to") LocalDate to,
                                                 @Param("projectId") Long projectId);

    /** 같은 칸의 마지막 순번. 새 카드는 맨 아래에 붙인다. */
    @Query("""
            select coalesce(max(c.sortOrder), -1) from KanbanCard c
            where c.project.id = :projectId and c.status = :status
            """)
    int findMaxSortOrder(@Param("projectId") Long projectId, @Param("status") KanbanStatus status);
}
