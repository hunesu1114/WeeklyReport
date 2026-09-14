package com.khs.weeklyreport.domain;

public enum InvitationStatus {
    /** 상대가 아직 답하지 않았다 */
    PENDING,
    ACCEPTED,
    DECLINED,
    /** 보낸 쪽이 거둬들였다 */
    CANCELED
}
