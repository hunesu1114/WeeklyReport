package com.khs.weeklyreport.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;

/**
 * 메모 그림 주소에 붙이는 서명.
 *
 * <p>{@code <img src>} 는 로그인 토큰 헤더를 싣지 못한다. 그래서 그림 주소만은 로그인 없이 열고,
 * 대신 주소에 만료 시각과 서명을 붙인다. 서명은 메모를 연 사람에게만 내려가므로,
 * 주소를 가진 사람은 만료 전까지 그 그림 한 장만 볼 수 있다.
 *
 * <p>만료는 하루 단위로 맞춘다. 같은 날 다시 열면 주소가 그대로라 브라우저 캐시가 먹고,
 * 받은 주소는 적어도 하루 동안 쓸 수 있다.
 */
@Component
public class MemoImageSigner {

    private static final long DAY = 86_400;

    private final byte[] key;
    private final Clock clock;

    @Autowired
    public MemoImageSigner(JwtService jwtService) {
        this(jwtService.derivedKey("memo-image-url"), Clock.systemUTC());
    }

    public MemoImageSigner(byte[] key, Clock clock) {
        this.key = key.clone();
        this.clock = clock;
    }

    /** 이틀 뒤 0시(UTC). 지금이 몇 시든 남은 시간은 24~48시간이다. */
    public long expiry() {
        long now = clock.instant().getEpochSecond();
        return (Math.floorDiv(now, DAY) + 2) * DAY;
    }

    public String sign(String imageId, long expiresAt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] raw = mac.doFinal((imageId + ":" + expiresAt).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("그림 주소에 서명하지 못했습니다.", e);
        }
    }

    /** 만료 전이고 서명이 맞는지. 비교는 걸린 시간으로 답이 새지 않게 고정 시간으로 한다. */
    public boolean verify(String imageId, long expiresAt, String signature) {
        if (imageId == null || signature == null) return false;
        if (expiresAt <= clock.instant().getEpochSecond()) return false;
        byte[] expected = sign(imageId, expiresAt).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
    }

    /** 원본이면 {@code /api/memo-images/{id}}, 섬네일이면 뒤에 {@code /thumb}. 서명은 둘이 같다. */
    public String url(String imageId, boolean thumb, long expiresAt) {
        return "/api/memo-images/" + imageId + (thumb ? "/thumb" : "")
                + "?exp=" + expiresAt + "&sig=" + sign(imageId, expiresAt);
    }

    /** 남은 시간(초). 응답의 캐시 수명으로 쓴다. */
    public long secondsLeft(long expiresAt) {
        return Math.max(0, expiresAt - clock.instant().getEpochSecond());
    }
}
