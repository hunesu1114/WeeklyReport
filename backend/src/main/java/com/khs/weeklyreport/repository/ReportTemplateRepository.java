package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.ReportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {

    List<ReportTemplate> findByActiveTrueOrderBySortOrderAscIdAsc();

    Optional<ReportTemplate> findByTemplateKey(String templateKey);
}
