package com.khs.weeklyreport.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final KanbanSocketHandler handler;

    public WebSocketConfig(KanbanSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 인증은 연결 직후 첫 메시지로 받는다(KanbanSocketHandler 참고).
        // 핸드셰이크 단계에서는 아직 누구인지 모르므로 오리진만 열어둔다.
        registry.addHandler(handler, "/ws/kanban").setAllowedOriginPatterns("*");
    }
}
