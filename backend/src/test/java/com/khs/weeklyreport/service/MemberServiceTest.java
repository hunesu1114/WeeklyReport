package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.InvitationStatus;
import com.khs.weeklyreport.domain.Project;
import com.khs.weeklyreport.domain.ProjectMember;
import com.khs.weeklyreport.domain.ProjectRole;
import com.khs.weeklyreport.realtime.RealtimePublisher;
import com.khs.weeklyreport.repository.AppUserRepository;
import com.khs.weeklyreport.repository.ProjectInvitationRepository;
import com.khs.weeklyreport.repository.ProjectMemberRepository;
import com.khs.weeklyreport.security.CurrentUser;
import com.khs.weeklyreport.web.dto.TeamDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberServiceTest {

    private static final Long PROJECT_ID = 3L;
    private static final Long ME = 1L;
    private static final Long OTHER = 2L;

    private ProjectMemberRepository memberRepository;
    private ProjectInvitationRepository invitationRepository;
    private AppUserRepository userRepository;
    private ProjectAccess access;
    private MemberService service;

    private Project project;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setName("결제 개편");

        memberRepository = mock(ProjectMemberRepository.class);
        invitationRepository = mock(ProjectInvitationRepository.class);
        userRepository = mock(AppUserRepository.class);
        access = mock(ProjectAccess.class);

        when(access.requireRead(anyLong())).thenReturn(project);
        when(access.requireOwner(anyLong())).thenReturn(project);
        when(memberRepository.save(any(ProjectMember.class))).thenAnswer(c -> c.getArgument(0));

        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.requireId()).thenReturn(ME);
        when(currentUser.requireEntity()).thenReturn(user(ME, "나"));

        service = new MemberService(memberRepository, invitationRepository, userRepository,
                access, mock(NotificationService.class), mock(ActivityService.class),
                currentUser, mock(RealtimePublisher.class));
    }

    @Test
    void 마지막_관리자는_나갈_수_없다() {
        givenMember(ME, ProjectRole.OWNER);
        when(memberRepository.countByProjectIdAndRole(PROJECT_ID, ProjectRole.OWNER)).thenReturn(1L);

        // 나가버리면 아무도 참여자를 관리할 수 없는 보드가 남는다
        assertThatThrownBy(() -> service.remove(PROJECT_ID, ME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("마지막 관리자");
        verify(memberRepository, never()).delete(any());
    }

    @Test
    void 관리자가_둘이면_나갈_수_있다() {
        givenMember(ME, ProjectRole.OWNER);
        when(memberRepository.countByProjectIdAndRole(PROJECT_ID, ProjectRole.OWNER)).thenReturn(2L);

        service.remove(PROJECT_ID, ME);

        verify(memberRepository).delete(any(ProjectMember.class));
    }

    @Test
    void 마지막_관리자는_강등할_수_없다() {
        givenMember(OTHER, ProjectRole.OWNER);
        when(memberRepository.countByProjectIdAndRole(PROJECT_ID, ProjectRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(PROJECT_ID, OTHER, ProjectRole.MEMBER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 관리자를_관리자로_두는_것은_언제나_된다() {
        givenMember(OTHER, ProjectRole.OWNER);
        when(memberRepository.countByProjectIdAndRole(PROJECT_ID, ProjectRole.OWNER)).thenReturn(1L);

        TeamDtos.MemberView view = service.changeRole(PROJECT_ID, OTHER, ProjectRole.OWNER);

        assertThat(view.role()).isEqualTo(ProjectRole.OWNER);
    }

    @Test
    void 이미_참여_중인_사람은_다시_초대할_수_없다() {
        AppUser invitee = user(OTHER, "박보람");
        when(userRepository.findById(OTHER)).thenReturn(Optional.of(invitee));
        when(memberRepository.existsByProjectIdAndUserId(PROJECT_ID, OTHER)).thenReturn(true);

        assertThatThrownBy(() -> service.invite(PROJECT_ID, new TeamDtos.InviteRequest(OTHER, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 참여 중");
    }

    @Test
    void 답을_기다리는_초대가_있으면_또_보내지_않는다() {
        AppUser invitee = user(OTHER, "박보람");
        when(userRepository.findById(OTHER)).thenReturn(Optional.of(invitee));
        when(memberRepository.existsByProjectIdAndUserId(PROJECT_ID, OTHER)).thenReturn(false);
        when(invitationRepository.findByProjectIdAndInviteeIdAndStatus(
                PROJECT_ID, OTHER, InvitationStatus.PENDING))
                .thenReturn(Optional.of(new com.khs.weeklyreport.domain.ProjectInvitation()));

        assertThatThrownBy(() -> service.invite(PROJECT_ID, new TeamDtos.InviteRequest(OTHER, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 보낸 초대");
    }

    @Test
    void 참여자가_아닌_사람은_내보낼_수_없다() {
        when(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OTHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(PROJECT_ID, OTHER))
                .isInstanceOf(NotFoundException.class);
    }

    private void givenMember(Long userId, ProjectRole role) {
        ProjectMember member = ProjectMember.of(project, user(userId, "사람" + userId), role);
        when(memberRepository.findByProjectIdAndUserId(PROJECT_ID, userId))
                .thenReturn(Optional.of(member));
    }

    private static AppUser user(Long id, String displayName) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername("u" + id);
        user.setDisplayName(displayName);
        return user;
    }
}
