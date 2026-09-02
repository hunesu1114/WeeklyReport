package com.khs.weeklyreport.excel;

import com.khs.weeklyreport.domain.ReportTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * report_template.renderer_bean 값을 실제 {@link ReportRenderer} 빈으로 연결한다.
 * DB 행 하나가 곧 배선(wiring)이 되도록 해서, 양식 추가 시 코드 변경 지점을 최소화한다.
 */
@Component
public class RendererRegistry {

    private final ApplicationContext applicationContext;

    public RendererRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public ReportRenderer resolve(ReportTemplate template) {
        Map<String, ReportRenderer> renderers = applicationContext.getBeansOfType(ReportRenderer.class);
        ReportRenderer renderer = renderers.get(template.getRendererBean());
        if (renderer == null) {
            throw new IllegalStateException(
                    "양식 [%s] 에 연결된 렌더러 빈 [%s] 을(를) 찾을 수 없습니다. 등록된 렌더러: %s"
                            .formatted(template.getTemplateKey(), template.getRendererBean(), renderers.keySet()));
        }
        return renderer;
    }
}
