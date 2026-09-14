package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.UserAvatar;
import com.khs.weeklyreport.repository.AppUserRepository;
import com.khs.weeklyreport.repository.UserAvatarRepository;
import com.khs.weeklyreport.security.CurrentUser;
import com.khs.weeklyreport.web.dto.AuthDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceTest {

    private AppUser me;
    private UserAvatarRepository avatarRepository;
    private ProfileService service;

    @BeforeEach
    void setUp() {
        me = new AppUser();
        me.setId(1L);
        me.setUsername("kim");
        me.setDisplayName("김현수");

        AppUserRepository repository = mock(AppUserRepository.class);
        when(repository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));
        when(repository.findById(1L)).thenReturn(Optional.of(me));

        avatarRepository = mock(UserAvatarRepository.class);
        when(avatarRepository.save(any(UserAvatar.class))).thenAnswer(call -> call.getArgument(0));
        when(avatarRepository.findById(any())).thenReturn(Optional.empty());

        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.requireEntity()).thenReturn(me);

        service = new ProfileService(repository, avatarRepository, currentUser);
    }

    @Test
    void 닉네임은_앞뒤_공백을_떼고_저장한다() {
        AuthDtos.UserView view = service.updateProfile(new AuthDtos.ProfileRequest("  현수  "));

        assertThat(view.displayName()).isEqualTo("현수");
    }

    @Test
    void 사진을_올리면_버전이_올라간다() {
        assertThat(me.getAvatarVersion()).isZero();

        service.uploadAvatar(png("one"));
        assertThat(me.getAvatarVersion()).isEqualTo(1);

        // 주소가 그대로이므로 버전이 오르지 않으면 브라우저가 옛 사진을 계속 보여준다
        service.uploadAvatar(png("two"));
        assertThat(me.getAvatarVersion()).isEqualTo(2);
        assertThat(me.hasAvatar()).isTrue();
    }

    @Test
    void 사진을_지워도_버전은_오른다() {
        service.uploadAvatar(png("one"));
        AuthDtos.UserView view = service.deleteAvatar();

        assertThat(view.hasAvatar()).isFalse();
        assertThat(me.getAvatarVersion()).isEqualTo(2);
    }

    @Test
    void 이미지가_아니면_받지_않는다() {
        MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", "x".getBytes());

        assertThatThrownBy(() -> service.uploadAvatar(pdf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미지만");
    }

    @Test
    void SVG_는_스크립트를_품을_수_있어_막는다() {
        MockMultipartFile svg = new MockMultipartFile(
                "file", "a.svg", "image/svg+xml", "<svg/>".getBytes());

        assertThatThrownBy(() -> service.uploadAvatar(svg))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 너무_큰_이미지는_막는다() {
        MockMultipartFile huge = new MockMultipartFile(
                "file", "big.png", "image/png", new byte[600 * 1024]);

        assertThatThrownBy(() -> service.uploadAvatar(huge))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("512KB");
    }

    @Test
    void 사진이_없는_사람의_아바타는_없는_것으로_답한다() {
        assertThatThrownBy(() -> service.avatarOf(1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 사진_바이트는_옆_테이블에_들어간다() {
        // app_user 에 같이 두면 인증 필터가 요청마다 사진까지 끌고 온다
        service.uploadAvatar(png("one"));

        verify(avatarRepository).save(any(UserAvatar.class));
        assertThat(me.getAvatarType()).isEqualTo("image/png");
    }

    private static MockMultipartFile png(String content) {
        return new MockMultipartFile("file", "a.png", "image/png", content.getBytes());
    }
}
