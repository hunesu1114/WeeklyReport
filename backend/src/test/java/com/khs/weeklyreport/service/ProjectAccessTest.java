package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.Project;
import com.khs.weeklyreport.domain.ProjectMember;
import com.khs.weeklyreport.domain.ProjectRole;
import com.khs.weeklyreport.repository.ProjectMemberRepository;
import com.khs.weeklyreport.repository.ProjectRepository;
import com.khs.weeklyreport.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 권한 판정의 핵심은 <b>못 보는 보드는 404, 볼 수는 있으나 못 고치면 403</b> 이다.
 * 이 구분이 무너지면 id 를 하나씩 찍어보는 것만으로 남의 보드 존재를 알 수 있다.
 */
class ProjectAccessTest {

    private static final Long ME = 7L;
    private static final Long PROJECT_ID = 3L;

    private ProjectRepository projectRepository;
    private ProjectMemberRepository memberRepository;
    private ProjectAccess access;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        memberRepository = mock(ProjectMemberRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.requireId()).thenReturn(ME);

        access = new ProjectAccess(projectRepository, memberRepository, currentUser);
    }

    @Test
    void 참여자가_아니면_보드가_없는_것으로_답한다() {
        givenMembership(null);

        assertThatThrownBy(() -> access.requireRead(PROJECT_ID))
                .isInstanceOf(NotFoundException.class);
        // 403 이 아니어야 한다. 403 은 "여기 뭔가 있다"는 뜻이 된다.
        assertThatThrownBy(() -> access.requireWrite(PROJECT_ID))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> access.requireOwner(PROJECT_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 읽기전용_참여자는_볼_수_있고_고칠_수_없다() {
        givenMembership(ProjectRole.VIEWER);

        assertThat(access.requireRead(PROJECT_ID)).isNotNull();
        assertThatThrownBy(() -> access.requireWrite(PROJECT_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 멤버는_고칠_수_있지만_참여자_관리는_못_한다() {
        givenMembership(ProjectRole.MEMBER);

        assertThat(access.requireWrite(PROJECT_ID)).isNotNull();
        assertThatThrownBy(() -> access.requireOwner(PROJECT_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 관리자는_모두_할_수_있다() {
        givenMembership(ProjectRole.OWNER);

        assertThat(access.requireRead(PROJECT_ID)).isNotNull();
        assertThat(access.requireWrite(PROJECT_ID)).isNotNull();
        assertThat(access.requireOwner(PROJECT_ID)).isNotNull();
        assertThat(access.myRole(PROJECT_ID)).isEqualTo(ProjectRole.OWNER);
    }

    @Test
    void 남의_보드는_잠글_수도_없다() {
        givenMembership(null);

        // 권한 확인이 잠금보다 먼저다. 잠글 수 있으면 그 자체로 남의 보드를 붙잡아 둘 수 있다.
        assertThatThrownBy(() -> access.lockForReorder(PROJECT_ID))
                .isInstanceOf(NotFoundException.class);
    }

    private void givenMembership(ProjectRole role) {
        Project project = new Project();
        project.setName("보드");

        AppUser me = new AppUser();
        me.setUsername("me");
        me.setDisplayName("나");

        when(memberRepository.findByProjectIdAndUserId(PROJECT_ID, ME))
                .thenReturn(role == null
                        ? Optional.empty()
                        : Optional.of(ProjectMember.of(project, me, role)));
        when(projectRepository.findById(any())).thenReturn(Optional.of(project));
        when(projectRepository.findByIdForUpdate(any())).thenReturn(Optional.of(project));
    }
}
