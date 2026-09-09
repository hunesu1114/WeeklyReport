package com.khs.weeklyreport.security;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final JwtService jwt = new JwtService(SECRET, Duration.ofHours(12));

    @Test
    void 발급한_토큰에서_사용자를_되읽는다() {
        AuthenticatedUser parsed = jwt.parse(jwt.issue(user(7L, "hskim", UserRole.ADMIN))).orElseThrow();

        assertThat(parsed.id()).isEqualTo(7L);
        assertThat(parsed.username()).isEqualTo("hskim");
        assertThat(parsed.isAdmin()).isTrue();
    }

    @Test
    void 일반_사용자는_관리자가_아니다() {
        AuthenticatedUser parsed = jwt.parse(jwt.issue(user(2L, "chulsoo", UserRole.USER))).orElseThrow();

        assertThat(parsed.isAdmin()).isFalse();
    }

    @Test
    void 다른_키로_서명된_토큰은_받지_않는다() {
        String other = Base64.getEncoder()
                .encodeToString("ffffffffffffffffffffffffffffffff".getBytes());
        String forged = new JwtService(other, Duration.ofHours(12)).issue(user(1L, "attacker", UserRole.ADMIN));

        assertThat(jwt.parse(forged)).isEmpty();
    }

    @Test
    void 만료된_토큰은_받지_않는다() {
        JwtService expired = new JwtService(SECRET, Duration.ofSeconds(-1));

        assertThat(jwt.parse(expired.issue(user(1L, "hskim", UserRole.USER)))).isEmpty();
    }

    @Test
    void 형식이_아닌_문자열도_조용히_거절한다() {
        assertThat(jwt.parse("그냥 문자열")).isEmpty();
        assertThat(jwt.parse("")).isEmpty();
    }

    @Test
    void 너무_짧은_비밀키는_기동을_막는다() {
        // HS256 은 32바이트 이상이 필요하다. 짧은 키를 조용히 늘려 쓰면 안 된다.
        assertThatThrownBy(() -> new JwtService("short", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }

    private AppUser user(Long id, String username, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setRole(role);
        return user;
    }
}
