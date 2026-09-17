package com.khs.weeklyreport.web;

import com.khs.weeklyreport.service.AuthService;
import com.khs.weeklyreport.web.dto.AuthDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 로그인 화면이 '첫 계정 만들기'를 보여줄지 판단한다. 공개 경로. */
    @GetMapping("/setup-state")
    public AuthDtos.SetupState setupState() {
        return authService.setupState();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.TokenResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    /** 세션 연장. 아직 살아 있는 토큰으로만 부를 수 있다. */
    @PostMapping("/refresh")
    public AuthDtos.TokenResponse refresh() {
        return authService.refresh();
    }

    @GetMapping("/me")
    public AuthDtos.UserView me() {
        return authService.me();
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        authService.changePassword(request);
    }

    /** 아직 주인이 없는 데이터가 얼마나 남아 있는지. */
    @GetMapping("/orphans")
    public AuthDtos.OrphanSummary orphans() {
        return authService.orphans();
    }

    /**
     * 주인 없는 데이터를 지금 로그인한 계정으로 가져온다.
     * 가장 먼저 가입한 관리자 계정만 할 수 있고, 여러 번 실행해도 안전하다.
     */
    @PostMapping("/orphans/claim")
    public AuthDtos.ClaimResult claimOrphans() {
        return authService.claimOrphans();
    }
}
