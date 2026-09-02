package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.Report;
import com.khs.weeklyreport.domain.ReportTemplate;
import com.khs.weeklyreport.excel.ReportRenderer;
import com.khs.weeklyreport.excel.RendererRegistry;
import com.khs.weeklyreport.repository.ReportTemplateRepository;
import com.khs.weeklyreport.web.dto.ReportDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReportExportService {

    /** 파일명 패턴의 {date:yyyyMMdd} 치환자. */
    private static final Pattern DATE_TOKEN = Pattern.compile("\\{date:([^}]+)}");

    private final ReportService reportService;
    private final ReportTemplateRepository templateRepository;
    private final RendererRegistry rendererRegistry;

    public ReportExportService(ReportService reportService,
                               ReportTemplateRepository templateRepository,
                               RendererRegistry rendererRegistry) {
        this.reportService = reportService;
        this.templateRepository = templateRepository;
        this.rendererRegistry = rendererRegistry;
    }

    public record ExportResult(String filename, byte[] content) {
    }

    @Transactional(readOnly = true)
    public ExportResult export(Long reportId, String templateKeyOverride) {
        Report report = reportService.load(reportId);
        String templateKey = (templateKeyOverride == null || templateKeyOverride.isBlank())
                ? report.getTemplateKey()
                : templateKeyOverride;

        ReportTemplate template = templateRepository.findByTemplateKey(templateKey)
                .orElseThrow(() -> new NotFoundException("양식을 찾을 수 없습니다: " + templateKey));
        if (!template.isActive()) {
            throw new IllegalArgumentException("비활성 양식입니다: " + templateKey);
        }

        ReportRenderer renderer = rendererRegistry.resolve(template);
        byte[] content = renderer.render(report);
        return new ExportResult(resolveFilename(template.getFilenamePattern(), report), content);
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.TemplateOption> activeTemplates() {
        return templateRepository.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(ReportDtos.TemplateOption::from)
                .toList();
    }

    String resolveFilename(String pattern, Report report) {
        String result = pattern
                .replace("{author}", safe(report.getAuthorName()))
                .replace("{title}", safe(report.resolvedTitle()));

        Matcher matcher = DATE_TOKEN.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String formatted = DateTimeFormatter.ofPattern(matcher.group(1)).format(report.getReportDate());
            matcher.appendReplacement(sb, Matcher.quoteReplacement(formatted));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 파일명에 쓸 수 없는 문자를 걷어낸다. */
    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
