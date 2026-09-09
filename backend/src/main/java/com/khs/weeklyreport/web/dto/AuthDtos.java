package com.khs.weeklyreport.web.dto;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank
            @Size(min = 3, max = 50)
            // 아이디는 URL·로그에 그대로 실리므로 문자 범위를 좁게 잡는다
            @Pattern(regexp = "[A-Za-z0-9._-]+",
                    message = "아이디는 영문, 숫자, . _ - 만 쓸 수 있습니다.")
            String username,

            @NotBlank @Size(min = 8, max = 100) String password,

            @Size(max = 50) String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    /** 로그인/가입 성공 응답. 토큰은 본문으로만 내려간다. */
    public record TokenResponse(
            String token,
            long expiresInSeconds,
            UserView user
    ) {
    }

    public record UserView(
            Long id,
            String username,
            String displayName,
            UserRole role,
            Instant createdAt
    ) {
        public static UserView of(AppUser user) {
            return new UserView(user.getId(), user.getUsername(), user.getDisplayName(),
                    user.getRole(), user.getCreatedAt());
        }
    }

    /** 로그인 화면이 '첫 계정 만들기'를 보여줄지 판단하는 데 쓴다. */
    public record SetupState(boolean hasAnyUser) {
    }

    /** 주인 없는 데이터가 얼마나 남아 있는지. */
    public record OrphanSummary(
            long reports,
            long projects,
            long cards
    ) {
        public boolean isEmpty() {
            return reports == 0 && projects == 0 && cards == 0;
        }
    }

    /** 가져오기 결과. 실제로 옮겨간 건수. */
    public record ClaimResult(
            long reports,
            long projects,
            long cards,
            String message
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword
    ) {
    }
}
