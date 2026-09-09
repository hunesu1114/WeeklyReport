package com.khs.weeklyreport.security;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.repository.AppUserRepository;
import com.khs.weeklyreport.service.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 지금 요청을 보낸 사용자를 꺼내는 통로.
 *
 * <p>컨트롤러마다 SecurityContextHolder 를 직접 뒤지면 같은 코드가 흩어진다.
 * 서비스가 소유자 필터를 걸 때도 여기만 보면 된다.
 */
@Component
public class CurrentUser {

    private final AppUserRepository userRepository;

    public CurrentUser(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<AuthenticatedUser> principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    /** 인증이 필요한 경로에서 쓴다. 필터를 통과했다면 반드시 값이 있다. */
    public AuthenticatedUser require() {
        return principal().orElseThrow(() -> new NotFoundException("로그인 정보를 찾을 수 없습니다."));
    }

    public Long requireId() {
        return require().id();
    }

    /** 엔티티 연관을 걸어야 할 때. 토큰은 유효한데 계정이 지워졌을 수 있으므로 확인한다. */
    public AppUser requireEntity() {
        Long id = requireId();
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("계정을 찾을 수 없습니다. id=" + id));
    }
}
