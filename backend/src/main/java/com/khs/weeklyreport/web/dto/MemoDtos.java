package com.khs.weeklyreport.web.dto;

import com.khs.weeklyreport.domain.Memo;
import com.khs.weeklyreport.domain.MemoFolder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 메모와 폴더 DTO. */
public final class MemoDtos {

    private MemoDtos() {
    }

    // ── 폴더 ─────────────────────────────────────────────────

    public record FolderRequest(
            @NotBlank @Size(max = 100) String name,
            /** 비우면 최상위. */
            Long parentId
    ) {
    }

    /** 옮기기 전용. 이름은 그대로 두고 위치만 바꾼다. */
    public record FolderMoveRequest(Long parentId) {
    }

    public record FolderView(
            Long id,
            Long parentId,
            String name,
            int sortOrder,
            /** 이 폴더에 바로 들어 있는 메모 수. 하위 폴더 것은 세지 않는다. */
            long memoCount,
            Instant updatedAt
    ) {
        public static FolderView of(MemoFolder folder, long memoCount) {
            return new FolderView(folder.getId(), folder.parentId(), folder.getName(),
                    folder.getSortOrder(), memoCount, folder.getUpdatedAt());
        }
    }

    // ── 메모 ─────────────────────────────────────────────────

    public record MemoRequest(
            @Size(max = 200) String title,
            String content,
            Long folderId,
            @Size(max = 60) String fontFamily,
            @Min(9) @Max(48) Integer fontSize,
            @Size(max = 20) String fontColor,
            Boolean wordWrap
    ) {
    }

    /** 옮기기 전용. 트리에서 끌어다 놓을 때 쓴다. */
    public record MemoMoveRequest(Long folderId) {
    }

    /** 목록용. 본문 대신 첫 줄 미리보기만 싣는다. */
    public record MemoSummary(
            Long id,
            Long folderId,
            String title,
            String preview,
            int chars,
            Instant updatedAt
    ) {
        public static MemoSummary of(Memo memo) {
            return new MemoSummary(memo.getId(), memo.folderId(), memo.getTitle(), memo.preview(),
                    memo.getContent() == null ? 0 : memo.getContent().length(), memo.getUpdatedAt());
        }
    }

    public record MemoView(
            Long id,
            Long folderId,
            String title,
            String content,
            String fontFamily,
            Integer fontSize,
            String fontColor,
            boolean wordWrap,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static MemoView of(Memo memo) {
            return new MemoView(memo.getId(), memo.folderId(), memo.getTitle(), memo.getContent(),
                    memo.getFontFamily(), memo.getFontSize(), memo.getFontColor(), memo.isWordWrap(),
                    memo.getCreatedAt(), memo.getUpdatedAt());
        }
    }

    /** 화면이 처음에 한 번에 받아가는 것. 폴더 트리와 메모 목록을 따로 부르지 않는다. */
    public record WorkspaceView(
            java.util.List<FolderView> folders,
            java.util.List<MemoSummary> memos
    ) {
    }
}
