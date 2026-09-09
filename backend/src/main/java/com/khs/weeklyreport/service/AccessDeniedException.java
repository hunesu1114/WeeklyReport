package com.khs.weeklyreport.service;

/** 로그인은 되어 있으나 그 일을 할 권한이 없을 때. HTTP 403 으로 나간다. */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
