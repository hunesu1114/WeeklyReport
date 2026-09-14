package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /** 내가 멤버인 보드. 소유 여부가 아니라 참여 여부로 고른다. */
    @Query("""
            select p from Project p
            where exists (select 1 from ProjectMember m
                          where m.project = p and m.user.id = :userId)
            order by p.sortOrder asc, p.name asc
            """)
    List<Project> findMine(@Param("userId") Long userId);

    @Query("""
            select p from Project p
            where p.active = true
              and exists (select 1 from ProjectMember m
                          where m.project = p and m.user.id = :userId)
            order by p.sortOrder asc, p.name asc
            """)
    List<Project> findMineActive(@Param("userId") Long userId);

    @Query("""
            select count(p) from Project p
            where exists (select 1 from ProjectMember m
                          where m.project = p and m.user.id = :userId)
            """)
    long countMine(@Param("userId") Long userId);

    /**
     * 카드 순서를 다시 매기는 동안 보드를 잠근다.
     *
     * <p>두 사람이 같은 보드에서 동시에 카드를 옮기면, 둘 다 같은 순서를 읽고
     * 나중에 커밋한 쪽이 앞사람의 이동을 덮어쓴다. 보드 단위로 줄을 세워 막는다.
     * 이동 트랜잭션은 짧아서 대기가 체감되지 않고, 다른 보드끼리는 서로 막지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") Long id);

    // ── 주인 없는 데이터 가져오기 ────────────────────────────

    @Query("select count(p) from Project p where p.owner is null")
    long countOrphans();

    /** 카드는 프로젝트에 매달려 있으므로 프로젝트만 옮기면 함께 따라온다. */
    @Query("select count(c) from KanbanCard c where c.project.owner is null")
    long countOrphanCards();

    /** 아직 주인이 없는 보드. 회수할 때 멤버로도 넣어야 하므로 목록으로 받는다. */
    @Query("select p from Project p where p.owner is null")
    List<Project> findOrphans();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Project p set p.owner = :owner where p.owner is null")
    int claimOrphans(@Param("owner") AppUser owner);
}
