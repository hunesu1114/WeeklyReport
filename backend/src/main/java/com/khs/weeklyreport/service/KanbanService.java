package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.CardPriority;
import com.khs.weeklyreport.domain.KanbanCard;
import com.khs.weeklyreport.domain.KanbanStatus;
import com.khs.weeklyreport.domain.Project;
import com.khs.weeklyreport.repository.KanbanCardRepository;
import com.khs.weeklyreport.repository.ProjectRepository;
import com.khs.weeklyreport.web.dto.KanbanDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

    public KanbanService(ProjectRepository projectRepository, KanbanCardRepository cardRepository) {
        this.projectRepository = projectRepository;
        this.cardRepository = cardRepository;
    }

    // ── 프로젝트 ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<KanbanDtos.ProjectView> projects(boolean activeOnly) {
        LocalDate today = LocalDate.now();
        List<Project> projects = activeOnly
                ? projectRepository.findByActiveTrueOrderBySortOrderAscNameAsc()
                : projectRepository.findAllByOrderBySortOrderAscNameAsc();
        return projects.stream().map(project -> toProjectView(project, today)).toList();
    }

    @Transactional
    public KanbanDtos.ProjectView createProject(KanbanDtos.ProjectRequest request) {
        if (projectRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new IllegalArgumentException("이미 있는 프로젝트 이름입니다: " + request.name().trim());
        }
        Project project = new Project();
        apply(project, request);
        if (request.sortOrder() == null) {
            // 새 프로젝트는 목록 맨 뒤에 붙인다
            project.setSortOrder((int) projectRepository.count());
        }
        return toProjectView(projectRepository.save(project), LocalDate.now());
    }

    @Transactional
    public KanbanDtos.ProjectView updateProject(Long id, KanbanDtos.ProjectRequest request) {
        Project project = loadProject(id);
        String name = request.name().trim();
        if (!project.getName().equalsIgnoreCase(name) && projectRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("이미 있는 프로젝트 이름입니다: " + name);
        }
        apply(project, request);
        return toProjectView(projectRepository.save(project), LocalDate.now());
    }

    /** 프로젝트를 지우면 그 보드의 카드도 함께 사라진다. */
    @Transactional
    public void deleteProject(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new NotFoundException("프로젝트를 찾을 수 없습니다. id=" + id);
        }
        projectRepository.deleteById(id);
    }

    // ── 보드 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public KanbanDtos.BoardView board(Long projectId) {
        Project project = loadProject(projectId);
        LocalDate today = LocalDate.now();

        Map<KanbanStatus, List<KanbanDtos.CardView>> columns = new EnumMap<>(KanbanStatus.class);
        for (KanbanStatus status : KanbanStatus.values()) {
            columns.put(status, new ArrayList<>());
        }
        for (KanbanCard card : cardRepository.findByProjectIdOrderByStatusAscSortOrderAscIdAsc(projectId)) {
            columns.get(card.getStatus()).add(KanbanDtos.CardView.of(card, today, DUE_SOON_DAYS));
        }
        return new KanbanDtos.BoardView(toProjectView(project, today), columns);
    }

    // ── 카드 ─────────────────────────────────────────────────

    @Transactional
    public KanbanDtos.CardView createCard(KanbanDtos.CardRequest request) {
        Project project = loadProject(request.projectId());

        KanbanCard card = new KanbanCard();
        card.setProject(project);
        card.setStatus(request.status() == null ? KanbanStatus.BACKLOG : request.status());
        applyCardFields(card, request);
        // 새 카드는 그 칸의 맨 아래로
        card.setSortOrder(cardRepository.findMaxSortOrder(project.getId(), card.getStatus()) + 1);

        return KanbanDtos.CardView.of(cardRepository.save(card), LocalDate.now(), DUE_SOON_DAYS);
    }

    @Transactional
    public KanbanDtos.CardView updateCard(Long id, KanbanDtos.CardRequest request) {
        KanbanCard card = loadCard(id);

        // 카드를 다른 프로젝트로 옮기는 것도 허용한다
        if (!card.getProject().getId().equals(request.projectId())) {
            card.setProject(loadProject(request.projectId()));
            card.setSortOrder(cardRepository.findMaxSortOrder(request.projectId(), card.getStatus()) + 1);
        }
        if (request.status() != null && request.status() != card.getStatus()) {
            card.setStatus(request.status());
            card.setSortOrder(cardRepository.findMaxSortOrder(card.getProject().getId(), request.status()) + 1);
        }
        applyCardFields(card, request);

        return KanbanDtos.CardView.of(cardRepository.save(card), LocalDate.now(), DUE_SOON_DAYS);
    }

    /**
     * 카드를 다른 칸으로 옮기거나 같은 칸 안에서 순서를 바꾼다.
     *
     * <p>옮긴 뒤 관련된 칸의 순번을 0부터 다시 매긴다. 순번이 듬성듬성해지면
     * 다음 이동에서 위치 계산이 어긋난다.
     */
    @Transactional
    public KanbanDtos.CardView moveCard(Long id, KanbanDtos.MoveRequest request) {
        KanbanCard card = loadCard(id);
        Long projectId = card.getProject().getId();
        KanbanStatus from = card.getStatus();
        KanbanStatus to = request.status();

        List<KanbanCard> target = cardsIn(projectId, to);
        target.removeIf(c -> c.getId().equals(card.getId()));

        int position = request.sortOrder() == null
                ? target.size()
                : Math.max(0, Math.min(request.sortOrder(), target.size()));

        card.setStatus(to);
        target.add(position, card);
        renumber(target);

        if (from != to) {
            renumber(cardsIn(projectId, from).stream()
                    .filter(c -> !c.getId().equals(card.getId()))
                    .toList());
        }

        cardRepository.save(card);
        return KanbanDtos.CardView.of(card, LocalDate.now(), DUE_SOON_DAYS);
    }

    @Transactional
    public void deleteCard(Long id) {
        KanbanCard card = loadCard(id);
        Long projectId = card.getProject().getId();
        KanbanStatus status = card.getStatus();
        cardRepository.delete(card);
        cardRepository.flush();
        renumber(cardsIn(projectId, status));
    }

    // ── 조회 ─────────────────────────────────────────────────

    /** 완료일이 임박한 카드. 전 프로젝트를 훑는다. */
    @Transactional(readOnly = true)
    public List<KanbanDtos.CardView> dueSoon(Integer days) {
        int window = days == null ? DUE_SOON_DAYS : Math.max(0, days);
        LocalDate today = LocalDate.now();
        return cardRepository.findDueUntil(today.plusDays(window)).stream()
                .sorted(BY_URGENCY)
                .map(card -> KanbanDtos.CardView.of(card, today, window))
                .toList();
    }

    /** 시작일이 기간 안에 있는 카드. 주간보고 작성 화면이 쓴다. */
    @Transactional(readOnly = true)
    public List<KanbanDtos.CardView> startedBetween(LocalDate from, LocalDate to, Long projectId) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("조회 기간(from, to)이 필요합니다.");
        }
        LocalDate today = LocalDate.now();
        List<KanbanCard> cards = projectId == null
                ? cardRepository.findStartedBetween(from, to)
                : cardRepository.findStartedBetweenInProject(from, to, projectId);
        return cards.stream().map(card -> KanbanDtos.CardView.of(card, today, DUE_SOON_DAYS)).toList();
    }

    // ── 내부 ─────────────────────────────────────────────────

    private List<KanbanCard> cardsIn(Long projectId, KanbanStatus status) {
        return new ArrayList<>(cardRepository.findByProjectIdOrderByStatusAscSortOrderAscIdAsc(projectId).stream()
                .filter(c -> c.getStatus() == status)
                .toList());
    }

    private void renumber(List<KanbanCard> cards) {
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).setSortOrder(i);
        }
        cardRepository.saveAll(cards);
    }

    private Project loadProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("프로젝트를 찾을 수 없습니다. id=" + id));
    }

    private KanbanCard loadCard(Long id) {
        return cardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("카드를 찾을 수 없습니다. id=" + id));
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

    private void applyCardFields(KanbanCard card, KanbanDtos.CardRequest request) {
        card.setTitle(request.title().trim());
        card.setContent(blankToNull(request.content()));
        card.setPriority(request.priority() == null ? CardPriority.NORMAL : request.priority());
        // 시작일을 비워두면 오늘로 잡는다. 보고서 연동이 시작일을 기준으로 돌기 때문에
        // 비어 있는 카드는 어느 주에도 잡히지 않는다.
        card.setStartDate(request.startDate() == null ? LocalDate.now() : request.startDate());
        card.setDueDate(request.dueDate());
    }

    private KanbanDtos.ProjectView toProjectView(Project project, LocalDate today) {
        List<KanbanCard> cards = cardRepository.findByProjectIdOrderByStatusAscSortOrderAscIdAsc(project.getId());
        long open = cards.stream().filter(c -> c.getStatus() != KanbanStatus.DONE).count();
        long dueSoon = cards.stream().filter(c -> c.isDueSoon(today, DUE_SOON_DAYS)).count();
        return KanbanDtos.ProjectView.of(project, cards.size(), open, dueSoon);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
