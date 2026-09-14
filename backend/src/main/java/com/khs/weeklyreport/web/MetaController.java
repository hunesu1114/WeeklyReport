package com.khs.weeklyreport.web;

import com.khs.weeklyreport.service.MemberService;
import com.khs.weeklyreport.service.ReportExportService;
import com.khs.weeklyreport.service.ReportService;
import com.khs.weeklyreport.web.dto.ReportDtos;
import com.khs.weeklyreport.web.dto.TeamDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 화면 보조 데이터 (양식 목록, 상태 후보, 업무명·사용자 자동완성). */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final ReportService reportService;
    private final ReportExportService exportService;
    private final MemberService memberService;

    public MetaController(ReportService reportService,
                          ReportExportService exportService,
                          MemberService memberService) {
        this.reportService = reportService;
        this.exportService = exportService;
        this.memberService = memberService;
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

    /**
     * 초대할 사람 찾기.
     *
     * <p>남의 계정을 찾아야 하지만 그 이상은 알 필요가 없으므로 username 과
     * 표시 이름만 내려간다. projectId 를 주면 이미 참여 중인 사람은 후보에서 뺀다.
     */
    @GetMapping("/users")
    public List<TeamDtos.UserBrief> users(@RequestParam String query,
                                          @RequestParam(required = false) Long projectId) {
        return memberService.searchUsers(query, projectId);
    }
}
