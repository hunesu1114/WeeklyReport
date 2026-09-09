package com.khs.weeklyreport.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class WebConfig {

    /** 개발 중 Vite dev server 에서 바로 호출할 수 있도록. 운영에서는 nginx 가 같은 오리진으로 프록시한다. */
    @Value("${app.cors.allowed-origins:http://localhost:5174}")
    private String[] allowedOrigins;

    /**
     * WebMvcConfigurer#addCorsMappings 가 아니라 빈으로 노출한다.
     * Spring Security 의 cors() 는 CorsConfigurationSource 빈을 찾기 때문에,
     * MVC 쪽에만 설정해두면 보안 필터 앞단에서 사전 요청이 막힌다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // 다운로드 파일명과 토큰 재발급 헤더를 브라우저가 읽을 수 있어야 한다
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
