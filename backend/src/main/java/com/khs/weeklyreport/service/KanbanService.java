package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.*;
import com.khs.weeklyreport.realtime.RealtimeEvent;
import com.khs.weeklyreport.realtime.RealtimePublisher;
import com.khs.weeklyreport.repository.AppUserRepository;
import com.khs.weeklyreport.repository.KanbanCardRepository;
import com.khs.weeklyreport.repository.ProjectMemberRepository;
import com.khs.weeklyreport.repository.ProjectRepository;
import com.khs.weeklyreport.security.CurrentUser;
import com.khs.weeklyreport.web.dto.KanbanDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class KanbanService {

    /** 완료일이 이 일수 안으로 남으면 임박으로 본다. */
    public static final int DUE_SOON_DAYS = 3;

    /** 임박 목록 정렬: 완료일이 급한 순 -> 중요도 높은 순. */
    private static final Comparator<KanbanCard> BY_URGENCY =
            Comparator.comparing(KanbanCard::getDueDate)
                    .thenComparing(Comparator.comparingInt(
                            (KanbanCard card) -> card.getPriority().ordinal()).reversed())
                    .thenComparing(KanbanCard::getId);

    private final ProjectRepository projectRepository;
    private final KanbanCardRepository cardRepository;
    private final ProjectMemberRepository memberRepository;
    private final AppUserRepository userRepository;
    private final ProjectAccess access;
    private final ActivityService activities;
    private final NotificationService notifications;
    private final CurrentUser currentUser;
    private final RealtimePublisher realtime;

    public KanbanService(ProjectRepository projectRepository,
                         KanbanCardRepository cardRepository,
                         ProjectMemberRepository memberRepository,
                         AppUserRepository userRepository,
                         ProjectAccess access,
                         ActivityService activities,
                         NotificationService notifications,
                         CurrentUser currentUser,
                         RealtimePublisher realtime) {
        this.projectRepository = projectRepository;
        this.cardRepository = cardRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.activities = activities;
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.realtime = realtime;
    }

    // ── 프로젝트 ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<KanbanDtos.ProjectView> projects(boolean activeOnly) {
        Long userId = currentUser.requireId();
        List<Project> projects = activeOnly
                ? projectRepository.findMineActive(userId)
                : projectRepository.findMine(userId);
        return toProjectViews(projects, userId);
    }

    /** 만든 사람이 곧 첫 OWNER 가 된다. */
    @Transactional
    public KanbanDtos.ProjectView createProject(KanbanDtos.ProjectRequest request) {
        AppUser me = currentUser.requireEntity();

        Project project = new Project();
        project.setOwner(me);
        apply(project, request);
        if (request.sortOrder() == null) {
            project.setSortOrder((int) projectRepository.countMine(me.getId()));
        }
        Project saved = projectRepository.save(project);
        memberRepository.save(ProjectMember.of(saved, me, ProjectRole.OWNER));

        activities.project(saved, ActivityType.PROJECT_CREATED);
        return toProjectViews(List.of(saved), me.getId()).get(0);
    }

    @Transactional
    public KanbanDtos.ProjectView updateProject(Long id, KanbanDtos.ProjectRequest request) {
        Project project = access.requireOwner(id);
        apply(project, request);
        Project saved = projectRepository.save(project);

        activities.project(saved, ActivityType.PROJECT_UPDATED);
        realtime.toProject(id, RealtimeEvent.members(id, currentUser.requireId(), actorName()));
        return toProjectViews(List.of(saved), currentUser.requireId()).get(0);
    }

    @Transactional
    public void deleteProject(Long id) {
        projectRepository.delete(access.requireOwner(id));
    }

    // ── 보드 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public KanbanDtos.BoardView board(Long projectId) {
        Project project = access.requireRead(projectId);
        Long userId = currentUser.requireId();
        LocalDate today = LocalDate.now();

        Map<KanbanStatus, List<KanbanDtos.CardView>> columns = new EnumMap<>(KanbanStatus.class);
        for (KanbanStatus status : KanbanStatus.values()) {
            columns.put(status, new ArrayList<>());
        }
        for (KanbanCard card : cardRepository.findBoard(projectId)) {
            columns.get(card.getStatus()).add(KanbanDtos.CardView.of(card, today, DUE_SOON_DAYS));
        }

        long unassignedDue = cardRepository.countUnassignedDue(projectId, today.plusDays(DUE_SOON_DAYS));
        return new KanbanDtos.BoardView(
                toProjectViews(List.of(project), userId).get(0),
                columns,
                unassignedDue);
    }

    // ── 카드 ─────────────────────────────────────────────────

    @Transactional
    public KanbanDtos.CardView createCard(KanbanDtos.CardRequest request) {
        Project project = access.requireWrite(request.projectId());

        KanbanCard card = new KanbanCard();
        card.setProject(project);
        card.setStatus(request.status() == null ? KanbanStatus.BACKLOG : request.status());
        applyCardFields(card, request, project);
        card.setSortOrder(cardRepository.findMaxSortOrder(project.getId(), card.getStatus()) + 1);

        KanbanCard saved = cardRepository.save(card);
        activities.card(project, saved, ActivityType.CARD_CREATED);
        notifyAssigned(saved, null);
        publishBoard(project, "카드를 추가했습니다");

        return KanbanDtos.CardView.of(saved, LocalDate.now(), DUE_SOON_DAYS);
    }

    @Transactional
    public KanbanDtos.CardView updateCard(Long id, KanbanDtos.CardRequest request) {
        KanbanCard card = loadCardForWrite(id);
        Project project = card.getProject();

        // 두 사람이 같은 카드를 열어두고 각자 저장하면 뒤가 조용히 이긴다.
        // 화면이 들고 온 버전이 다르면 덮어쓰지 않고 알린다.
        if (request.version() != null && request.version() != card.getVersion()) {
            throw new StaleCardException(
                    "다른 사람이 먼저 수정했습니다.",
                    KanbanDtos.CardView.of(card, LocalDate.now(), DUE_SOON_DAYS));
        }

        AppUser previousAssignee = card.getAssignee();
        KanbanStatus previousStatus = card.getStatus();

        // 카드를 다른 보드로 옮기는 것도 허용한다. 옮겨갈 보드에도 권한이 있어야 한다.
        if (!project.getId().equals(request.projectId())) {
            project = access.requireWrite(request.projectId());
            card.setProject(project);
            card.setSortOrder(cardRepository.findMaxSortOrder(project.getId(), card.getStatus()) + 1);
        }
        if (request.status() != null && request.status() != card.getStatus()) {
            card.setStatus(request.status());
            card.setSortOrder(cardRepository.findMaxSortOrder(project.getId(), request.status()) + 1);
        }
        applyCardFields(card, request, project);

        KanbanCard saved = cardRepository.save(card);
        activities.card(project, saved, ActivityType.CARD_UPDATED);
        if (!Objects.equals(idOf(previousAssignee), idOf(saved.getAssignee()))) {
            activities.cardAssigned(project, saved, saved.getAssignee());
            notifyAssigned(saved, previousAssignee);
        }
        if (previousStatus != saved.getStatus()) {
            activities.cardMoved(project, saved, previousStatus, saved.getStatus());
        }
        publishBoard(project, "카드를 수정했습니다");

        return KanbanDtos.CardView.of(saved, LocalDate.now(), DUE_SOON_DAYS);
    }

    /**
     * 카드를 다른 칸으로 옮기거나 같은 칸 안에서 순서를 바꾼다.
     *
     * <p>보드를 잠그고 시작한다. 두 사람이 동시에 옮기면 둘 다 같은 순서를 읽고
     * 나중에 커밋한 쪽이 앞사람의 이동을 덮어쓰기 때문이다. 이동은 짧은 작업이라
     * 줄을 세워도 체감되지 않는다.
     *
     * <p>이동에는 버전을 요구하지 않는다. 드래그는 원자적 조작이고, 잠금으로
     * 이미 직렬화된다.
     */
    @Transactional
    public KanbanDtos.CardView moveCard(Long id, KanbanDtos.MoveRequest request) {
        KanbanCard card = cardRepository.findWithProject(id)
                .orElseThrow(() -> new NotFoundException("카드를 찾을 수 없습니다. id=" + id));
        Long projectId = card.getProject().getId();

        Project project = access.lockForReorder(projectId);
        KanbanStatus from = card.getStatus();
        KanbanStatus to = request.status();

        List<KanbanCard> target = new ArrayList<>(
                cardRepository.findByProjectIdAndStatusOrderBySortOrderAscIdAsc(projectId, to));
        target.removeIf(c -> c.getId().equals(card.getId()));

        int position = request.sortOrder() == null
                ? target.size()
                : Math.max(0, Math.min(request.sortOrder(), target.size()));

        card.setStatus(to);
        target.add(position, card);
        renumber(target);

        if (from != to) {
            renumber(cardRepository.findByProjectIdAndStatusOrderBySortOrderAscIdAsc(projectId, from)
                    .stream().filter(c -> !c.getId().equals(card.getId())).toList());
        }

        cardRepository.save(card);
        if (from != to) {
            activities.cardMoved(project, card, from, to);
        }
        publishBoard(project, from == to ? "카드 순서를 바꿨습니다" : "카드를 옮겼습니다");

        return KanbanDtos.CardView.of(card, LocalDate.now(), DUE_SOON_DAYS);
    }

    @Transactional
    public void deleteCard(Long id) {
        KanbanCard card = cardRepository.findWithProject(id)
                .orElseThrow(() -> new NotFoundException("카드를 찾을 수 없습니다. id=" + id));
        Long projectId = card.getProject().getId();

        Project project = access.lockForReorder(projectId);
        KanbanStatus status = card.getStatus();
        String title = card.getTitle();

        cardRepository.delete(card);
        cardRepository.flush();
        renumber(cardRepository.findByProjectIdAndStatusOrderBySortOrderAscIdAsc(projectId, status));

        activities.cardDeleted(project, title);
        publishBoard(project, "카드를 지웠습니다");
    }

    // ── 조회 ─────────────────────────────────────────────────

    /**
     * 완료일이 임박한 카드.
     * 기본은 <b>내가 담당한 것</b>. 팀 전체 임박이 모두에게 울리면 아무도 보지 않는다.
     */
    @Transactional(readOnly = true)
    public List<KanbanDtos.CardView> dueSoon(Integer days, boolean teamScope) {
        int window = days == null ? DUE_SOON_DAYS : Math.max(0, days);
        LocalDate today = LocalDate.now();
        Long userId = currentUser.requireId();
        LocalDate until = today.plusDays(window);

        List<KanbanCard> cards = teamScope
                ? cardRepository.findDueInMyProjects(until, userId)
                : cardRepository.findDueForAssignee(until, userId);

        return cards.stream()
                .sorted(BY_URGENCY)
                .map(card -> KanbanDtos.CardView.of(card, today, window))
                .toList();
    }

    /**
     * 시작일이 기간 안에 있는 카드. 주간보고 작성 화면이 쓴다.
     * 기본은 내가 담당한 것 — 팀 보드의 남의 카드가 내 주간보고에 쏟아지면 안 된다.
     */
    @Transactional(readOnly = true)
    public List<KanbanDtos.CardView> startedBetween(LocalDate from, LocalDate to,
                                                    Long projectId, boolean mineOnly) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("조회 기간(from, to)이 필요합니다.");
        }
        LocalDate today = LocalDate.now();
        Long userId = currentUser.requireId();

        List<KanbanCard> cards;
        if (projectId != null) {
            access.requireRead(projectId);
            cards = cardRepository.findStartedInProject(from, to, projectId);
            if (mineOnly) {
                cards = cards.stream().filter(c -> userId.equals(idOf(c.getAssignee()))).toList();
            }
        } else if (mineOnly) {
            cards = cardRepository.findStartedByAssignee(from, to, userId);
        } else {
            cards = cardRepository.findStartedInMyProjects(from, to, userId);
        }
        return cards.stream().map(card -> KanbanDtos.CardView.of(card, today, DUE_SOON_DAYS)).toList();
    }

    // ── 내부 ─────────────────────────────────────────────────

    private void renumber(List<KanbanCard> cards) {
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).setSortOrder(i);
        }
        cardRepository.saveAll(cards);
    }

    private KanbanCard loadCardForWrite(Long id) {
        KanbanCard card = cardRepository.findWithProject(id)
                .orElseThrow(() -> new NotFoundException("카드를 찾을 수 없습니다. id=" + id));
        access.requireWrite(card.getProject().getId());
        return card;
    }

    private void apply(Project project, KanbanDtos.ProjectRequest request) {
        project.setName(request.name().trim());
        project.setDescription(blankToNull(request.description()));
        project.setColor(blankToNull(request.color()));
        if (request.active() != null) {
            project.setActive(request.active());
        }
        if (request.sortOrder() != null) {
            project.setSortOrder(request.sortOrder());
        }
    }

    private void applyCardFields(KanbanCard card, KanbanDtos.CardRequest request, Project project) {
        card.setTitle(request.title().trim());
        card.setContent(blankToNull(request.content()));
        card.setPriority(request.priority() == null ? CardPriority.NORMAL : request.priority());
        // 시작일을 비우면 오늘로 잡는다. 보고서 연동이 시작일 기준으로 돌기 때문에
        // 비어 있는 카드는 어느 주에도 잡히지 않는다.
        card.setStartDate(request.startDate() == null ? LocalDate.now() : request.startDate());
        card.setDueDate(request.dueDate());
        card.setAssignee(resolveAssignee(request.assigneeId(), project));
    }

    /** 담당자는 그 보드의 참여자여야 한다. 남을 아무나 담당으로 걸 수 없다. */
    private AppUser resolveAssignee(Long assigneeId, Project project) {
        if (assigneeId == null) return null;
        if (!memberRepository.existsByProjectIdAndUserId(project.getId(), assigneeId)) {
            throw new IllegalArgumentException("이 보드의 참여자만 담당자로 지정할 수 있습니다.");
        }
        return userRepository.findById(assigneeId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
    }

    /** 내가 나에게 맡긴 것까지 알릴 필요는 없다. */
    private void notifyAssigned(KanbanCard card, AppUser previous) {
        AppUser assignee = card.getAssignee();
        if (assignee == null) return;
        if (Objects.equals(idOf(previous), assignee.getId())) return;
        if (assignee.getId().equals(currentUser.requireId())) return;

        notifications.send(assignee, NotificationType.CARD_ASSIGNED,
                "'%s' 카드의 담당자가 되었습니다".formatted(card.getTitle()),
                card.getProject().getName(),
                "/kanban/" + card.getProject().getId());
    }

    private void publishBoard(Project project, String detail) {
        realtime.toProject(project.getId(),
                RealtimeEvent.board(project.getId(), currentUser.requireId(), actorName(), detail));
    }

    /** 보드 목록 집계. 프로젝트마다 카드를 전건 읽지 않고 한 번에 센다. */
    private List<KanbanDtos.ProjectView> toProjectViews(List<Project> projects, Long userId) {
        if (projects.isEmpty()) return List.of();

        List<Long> ids = projects.stream().map(Project::getId).toList();
        LocalDate until = LocalDate.now().plusDays(DUE_SOON_DAYS);

        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : cardRepository.summarize(ids, until)) {
            counts.put((Long) row[0], new long[]{
                    toLong(row[1]), toLong(row[2]), toLong(row[3])});
        }
        Map<Long, ProjectRole> roles = new HashMap<>();
        for (Long id : ids) {
            access.roleOf(id, userId).ifPresent(role -> roles.put(id, role));
        }

        return projects.stream().map(project -> {
            long[] c = counts.getOrDefault(project.getId(), new long[]{0, 0, 0});
            return KanbanDtos.ProjectView.of(project, c[0], c[1], c[2], roles.get(project.getId()));
        }).toList();
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static Long idOf(AppUser user) {
        return user == null ? null : user.getId();
    }

    private String actorName() {
        return currentUser.requireEntity().getDisplayName();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
