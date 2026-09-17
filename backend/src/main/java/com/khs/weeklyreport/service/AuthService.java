package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.Project;
import com.khs.weeklyreport.domain.ProjectMember;
import com.khs.weeklyreport.domain.ProjectRole;
import com.khs.weeklyreport.domain.UserRole;
import com.khs.weeklyreport.repository.AppUserRepository;
import com.khs.weeklyreport.repository.ProjectMemberRepository;
import com.khs.weeklyreport.repository.ProjectRepository;
import com.khs.weeklyreport.repository.ReportRepository;
import com.khs.weeklyreport.security.CurrentUser;
import com.khs.weeklyreport.security.JwtService;
import com.khs.weeklyreport.web.dto.AuthDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository userRepository;
    private final ReportRepository reportRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUser currentUser;

    public AuthService(AppUserRepository userRepository,
                       ReportRepository reportRepository,
                       ProjectRepository projectRepository,
                       ProjectMemberRepository memberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.reportRepository = reportRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public AuthDtos.SetupState setupState() {
        return new AuthDtos.SetupState(userRepository.count() > 0);
    }

    /** 가장 먼저 가입한 계정이 ADMIN 이 된다. 그 계정만 주인 없는 데이터를 가져올 수 있다. */
    @Transactional
    public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("이미 쓰고 있는 아이디입니다: " + username);
        }

        boolean first = userRepository.count() == 0;

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(
                request.displayName() == null || request.displayName().isBlank()
                        ? username
                        : request.displayName().trim());
        user.setRole(first ? UserRole.ADMIN : UserRole.USER);
        user.setEnabled(true);

        AppUser saved = userRepository.save(user);
        log.info("계정 생성: {} (role={})", saved.getUsername(), saved.getRole());
        return token(saved);
    }

    @Transactional(readOnly = true)
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(AuthService::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("사용할 수 없는 계정입니다. 관리자에게 문의하세요.");
        }
        return token(user);
    }

    /**
     * 토큰을 새로 끊어 세션을 연장한다.
     *
     * <p>필터를 통과했다는 것은 아직 살아 있는 토큰이라는 뜻이므로, 여기서는
     * 계정이 여전히 쓸 수 있는지만 본다.
     *
     * <p>이건 <b>사람이 버튼을 눌러야</b> 도는 길이다. 자동으로 갱신하면 열어만 둔
     * 탭이 영원히 로그인 상태로 남는다. 자리를 비운 세션은 그대로 만료되어야 한다.
     */
    @Transactional(readOnly = true)
    public AuthDtos.TokenResponse refresh() {
        AppUser user = currentUser.requireEntity();
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("사용할 수 없는 계정입니다. 관리자에게 문의하세요.");
        }
        return token(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserView me() {
        return AuthDtos.UserView.of(currentUser.requireEntity());
    }

    @Transactional
    public void changePassword(AuthDtos.ChangePasswordRequest request) {
        AppUser user = currentUser.requireEntity();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 맞지 않습니다.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    // ── 주인 없는 데이터 가져오기 ────────────────────────────

    @Transactional(readOnly = true)
    public AuthDtos.OrphanSummary orphans() {
        return new AuthDtos.OrphanSummary(
                reportRepository.countOrphans(),
                projectRepository.countOrphans(),
                projectRepository.countOrphanCards());
    }

    /**
     * 로그인 도입 이전에 쌓인, 주인이 없는 데이터를 지금 로그인한 계정으로 옮긴다.
     *
     * <p>ADMIN(= 가장 먼저 가입한 계정)만 할 수 있다. 아무나 가져갈 수 있으면
     * 나중에 가입한 사람이 남의 보고서를 통째로 들고 갈 수 있다.
     *
     * <p>여러 번 실행해도 안전하다. 두 번째부터는 옮길 것이 없어 0 건이 된다.
     * 칸반 카드는 프로젝트에 매달려 있어 프로젝트를 옮기면 함께 따라온다.
     *
     * <p>보드는 소유자가 아니라 <b>참여자 명단</b>으로 보인다. owner 만 바꾸고
     * 명단에 넣지 않으면, 가져오기는 성공했는데 칸반 화면에는 아무것도 없는
     * 상태가 된다. 그래서 명단 추가까지 한 트랜잭션에서 끝낸다.
     */
    @Transactional
    public AuthDtos.ClaimResult claimOrphans() {
        AppUser user = currentUser.requireEntity();
        if (!user.isAdmin()) {
            throw new AccessDeniedException(
                    "주인 없는 데이터는 가장 먼저 가입한 관리자 계정만 가져올 수 있습니다.");
        }
        Long userId = user.getId();
        String username = user.getUsername();

        // 벌크 update 가 끝나면 '주인 없음' 조건에 걸리는 보드가 사라지므로 먼저 집어둔다
        List<Long> orphanProjectIds = projectRepository.findOrphans().stream()
                .map(Project::getId)
                .toList();

        long cards = projectRepository.countOrphanCards();
        int reports = reportRepository.claimOrphans(user);
        int projects = projectRepository.claimOrphans(user);

        // 벌크 update 가 영속성 컨텍스트를 비우므로 앞서 읽은 엔티티는 떨어져 나갔다
        AppUser owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("계정을 찾을 수 없습니다. id=" + userId));
        for (Long projectId : orphanProjectIds) {
            if (memberRepository.existsByProjectIdAndUserId(projectId, userId)) continue;
            projectRepository.findById(projectId).ifPresent(project ->
                    memberRepository.save(ProjectMember.of(project, owner, ProjectRole.OWNER)));
        }

        log.info("주인 없는 데이터 귀속: user={} reports={} projects={} cards={}",
                username, reports, projects, cards);

        String message = (reports == 0 && projects == 0)
                ? "가져올 데이터가 없습니다. 이미 모두 귀속되어 있습니다."
                : "보고서 %d건, 프로젝트 %d개(카드 %d장)를 '%s' 계정으로 가져왔습니다."
                        .formatted(reports, projects, cards, username);

        return new AuthDtos.ClaimResult(reports, projects, cards, message);
    }

    private AuthDtos.TokenResponse token(AppUser user) {
        return new AuthDtos.TokenResponse(
                jwtService.issue(user), jwtService.ttlSeconds(), AuthDtos.UserView.of(user));
    }

    /** 아이디가 없는 것과 비밀번호가 틀린 것을 구분해주지 않는다. */
    private static IllegalArgumentException invalidCredentials() {
        return new IllegalArgumentException("아이디 또는 비밀번호가 맞지 않습니다.");
    }
}
