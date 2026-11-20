package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.Memo;
import com.khs.weeklyreport.domain.MemoFolder;
import com.khs.weeklyreport.repository.MemoFolderRepository;
import com.khs.weeklyreport.repository.MemoRepository;
import com.khs.weeklyreport.security.CurrentUser;
import com.khs.weeklyreport.web.dto.MemoDtos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 메모와 폴더.
 *
 * <p>개인 것이라 권한 판정이 단순하다 — 모든 조회에 owner 를 함께 건다.
 * 남의 메모는 없는 것으로 답한다(404). 있는데 못 본다고 알려줄 이유가 없다.
 */
@Service
public class MemoService {

    private static final String UNTITLED = "제목 없음";

    /** 폴더 중첩 한계. 이보다 깊어지면 트리를 그리기도, 찾기도 어려워진다. */
    private static final int MAX_DEPTH = 8;

    /** 휴지통에 이만큼 들어 있으면 정리한다. */
    static final Duration TRASH_KEEP = Duration.ofDays(30);

    /** 검색 결과 상한. 개인 메모에서 이보다 많이 걸리면 검색어를 좁히는 편이 빠르다. */
    private static final int SEARCH_LIMIT = 100;

    /** 검색 결과에 보일 본문 토막 길이와, 찾은 말 앞에 남길 글자 수. */
    private static final int SNIPPET_LENGTH = 120;
    private static final int SNIPPET_LEAD = 16;

    private final MemoRepository memoRepository;
    private final MemoFolderRepository folderRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    @Autowired
    public MemoService(MemoRepository memoRepository,
                       MemoFolderRepository folderRepository,
                       CurrentUser currentUser) {
        this(memoRepository, folderRepository, currentUser, Clock.systemUTC());
    }

    MemoService(MemoRepository memoRepository,
                MemoFolderRepository folderRepository,
                CurrentUser currentUser,
                Clock clock) {
        this.memoRepository = memoRepository;
        this.folderRepository = folderRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ── 한 번에 읽기 ─────────────────────────────────────────

    /**
     * 폴더 · 메모 · 휴지통을 한 번에.
     *
     * <p>휴지통 정리를 여기서 한다. 따로 스케줄러를 두면 서버가 꺼져 있던 동안의 몫이
     * 밀리고, 어차피 휴지통을 보는 곳은 이 화면뿐이라 열 때 치우면 충분하다.
     */
    @Transactional
    public MemoDtos.WorkspaceView workspace() {
        Long ownerId = currentUser.requireId();
        memoRepository.purgeTrashedBefore(ownerId, clock.instant().minus(TRASH_KEEP));

        List<Memo> memos = memoRepository.findMine(ownerId);

        // 폴더마다 세면 폴더 수만큼 쿼리가 나간다. 이미 읽은 목록에서 센다.
        Map<Long, Long> counts = new HashMap<>();
        for (Memo memo : memos) {
            Long folderId = memo.folderId();
            if (folderId != null) counts.merge(folderId, 1L, Long::sum);
        }

        List<MemoDtos.FolderView> folders = folderRepository.findMine(ownerId).stream()
                .map(folder -> MemoDtos.FolderView.of(folder, counts.getOrDefault(folder.getId(), 0L)))
                .toList();

        return new MemoDtos.WorkspaceView(
                folders,
                memos.stream().map(MemoDtos.MemoSummary::of).toList(),
                memoRepository.findTrash(ownerId).stream().map(MemoDtos.MemoSummary::of).toList());
    }

    // ── 찾기 ─────────────────────────────────────────────────

    /**
     * 제목과 본문에서 찾는다.
     *
     * <p>어느 폴더에 넣었는지 기억나지 않아서 찾는 것이므로 폴더를 가리지 않는다.
     * 제목에서 찾은 것이 앞에 온다 — 사람은 찾는 말을 대개 제목으로 기억한다.
     */
    @Transactional(readOnly = true)
    public List<MemoDtos.SearchHit> search(String query) {
        String q = query == null ? "" : query.strip();
        if (q.isEmpty()) return List.of();

        String needle = q.toLowerCase(Locale.ROOT);
        List<MemoDtos.SearchHit> hits = memoRepository.search(currentUser.requireId(), likePattern(needle))
                .stream()
                .limit(SEARCH_LIMIT)
                .map(memo -> {
                    boolean inTitle = memo.getTitle().toLowerCase(Locale.ROOT).contains(needle);
                    return new MemoDtos.SearchHit(MemoDtos.MemoSummary.of(memo),
                            snippet(memo.getContent(), needle, memo.preview()), inTitle);
                })
                .toList();

        return hits.stream()
                .sorted((a, b) -> Boolean.compare(b.inTitle(), a.inTitle()))
                .toList();
    }

    /**
     * 찾는 말이 든 본문 한 토막. 줄 바꿈은 한 칸으로 접는다 — 목록 한 줄에 들어가야 한다.
     * 본문에 없으면(제목에서만 찾았으면) 첫 줄 미리보기를 쓴다.
     */
    static String snippet(String content, String needle, String fallback) {
        if (content == null || content.isBlank()) return fallback;
        String flat = content.strip().replaceAll("\\s+", " ");
        int at = flat.toLowerCase(Locale.ROOT).indexOf(needle);
        if (at < 0) return fallback;

        int start = Math.max(0, at - SNIPPET_LEAD);
        int end = Math.min(flat.length(), start + SNIPPET_LENGTH);
        return (start > 0 ? "…" : "") + flat.substring(start, end) + (end < flat.length() ? "…" : "");
    }

    private static String likePattern(String lowered) {
        String escaped = lowered
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    // ── 폴더 ─────────────────────────────────────────────────

    @Transactional
    public MemoDtos.FolderView createFolder(MemoDtos.FolderRequest request) {
        AppUser me = currentUser.requireEntity();

        MemoFolder folder = new MemoFolder();
        folder.setOwner(me);
        folder.setName(request.name().trim());
        folder.setParent(resolveParent(request.parentId(), null));
        folder.setSortOrder((int) folderRepository.countByOwnerId(me.getId()));

        return MemoDtos.FolderView.of(folderRepository.save(folder), 0);
    }

    @Transactional
    public MemoDtos.FolderView renameFolder(Long id, MemoDtos.FolderRequest request) {
        MemoFolder folder = loadFolder(id);
        folder.setName(request.name().trim());
        return MemoDtos.FolderView.of(folderRepository.save(folder), memosIn(id));
    }

    @Transactional
    public MemoDtos.FolderView moveFolder(Long id, MemoDtos.FolderMoveRequest request) {
        MemoFolder folder = loadFolder(id);
        folder.setParent(resolveParent(request.parentId(), id));
        return MemoDtos.FolderView.of(folderRepository.save(folder), memosIn(id));
    }

    /**
     * 폴더를 지운다. 하위 폴더는 함께 사라지지만 <b>메모는 남는다</b> —
     * DB 가 folder_id 를 비워 최상위로 내려보낸다. 폴더를 정리하다 글이
     * 통째로 없어지는 것보다, 최상위에 나와 있는 편이 낫다.
     */
    @Transactional
    public void deleteFolder(Long id) {
        folderRepository.delete(loadFolder(id));
    }

    // ── 메모 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MemoDtos.MemoView get(Long id) {
        return MemoDtos.MemoView.of(loadMemo(id));
    }

    @Transactional
    public MemoDtos.MemoView create(MemoDtos.MemoRequest request) {
        AppUser me = currentUser.requireEntity();

        Memo memo = new Memo();
        memo.setOwner(me);
        memo.setSortOrder((int) memoRepository.countMine(me.getId()));
        apply(memo, request);

        return MemoDtos.MemoView.of(memoRepository.save(memo));
    }

    @Transactional
    public MemoDtos.MemoView update(Long id, MemoDtos.MemoRequest request) {
        Memo memo = loadMemo(id);
        if (memo.isTrashed()) {
            throw new IllegalArgumentException("휴지통에 있는 메모는 고칠 수 없습니다. 먼저 되살리세요.");
        }
        apply(memo, request);
        return MemoDtos.MemoView.of(memoRepository.save(memo));
    }

    @Transactional
    public MemoDtos.MemoView move(Long id, MemoDtos.MemoMoveRequest request) {
        Memo memo = loadMemo(id);
        memo.setFolder(request.folderId() == null ? null : loadFolder(request.folderId()));
        return MemoDtos.MemoView.of(memoRepository.save(memo));
    }

    /**
     * 폴더 안에서 맨 위로 올리거나 내린다.
     *
     * <p>깃발은 고친 시각을 건드리지 않는 쿼리로 바꾼다. 그 쿼리가 영속성 컨텍스트를
     * 비우므로, 바뀐 값을 담아 돌려주려면 한 번 더 읽어야 한다.
     */
    @Transactional
    public MemoDtos.MemoView setPinned(Long id, boolean value) {
        Memo memo = loadMemo(id);
        if (memo.isPinned() == value) return MemoDtos.MemoView.of(memo);

        memoRepository.updatePinned(id, currentUser.requireId(), value);
        return MemoDtos.MemoView.of(loadMemo(id));
    }

    @Transactional
    public MemoDtos.MemoView setFavorite(Long id, boolean value) {
        Memo memo = loadMemo(id);
        if (memo.isFavorite() == value) return MemoDtos.MemoView.of(memo);

        memoRepository.updateFavorite(id, currentUser.requireId(), value);
        return MemoDtos.MemoView.of(loadMemo(id));
    }

    /**
     * 휴지통으로 보낸다. 지운 시각만 적고 행은 남긴다 — 잘못 눌러도 되살릴 수 있다.
     * 이미 휴지통에 있으면 아무 일도 하지 않는다(지운 시각이 늦춰지지 않게).
     */
    @Transactional
    public void delete(Long id) {
        Memo memo = loadMemo(id);
        if (memo.isTrashed()) return;
        memoRepository.updateDeletedAt(id, currentUser.requireId(), clock.instant());
    }

    /** 휴지통에서 꺼낸다. 고친 시각은 그대로라 목록의 원래 자리로 돌아간다. */
    @Transactional
    public MemoDtos.MemoView restore(Long id) {
        Memo memo = loadMemo(id);
        if (!memo.isTrashed()) return MemoDtos.MemoView.of(memo);

        memoRepository.updateDeletedAt(id, currentUser.requireId(), null);
        return MemoDtos.MemoView.of(loadMemo(id));
    }

    /** 휴지통에 든 것만 영구히 지운다. 살아 있는 메모를 한 번에 없애는 길은 두지 않는다. */
    @Transactional
    public void purge(Long id) {
        Memo memo = loadMemo(id);
        if (!memo.isTrashed()) {
            throw new IllegalArgumentException("휴지통에 있는 메모만 영구 삭제할 수 있습니다.");
        }
        memoRepository.delete(memo);
    }

    /** 휴지통 비우기. 지운 장 수를 돌려준다. */
    @Transactional
    public int emptyTrash() {
        return memoRepository.purgeTrash(currentUser.requireId());
    }

    // ── 내보내기 ─────────────────────────────────────────────

    public record TextFile(String filename, byte[] content) {
    }

    /**
     * 메모 하나를 txt 로. 줄 끝은 CRLF 로 바꾼다 —
     * 메모장으로 열었을 때 줄이 안 나뉘고 한 줄로 붙어 보이는 것을 막는다.
     */
    @Transactional(readOnly = true)
    public TextFile exportText(Long id) {
        Memo memo = loadMemo(id);
        String body = memo.getContent() == null ? "" : memo.getContent();
        byte[] bytes = body.replace("\r\n", "\n").replace("\n", "\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return new TextFile(safeFilename(memo.getTitle()) + ".txt", bytes);
    }

    // ── 내부 ─────────────────────────────────────────────────

    private void apply(Memo memo, MemoDtos.MemoRequest request) {
        String title = request.title() == null ? "" : request.title().trim();
        memo.setTitle(title.isEmpty() ? UNTITLED : title);
        memo.setContent(request.content() == null ? "" : request.content());
        memo.setFolder(request.folderId() == null ? null : loadFolder(request.folderId()));

        memo.setFontFamily(blankToNull(request.fontFamily()));
        memo.setFontSize(request.fontSize());
        memo.setFontColor(blankToNull(request.fontColor()));
        if (request.wordWrap() != null) {
            memo.setWordWrap(request.wordWrap());
        }
    }

    /**
     * 옮겨갈 부모를 찾는다.
     *
     * <p>{@code movingId} 가 주어지면 그 폴더를 자기 자신이나 자기 후손 밑으로
     * 넣으려는지 본다. 막지 않으면 트리에서 떨어져 나와 어느 화면에도 나타나지 않는
     * 고리가 생기고, 되돌릴 방법도 없다.
     */
    private MemoFolder resolveParent(Long parentId, Long movingId) {
        if (parentId == null) return null;

        MemoFolder parent = loadFolder(parentId);
        int depth = 1;
        for (MemoFolder cursor = parent; cursor != null; cursor = cursor.getParent()) {
            if (movingId != null && movingId.equals(cursor.getId())) {
                throw new IllegalArgumentException("폴더를 자기 자신이나 하위 폴더로 옮길 수 없습니다.");
            }
            if (++depth > MAX_DEPTH) {
                throw new IllegalArgumentException("폴더는 %d단계까지만 만들 수 있습니다.".formatted(MAX_DEPTH));
            }
        }
        return parent;
    }

    private long memosIn(Long folderId) {
        return memoRepository.countByOwnerIdAndFolderIdAndDeletedAtIsNull(currentUser.requireId(), folderId);
    }

    private MemoFolder loadFolder(Long id) {
        return folderRepository.findByIdAndOwnerId(id, currentUser.requireId())
                .orElseThrow(() -> new NotFoundException("폴더를 찾을 수 없습니다. id=" + id));
    }

    private Memo loadMemo(Long id) {
        return memoRepository.findByIdAndOwnerId(id, currentUser.requireId())
                .orElseThrow(() -> new NotFoundException("메모를 찾을 수 없습니다. id=" + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 파일명에 쓸 수 없는 문자를 걷어낸다. 제목은 사용자가 자유롭게 적는다. */
    private static String safeFilename(String title) {
        if (title == null || title.isBlank()) return UNTITLED;
        String cleaned = title.trim().replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }
}
