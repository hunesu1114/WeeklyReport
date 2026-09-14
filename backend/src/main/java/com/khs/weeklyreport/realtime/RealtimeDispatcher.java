package com.khs.weeklyreport.realtime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋이 끝난 뒤에만 신호를 내보낸다.
 *
 * <p>트랜잭션 도중에 보내면 받은 쪽이 곧바로 조회하러 오는데, 그 시점에는 아직
 * 커밋 전이라 예전 값을 읽는다. 롤백되면 있지도 않은 변경을 알린 꼴이 된다.
 */
@Component
public class RealtimeDispatcher {

    private final KanbanSocketHandler socket;

    public RealtimeDispatcher(KanbanSocketHandler socket) {
        this.socket = socket;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(RealtimePublisher.Envelope envelope) {
        switch (envelope.target()) {
            case PROJECT -> socket.sendToProject(envelope.id(), envelope.event());
            case USER -> socket.sendToUser(envelope.id(), envelope.event());
        }
    }
}
