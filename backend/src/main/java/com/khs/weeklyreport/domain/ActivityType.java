package com.khs.weeklyreport.domain;

/** 활동 기록의 종류. 화면 문구는 프런트엔드가 붙인다. */
public enum ActivityType {
    PROJECT_CREATED,
    PROJECT_UPDATED,

    MEMBER_INVITED,
    MEMBER_JOINED,
    MEMBER_LEFT,
    MEMBER_REMOVED,
    MEMBER_ROLE_CHANGED,

    CARD_CREATED,
    CARD_UPDATED,
    CARD_MOVED,
    CARD_ASSIGNED,
    CARD_DELETED
}
