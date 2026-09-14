package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.Project;
import com.khs.weeklyreport.domain.ProjectMember;
import com.khs.weeklyreport.domain.ProjectRole;
import com.khs.weeklyreport.repository.ProjectMemberRepository;
import com.khs.weeklyreport.repository.ProjectRepository;
import com.khs.weeklyreport.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 보드 권한 판정을 한 곳에 모은다.
 *
 * <p>중요한 구분: <b>못 보는 보드는 404, 볼 수는 있으나 못 고치면 403</b> 이다.
 * 없는 보드와 남의 보드를 다르게 답하면, id 를 하나씩 찍어보는 것만으로
 * 어떤 보드가 존재하는지 알아낼 수 있다.
 */
@Component
public class ProjectAccess {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final CurrentUser currentUser;

    public ProjectAccess(ProjectRepository projectRepository,
                         ProjectMemberRepository memberRepository,
                         CurrentUser currentUser) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.currentUser = currentUser;
    }

    /** 보기 권한. 멤버가 아니면 없는 것으로 친다. */
    public Project requireRead(Long projectId) {
        Long userId = currentUser.requireId();
        memberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> notFound(projectId));
        return projectRepository.findById(projectId).orElseThrow(() -> notFound(projectId));
    }

    /** 카드를 건드릴 권한. VIEWER 는 볼 수는 있으니 403 으로 답한다. */
    public Project requireWrite(Long projectId) {
        ProjectMember member = requireMember(projectId);
        if (!member.getRole().canWrite()) {
            throw new AccessDeniedException("읽기 전용으로 참여 중입니다. 카드를 수정할 수 없습니다.");
        }
        return projectRepository.findById(projectId).orElseThrow(() -> notFound(projectId));
    }

    /** 멤버 관리·보드 설정·삭제. OWNER 만. */
    public Project requireOwner(Long projectId) {
        ProjectMember member = requireMember(projectId);
        if (!member.getRole().isOwner()) {
            throw new AccessDeniedException("보드 관리자만 할 수 있습니다.");
        }
        return projectRepository.findById(projectId).orElseThrow(() -> notFound(projectId));
    }

    /**
     * 카드 순서를 다시 매기기 전에 보드를 잠근다.
     * 권한 확인이 먼저다 — 남의 보드를 잠글 수 있으면 그 자체가 문제다.
     */
    public Project lockForReorder(Long projectId) {
        requireWrite(projectId);
        return projectRepository.findByIdForUpdate(projectId).orElseThrow(() -> notFound(projectId));
    }

    public ProjectMember requireMember(Long projectId) {
        return memberRepository.findByProjectIdAndUserId(projectId, currentUser.requireId())
                .orElseThrow(() -> notFound(projectId));
    }

    public Optional<ProjectRole> roleOf(Long projectId, Long userId) {
        return memberRepository.findByProjectIdAndUserId(projectId, userId).map(ProjectMember::getRole);
    }

    public ProjectRole myRole(Long projectId) {
        return requireMember(projectId).getRole();
    }

    private NotFoundException notFound(Long projectId) {
        return new NotFoundException("프로젝트를 찾을 수 없습니다. id=" + projectId);
    }
}
