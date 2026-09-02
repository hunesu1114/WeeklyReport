package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.ReportItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportItemRepository extends JpaRepository<ReportItem, Long> {

    /** {@code pattern} 은 항상 소문자 LIKE 패턴(검색어가 없으면 "%"). */
    @Query("""
            select distinct i.taskName from ReportItem i
            where i.taskName is not null and i.taskName <> ''
              and lower(i.taskName) like :pattern escape '!'
            order by i.taskName
            """)
    List<String> findTaskNameSuggestions(@Param("pattern") String pattern);

    @Query("""
            select distinct i.status from ReportItem i
            where i.status is not null and i.status <> ''
            order by i.status
            """)
    List<String> findUsedStatuses();
}
