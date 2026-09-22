package com.khs.weeklyreport.web;

import com.khs.weeklyreport.service.MemoService;
import com.khs.weeklyreport.web.dto.MemoDtos;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/** 메모장. 개인 것이라 모든 경로가 로그인한 사람 기준으로만 동작한다. */
@RestController
@RequestMapping("/api/memos")
public class MemoController {

    private static final MediaType TEXT_UTF8 =
            new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final MemoService memoService;

    public MemoController(MemoService memoService) {
        this.memoService = memoService;
    }

    /** 폴더 트리와 메모 목록을 한 번에. 화면이 두 번 부르지 않게 한다. */
    @GetMapping
    public MemoDtos.WorkspaceView workspace() {
        return memoService.workspace();
    }

    // ── 폴더 ─────────────────────────────────────────────────

    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public MemoDtos.FolderView createFolder(@Valid @RequestBody MemoDtos.FolderRequest request) {
        return memoService.createFolder(request);
    }

    @PutMapping("/folders/{id}")
    public MemoDtos.FolderView renameFolder(@PathVariable Long id,
                                            @Valid @RequestBody MemoDtos.FolderRequest request) {
        return memoService.renameFolder(id, request);
    }

    @PutMapping("/folders/{id}/move")
    public MemoDtos.FolderView moveFolder(@PathVariable Long id,
                                          @RequestBody MemoDtos.FolderMoveRequest request) {
        return memoService.moveFolder(id, request);
    }

    /** 하위 폴더는 함께 사라지지만 메모는 최상위로 내려온다. */
    @DeleteMapping("/folders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@PathVariable Long id) {
        memoService.deleteFolder(id);
    }

    // ── 메모 ─────────────────────────────────────────────────

    @GetMapping("/{id}")
    public MemoDtos.MemoView get(@PathVariable Long id) {
        return memoService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemoDtos.MemoView create(@Valid @RequestBody MemoDtos.MemoRequest request) {
        return memoService.create(request);
    }

    @PutMapping("/{id}")
    public MemoDtos.MemoView update(@PathVariable Long id,
                                    @Valid @RequestBody MemoDtos.MemoRequest request) {
        return memoService.update(id, request);
    }

    @PutMapping("/{id}/move")
    public MemoDtos.MemoView move(@PathVariable Long id,
                                  @RequestBody MemoDtos.MemoMoveRequest request) {
        return memoService.move(id, request);
    }

    /*
     * 깃발은 본문 저장과 따로 받는다. 저장 경로에 얹으면 편집기가 1.2초마다 보내는
     * 자동 저장이 그때 열려 있던 값으로 깃발을 되돌린다 — 다른 탭에서 방금 누른 별이
     * 소리 없이 풀린다.
     */

    @PutMapping("/{id}/pin")
    public MemoDtos.MemoView pin(@PathVariable Long id,
                                 @Valid @RequestBody MemoDtos.FlagRequest request) {
        return memoService.setPinned(id, request.value());
    }

    @PutMapping("/{id}/favorite")
    public MemoDtos.MemoView favorite(@PathVariable Long id,
                                      @Valid @RequestBody MemoDtos.FlagRequest request) {
        return memoService.setFavorite(id, request.value());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        memoService.delete(id);
    }

    /** 메모를 txt 로 내려받는다. 줄 끝은 메모장이 읽을 수 있게 CRLF 로 나간다. */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        MemoService.TextFile file = memoService.exportText(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(TEXT_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(file.content());
    }
}
