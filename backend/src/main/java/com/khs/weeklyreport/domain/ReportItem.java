package com.khs.weeklyreport.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "report_item")
@Getter
@Setter
public class ReportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 20)
    private ReportSection section;

    /** 같은 구획 안에서의 표시 순서(0-based). 엑셀의 NO 컬럼은 여기서 파생된다. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 업무명 */
    @Column(name = "task_name", length = 200)
    private String taskName;

    /** 업무상세 - 여러 줄 텍스트 */
    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    /** 진행상태 (진행/완료/예정 ...) */
    @Column(name = "status", length = 30)
    private String status;

    /** 소요시간(H). 차주 예정 항목은 비워둔다. */
    @Column(name = "hours", precision = 6, scale = 2)
    private BigDecimal hours;
}
