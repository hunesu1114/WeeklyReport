package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.*;
import com.khs.weeklyreport.repository.ActivityLogRepository;
import com.khs.weeklyreport.security.CurrentUser;
import com.khs.weeklyreport.web.dto.TeamDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보드에서 일어난 일을 남긴다.
 *
 * <p>기록은 부가 기능이다. 여기서 예외가 나서 정작 카드 이동이 취소되면 안 되므로
 * 호출하는 쪽에서 감싸지 않고, 저장에 필요한 값만 받아 단순하게 넣는다.
 */
@Service
public class ActivityService {

    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "createdAt").and(
            Sort.by(Sort.Direction.DESC, "id"));

    private final ActivityLogRepository repository;
    private final ProjectAccess projectAccess;
    private final CurrentUser currentUser;

    public ActivityService(ActivityLogRepository repository,
                           ProjectAccess projectAccess,
                           CurrentUser currentUser) {
        this.repository = repository;
        this.projectAccess = projectAccess;
        this.currentUser = currentUser;
    }

    // ── 남기기 ───────────────────────────────────────────────

    public void card(Project project, KanbanCard card, ActivityType type) {
        ActivityLog log = base(project, type);
        log.setCard(card);
        log.setCardTitle(card.getTitle());
        log.setToStatus(card.getStatus());
        repository.save(log);
    }

    public void cardMoved(Project project, KanbanCard card, KanbanStatus from, KanbanStatus to) {
        ActivityLog log = base(project, ActivityType.CARD_MOVED);
        log.setCard(card);
        log.setCardTitle(card.getTitle());
        log.setFromStatus(from);
        log.setToStatus(to);
        repository.save(log);
    }

    public void cardAssigned(Project project, KanbanCard card, AppUser assignee) {
        ActivityLog log = base(project, ActivityType.CARD_ASSIGNED);
        log.setCard(card);
        log.setCardTitle(card.getTitle());
        log.setTargetName(assignee == null ? null : assignee.getDisplayName());
        log.setDetail(assignee == null ? "담당자를 비웠습니다" : null);
        repository.save(log);
    }

    /** 카드를 지운 뒤에도 무엇을 지웠는지 남는다. card_id 는 null 이 된다. */
    public void cardDeleted(Project project, String title) {
        ActivityLog log = base(project, ActivityType.CARD_DELETED);
        log.setCardTitle(title);
        repository.save(log);
    }

    public void project(Project project, ActivityType type) {
        repository.save(base(project, type));
    }

    public void member(Project project, ActivityType type, String targetName, String detail) {
        ActivityLog log = base(project, type);
        log.setTargetName(targetName);
        log.setDetail(detail);
        repository.save(log);
    }

    private ActivityLog base(Project project, ActivityType type) {
        ActivityLog log = new ActivityLog();
        log.setProject(project);
        log.setActor(currentUser.principal().isPresent() ? currentUser.requireEntity() : null);
        log.setType(type);
        return log;
    }

    // ── 조회 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TeamDtos.ActivityView> forProject(Long projectId, int page, int size) {
        projectAccess.requireRead(projectId);
        return repository.findByProject(projectId, PageRequest.of(page, capped(size), NEWEST))
                .map(TeamDtos.ActivityView::of);
    }

    @Transactional(readOnly = true)
    public Page<TeamDtos.ActivityView> forCard(Long projectId, Long cardId, int page, int size) {
        projectAccess.requireRead(projectId);
        return repository.findByCard(cardId, PageRequest.of(page, capped(size), NEWEST))
                .map(TeamDtos.ActivityView::of);
    }

    /** 내가 속한 모든 보드의 활동. */
    @Transactional(readOnly = true)
    public Page<TeamDtos.ActivityView> forMe(int page, int size) {
        return repository.findForUser(currentUser.requireId(), PageRequest.of(page, capped(size), NEWEST))
                .map(TeamDtos.ActivityView::of);
    }

    private static int capped(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
