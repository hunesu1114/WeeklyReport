package com.khs.weeklyreport.excel;

import com.khs.weeklyreport.domain.Report;

/**
 * 하나의 다운로드 양식을 xlsx 바이트로 그려내는 전략.
 *
 * <p>양식을 추가할 때는 이 인터페이스 구현 빈을 하나 만들고
 * {@code report_template} 테이블에 그 빈 이름으로 행을 추가하기만 하면 된다.
 * 컨트롤러/서비스/화면은 수정하지 않는다.
 */
public interface ReportRenderer {

    /** 이 렌더러가 담당하는 양식 키 (report_template.template_key). */
    String templateKey();

    /** 렌더링 결과 xlsx 바이너리. */
    byte[] render(Report report);
}
