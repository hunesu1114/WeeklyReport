package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.UserAvatar;
import com.khs.weeklyreport.repository.AppUserRepository;
import com.khs.weeklyreport.repository.UserAvatarRepository;
import com.khs.weeklyreport.security.CurrentUser;
import com.khs.weeklyreport.web.dto.AuthDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/** 마이페이지: 표시 이름과 프로필 사진. */
@Service
public class ProfileService {

    /**
     * 사진 크기 상한. 화면에서 256px 정사각형으로 줄여 올리므로 보통 30~60KB 다.
     * 상한을 두는 이유는 용량보다도, 원본을 그대로 올리는 경로를 막기 위해서다.
     */
    private static final long MAX_AVATAR_BYTES = 512 * 1024;

    /** 브라우저가 &lt;img&gt; 로 바로 그릴 수 있는 형식만 받는다. SVG 는 스크립트를 품을 수 있어 뺀다. */
    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final AppUserRepository userRepository;
    private final UserAvatarRepository avatarRepository;
    private final CurrentUser currentUser;

    public ProfileService(AppUserRepository userRepository,
                          UserAvatarRepository avatarRepository,
                          CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.avatarRepository = avatarRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public AuthDtos.UserView updateProfile(AuthDtos.ProfileRequest request) {
        AppUser user = currentUser.requireEntity();
        user.setDisplayName(request.displayName().trim());
        return AuthDtos.UserView.of(userRepository.save(user));
    }

    @Transactional
    public AuthDtos.UserView uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("올릴 이미지를 선택하세요.");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("이미지가 너무 큽니다. 512KB 이하로 올려주세요.");
        }

        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new IllegalArgumentException("PNG, JPG, WEBP, GIF 이미지만 올릴 수 있습니다.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지를 읽지 못했습니다. 다시 시도해 주세요.");
        }

        AppUser user = currentUser.requireEntity();
        avatarRepository.save(UserAvatar.of(user.getId(), bytes));

        user.setAvatarType(type);
        // 주소는 그대로이므로 버전을 올려 브라우저 캐시를 끊는다
        user.setAvatarVersion(user.getAvatarVersion() + 1);
        return AuthDtos.UserView.of(userRepository.save(user));
    }

    @Transactional
    public AuthDtos.UserView deleteAvatar() {
        AppUser user = currentUser.requireEntity();
        avatarRepository.deleteById(user.getId());

        user.setAvatarType(null);
        user.setAvatarVersion(user.getAvatarVersion() + 1);
        return AuthDtos.UserView.of(userRepository.save(user));
    }

    /**
     * 남의 사진도 내려준다 — 참여자 목록과 카드 담당자 표시에 필요하다.
     * 사진 말고 다른 정보는 실리지 않으므로 로그인만 되어 있으면 충분하다.
     */
    @Transactional(readOnly = true)
    public Avatar avatarOf(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다. id=" + userId));

        UserAvatar avatar = avatarRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("프로필 사진이 없습니다."));

        String type = user.getAvatarType() == null ? "image/png" : user.getAvatarType();
        return new Avatar(avatar.getBytes(), type, user.getAvatarVersion());
    }

    public record Avatar(byte[] bytes, String contentType, long version) {
    }
}
