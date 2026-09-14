package com.khs.weeklyreport.domain;

/** 프로젝트 안에서의 역할. 앱 전역 역할(UserRole)과는 별개다. */
public enum ProjectRole {
    /** 멤버 관리, 보드 설정, 삭제까지 */
    OWNER,
    /** 카드를 만들고 고치고 옮긴다 */
    MEMBER,
    /** 보기만 한다 */
    VIEWER;

    public boolean canWrite() {
        return this != VIEWER;
    }

    public boolean isOwner() {
        return this == OWNER;
    }
}
