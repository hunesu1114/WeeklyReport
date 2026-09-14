package com.khs.weeklyreport.domain;

public enum NotificationType {
    /** 프로젝트에 초대받았다. 수락/거절 버튼이 붙는다. */
    PROJECT_INVITE,
    INVITE_ACCEPTED,
    INVITE_DECLINED,
    CARD_ASSIGNED,
    MEMBER_REMOVED
}
