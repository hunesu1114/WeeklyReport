package com.khs.weeklyreport.web;

import com.khs.weeklyreport.service.MemoImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 메모 그림 내려주기. {@code <img src>} 가 부르는 경로라 로그인 없이 열려 있고,
 * 주소에 붙은 서명으로 확인한다(SecurityConfig 참고).
 *
 * <p>올리기는 메모에 딸린 일이라 {@link MemoController} 에 있다.
 */
@RestController
@RequestMapping("/api/memo-images")
public class MemoImageController {

    private final MemoImageService imageService;

    public MemoImageController(MemoImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> original(@PathVariable String id,
                                           @RequestParam("exp") long expiresAt,
                                           @RequestParam("sig") String signature) {
        return respond(imageService.read(id, false, expiresAt, signature));
    }

    @GetMapping("/{id}/thumb")
    public ResponseEntity<byte[]> thumb(@PathVariable String id,
                                        @RequestParam("exp") long expiresAt,
                                        @RequestParam("sig") String signature) {
        return respond(imageService.read(id, true, expiresAt, signature));
    }

    /*
     * 한 번 올린 그림은 바뀌지 않는다. 주소가 만료될 때까지 브라우저가 다시 묻지 않게 한다.
     * 남의 PC 공용 캐시에 남지 않도록 private 으로 둔다.
     *
     * 브라우저가 내용을 보고 형식을 짐작하지 않게(nosniff), 새 탭에서 바로 열어도
     * 스크립트가 돌 수 없게(sandbox) 막는다. 형식은 올릴 때 바이트로 확인했지만 한 겹 더 둔다.
     */
    private static ResponseEntity<byte[]> respond(MemoImageService.ImageFile file) {
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(file.maxAgeSeconds()))
                        .cachePrivate()
                        .immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .body(file.bytes());
    }
}
