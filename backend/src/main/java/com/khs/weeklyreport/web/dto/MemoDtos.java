package com.khs.weeklyreport.web.dto;

import com.khs.weeklyreport.domain.Memo;
import com.khs.weeklyreport.domain.MemoFolder;
import com.khs.weeklyreport.domain.MemoImageTokens;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

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

    /**
     * 고정·즐겨찾기를 켜고 끈다.
     *
     * <p>값을 받아서 켠다 — 서버가 알아서 뒤집게 두면 버튼을 두 번 누르거나
     * 탭 두 개에서 누를 때 눌린 모양과 실제 상태가 엇갈린다.
     */
    public record FlagRequest(@NotNull Boolean value) {
    }

    /** 목록용. 본문 대신 첫 줄 미리보기만 싣는다. */
    public record MemoSummary(
            Long id,
            Long folderId,
            String title,
            String preview,
            int chars,
            boolean pinned,
            boolean favorite,
            Instant updatedAt,
            /** 휴지통에 넣은 시각. 살아 있으면 비어 있다. */
            Instant deletedAt,
            /** 본문에 든 그림 수. 목록 줄에 그림 표시를 단다. */
            int images
    ) {
        public static MemoSummary of(Memo memo) {
            return new MemoSummary(memo.getId(), memo.folderId(), memo.getTitle(), memo.preview(),
                    memo.getContent() == null ? 0 : memo.getContent().length(),
                    memo.isPinned(), memo.isFavorite(), memo.getUpdatedAt(), memo.getDeletedAt(),
                    MemoImageTokens.count(memo.getContent()));
        }
    }

    /**
     * 찾은 메모 하나.
     *
     * @param snippet 찾는 말이 든 본문 한 토막. 앞이 잘렸으면 '…'로 시작한다.
     *                제목에만 있으면 첫 줄 미리보기.
     * @param inTitle 제목에서 찾았는지. 목록이 '제목에 있음 / 본문에 있음'으로 묶는다.
     */
    public record SearchHit(MemoSummary memo, String snippet, boolean inTitle) {
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
            boolean pinned,
            boolean favorite,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt,
            /**
             * 본문 토큰이 가리키는 그림. 본문 순서대로. 토큰은 있는데 여기 없으면
             * 지워졌거나 남의 그림이다 — 화면은 '그림 없음'으로 그린다.
             */
            List<ImageView> images
    ) {
        public static MemoView of(Memo memo, List<ImageView> images) {
            return new MemoView(memo.getId(), memo.folderId(), memo.getTitle(), memo.getContent(),
                    memo.getFontFamily(), memo.getFontSize(), memo.getFontColor(), memo.isWordWrap(),
                    memo.isPinned(), memo.isFavorite(), memo.getCreatedAt(), memo.getUpdatedAt(),
                    memo.getDeletedAt(), images);
        }
    }

    // ── 그림 ─────────────────────────────────────────────────

    /**
     * 그림 한 장.
     *
     * @param url      원본 주소. 서명과 만료가 붙어 있어 {@code <img src>} 로 바로 쓴다.
     * @param thumbUrl 긴 변 320px 섬네일 주소.
     * @param name     올릴 때의 파일 이름. 내려받을 때 쓴다.
     */
    public record ImageView(
            String id,
            String url,
            String thumbUrl,
            String contentType,
            int byteSize,
            int width,
            int height,
            String name
    ) {
    }

    /**
     * 그림이 쓰는 공간. 마이페이지에 보인다.
     *
     * @param bytes        지금 쓰는 바이트. 한도를 잴 때 세는 것과 같다(곧 지워질 것도 든다).
     * @param orphanCount  아무도 가리키지 않아 하루 안에 지워질 그림 수
     * @param memos        그림을 많이 쓰는 메모. 바이트가 큰 순서
     */
    public record ImageUsage(
            long count,
            long bytes,
            long quotaBytes,
            long maxBytes,
            int maxPerMemo,
            long orphanCount,
            long orphanBytes,
            List<MemoImageUsage> memos
    ) {
    }

    public record MemoImageUsage(Long id, String title, boolean trashed, long images, long bytes) {
    }

    /**
     * 화면이 처음에 한 번에 받아가는 것. 폴더 트리와 메모 목록을 따로 부르지 않는다.
     * 휴지통도 함께 싣는다 — 사이드바가 몇 장 들었는지 늘 보여준다.
     */
    public record WorkspaceView(
            List<FolderView> folders,
            List<MemoSummary> memos,
            List<MemoSummary> trash
    ) {
    }
}
