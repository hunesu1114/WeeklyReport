package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.Memo;
import com.khs.weeklyreport.domain.MemoFolder;
import com.khs.weeklyreport.repository.MemoFolderRepository;
import com.khs.weeklyreport.repository.MemoRepository;
import com.khs.weeklyreport.security.CurrentUser;
import com.khs.weeklyreport.web.dto.MemoDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoServiceTest {

    private static final Long ME = 1L;

    private final Map<Long, MemoFolder> folders = new HashMap<>();
    private MemoRepository memoRepository;
    private MemoService service;
    private AppUser me;

    @BeforeEach
    void setUp() {
        me = new AppUser();
        me.setId(ME);
        me.setUsername("kim");
        me.setDisplayName("김현수");

        memoRepository = mock(MemoRepository.class);
        when(memoRepository.save(any(Memo.class))).thenAnswer(c -> c.getArgument(0));
        when(memoRepository.countMine(anyLong())).thenReturn(0L);

        MemoFolderRepository folderRepository = mock(MemoFolderRepository.class);
        when(folderRepository.save(any(MemoFolder.class))).thenAnswer(c -> c.getArgument(0));
        when(folderRepository.countByOwnerId(anyLong())).thenReturn(0L);
        when(folderRepository.findByIdAndOwnerId(anyLong(), anyLong()))
                .thenAnswer(c -> Optional.ofNullable(folders.get(c.<Long>getArgument(0))));

        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.requireId()).thenReturn(ME);
        when(currentUser.requireEntity()).thenReturn(me);

        service = new MemoService(memoRepository, folderRepository, currentUser);
    }

    // ── 폴더 트리 ────────────────────────────────────────────

    @Test
    void 폴더를_자기_자신_밑으로_옮길_수_없다() {
        folder(10L, null);

        assertThatThrownBy(() -> service.moveFolder(10L, new MemoDtos.FolderMoveRequest(10L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("하위 폴더로 옮길 수 없습니다");
    }

    @Test
    void 폴더를_자기_후손_밑으로_옮길_수_없다() {
        // 업무 > 2026 > 9월  —  '업무'를 '9월' 밑으로 넣으면 트리에서 떨어져 나간다
        MemoFolder work = folder(10L, null);
        MemoFolder year = folder(11L, work);
        folder(12L, year);

        assertThatThrownBy(() -> service.moveFolder(10L, new MemoDtos.FolderMoveRequest(12L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 형제_폴더_밑으로는_옮길_수_있다() {
        folder(10L, null);
        folder(20L, null);

        MemoDtos.FolderView moved = service.moveFolder(10L, new MemoDtos.FolderMoveRequest(20L));

        assertThat(moved.parentId()).isEqualTo(20L);
    }

    @Test
    void 최상위로_되돌릴_수_있다() {
        MemoFolder parent = folder(20L, null);
        folder(10L, parent);

        assertThat(service.moveFolder(10L, new MemoDtos.FolderMoveRequest(null)).parentId()).isNull();
    }

    @Test
    void 너무_깊은_폴더는_막는다() {
        MemoFolder cursor = null;
        for (long id = 1; id <= 8; id++) {
            cursor = folder(100 + id, cursor);
        }
        folder(200L, null);

        assertThatThrownBy(() -> service.moveFolder(200L, new MemoDtos.FolderMoveRequest(108L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("단계까지만");
    }

    // ── 메모 ─────────────────────────────────────────────────

    @Test
    void 제목을_비우면_제목_없음이_된다() {
        MemoDtos.MemoView view = service.create(new MemoDtos.MemoRequest(
                "   ", "본문", null, null, null, null, null));

        assertThat(view.title()).isEqualTo("제목 없음");
    }

    @Test
    void 남의_메모는_없는_것으로_답한다() {
        when(memoRepository.findByIdAndOwnerId(99L, ME)).thenReturn(Optional.empty());

        // 있는데 못 본다고 알려줄 이유가 없다
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 글꼴_설정은_메모마다_남는다() {
        MemoDtos.MemoView view = service.create(new MemoDtos.MemoRequest(
                "메모", "본문", null, "D2Coding", 16, "#dc2626", false));

        assertThat(view.fontFamily()).isEqualTo("D2Coding");
        assertThat(view.fontSize()).isEqualTo(16);
        assertThat(view.fontColor()).isEqualTo("#dc2626");
        assertThat(view.wordWrap()).isFalse();
    }

    // ── 고정과 즐겨찾기 ──────────────────────────────────────

    @Test
    void 고정하면_고친_시각은_그대로_둔다() {
        Memo memo = memo(7L);
        Instant before = memo.getUpdatedAt();

        MemoDtos.MemoView view = service.setPinned(7L, true);

        assertThat(view.pinned()).isTrue();
        // 별을 눌렀을 뿐인데 목록 맨 위로 튀어 오르면 안 된다
        assertThat(view.updatedAt()).isEqualTo(before);
    }

    @Test
    void 이미_고정된_메모를_또_고정해도_쿼리를_보내지_않는다() {
        memo(7L).setPinned(true);

        assertThat(service.setPinned(7L, true).pinned()).isTrue();
        verify(memoRepository, never()).updatePinned(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void 즐겨찾기는_고정과_따로_켜진다() {
        memo(7L);

        MemoDtos.MemoView view = service.setFavorite(7L, true);

        assertThat(view.favorite()).isTrue();
        // 폴더 안 자리(고정)와 어디서나 꺼내 보기(즐겨찾기)는 다른 이야기다
        assertThat(view.pinned()).isFalse();
    }

    @Test
    void 남의_메모는_고정할_수_없다() {
        when(memoRepository.findByIdAndOwnerId(99L, ME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPinned(99L, true)).isInstanceOf(NotFoundException.class);
        verify(memoRepository, never()).updatePinned(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void 내보낸_txt_는_줄_끝이_CRLF_다() {
        Memo memo = new Memo();
        memo.setId(5L);
        memo.setOwner(me);
        memo.setTitle("회의 기록");
        memo.setContent("첫 줄\n둘째 줄");
        when(memoRepository.findByIdAndOwnerId(5L, ME)).thenReturn(Optional.of(memo));

        MemoService.TextFile file = service.exportText(5L);

        assertThat(file.filename()).isEqualTo("회의 기록.txt");
        // LF 만 내보내면 메모장에서 한 줄로 붙어 보인다
        assertThat(new String(file.content(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("첫 줄\r\n둘째 줄");
    }

    @Test
    void 파일명에_쓸_수_없는_문자는_걷어낸다() {
        Memo memo = new Memo();
        memo.setId(6L);
        memo.setOwner(me);
        memo.setTitle("9/16 회의: 결론?");
        memo.setContent("");
        when(memoRepository.findByIdAndOwnerId(6L, ME)).thenReturn(Optional.of(memo));

        assertThat(service.exportText(6L).filename()).isEqualTo("9_16 회의_ 결론_.txt");
    }

    /** 내 메모 한 장. 깃발 쿼리는 DB 가 하듯 그 자리에서 값만 바꾼다. */
    private Memo memo(Long id) {
        Memo memo = new Memo();
        memo.setId(id);
        memo.setOwner(me);
        memo.setTitle("메모" + id);
        memo.setContent("본문");
        memo.setUpdatedAt(Instant.parse("2026-09-01T00:00:00Z"));
        when(memoRepository.findByIdAndOwnerId(id, ME)).thenReturn(Optional.of(memo));

        doAnswer(c -> {
            memo.setPinned(c.getArgument(2));
            return null;
        }).when(memoRepository).updatePinned(eq(id), eq(ME), anyBoolean());

        doAnswer(c -> {
            memo.setFavorite(c.getArgument(2));
            return null;
        }).when(memoRepository).updateFavorite(eq(id), eq(ME), anyBoolean());

        return memo;
    }

    private MemoFolder folder(Long id, MemoFolder parent) {
        MemoFolder folder = new MemoFolder();
        folder.setId(id);
        folder.setOwner(me);
        folder.setParent(parent);
        folder.setName("폴더" + id);
        folders.put(id, folder);
        return folder;
    }
}
