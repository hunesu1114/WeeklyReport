package com.khs.weeklyreport.realtime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 변경 신호를 보낸다.
 *
 * <p>바로 쏘지 않고 스프링 이벤트로 넘긴다. 수신자는 커밋 후에 실제로 내보낸다
 * ({@link RealtimeDispatcher}). 트랜잭션 안에서 곧바로 보내면, 롤백된 변경을
 * 남의 화면이 읽으러 오는 일이 생긴다.
 */
@Component
public class RealtimePublisher {

    private final ApplicationEventPublisher events;

    public RealtimePublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    /** 그 보드를 보고 있는 사람들에게. */
    public void toProject(Long projectId, RealtimeEvent event) {
        events.publishEvent(new Envelope(Target.PROJECT, projectId, event));
    }

    /** 특정 사용자에게. 알림함 갱신 등. */
    public void toUser(Long userId, RealtimeEvent event) {
        events.publishEvent(new Envelope(Target.USER, userId, event));
    }

    enum Target { PROJECT, USER }

    record Envelope(Target target, Long id, RealtimeEvent event) {
    }
}
