package com.khs.weeklyreport.web;

import com.khs.weeklyreport.service.KanbanService;
import com.khs.weeklyreport.web.dto.KanbanDtos;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/kanban")
public class KanbanController {

    private final KanbanService kanbanService;

    public KanbanController(KanbanService kanbanService) {
        this.kanbanService = kanbanService;
    }

    // ── 프로젝트 ──────────────────────────────────────────────

    @GetMapping("/projects")
    public List<KanbanDtos.ProjectView> projects(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        return kanbanService.projects(activeOnly);
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public KanbanDtos.ProjectView createProject(@Valid @RequestBody KanbanDtos.ProjectRequest request) {
        return kanbanService.createProject(request);
    }

    @PutMapping("/projects/{id}")
    public KanbanDtos.ProjectView updateProject(@PathVariable Long id,
                                                @Valid @RequestBody KanbanDtos.ProjectRequest request) {
        return kanbanService.updateProject(id, request);
    }

    @DeleteMapping("/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable Long id) {
        kanbanService.deleteProject(id);
    }

    // ── 보드 ─────────────────────────────────────────────────

    @GetMapping("/projects/{id}/board")
    public KanbanDtos.BoardView board(@PathVariable Long id) {
        return kanbanService.board(id);
    }

    // ── 카드 ─────────────────────────────────────────────────

    @PostMapping("/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public KanbanDtos.CardView createCard(@Valid @RequestBody KanbanDtos.CardRequest request) {
        return kanbanService.createCard(request);
    }

    @PutMapping("/cards/{id}")
    public KanbanDtos.CardView updateCard(@PathVariable Long id,
                                          @Valid @RequestBody KanbanDtos.CardRequest request) {
        return kanbanService.updateCard(id, request);
    }

    /** 카드를 다른 칸으로 옮기거나 같은 칸 안에서 순서를 바꾼다. */
    @PutMapping("/cards/{id}/move")
    public KanbanDtos.CardView moveCard(@PathVariable Long id,
                                        @Valid @RequestBody KanbanDtos.MoveRequest request) {
        return kanbanService.moveCard(id, request);
    }

    @DeleteMapping("/cards/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCard(@PathVariable Long id) {
        kanbanService.deleteCard(id);
    }

    // ── 조회 ─────────────────────────────────────────────────

    /**
     * 완료일이 임박한 카드. 헤더 알림이 쓴다.
     * 기본은 내가 담당한 것. scope=team 이면 내가 속한 보드 전체를 본다.
     */
    @GetMapping("/cards/due-soon")
    public List<KanbanDtos.CardView> dueSoon(@RequestParam(required = false) Integer days,
                                             @RequestParam(required = false) String scope) {
        return kanbanService.dueSoon(days, "team".equalsIgnoreCase(scope));
    }

    /**
     * 시작일이 기간 안에 있는 카드. 주간보고 작성 화면이 쓴다.
     * 기본은 내가 담당한 것 — 팀 보드의 남의 카드가 내 주간보고에 쏟아지면 안 된다.
     */
    @GetMapping("/cards/started-between")
    public List<KanbanDtos.CardView> startedBetween(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "true") boolean mineOnly) {
        return kanbanService.startedBetween(from, to, projectId, mineOnly);
    }
}
