package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 검색어는 항상 소문자 LIKE 패턴으로 넘긴다(검색어가 없으면 "%").
     * PostgreSQL 은 null 바인딩의 타입을 추론하지 못해 {@code lower(bytea)} 오류를 내므로,
     * {@code :pattern is null} 같은 비교를 쿼리 안에 두지 않는다.
     *
     * <p>주인이 다른 보고서는 애초에 조회되지 않는다.
     */
    @Query("""
            select r from Report r
            where r.owner.id = :ownerId
              and (lower(r.authorName) like :pattern escape '!'
                   or lower(coalesce(r.titleOverride, '')) like :pattern escape '!'
                   or exists (select 1 from ReportItem i
                              where i.report = r
                                and (lower(coalesce(i.taskName, '')) like :pattern escape '!'
                                     or lower(coalesce(i.detail, '')) like :pattern escape '!')))
            """)
    Page<Report> search(@Param("pattern") String pattern,
                        @Param("ownerId") Long ownerId,
                        Pageable pageable);

    Optional<Report> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<Report> findFirstByOwnerIdAndAuthorNameOrderByReportDateDescIdDesc(Long ownerId,
                                                                               String authorName);

    Optional<Report> findFirstByOwnerIdOrderByReportDateDescIdDesc(Long ownerId);

    @Query("select distinct r.authorName from Report r where r.owner.id = :ownerId order by r.authorName")
    List<String> findDistinctAuthorNames(@Param("ownerId") Long ownerId);

    // ── 주인 없는 데이터 가져오기 ────────────────────────────

    @Query("select count(r) from Report r where r.owner is null")
    long countOrphans();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Report r set r.owner = :owner where r.owner is null")
    int claimOrphans(@Param("owner") AppUser owner);
}
