package com.khs.weeklyreport.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khs.weeklyreport.repository.ProjectMemberRepository;
import com.khs.weeklyreport.security.AuthenticatedUser;
import com.khs.weeklyreport.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 보드 변경을 실시간으로 알리는 WebSocket.
 *
 * <p><b>인증</b>: 브라우저 WebSocket 은 헤더를 붙일 수 없다. 토큰을 쿼리스트링에
 * 실으면 nginx 액세스 로그에 그대로 남으므로, 연결 직후 첫 메시지로 받는다.
 * 정해진 시간 안에 인증하지 않으면 끊는다.
 *
 * <pre>
 *   →  {"type":"auth","token":"..."}
 *   ←  {"type":"ready"}
 *   →  {"type":"subscribe","projectId":3}
 *   ←  {"type":"board-changed","projectId":3,...}
 * </pre>
 */
@Component
public class KanbanSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(KanbanSocketHandler.class);

    /** 이 시간 안에 인증하지 않으면 끊는다. */
    private static final long AUTH_TIMEOUT_SECONDS = 10;

    private final JwtService jwtService;
    private final ProjectMemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> byProject = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> byUser = new ConcurrentHashMap<>();

    private final ScheduledExecutorService reaper =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-auth-reaper");
                t.setDaemon(true);
                return t;
            });

    public KanbanSocketHandler(JwtService jwtService,
                               ProjectMemberRepository memberRepository,
                               ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
    }

    private static final class SessionState {
        final WebSocketSession session;
        volatile Long userId;
        volatile Long projectId;

        SessionState(WebSocketSession session) {
            this.session = session;
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), new SessionState(session));

        reaper.schedule(() -> {
            SessionState state = sessions.get(session.getId());
            if (state != null && state.userId == null) {
                close(session, CloseStatus.POLICY_VIOLATION.withReason("auth timeout"));
            }
        }, AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SessionState state = sessions.get(session.getId());
        if (state == null) return;

        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (IOException e) {
            return; // 알 수 없는 프레임은 무시한다
        }
        String type = node.path("type").asText("");

        switch (type) {
            case "auth" -> authenticate(state, node.path("token").asText(""));
            case "subscribe" -> subscribe(state, node.path("projectId").asLong(0));
            case "unsubscribe" -> unsubscribe(state);
            // 프록시가 조용한 연결을 끊지 않도록 주기적으로 오간다
            case "ping" -> send(state.session, "{\"type\":\"pong\"}");
            default -> { }
        }
    }

    private void authenticate(SessionState state, String token) {
        Optional<AuthenticatedUser> user = jwtService.parse(token);
        if (user.isEmpty()) {
            close(state.session, CloseStatus.POLICY_VIOLATION.withReason("invalid token"));
            return;
        }
        state.userId = user.get().id();
        byUser.computeIfAbsent(state.userId, k -> new CopyOnWriteArraySet<>()).add(state.session.getId());
        send(state.session, "{\"type\":\"ready\"}");
    }

    /** 멤버가 아닌 보드는 구독할 수 없다. 실시간이 권한의 뒷문이 되면 안 된다. */
    private void subscribe(SessionState state, long projectId) {
        if (state.userId == null || projectId <= 0) return;
        if (!memberRepository.existsByProjectIdAndUserId(projectId, state.userId)) {
            send(state.session, "{\"type\":\"denied\"}");
            return;
        }
        unsubscribe(state);
        state.projectId = projectId;
        byProject.computeIfAbsent(projectId, k -> new CopyOnWriteArraySet<>()).add(state.session.getId());
        send(state.session, "{\"type\":\"subscribed\",\"projectId\":" + projectId + "}");
    }

    private void unsubscribe(SessionState state) {
        Long previous = state.projectId;
        if (previous != null) {
            Set<String> ids = byProject.get(previous);
            if (ids != null) {
                ids.remove(state.session.getId());
                if (ids.isEmpty()) byProject.remove(previous);
            }
            state.projectId = null;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionState state = sessions.remove(session.getId());
        if (state == null) return;
        unsubscribe(state);
        if (state.userId != null) {
            Set<String> ids = byUser.get(state.userId);
            if (ids != null) {
                ids.remove(session.getId());
                if (ids.isEmpty()) byUser.remove(state.userId);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WebSocket 오류: {}", exception.getMessage());
        close(session, CloseStatus.SERVER_ERROR);
    }

    // ── 내보내기 ─────────────────────────────────────────────

    public void sendToProject(Long projectId, RealtimeEvent event) {
        broadcast(byProject.get(projectId), event);
    }

    public void sendToUser(Long userId, RealtimeEvent event) {
        broadcast(byUser.get(userId), event);
    }

    private void broadcast(Set<String> sessionIds, RealtimeEvent event) {
        if (sessionIds == null || sessionIds.isEmpty()) return;
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (IOException e) {
            log.warn("실시간 이벤트를 직렬화하지 못했습니다", e);
            return;
        }
        for (String id : sessionIds) {
            SessionState state = sessions.get(id);
            if (state != null) send(state.session, payload);
        }
    }

    /**
     * WebSocketSession 은 동시 전송에 안전하지 않다.
     * 한 세션에 두 스레드가 동시에 쓰면 프레임이 섞여 연결이 깨진다.
     */
    private void send(WebSocketSession session, String payload) {
        if (!session.isOpen()) return;
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException | IllegalStateException e) {
            log.debug("전송 실패, 연결을 닫습니다: {}", e.getMessage());
            close(session, CloseStatus.SERVER_ERROR);
        }
    }

    private void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {
            // 이미 끊긴 연결이다
        }
    }
}
