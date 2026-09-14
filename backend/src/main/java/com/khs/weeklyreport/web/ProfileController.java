package com.khs.weeklyreport.web;

import com.khs.weeklyreport.service.ProfileService;
import com.khs.weeklyreport.web.dto.AuthDtos;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/** 마이페이지. 비밀번호는 {@link AuthController} 쪽에 이미 있다. */
@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PutMapping("/profile")
    public AuthDtos.UserView updateProfile(@Valid @RequestBody AuthDtos.ProfileRequest request) {
        return profileService.updateProfile(request);
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AuthDtos.UserView uploadAvatar(@RequestPart("file") MultipartFile file) {
        return profileService.uploadAvatar(file);
    }

    @DeleteMapping("/profile/avatar")
    public AuthDtos.UserView deleteAvatar() {
        return profileService.deleteAvatar();
    }

    /**
     * 프로필 사진. 주소가 바뀌지 않으므로 화면은 {@code ?v=avatarVersion} 을 붙여 부른다.
     * 그래서 오래 캐시해도 안전하다 — 사진이 바뀌면 버전이 올라 주소가 달라진다.
     */
    @GetMapping("/users/{userId}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable Long userId) {
        ProfileService.Avatar avatar = profileService.avatarOf(userId);
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate())
                .eTag("\"%d-%d\"".formatted(userId, avatar.version()))
                .body(avatar.bytes());
    }
}
