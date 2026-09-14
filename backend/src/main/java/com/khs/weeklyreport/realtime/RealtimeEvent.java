package com.khs.weeklyreport.realtime;

import java.time.Instant;

/**
 * 화면에 보내는 신호.
 *
 * <p>바뀐 내용을 싣지 않고 "무엇이 바뀌었다"만 알린다. 받은 쪽은 REST 로 다시 읽는다.
 * 이렇게 하면 이벤트 하나를 놓쳐도 다음 신호에 복구되고, 권한에 따라 보여줄 내용이
 * 다른 문제도 조회 시점에 자연스럽게 풀린다.
 */
public record RealtimeEvent(
        String type,
        Long projectId,
        /** 이 변경을 일으킨 사람. 자기가 만든 신호로 자기 화면을 다시 그리지 않게 한다. */
        Long actorId,
        String actorName,
        String detail,
        Instant at
) {
    public static final String BOARD_CHANGED = "board-changed";
    public static final String MEMBERS_CHANGED = "members-changed";
    public static final String INBOX_CHANGED = "inbox-changed";

    public static RealtimeEvent board(Long projectId, Long actorId, String actorName, String detail) {
        return new RealtimeEvent(BOARD_CHANGED, projectId, actorId, actorName, detail, Instant.now());
    }

    public static RealtimeEvent members(Long projectId, Long actorId, String actorName) {
        return new RealtimeEvent(MEMBERS_CHANGED, projectId, actorId, actorName, null, Instant.now());
    }

    public static RealtimeEvent inbox() {
        return new RealtimeEvent(INBOX_CHANGED, null, null, null, null, Instant.now());
    }
}
