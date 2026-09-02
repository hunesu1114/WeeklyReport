package com.khs.weeklyreport.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 다운로드 양식 카탈로그.
 *
 * <p>MVP 에서는 DEFAULT_V1 한 종류만 활성화되어 있지만, 양식이 늘어날 때
 * 이 테이블에 행을 추가하고 {@code renderer_bean} 에 해당
 * {@link com.khs.weeklyreport.excel.ReportRenderer} 빈 이름을 적어주면
 * 나머지 코드는 손대지 않아도 된다.
 */
@Entity
@Table(name = "report_template")
@Getter
@Setter
public class ReportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_key", nullable = false, unique = true, length = 50)
    private String templateKey;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** 이 양식을 그려내는 ReportRenderer 스프링 빈 이름. */
    @Column(name = "renderer_bean", nullable = false, length = 100)
    private String rendererBean;

    /** 파일명 패턴. {author} / {date:패턴} 치환자를 지원한다. */
    @Column(name = "filename_pattern", nullable = false, length = 200)
    private String filenamePattern;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
