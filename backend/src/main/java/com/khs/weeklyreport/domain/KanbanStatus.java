package com.khs.weeklyreport.domain;

/** 칸반 보드의 칸. 순서가 곧 화면의 좌→우 순서다. */
public enum KanbanStatus {
    /** 언젠가 할 일 */
    BACKLOG,
    /** 이번에 할 일 */
    TODO,
    /** 진행 중 */
    ING,
    /** 완료 */
    DONE
}
