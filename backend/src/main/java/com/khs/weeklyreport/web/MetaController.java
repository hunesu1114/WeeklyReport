package com.khs.weeklyreport.web;

import com.khs.weeklyreport.service.ReportExportService;
import com.khs.weeklyreport.service.ReportService;
import com.khs.weeklyreport.web.dto.ReportDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 화면 보조 데이터 (양식 목록, 상태 후보, 업무명 자동완성). */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final ReportService reportService;
    private final ReportExportService exportService;

    public MetaController(ReportService reportService, ReportExportService exportService) {
        this.reportService = reportService;
        this.exportService = exportService;
    }

    @GetMapping("/templates")
    public List<ReportDtos.TemplateOption> templates() {
        return exportService.activeTemplates();
    }

    @GetMapping("/statuses")
    public List<String> statuses() {
        return reportService.statusOptions();
    }

    @GetMapping("/task-names")
    public List<String> taskNames(@RequestParam(required = false) String query) {
        return reportService.taskNameSuggestions(query);
    }

    @GetMapping("/authors")
    public List<String> authors() {
        return reportService.authors();
    }
}
