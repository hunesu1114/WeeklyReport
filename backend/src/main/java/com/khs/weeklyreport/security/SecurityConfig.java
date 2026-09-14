package com.khs.weeklyreport.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            // MVC 의 mvcHandlerMappingIntrospector 도 CorsConfigurationSource 를 구현한다.
            // 이름으로 집어주지 않으면 후보가 둘이라 기동에 실패한다.
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource) throws Exception {
        http
                // 토큰 기반이라 세션도 CSRF 토큰도 쓰지 않는다
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsSource))
                // 브라우저 기본 로그인 창이 뜨면 API 클라이언트가 곤란해진다
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // CORS 사전 요청은 인증 대상이 아니다
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 배포 헬스체크가 여기를 찌른다. 막으면 CI 배포가 실패한다.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()

                        // 로그인/가입은 당연히 열려 있어야 한다.
                        // /api/auth/setup-state 는 '가입한 사람이 있는지'만 알려준다.
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/setup-state")
                        .permitAll()

                        // WebSocket 핸드셰이크. 누구인지는 연결 직후 첫 메시지로 확인하고,
                        // 인증에 실패하거나 시간을 넘기면 핸들러가 연결을 끊는다.
                        .requestMatchers("/ws/**").permitAll()

                        .requestMatchers("/api/**").authenticated()

                        // SPA 정적 파일은 앞단 nginx 가 준다. 여기까지 오는 것은 없다.
                        .anyRequest().permitAll())

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                write(response, HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요합니다."))
                        .accessDeniedHandler((request, response, e) ->
                                write(response, HttpServletResponse.SC_FORBIDDEN, "권한이 없습니다.")))

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 화면이 그대로 띄울 수 있도록 에러도 API 와 같은 모양으로 내려준다. */
    private void write(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("message", message);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
