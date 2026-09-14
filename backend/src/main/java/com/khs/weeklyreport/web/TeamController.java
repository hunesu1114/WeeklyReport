package com.khs.weeklyreport.web;

import com.khs.weeklyreport.service.ActivityService;
import com.khs.weeklyreport.service.MemberService;
import com.khs.weeklyreport.service.NotificationService;
import com.khs.weeklyreport.web.dto.TeamDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 멤버 · 초대 · 알림 · 활동 기록. */
@RestController
@RequestMapping("/api/kanban")
public class TeamController {

    private final MemberService memberService;
    private final NotificationService notificationService;
    private final ActivityService activityService;

    public TeamController(MemberService memberService,
                          NotificationService notificationService,
                          ActivityService activityService) {
        this.memberService = memberService;
        this.notificationService = notificationService;
        this.activityService = activityService;
    }

    // ── 멤버 ─────────────────────────────────────────────────

    @GetMapping("/projects/{projectId}/members")
    public List<TeamDtos.MemberView> members(@PathVariable Long projectId) {
        return memberService.members(projectId);
    }

    @PatchMapping("/projects/{projectId}/members/{userId}")
    public TeamDtos.MemberView changeRole(@PathVariable Long projectId,
                                          @PathVariable Long userId,
                                          @Valid @RequestBody TeamDtos.RoleRequest request) {
        return memberService.changeRole(projectId, userId, request.role());
    }

    /** 남을 내보내는 것은 OWNER 만. 본인이면 '나가기'다. */
    @DeleteMapping("/projects/{projectId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long projectId, @PathVariable Long userId) {
        memberService.remove(projectId, userId);
    }

    // ── 초대 ─────────────────────────────────────────────────

    @PostMapping("/projects/{projectId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamDtos.InvitationView invite(@PathVariable Long projectId,
                                          @Valid @RequestBody TeamDtos.InviteRequest request) {
        return memberService.invite(projectId, request);
    }

    /** 이 보드에서 답을 기다리는 초대. */
    @GetMapping("/projects/{projectId}/invitations")
    public List<TeamDtos.InvitationView> pendingInvitations(@PathVariable Long projectId) {
        return memberService.pendingOf(projectId);
    }

    @DeleteMapping("/projects/{projectId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelInvitation(@PathVariable Long projectId, @PathVariable Long invitationId) {
        memberService.cancel(projectId, invitationId);
    }

    /** 내가 받은, 아직 답하지 않은 초대. */
    @GetMapping("/invitations")
    public List<TeamDtos.InvitationView> myInvitations() {
        return memberService.myPending();
    }

    @PostMapping("/invitations/{id}/accept")
    public TeamDtos.MemberView accept(@PathVariable Long id) {
        return memberService.accept(id);
    }

    @PostMapping("/invitations/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable Long id) {
        memberService.decline(id);
    }

    /** 초대할 사람 찾기. username 과 표시 이름만 내려간다. */
    @GetMapping("/users/search")
    public List<TeamDtos.UserBrief> searchUsers(@RequestParam String query,
                                                @RequestParam(required = false) Long projectId) {
        return memberService.searchUsers(query, projectId);
    }

    // ── 알림 ─────────────────────────────────────────────────

    @GetMapping("/notifications")
    public TeamDtos.InboxView inbox(@RequestParam(defaultValue = "30") int limit) {
        return notificationService.inbox(limit);
    }

    @PostMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead() {
        notificationService.markAllRead();
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id) {
        notificationService.markRead(id);
    }

    // ── 활동 기록 ────────────────────────────────────────────

    @GetMapping("/projects/{projectId}/activities")
    public Page<TeamDtos.ActivityView> projectActivities(@PathVariable Long projectId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "30") int size) {
        return activityService.forProject(projectId, page, size);
    }

    @GetMapping("/projects/{projectId}/cards/{cardId}/activities")
    public Page<TeamDtos.ActivityView> cardActivities(@PathVariable Long projectId,
                                                      @PathVariable Long cardId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return activityService.forCard(projectId, cardId, page, size);
    }

    /** 내가 속한 모든 보드의 활동. */
    @GetMapping("/activities")
    public Page<TeamDtos.ActivityView> myActivities(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "30") int size) {
        return activityService.forMe(page, size);
    }
}
