package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.Memo;
import com.khs.weeklyreport.domain.MemoImage;
import com.khs.weeklyreport.domain.MemoImageBlob;
import com.khs.weeklyreport.domain.MemoImageTokens;
import com.khs.weeklyreport.repository.MemoImageBlobRepository;
import com.khs.weeklyreport.repository.MemoImageRepository;
import com.khs.weeklyreport.repository.MemoRepository;
import com.khs.weeklyreport.security.CurrentUser;
import com.khs.weeklyreport.security.MemoImageSigner;
import com.khs.weeklyreport.web.dto.MemoDtos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 메모 그림: 올리기 · 내려주기 · 메모가 가리키는 그림 맞추기 · 정리.
 *
 * <p>그림을 줄이는 일은 브라우저가 한다(긴 변 2000px, 섬네일 320px). 서버는 이미지
 * 라이브러리 없이 받은 것을 검사만 한다 — 형식은 파일 앞부분 바이트로 직접 보고,
 * 크기 · 장 수 · 사람당 용량을 센다.
 */
@Service
public class MemoImageService {

    /** 아무도 가리키지 않게 된 그림을 이만큼 기다렸다 지운다. 지운 토큰을 되살릴 여유다. */
    static final Duration ORPHAN_GRACE = Duration.ofHours(24);

    /** 섬네일 상한. 320px 이면 보통 20~40KB 다. 섬네일 칸에 원본을 실어 한도를 피해 가지 못하게 막는다. */
    private static final int MAX_THUMB_BYTES = 256 * 1024;

    /** 가로 · 세로 상한. 브라우저가 2000px 로 줄여 보내므로 넉넉하다. 화면 배치에만 쓰는 값이다. */
    private static final int MAX_SIDE = 20_000;

    /** 마이페이지에 보일 '그림을 많이 쓰는 메모' 수. */
    private static final int HEAVY_MEMOS = 5;

    /** zip 안 그림 파일 이름에 붙일 설명 길이(글자). 넘으면 자른다. */
    private static final int SLUG_LENGTH = 40;

    private final MemoImageRepository imageRepository;
    private final MemoImageBlobRepository blobRepository;
    private final MemoRepository memoRepository;
    private final CurrentUser currentUser;
    private final MemoImageSigner signer;
    private final Clock clock;
    private final Limits limits;

    /** 한도. 운영 서버 디스크와 백업 시간에 맞춰 설정으로 바꾼다. */
    public record Limits(long maxBytes, int maxPerMemo, long quotaBytes) {
    }

    @Autowired
    public MemoImageService(MemoImageRepository imageRepository,
                            MemoImageBlobRepository blobRepository,
                            MemoRepository memoRepository,
                            CurrentUser currentUser,
                            MemoImageSigner signer,
                            @Value("${app.memo-image.max-bytes:5MB}") DataSize maxBytes,
                            @Value("${app.memo-image.max-per-memo:50}") int maxPerMemo,
                            @Value("${app.memo-image.quota-per-user:300MB}") DataSize quota) {
        this(imageRepository, blobRepository, memoRepository, currentUser, signer, Clock.systemUTC(),
                new Limits(maxBytes.toBytes(), maxPerMemo, quota.toBytes()));
    }

    MemoImageService(MemoImageRepository imageRepository,
                     MemoImageBlobRepository blobRepository,
                     MemoRepository memoRepository,
                     CurrentUser currentUser,
                     MemoImageSigner signer,
                     Clock clock,
                     Limits limits) {
        this.imageRepository = imageRepository;
        this.blobRepository = blobRepository;
        this.memoRepository = memoRepository;
        this.currentUser = currentUser;
        this.signer = signer;
        this.clock = clock;
        this.limits = limits;
    }

    // ── 올리기 ───────────────────────────────────────────────

    /**
     * 그림 한 장을 받는다.
     *
     * <p>토큰은 이미 본문에 들어가 있다. 아이디를 브라우저가 정하므로, 같은 아이디로
     * 다시 보내면(응답을 못 받고 다시 올린 경우) 새로 만들지 않고 있는 것을 돌려준다.
     */
    @Transactional
    public MemoDtos.ImageView upload(Long memoId, String imageId, MultipartFile file, MultipartFile thumb,
                                     int width, int height, String name) {
        Long ownerId = currentUser.requireId();
        if (imageId == null || !MemoImageTokens.ID.matcher(imageId).matches()) {
            throw new IllegalArgumentException("그림 아이디가 올바르지 않습니다.");
        }

        Memo memo = memoRepository.findByIdAndOwnerId(memoId, ownerId)
                .orElseThrow(() -> new NotFoundException("메모를 찾을 수 없습니다. id=" + memoId));
        if (memo.isTrashed()) {
            throw new IllegalArgumentException("휴지통에 있는 메모에는 그림을 넣을 수 없습니다.");
        }

        var existing = imageRepository.findById(imageId);
        if (existing.isPresent()) {
            if (!existing.get().getOwnerId().equals(ownerId)) {
                // 10자 무작위 아이디가 겹칠 일은 사실상 없다. 겹치면 남의 그림을 덮지 않고 거절한다
                throw new IllegalArgumentException("그림 아이디가 겹칩니다. 다시 넣어 주세요.");
            }
            return view(existing.get(), signer.expiry());
        }

        byte[] bytes = read(file, "올릴 그림이 없습니다.");
        if (bytes.length > limits.maxBytes()) {
            throw new IllegalArgumentException("그림이 너무 큽니다. 한 장에 %s까지 올릴 수 있습니다."
                    .formatted(human(limits.maxBytes())));
        }
        String type = sniff(bytes);
        if (type == null) {
            throw new IllegalArgumentException("PNG, JPG, WEBP, GIF 그림만 넣을 수 있습니다.");
        }

        byte[] thumbBytes = read(thumb, "섬네일이 없습니다.");
        if (thumbBytes.length > MAX_THUMB_BYTES || sniff(thumbBytes) == null) {
            throw new IllegalArgumentException("섬네일이 올바르지 않습니다.");
        }
        if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE) {
            throw new IllegalArgumentException("그림 크기가 올바르지 않습니다.");
        }

        if (imageRepository.countForMemo(ownerId, memoId) >= limits.maxPerMemo()) {
            throw new IllegalArgumentException("메모 하나에는 그림을 %d장까지 넣을 수 있습니다."
                    .formatted(limits.maxPerMemo()));
        }
        if (imageRepository.usedBytes(ownerId) + bytes.length > limits.quotaBytes()) {
            throw new IllegalArgumentException("그림 저장 공간(%s)을 다 썼습니다. 쓰지 않는 그림을 지운 뒤 다시 넣어 주세요."
                    .formatted(human(limits.quotaBytes())));
        }

        Instant now = clock.instant();
        MemoImage image = new MemoImage();
        image.setId(imageId);
        image.setOwnerId(ownerId);
        image.setMemoId(memoId);
        image.setContentType(type);
        image.setByteSize(bytes.length);
        image.setWidth(width);
        image.setHeight(height);
        image.setOriginalName(cleanName(name));
        image.setCreatedAt(now);
        // 태어날 때는 아무도 가리키지 않는다. 아래에서 본문에 토큰이 있으면 바로 지운다
        image.setOrphanedAt(now);
        imageRepository.save(image);
        blobRepository.save(MemoImageBlob.of(imageId, bytes, thumbBytes));

        /*
         * 자동 저장이 올리기보다 먼저 끝났을 수 있다. 그때는 저장된 본문에 토큰이 이미 있는데
         * 그림이 아직 없어 참조를 걸지 못했다. 사용자가 더 치지 않으면 다음 저장이 오지 않으니,
         * 여기서 저장된 본문으로 한 번 더 맞춘다.
         */
        if (MemoImageTokens.ids(memo.getContent()).contains(imageId)) {
            syncRefs(memo);
        }
        return view(image, signer.expiry());
    }

    // ── 내려주기 ─────────────────────────────────────────────

    public record ImageFile(byte[] bytes, String contentType, String filename, long maxAgeSeconds) {
    }

    /**
     * 서명이 맞으면 그림을 내준다. 이 경로는 로그인 없이 열려 있다.
     * 서명이 틀리거나 지났으면 그림이 있는지조차 알리지 않는다(404).
     */
    @Transactional(readOnly = true)
    public ImageFile read(String imageId, boolean thumb, long expiresAt, String signature) {
        if (!signer.verify(imageId, expiresAt, signature)) {
            throw new NotFoundException("그림을 찾을 수 없습니다.");
        }
        MemoImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("그림을 찾을 수 없습니다."));
        byte[] bytes = thumb ? blobRepository.findThumb(imageId) : blobRepository.findBytes(imageId);
        if (bytes == null) throw new NotFoundException("그림을 찾을 수 없습니다.");

        String type = thumb ? sniff(bytes) : image.getContentType();
        return new ImageFile(bytes, type == null ? "application/octet-stream" : type,
                downloadName(image), signer.secondsLeft(expiresAt));
    }

    // ── 메모가 가리키는 그림 ─────────────────────────────────

    /**
     * 저장된 본문의 토큰으로 가리키는 줄을 다시 맞춘다. 자동 저장마다 불린다 —
     * 정규식 한 번과 짧은 쿼리 몇 개라 가볍다. 남의 그림 아이디는 걸리지 않는다.
     */
    @Transactional
    public void syncRefs(Memo memo) {
        List<String> ids = MemoImageTokens.ids(memo.getContent());
        if (ids.isEmpty()) {
            imageRepository.clearRefs(memo.getId());
            return;
        }
        Long ownerId = memo.getOwner().getId();
        imageRepository.clearRefsExcept(memo.getId(), ids);
        imageRepository.addRefs(memo.getId(), ownerId, ids);
        imageRepository.adopt(ownerId, ids);
    }

    /** 본문 토큰이 가리키는 내 그림. 본문 순서대로. 없는 것은 빠진다. */
    @Transactional(readOnly = true)
    public List<MemoDtos.ImageView> viewsFor(Long ownerId, String content) {
        List<String> ids = MemoImageTokens.ids(content);
        if (ids.isEmpty()) return List.of();

        Map<String, MemoImage> found = imageRepository.findByOwnerIdAndIdIn(ownerId, ids).stream()
                .collect(Collectors.toMap(MemoImage::getId, Function.identity()));
        long expiresAt = signer.expiry();
        return ids.stream()
                .map(found::get)
                .filter(java.util.Objects::nonNull)
                .map(image -> view(image, expiresAt))
                .toList();
    }

    // ── 정리 ─────────────────────────────────────────────────

    /**
     * 아무도 가리키지 않은 지 하루가 지난 그림을 지운다.
     *
     * <p>휴지통처럼 메모 화면을 열 때 한다. 휴지통에 든 메모는 가리키는 줄을 그대로 두므로
     * 그 그림은 남아 있고, 30일 뒤 메모가 영구 삭제되면 그다음 정리부터 하루를 센다.
     */
    @Transactional
    public void collectGarbage(Long ownerId) {
        Instant now = clock.instant();
        imageRepository.markOrphans(ownerId, now);
        imageRepository.deleteOrphansBefore(ownerId, now.minus(ORPHAN_GRACE));
    }

    // ── 사용량 ───────────────────────────────────────────────

    /**
     * 내 그림이 쓰는 공간. 정리를 먼저 한다 — 그래야 '하루 안에 지워질 것' 숫자가
     * 화면을 연 시각과 맞고, 이미 지울 때가 지난 것이 쓰는 공간으로 잡히지 않는다.
     */
    @Transactional
    public MemoDtos.ImageUsage usage() {
        Long ownerId = currentUser.requireId();
        collectGarbage(ownerId);

        List<MemoDtos.MemoImageUsage> memos = imageRepository.heaviestMemos(ownerId, HEAVY_MEMOS).stream()
                .map(row -> new MemoDtos.MemoImageUsage(row.getMemoId(), row.getTitle(),
                        Boolean.TRUE.equals(row.getTrashed()), row.getImages(), row.getBytes()))
                .toList();
        return new MemoDtos.ImageUsage(
                imageRepository.countByOwnerId(ownerId),
                imageRepository.usedBytes(ownerId),
                limits.quotaBytes(),
                limits.maxBytes(),
                limits.maxPerMemo(),
                imageRepository.countByOwnerIdAndOrphanedAtIsNotNull(ownerId),
                imageRepository.orphanedBytes(ownerId),
                memos);
    }

    // ── zip 내보내기 ─────────────────────────────────────────

    /**
     * zip 에 담을 것.
     *
     * @param filename 내려받을 이름 ({@code 제목.zip})
     * @param textName zip 안의 본문 파일 이름 ({@code 제목.txt})
     * @param text     본문. 토큰은 zip 안의 그림 경로를 가리키게 바뀌어 있다
     */
    public record ZipPlan(String filename, String textName, byte[] text, List<ZipImage> images) {
    }

    public record ZipImage(String path, String imageId) {
    }

    /**
     * 메모 하나를 본문과 그림으로 묶을 준비를 한다. 바이트는 아직 읽지 않는다.
     *
     * <p>그림은 {@code images/01-설명.png} 로 들어간다. 번호는 그림 줄과 같다 — 본문에 처음 나온
     * 순서이고, 찾을 수 없는 그림도 번호 하나를 차지한다(화면의 '그림 3' 이 파일 03 이다).
     * 본문 토큰은 {@code ![설명](images/01-설명.png)} 로 바뀌어 마크다운 편집기에서는 그림이
     * 바로 보인다. 찾을 수 없는 그림은 txt 내보내기처럼 {@code [그림: 설명]} 으로 남긴다.
     */
    @Transactional(readOnly = true)
    public ZipPlan prepareZip(Long memoId) {
        Long ownerId = currentUser.requireId();
        Memo memo = memoRepository.findByIdAndOwnerId(memoId, ownerId)
                .orElseThrow(() -> new NotFoundException("메모를 찾을 수 없습니다. id=" + memoId));
        String content = memo.getContent() == null ? "" : memo.getContent();

        // 같은 그림이 여러 번 나오면 처음 나온 자리의 설명으로 이름을 짓는다
        Map<String, String> altOf = new LinkedHashMap<>();
        for (MemoImageTokens.Token token : MemoImageTokens.tokens(content)) {
            altOf.putIfAbsent(token.id(), token.alt());
        }
        Map<String, MemoImage> found = altOf.isEmpty() ? Map.of()
                : imageRepository.findByOwnerIdAndIdIn(ownerId, altOf.keySet()).stream()
                        .collect(Collectors.toMap(MemoImage::getId, Function.identity()));

        String numberFormat = "%0" + Math.max(2, String.valueOf(altOf.size()).length()) + "d";
        Map<String, String> paths = new HashMap<>();
        List<ZipImage> images = new ArrayList<>();
        int n = 0;
        for (Map.Entry<String, String> entry : altOf.entrySet()) {
            n++;
            MemoImage image = found.get(entry.getKey());
            if (image == null) continue;
            String name = slug(entry.getValue());
            if (name.isEmpty()) name = slug(stem(image.getOriginalName()));
            String path = "images/" + numberFormat.formatted(n) + (name.isEmpty() ? "" : "-" + name)
                    + extension(image.getContentType());
            paths.put(image.getId(), path);
            images.add(new ZipImage(path, image.getId()));
        }

        String body = MemoImageTokens.replace(content, (alt, id) -> {
            String path = paths.get(id);
            return path == null ? MemoImageTokens.label(alt) : "![" + alt + "](" + path + ")";
        });
        String base = MemoService.safeFilename(memo.getTitle());
        return new ZipPlan(base + ".zip", base + ".txt",
                MemoService.windowsLines(body).getBytes(StandardCharsets.UTF_8), images);
    }

    /**
     * zip 을 흘려 보낸다. 그림을 한 장씩 읽어 바로 쓰므로 그림이 50장이어도 서버 메모리에
     * 한꺼번에 올리지 않는다. 트랜잭션으로 묶지 않는다 — 느린 망으로 내려받는 동안 DB 연결을
     * 붙잡지 않게, 한 장마다 짧게 읽는다.
     *
     * <p>그림은 압축하지 않고 담는다(STORED). PNG · JPG · WEBP 는 이미 압축돼 있어 더 줄지 않는다.
     * 파일 이름은 UTF-8 로 쓰고 그 표시도 단다 — 한글 이름이 윈도 탐색기에서 깨지지 않는다.
     */
    public void writeZip(ZipPlan plan, OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        zip.putNextEntry(new ZipEntry(plan.textName()));
        zip.write(plan.text());
        zip.closeEntry();

        for (ZipImage image : plan.images()) {
            byte[] bytes = blobRepository.findBytes(image.imageId());
            if (bytes == null) continue; // 준비한 뒤 그 사이에 지워졌다
            ZipEntry entry = new ZipEntry(image.path());
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(bytes.length);
            entry.setCompressedSize(bytes.length);
            CRC32 crc = new CRC32();
            crc.update(bytes);
            entry.setCrc(crc.getValue());
            zip.putNextEntry(entry);
            zip.write(bytes);
            zip.closeEntry();
        }
        // 닫지는 않는다. 응답 스트림은 서블릿 컨테이너가 닫는다
        zip.finish();
        zip.flush();
    }

    /**
     * zip 안 파일 이름에 쓸 말. 경로나 마크다운 주소를 깨는 글자는 걷어 내고 빈칸은 - 로 잇는다.
     * 괄호도 뺀다 — {@code ![설명](images/01-버튼(가려짐).png)} 는 편집기마다 다르게 읽는다.
     */
    static String slug(String text) {
        if (text == null) return "";
        String s = text.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|#%()\\[\\]{}]", " ")
                .strip()
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[-.]+|[-.]+$", "");
        if (s.codePointCount(0, s.length()) > SLUG_LENGTH) {
            s = s.substring(0, s.offsetByCodePoints(0, SLUG_LENGTH)).replaceAll("[-.]+$", "");
        }
        return s;
    }

    // ── 내부 ─────────────────────────────────────────────────

    private MemoDtos.ImageView view(MemoImage image, long expiresAt) {
        return new MemoDtos.ImageView(image.getId(),
                signer.url(image.getId(), false, expiresAt),
                signer.url(image.getId(), true, expiresAt),
                image.getContentType(), image.getByteSize(), image.getWidth(), image.getHeight(),
                image.getOriginalName());
    }

    private static byte[] read(MultipartFile file, String emptyMessage) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException(emptyMessage);
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("그림을 읽지 못했습니다. 다시 시도해 주세요.");
        }
    }

    /**
     * 파일 앞부분 바이트로 형식을 가린다. 브라우저가 붙여 보낸 Content-Type 은 믿지 않는다.
     * SVG 는 받지 않는다 — 글자로 된 형식이라 스크립트를 심을 수 있다.
     */
    static String sniff(byte[] b) {
        if (b == null) return null;
        if (starts(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        if (starts(b, 0, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (ascii(b, 0, "GIF87a") || ascii(b, 0, "GIF89a")) return "image/gif";
        if (ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP")) return "image/webp";
        return null;
    }

    private static boolean starts(byte[] b, int at, int... expected) {
        if (b.length < at + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((b[at + i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private static boolean ascii(byte[] b, int at, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        if (b.length < at + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if (b[at + i] != expected[i]) return false;
        }
        return true;
    }

    /** 파일 이름은 내려받을 때만 쓴다. 경로와 쓸 수 없는 글자를 걷어 낸다. */
    static String cleanName(String name) {
        if (name == null) return null;
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}:*?\"<>|]", "_")
                .strip();
        if (base.isEmpty()) return null;
        return base.length() > 200 ? base.substring(0, 200) : base;
    }

    /** 내려받을 이름. 형식이 바뀌었을 수 있으므로(PNG → WEBP) 확장자는 실제 형식으로 맞춘다. */
    private static String downloadName(MemoImage image) {
        String stem = stem(image.getOriginalName());
        if (stem == null || stem.isBlank()) stem = "그림-" + image.getId();
        return stem + extension(image.getContentType());
    }

    private static String extension(String contentType) {
        return switch (contentType == null ? "" : contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }

    /** 확장자를 뗀 파일 이름. */
    private static String stem(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String human(long bytes) {
        long mb = bytes / (1024 * 1024);
        return mb >= 1 ? mb + "MB" : (bytes / 1024) + "KB";
    }
}
