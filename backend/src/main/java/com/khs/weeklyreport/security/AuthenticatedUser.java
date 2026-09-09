package com.khs.weeklyreport.security;

/**
 * 토큰에서 꺼낸 사용자. SecurityContext 의 principal 로 들어간다.
 * DB 를 다시 읽지 않아도 되는 최소한의 정보만 담는다.
 */
public record AuthenticatedUser(Long id, String username, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
