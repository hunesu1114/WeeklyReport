package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.*;
import com.khs.weeklyreport.realtime.RealtimeEvent;
import com.khs.weeklyreport.realtime.RealtimePublisher;
import com.khs.weeklyreport.repository.AppUserRepository;
import com.khs.weeklyreport.repository.KanbanCardRepository;
import com.khs.weeklyreport.repository.ProjectInvitationRepository;
import com.khs.weeklyreport.repository.ProjectMemberRepository;
import com.khs.weeklyreport.security.CurrentUser;
import com.khs.weeklyreport.web.dto.TeamDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 보드 참여자와 초대. */
@Service
public class MemberService {

    private final ProjectMemberRepository memberRepository;
    private final ProjectInvitationRepository invitationRepository;
    private final AppUserRepository userRepository;
    private final KanbanCardRepository cardRepository;
    private final ProjectAccess projectAccess;
    private final NotificationService notifications;
    private final ActivityService activities;
    private final CurrentUser currentUser;
    private final RealtimePublisher realtime;

    public MemberService(ProjectMemberRepository memberRepository,
                         ProjectInvitationRepository invitationRepository,
                         AppUserRepository userRepository,
                         KanbanCardRepository cardRepository,
                         ProjectAccess projectAccess,
                         NotificationService notifications,
                         ActivityService activities,
                         CurrentUser currentUser,
                         RealtimePublisher realtime) {
        this.memberRepository = memberRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.cardRepository = cardRepository;
        this.projectAccess = projectAccess;
        this.notifications = notifications;
        this.activities = activities;
        this.currentUser = currentUser;
        this.realtime = realtime;
    }

    // ── 멤버 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TeamDtos.MemberView> members(Long projectId) {
        projectAccess.requireRead(projectId);

        // 사람마다 카드를 세면 멤버 수만큼 쿼리가 나간다. 한 번에 묶어 센다.
        Map<Long, Long> assigned = new HashMap<>();
        for (Object[] row : cardRepository.countAssignedPerMember(projectId)) {
            assigned.put((Long) row[0], ((Number) row[1]).longValue());
        }

        return memberRepository.findMembers(projectId).stream()
                .map(member -> TeamDtos.MemberView.of(
                        member, assigned.getOrDefault(member.getUser().getId(), 0L)))
                .toList();
    }

    @Transactional
    public TeamDtos.MemberView changeRole(Long projectId, Long userId, ProjectRole role) {
        Project project = projectAccess.requireOwner(projectId);
        ProjectMember member = memberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException("참여자가 아닙니다."));

        // 마지막 OWNER 를 강등하면 아무도 멤버를 관리할 수 없는 보드가 된다
        if (member.getRole().isOwner() && role != ProjectRole.OWNER) {
            requireAnotherOwner(projectId, userId);
        }

        ProjectRole before = member.getRole();
        member.setRole(role);
        memberRepository.save(member);

        activities.member(project, ActivityType.MEMBER_ROLE_CHANGED,
                member.getUser().getDisplayName(), "%s → %s".formatted(before, role));
        realtime.toProject(projectId, RealtimeEvent.members(projectId, currentUser.requireId(), actorName()));
        return TeamDtos.MemberView.of(member);
    }

    @Transactional
    public void remove(Long projectId, Long userId) {
        Long me = currentUser.requireId();
        boolean leavingMyself = me.equals(userId);

        // 나가는 것은 누구나, 남을 내보내는 것은 OWNER 만
        Project project = leavingMyself ? projectAccess.requireRead(projectId)
                : projectAccess.requireOwner(projectId);

        ProjectMember member = memberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException("참여자가 아닙니다."));

        if (member.getRole().isOwner()) {
            requireAnotherOwner(projectId, userId);
        }

        String name = member.getUser().getDisplayName();

        // 명단에서 빠진 사람이 카드에 담당자로 남아 있으면, 그 카드는 제목 하나
        // 고치려 해도 "참여자만 담당자로 지정할 수 있다"며 거절당해 손댈 수 없게 된다.
        int unassigned = cardRepository.clearAssignee(projectId, userId);
        memberRepository.delete(member);

        String detail = unassigned == 0 ? null : "담당 카드 %d장이 담당 없음이 되었습니다".formatted(unassigned);
        if (leavingMyself) {
            activities.member(project, ActivityType.MEMBER_LEFT, name, detail);
        } else {
            activities.member(project, ActivityType.MEMBER_REMOVED, name, detail);
            notifications.send(member.getUser(), NotificationType.MEMBER_REMOVED,
                    "'%s' 보드에서 제외되었습니다".formatted(project.getName()), null, null);
        }
        realtime.toProject(projectId, RealtimeEvent.members(projectId, me, actorName()));
    }

    // ── 초대 ─────────────────────────────────────────────────

    @Transactional
    public TeamDtos.InvitationView invite(Long projectId, TeamDtos.InviteRequest request) {
        Project project = projectAccess.requireOwner(projectId);

        AppUser invitee = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        if (memberRepository.existsByProjectIdAndUserId(projectId, invitee.getId())) {
            throw new IllegalArgumentException("%s 님은 이미 참여 중입니다.".formatted(invitee.getDisplayName()));
        }
        invitationRepository
                .findByProjectIdAndInviteeIdAndStatus(projectId, invitee.getId(), InvitationStatus.PENDING)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "%s 님에게 이미 보낸 초대가 있습니다.".formatted(invitee.getDisplayName()));
                });

        ProjectInvitation invitation = new ProjectInvitation();
        invitation.setProject(project);
        invitation.setInviter(currentUser.requireEntity());
        invitation.setInvitee(invitee);
        invitation.setRole(request.roleOrDefault());
        invitationRepository.save(invitation);

        notifications.sendInvite(invitation);
        activities.member(project, ActivityType.MEMBER_INVITED, invitee.getDisplayName(),
                request.roleOrDefault().name());

        return TeamDtos.InvitationView.of(invitation);
    }

    @Transactional(readOnly = true)
    public List<TeamDtos.InvitationView> pendingOf(Long projectId) {
        projectAccess.requireRead(projectId);
        return invitationRepository.findByProjectAndStatus(projectId, InvitationStatus.PENDING)
                .stream().map(TeamDtos.InvitationView::of).toList();
    }

    /** 내가 받은, 아직 답하지 않은 초대. */
    @Transactional(readOnly = true)
    public List<TeamDtos.InvitationView> myPending() {
        return invitationRepository.findPendingFor(currentUser.requireId())
                .stream().map(TeamDtos.InvitationView::of).toList();
    }

    @Transactional
    public TeamDtos.MemberView accept(Long invitationId) {
        ProjectInvitation invitation = loadMyInvitation(invitationId);
        Project project = invitation.getProject();
        AppUser me = currentUser.requireEntity();

        invitation.respond(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        // 초대를 받은 사이에 다른 경로로 이미 들어와 있을 수 있다
        ProjectMember member = memberRepository.findByProjectIdAndUserId(project.getId(), me.getId())
                .orElseGet(() -> memberRepository.save(
                        ProjectMember.of(project, me, invitation.getRole())));

        activities.member(project, ActivityType.MEMBER_JOINED, me.getDisplayName(), null);
        notifications.send(invitation.getInviter(), NotificationType.INVITE_ACCEPTED,
                "%s 님이 '%s' 보드 초대를 수락했습니다".formatted(me.getDisplayName(), project.getName()),
                null, "/kanban/" + project.getId());
        realtime.toProject(project.getId(),
                RealtimeEvent.members(project.getId(), me.getId(), me.getDisplayName()));

        return TeamDtos.MemberView.of(member);
    }

    @Transactional
    public void decline(Long invitationId) {
        ProjectInvitation invitation = loadMyInvitation(invitationId);
        AppUser me = currentUser.requireEntity();

        invitation.respond(InvitationStatus.DECLINED);
        invitationRepository.save(invitation);

        notifications.send(invitation.getInviter(), NotificationType.INVITE_DECLINED,
                "%s 님이 '%s' 보드 초대를 거절했습니다"
                        .formatted(me.getDisplayName(), invitation.getProject().getName()),
                null, null);
    }

    /** 보낸 초대를 거둬들인다. */
    @Transactional
    public void cancel(Long projectId, Long invitationId) {
        projectAccess.requireOwner(projectId);
        ProjectInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("초대를 찾을 수 없습니다."));
        if (!invitation.getProject().getId().equals(projectId)) {
            throw new NotFoundException("초대를 찾을 수 없습니다.");
        }
        if (invitation.isPending()) {
            invitation.respond(InvitationStatus.CANCELED);
            invitationRepository.save(invitation);
        }
        notifications.dropInviteNotifications(invitationId, invitation.getInvitee().getId());
    }

    // ── 사용자 찾기 ──────────────────────────────────────────

    /** 초대할 사람을 찾는다. username 과 표시 이름만 내려준다. */
    @Transactional(readOnly = true)
    public List<TeamDtos.UserBrief> searchUsers(String query, Long projectId) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 1) return List.of();

        List<AppUser> found = userRepository.search("%" + q.toLowerCase() + "%");
        Long me = currentUser.requireId();

        return found.stream()
                .filter(user -> !user.getId().equals(me))
                // 이미 들어와 있는 사람은 후보에서 뺀다
                .filter(user -> projectId == null
                        || !memberRepository.existsByProjectIdAndUserId(projectId, user.getId()))
                .limit(10)
                .map(TeamDtos.UserBrief::of)
                .toList();
    }

    // ── 내부 ─────────────────────────────────────────────────

    private ProjectInvitation loadMyInvitation(Long invitationId) {
        ProjectInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("초대를 찾을 수 없습니다."));
        if (!invitation.getInvitee().getId().equals(currentUser.requireId())) {
            throw new NotFoundException("초대를 찾을 수 없습니다.");
        }
        if (!invitation.isPending()) {
            throw new IllegalArgumentException("이미 처리된 초대입니다.");
        }
        return invitation;
    }

    /** 이 사람 말고 다른 OWNER 가 남아 있어야 한다. */
    private void requireAnotherOwner(Long projectId, Long userId) {
        long owners = memberRepository.countByProjectIdAndRole(projectId, ProjectRole.OWNER);
        if (owners <= 1) {
            throw new IllegalArgumentException(
                    "마지막 관리자입니다. 다른 참여자를 관리자로 지정한 뒤에 할 수 있습니다.");
        }
    }

    private String actorName() {
        return currentUser.requireEntity().getDisplayName();
    }
}
