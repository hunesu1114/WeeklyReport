package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerIdOrderBySortOrderAscNameAsc(Long ownerId);

    List<Project> findByOwnerIdAndActiveTrueOrderBySortOrderAscNameAsc(Long ownerId);

    Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerId(Long ownerId);

    /** 이름 충돌은 같은 사용자 안에서만 따진다. */
    @Query("""
            select count(p) > 0 from Project p
            where p.owner.id = :ownerId and lower(p.name) = lower(:name)
            """)
    boolean existsByOwnerIdAndName(@Param("ownerId") Long ownerId, @Param("name") String name);

    // ── 주인 없는 데이터 가져오기 ────────────────────────────

    @Query("select count(p) from Project p where p.owner is null")
    long countOrphans();

    /** 카드는 프로젝트에 매달려 있으므로 프로젝트만 옮기면 함께 따라온다. */
    @Query("select count(c) from KanbanCard c where c.project.owner is null")
    long countOrphanCards();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Project p set p.owner = :owner where p.owner is null")
    int claimOrphans(@Param("owner") AppUser owner);
}
