package com.khs.weeklyreport.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "report")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 이 보고서의 주인. 로그인 도입 이전에 쌓인 행은 비어 있고,
     * 첫 관리자 계정이 /api/auth/orphans/claim 으로 가져간다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    /** 보고서를 제출하는 날짜. 파일명 접미사이자 주차 계산의 기준점. */
    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    /** 작성자 이름. 제목("주간보고 - 홍길동")과 파일명에 함께 쓰인다. */
    @Column(name = "author_name", nullable = false, length = 50)
    private String authorName;

    /** 비워두면 "주간보고 - {authorName}" 으로 렌더링된다. */
    @Column(name = "title_override", length = 200)
    private String titleOverride;

    @Column(name = "this_week_start", nullable = false)
    private LocalDate thisWeekStart;

    @Column(name = "this_week_end", nullable = false)
    private LocalDate thisWeekEnd;

    @Column(name = "next_week_start", nullable = false)
    private LocalDate nextWeekStart;

    @Column(name = "next_week_end", nullable = false)
    private LocalDate nextWeekEnd;

    /** 제외시간 계산 기준(= 기준 근무시간 - 합계). 표준 근무 40H. */
    @Column(name = "base_hours", nullable = false, precision = 6, scale = 2)
    private BigDecimal baseHours = new BigDecimal("40");

    /** 비고 */
    @Column(name = "note", columnDefinition = "text")
    private String note;

    /** 어떤 양식으로 내보낼지. report_template.template_key 를 가리킨다. */
    @Column(name = "template_key", nullable = false, length = 50)
    private String templateKey = "DEFAULT_V1";

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("section ASC, sortOrder ASC")
    @BatchSize(size = 50)
    private List<ReportItem> items = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String resolvedTitle() {
        if (titleOverride != null && !titleOverride.isBlank()) {
            return titleOverride;
        }
        return "주간보고 - " + authorName;
    }

    public List<ReportItem> itemsOf(ReportSection section) {
        return items.stream()
                .filter(it -> it.getSection() == section)
                .sorted(Comparator.comparingInt(ReportItem::getSortOrder))
                .toList();
    }

    public void replaceItems(List<ReportItem> replacements) {
        items.clear();
        for (ReportItem item : replacements) {
            item.setReport(this);
            items.add(item);
        }
    }
}
