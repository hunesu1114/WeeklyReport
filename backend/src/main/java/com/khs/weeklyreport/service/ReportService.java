package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.Report;
import com.khs.weeklyreport.domain.ReportItem;
import com.khs.weeklyreport.domain.ReportSection;
import com.khs.weeklyreport.domain.ReportTemplate;
import com.khs.weeklyreport.repository.ReportItemRepository;
import com.khs.weeklyreport.repository.ReportRepository;
import com.khs.weeklyreport.repository.ReportTemplateRepository;
import com.khs.weeklyreport.web.dto.ReportDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ReportService {

    /** 화면 셀렉트의 기본 후보. 실제 사용된 값이 있으면 뒤에 합쳐서 내려준다. */
    private static final List<String> DEFAULT_STATUSES = List.of("진행", "완료", "예정", "보류", "취소");

    private final ReportRepository reportRepository;
    private final ReportItemRepository reportItemRepository;
    private final ReportTemplateRepository templateRepository;
    private final WeekCalculator weekCalculator;

    public ReportService(ReportRepository reportRepository,
                         ReportItemRepository reportItemRepository,
                         ReportTemplateRepository templateRepository,
                         WeekCalculator weekCalculator) {
        this.reportRepository = reportRepository;
        this.reportItemRepository = reportItemRepository;
        this.templateRepository = templateRepository;
        this.weekCalculator = weekCalculator;
    }

    @Transactional(readOnly = true)
    public Page<ReportDtos.ReportSummary> search(String query, Pageable pageable) {
        return reportRepository.search(likePattern(query), pageable).map(ReportDtos.ReportSummary::from);
    }

    @Transactional(readOnly = true)
    public ReportDtos.ReportDetail get(Long id) {
        return ReportDtos.ReportDetail.from(load(id));
    }

    @Transactional(readOnly = true)
    public Report load(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("보고서를 찾을 수 없습니다. id=" + id));
    }

    @Transactional
    public ReportDtos.ReportDetail create(ReportDtos.SaveRequest request) {
        Report report = new Report();
        apply(report, request);
        return ReportDtos.ReportDetail.from(reportRepository.save(report));
    }

    @Transactional
    public ReportDtos.ReportDetail update(Long id, ReportDtos.SaveRequest request) {
        Report report = load(id);
        apply(report, request);
        return ReportDtos.ReportDetail.from(reportRepository.save(report));
    }

    @Transactional
    public void delete(Long id) {
        if (!reportRepository.existsById(id)) {
            throw new NotFoundException("보고서를 찾을 수 없습니다. id=" + id);
        }
        reportRepository.deleteById(id);
    }

    /**
     * 이 보고서를 바탕으로 다음 주 보고서를 만든다.
     * 차주 예정 항목이 새 보고서의 금주 진행 항목으로 넘어간다.
     */
    @Transactional
    public ReportDtos.ReportDetail createFollowUp(Long id) {
        Report source = load(id);
        LocalDate reportDate = source.getReportDate().plusWeeks(1);

        Report next = new Report();
        next.setReportDate(reportDate);
        next.setAuthorName(source.getAuthorName());
        next.setTitleOverride(source.getTitleOverride());
        next.setThisWeekStart(source.getNextWeekStart());
        next.setThisWeekEnd(source.getNextWeekEnd());
        next.setNextWeekStart(weekCalculator.nextWeekStart(reportDate));
        next.setNextWeekEnd(weekCalculator.nextWeekEnd(reportDate));
        next.setBaseHours(source.getBaseHours());
        next.setTemplateKey(source.getTemplateKey());

        List<ReportItem> carried = new ArrayList<>();
        int order = 0;
        for (ReportItem item : source.itemsOf(ReportSection.NEXT_WEEK)) {
            ReportItem copy = new ReportItem();
            copy.setSection(ReportSection.THIS_WEEK);
            copy.setSortOrder(order++);
            copy.setTaskName(item.getTaskName());
            copy.setDetail(item.getDetail());
            copy.setStatus("진행");
            copy.setHours(null);
            carried.add(copy);
        }
        next.replaceItems(carried);
        return ReportDtos.ReportDetail.from(reportRepository.save(next));
    }

    /** 새 보고서 화면의 초기값. 직전 보고서의 차주 예정 항목을 끌어온다. */
    @Transactional(readOnly = true)
    public ReportDtos.ReportDefaults defaults(LocalDate reportDate, String authorName) {
        LocalDate base = reportDate == null ? LocalDate.now() : reportDate;

        Optional<Report> previous = (authorName == null || authorName.isBlank())
                ? reportRepository.findFirstByOrderByReportDateDescIdDesc()
                : reportRepository.findFirstByAuthorNameOrderByReportDateDescIdDesc(authorName.trim());

        List<ReportDtos.ItemPayload> carried = new ArrayList<>();
        previous.ifPresent(prev -> prev.itemsOf(ReportSection.NEXT_WEEK).forEach(item ->
                carried.add(new ReportDtos.ItemPayload(null, ReportSection.THIS_WEEK,
                        item.getTaskName(), item.getDetail(), "진행", null))));

        String resolvedAuthor = (authorName != null && !authorName.isBlank())
                ? authorName.trim()
                : previous.map(Report::getAuthorName).orElse("");

        String templateKey = previous.map(Report::getTemplateKey)
                .orElseGet(() -> templateRepository.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                        .findFirst().map(ReportTemplate::getTemplateKey).orElse("DEFAULT_V1"));

        return new ReportDtos.ReportDefaults(
                base,
                resolvedAuthor,
                weekCalculator.thisWeekStart(base),
                weekCalculator.thisWeekEnd(base),
                weekCalculator.nextWeekStart(base),
                weekCalculator.nextWeekEnd(base),
                previous.map(Report::getBaseHours).orElse(new BigDecimal("40")),
                templateKey,
                previous.map(Report::getId).orElse(null),
                carried);
    }

    @Transactional(readOnly = true)
    public List<String> statusOptions() {
        Set<String> merged = new LinkedHashSet<>(DEFAULT_STATUSES);
        merged.addAll(reportItemRepository.findUsedStatuses());
        return List.copyOf(merged);
    }

    @Transactional(readOnly = true)
    public List<String> taskNameSuggestions(String query) {
        return reportItemRepository.findTaskNameSuggestions(likePattern(query)).stream().limit(30).toList();
    }

    @Transactional(readOnly = true)
    public List<String> authors() {
        return reportRepository.findDistinctAuthorNames();
    }

    private void apply(Report report, ReportDtos.SaveRequest request) {
        templateRepository.findByTemplateKey(request.templateKey())
                .filter(ReportTemplate::isActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "사용할 수 없는 양식입니다: " + request.templateKey()));

        report.setReportDate(request.reportDate());
        report.setAuthorName(request.authorName().trim());
        report.setTitleOverride(blankToNull(request.titleOverride()));
        report.setThisWeekStart(request.thisWeekStart());
        report.setThisWeekEnd(request.thisWeekEnd());
        report.setNextWeekStart(request.nextWeekStart());
        report.setNextWeekEnd(request.nextWeekEnd());
        report.setBaseHours(request.baseHours());
        report.setNote(request.note());
        report.setTemplateKey(request.templateKey());

        List<ReportItem> items = new ArrayList<>();
        int thisWeekOrder = 0;
        int nextWeekOrder = 0;
        for (ReportDtos.ItemPayload payload : request.safeItems()) {
            ReportItem item = new ReportItem();
            item.setSection(payload.section());
            item.setSortOrder(payload.section() == ReportSection.THIS_WEEK ? thisWeekOrder++ : nextWeekOrder++);
            item.setTaskName(payload.taskName());
            item.setDetail(payload.detail());
            item.setStatus(payload.status());
            item.setHours(payload.section() == ReportSection.THIS_WEEK ? payload.hours() : null);
            items.add(item);
        }
        report.replaceItems(items);
    }

/**
     * 검색어를 소문자 LIKE 패턴으로 바꾼다. 검색어가 없으면 "%" 라서 전건이 걸린다.
     * null 을 그대로 바인딩하면 PostgreSQL 이 파라미터 타입을 추론하지 못한다.
     */
    private static String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return "%";
        }
        String escaped = query.trim().toLowerCase()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
