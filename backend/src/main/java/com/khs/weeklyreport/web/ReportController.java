package com.khs.weeklyreport.web;

import com.khs.weeklyreport.service.ReportExportService;
import com.khs.weeklyreport.service.ReportService;
import com.khs.weeklyreport.web.dto.ReportDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reportService;
    private final ReportExportService exportService;

    public ReportController(ReportService reportService, ReportExportService exportService) {
        this.reportService = reportService;
        this.exportService = exportService;
    }

    @GetMapping
    public Page<ReportDtos.ReportSummary> list(@RequestParam(required = false) String query,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return reportService.search(query, PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "reportDate").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    @GetMapping("/defaults")
    public ReportDtos.ReportDefaults defaults(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestParam(required = false) String authorName) {
        return reportService.defaults(reportDate, authorName);
    }

    @GetMapping("/{id}")
    public ReportDtos.ReportDetail get(@PathVariable Long id) {
        return reportService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportDtos.ReportDetail create(@Valid @RequestBody ReportDtos.SaveRequest request) {
        return reportService.create(request);
    }

    @PutMapping("/{id}")
    public ReportDtos.ReportDetail update(@PathVariable Long id,
                                          @Valid @RequestBody ReportDtos.SaveRequest request) {
        return reportService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        reportService.delete(id);
    }

    /** 이 보고서의 차주 예정 항목을 금주 진행으로 옮긴 다음 주 보고서를 만든다. */
    @PostMapping("/{id}/follow-up")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportDtos.ReportDetail followUp(@PathVariable Long id) {
        return reportService.createFollowUp(id);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id,
                                         @RequestParam(required = false) String templateKey) {
        ReportExportService.ExportResult result = exportService.export(id, templateKey);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(result.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(result.content());
    }
}
