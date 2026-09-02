package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 검색어는 항상 소문자 LIKE 패턴으로 넘긴다(검색어가 없으면 "%").
     * PostgreSQL 은 null 바인딩의 타입을 추론하지 못해 {@code lower(bytea)} 오류를 내므로,
     * {@code :pattern is null} 같은 비교를 쿼리 안에 두지 않는다.
     */
    @Query("""
            select r from Report r
            where lower(r.authorName) like :pattern escape '!'
               or lower(coalesce(r.titleOverride, '')) like :pattern escape '!'
               or exists (select 1 from ReportItem i
                          where i.report = r
                            and (lower(coalesce(i.taskName, '')) like :pattern escape '!'
                                 or lower(coalesce(i.detail, '')) like :pattern escape '!'))
            """)
    Page<Report> search(@Param("pattern") String pattern, Pageable pageable);

    Optional<Report> findFirstByAuthorNameOrderByReportDateDescIdDesc(String authorName);

    Optional<Report> findFirstByOrderByReportDateDescIdDesc();

    @Query("select distinct r.authorName from Report r order by r.authorName")
    List<String> findDistinctAuthorNames();
}
