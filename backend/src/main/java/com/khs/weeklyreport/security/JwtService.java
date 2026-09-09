package com.khs.weeklyreport.security;

import com.khs.weeklyreport.domain.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/** 로그인 토큰 발급과 검증. 대칭키(HS256)를 쓴다. */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_NAME = "name";

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.ttl:12h}") Duration ttl) {
        this.ttl = ttl;
        this.key = resolveKey(secret);
    }

    /**
     * 비밀키가 없으면 기동할 때 임의로 하나 만든다.
     *
     * <p>이렇게 하면 설정을 깜빡해도 앱이 뜨긴 하지만, 재시작할 때마다 키가 바뀌어
     * 로그인한 사람이 전부 튕긴다. 운영에서는 반드시 값을 넣어야 하므로 크게 경고한다.
     */
    private SecretKey resolveKey(String secret) {
        if (secret != null && !secret.isBlank()) {
            byte[] bytes = decode(secret);
            if (bytes.length < 32) {
                throw new IllegalStateException(
                        "app.jwt.secret 이 너무 짧습니다. HS256 은 32바이트 이상이 필요합니다. "
                                + "openssl rand -base64 32 로 만드세요.");
            }
            return Keys.hmacShaKeyFor(bytes);
        }

        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        log.warn("""

                ============================================================
                 APP_JWT_SECRET 이 설정되지 않아 임시 키로 기동합니다.
                 재시작하면 키가 바뀌어 로그인한 사용자가 모두 로그아웃됩니다.
                 운영에서는 반드시 아래처럼 만들어 넣으세요.

                   openssl rand -base64 32

                 서버: ~/weekly-report/.env.secrets 에 APP_JWT_SECRET=... 추가
                ============================================================
                """);
        return Keys.hmacShaKeyFor(random);
    }

    /** base64 로 보이면 디코드하고, 아니면 평문 바이트를 그대로 쓴다. */
    private static byte[] decode(String secret) {
        try {
            return Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_NAME, user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** 서명과 만료를 확인하고 사용자 정보를 꺼낸다. 문제가 있으면 비어 있는 값을 준다. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_NAME, String.class),
                    claims.get(CLAIM_ROLE, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            // 위조/만료된 토큰은 흔한 일이다. 스택트레이스까지 남길 필요는 없다.
            log.debug("사용할 수 없는 토큰: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }
}
