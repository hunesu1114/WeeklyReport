package com.khs.weeklyreport.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class MemoImageSignerTest {

    private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    private static MemoImageSigner at(String instant) {
        return new MemoImageSigner(KEY, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    void 만료는_이틀_뒤_0시로_맞춘다() {
        long morning = at("2026-11-20T01:00:00Z").expiry();
        long night = at("2026-11-20T23:59:00Z").expiry();

        // 같은 날이면 주소가 같아 브라우저 캐시가 먹는다
        assertThat(morning).isEqualTo(night).isEqualTo(Instant.parse("2026-11-22T00:00:00Z").getEpochSecond());
    }

    @Test
    void 맞는_서명은_통한다() {
        MemoImageSigner signer = at("2026-11-20T09:00:00Z");
        long exp = signer.expiry();

        assertThat(signer.verify("k3F9aQ2xZ7", exp, signer.sign("k3F9aQ2xZ7", exp))).isTrue();
    }

    @Test
    void 다른_그림이나_다른_만료로는_통하지_않는다() {
        MemoImageSigner signer = at("2026-11-20T09:00:00Z");
        long exp = signer.expiry();
        String sig = signer.sign("k3F9aQ2xZ7", exp);

        assertThat(signer.verify("Qm81LxV0pa", exp, sig)).isFalse();
        // 만료를 늘려 오래 쓰려 해도 서명이 맞지 않는다
        assertThat(signer.verify("k3F9aQ2xZ7", exp + 86_400, sig)).isFalse();
        assertThat(signer.verify("k3F9aQ2xZ7", exp, sig.substring(1) + "A")).isFalse();
        assertThat(signer.verify("k3F9aQ2xZ7", exp, null)).isFalse();
    }

    @Test
    void 만료가_지나면_통하지_않는다() {
        long exp = at("2026-11-20T09:00:00Z").expiry();
        String sig = at("2026-11-20T09:00:00Z").sign("k3F9aQ2xZ7", exp);

        assertThat(at("2026-11-21T23:59:59Z").verify("k3F9aQ2xZ7", exp, sig)).isTrue();
        assertThat(at("2026-11-22T00:00:00Z").verify("k3F9aQ2xZ7", exp, sig)).isFalse();
    }

    @Test
    void 키가_다르면_통하지_않는다() {
        MemoImageSigner mine = at("2026-11-20T09:00:00Z");
        MemoImageSigner other = new MemoImageSigner("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.US_ASCII),
                Clock.fixed(Instant.parse("2026-11-20T09:00:00Z"), ZoneOffset.UTC));
        long exp = mine.expiry();

        assertThat(mine.verify("k3F9aQ2xZ7", exp, other.sign("k3F9aQ2xZ7", exp))).isFalse();
    }

    @Test
    void 원본과_섬네일_주소() {
        MemoImageSigner signer = at("2026-11-20T09:00:00Z");
        long exp = signer.expiry();

        assertThat(signer.url("k3F9aQ2xZ7", false, exp)).startsWith("/api/memo-images/k3F9aQ2xZ7?exp=" + exp + "&sig=");
        assertThat(signer.url("k3F9aQ2xZ7", true, exp)).startsWith("/api/memo-images/k3F9aQ2xZ7/thumb?exp=");
    }
}
