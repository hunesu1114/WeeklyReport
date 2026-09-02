package com.khs.weeklyreport.web.dto;

import com.khs.weeklyreport.domain.Report;
import com.khs.weeklyreport.domain.ReportItem;
import com.khs.weeklyreport.domain.ReportSection;
import com.khs.weeklyreport.domain.ReportTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 요청/응답 DTO 모음. 엔티티를 그대로 노출하지 않는다. */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record ItemPayload(
            Long id,
            @NotNull ReportSection section,
            @Size(max = 200) String taskName,
            String detail,
            @Size(max = 30) String status,
            @PositiveOrZero BigDecimal hours
    ) {
        public static ItemPayload from(ReportItem item) {
            return new ItemPayload(item.getId(), item.getSection(), item.getTaskName(),
                    item.getDetail(), item.getStatus(), item.getHours());
        }
    }

    public record SaveRequest(
            @NotNull LocalDate reportDate,
            @NotBlank @Size(max = 50) String authorName,
            @Size(max = 200) String titleOverride,
            @NotNull LocalDate thisWeekStart,
            @NotNull LocalDate thisWeekEnd,
            @NotNull LocalDate nextWeekStart,
            @NotNull LocalDate nextWeekEnd,
            @NotNull @PositiveOrZero BigDecimal baseHours,
            String note,
            @NotBlank String templateKey,
            @Valid List<ItemPayload> items
    ) {
        public List<ItemPayload> safeItems() {
            return items == null ? List.of() : items;
        }
    }

    public record ReportDetail(
            Long id,
            LocalDate reportDate,
            String authorName,
            String title,
            String titleOverride,
            LocalDate thisWeekStart,
            LocalDate thisWeekEnd,
            LocalDate nextWeekStart,
            LocalDate nextWeekEnd,
            BigDecimal baseHours,
            String note,
            String templateKey,
            List<ItemPayload> thisWeekItems,
            List<ItemPayload> nextWeekItems,
            BigDecimal totalHours,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static ReportDetail from(Report report) {
            List<ItemPayload> thisWeek = new ArrayList<>();
            List<ItemPayload> nextWeek = new ArrayList<>();
            report.itemsOf(ReportSection.THIS_WEEK).forEach(it -> thisWeek.add(ItemPayload.from(it)));
            report.itemsOf(ReportSection.NEXT_WEEK).forEach(it -> nextWeek.add(ItemPayload.from(it)));
            BigDecimal total = report.itemsOf(ReportSection.THIS_WEEK).stream()
                    .map(ReportItem::getHours)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new ReportDetail(report.getId(), report.getReportDate(), report.getAuthorName(),
                    report.resolvedTitle(), report.getTitleOverride(),
                    report.getThisWeekStart(), report.getThisWeekEnd(),
                    report.getNextWeekStart(), report.getNextWeekEnd(),
                    report.getBaseHours(), report.getNote(), report.getTemplateKey(),
                    thisWeek, nextWeek, total, report.getCreatedAt(), report.getUpdatedAt());
        }
    }

    public record ReportSummary(
            Long id,
            LocalDate reportDate,
            String authorName,
            String title,
            LocalDate thisWeekStart,
            LocalDate thisWeekEnd,
            String templateKey,
            int thisWeekItemCount,
            int nextWeekItemCount,
            BigDecimal totalHours,
            Instant updatedAt
    ) {
        public static ReportSummary from(Report report) {
            BigDecimal total = report.itemsOf(ReportSection.THIS_WEEK).stream()
                    .map(ReportItem::getHours)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new ReportSummary(report.getId(), report.getReportDate(), report.getAuthorName(),
                    report.resolvedTitle(), report.getThisWeekStart(), report.getThisWeekEnd(),
                    report.getTemplateKey(),
                    report.itemsOf(ReportSection.THIS_WEEK).size(),
                    report.itemsOf(ReportSection.NEXT_WEEK).size(),
                    total, report.getUpdatedAt());
        }
    }

    /** 새 보고서를 열 때 화면이 미리 채워둘 값. */
    public record ReportDefaults(
            LocalDate reportDate,
            String authorName,
            LocalDate thisWeekStart,
            LocalDate thisWeekEnd,
            LocalDate nextWeekStart,
            LocalDate nextWeekEnd,
            BigDecimal baseHours,
            String templateKey,
            Long carriedFromReportId,
            List<ItemPayload> thisWeekItems
    ) {
    }

    public record TemplateOption(
            String templateKey,
            String name,
            String description,
            boolean active
    ) {
        public static TemplateOption from(ReportTemplate template) {
            return new TemplateOption(template.getTemplateKey(), template.getName(),
                    template.getDescription(), template.isActive());
        }
    }
}
