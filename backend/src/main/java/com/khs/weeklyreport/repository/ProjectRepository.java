package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByOrderBySortOrderAscNameAsc();

    List<Project> findByActiveTrueOrderBySortOrderAscNameAsc();

    Optional<Project> findByName(String name);

    boolean existsByNameIgnoreCase(String name);
}
