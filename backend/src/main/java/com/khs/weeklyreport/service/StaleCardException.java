package com.khs.weeklyreport.service;

import com.khs.weeklyreport.web.dto.KanbanDtos;

/**
 * 내가 화면에 띄워둔 사이에 남이 먼저 고친 카드를 저장하려 했다.
 *
 * <p>그냥 덮어쓰면 앞사람이 쓴 내용이 알림도 없이 사라진다. 409 로 돌려보내면서
 * <b>서버의 현재 값</b>을 함께 준다. 화면은 그걸로 "내 내용 유지 / 새로 받기"를
 * 고르게 한다.
 */
public class StaleCardException extends RuntimeException {

    private final transient KanbanDtos.CardView current;

    public StaleCardException(String message, KanbanDtos.CardView current) {
        super(message);
        this.current = current;
    }

    public KanbanDtos.CardView current() {
        return current;
    }
}
