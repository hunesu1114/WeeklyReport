package com.khs.weeklyreport.service;

import com.khs.weeklyreport.domain.AppUser;
import com.khs.weeklyreport.domain.Memo;
import com.khs.weeklyreport.domain.MemoImage;
import com.khs.weeklyreport.domain.MemoImageBlob;
import com.khs.weeklyreport.repository.MemoImageBlobRepository;
import com.khs.weeklyreport.repository.MemoImageRepository;
import com.khs.weeklyreport.repository.MemoRepository;
import com.khs.weeklyreport.security.CurrentUser;
import com.khs.weeklyreport.security.MemoImageSigner;
import com.khs.weeklyreport.web.dto.MemoDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoImageServiceTest {

    private static final Long ME = 1L;
    private static final Long MEMO = 7L;
    private static final String ID = "k3F9aQ2xZ7";
    private static final Instant NOW = Instant.parse("2026-11-20T09:00:00Z");

    private static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13);
    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 16);

    private MemoImageRepository imageRepository;
    private MemoImageBlobRepository blobRepository;
    private MemoRepository memoRepository;
    private MemoImageSigner signer;
    private MemoImageService service;
    private Memo memo;

    @BeforeEach
    void setUp() {
        AppUser me = new AppUser();
        me.setId(ME);

        memo = new Memo();
        memo.setId(MEMO);
        memo.setOwner(me);
        memo.setTitle("메모");
        memo.setContent("");

        imageRepository = mock(MemoImageRepository.class);
        when(imageRepository.findById(any())).thenReturn(Optional.empty());
        when(imageRepository.save(any(MemoImage.class))).thenAnswer(c -> c.getArgument(0));

        blobRepository = mock(MemoImageBlobRepository.class);
        when(blobRepository.save(any(MemoImageBlob.class))).thenAnswer(c -> c.getArgument(0));

        memoRepository = mock(MemoRepository.class);
        when(memoRepository.findByIdAndOwnerId(MEMO, ME)).thenReturn(Optional.of(memo));

        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.requireId()).thenReturn(ME);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        signer = new MemoImageSigner("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII), clock);

        service = new MemoImageService(imageRepository, blobRepository, memoRepository, currentUser, signer, clock,
                new MemoImageService.Limits(1000, 3, 5000));
    }

    // ── 형식 ─────────────────────────────────────────────────

    @Test
    void 형식은_앞부분_바이트로_가린다() {
        assertThat(MemoImageService.sniff(PNG)).isEqualTo("image/png");
        assertThat(MemoImageService.sniff(JPEG)).isEqualTo("image/jpeg");
        assertThat(MemoImageService.sniff("GIF89a....".getBytes(StandardCharsets.US_ASCII))).isEqualTo("image/gif");
        assertThat(MemoImageService.sniff("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII))).isEqualTo("image/webp");
    }

    @Test
    void SVG_와_그림이_아닌_것은_받지_않는다() {
        // SVG 는 글자로 된 형식이라 스크립트를 심을 수 있다. 확장자나 Content-Type 이 그림이라 해도 바이트를 본다
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        assertThat(MemoImageService.sniff(svg)).isNull();
        assertThat(MemoImageService.sniff("RIFF\0\0\0\0WAVE".getBytes(StandardCharsets.US_ASCII))).isNull();
        assertThat(MemoImageService.sniff(new byte[0])).isNull();

        assertThatThrownBy(() -> upload(ID, new MockMultipartFile("file", "a.png", "image/png", svg)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PNG, JPG, WEBP, GIF");
    }

    // ── 올리기 ───────────────────────────────────────────────

    @Test
    void 올린_그림은_서명된_주소와_함께_돌아온다() {
        MemoDtos.ImageView view = upload(ID, png("스크린샷.png"));

        assertThat(view.id()).isEqualTo(ID);
        assertThat(view.contentType()).isEqualTo("image/png");
        assertThat(view.url()).startsWith("/api/memo-images/" + ID + "?exp=");
        assertThat(view.thumbUrl()).startsWith("/api/memo-images/" + ID + "/thumb?exp=");

        ArgumentCaptor<MemoImage> saved = ArgumentCaptor.forClass(MemoImage.class);
        verify(imageRepository).save(saved.capture());
        assertThat(saved.getValue().getOwnerId()).isEqualTo(ME);
        assertThat(saved.getValue().getMemoId()).isEqualTo(MEMO);
        // 아직 아무도 가리키지 않는다. 저장된 본문에 토큰이 없으면 하루 뒤 정리 대상이다
        assertThat(saved.getValue().getOrphanedAt()).isEqualTo(NOW);
        verify(imageRepository, never()).addRefs(anyLong(), anyLong(), any());
    }

    @Test
    void 자동_저장이_먼저_끝났으면_올리면서_참조를_건다() {
        memo.setContent("앞\n![화면](img:" + ID + ")");

        upload(ID, png("a.png"));

        verify(imageRepository).addRefs(MEMO, ME, List.of(ID));
        verify(imageRepository).adopt(ME, List.of(ID));
    }

    @Test
    void 같은_아이디로_다시_올리면_있는_것을_돌려준다() {
        // 응답을 못 받고 다시 올린 경우. 두 장이 되거나 실패하면 안 된다
        when(imageRepository.findById(ID)).thenReturn(Optional.of(stored(ID, ME)));

        MemoDtos.ImageView view = upload(ID, png("a.png"));

        assertThat(view.id()).isEqualTo(ID);
        verify(imageRepository, never()).save(any());
        verify(blobRepository, never()).save(any());
    }

    @Test
    void 남의_그림_아이디는_덮어쓰지_않는다() {
        when(imageRepository.findById(ID)).thenReturn(Optional.of(stored(ID, 2L)));

        assertThatThrownBy(() -> upload(ID, png("a.png")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("겹칩니다");
        verify(imageRepository, never()).save(any());
    }

    @Test
    void 아이디_모양이_틀리면_받지_않는다() {
        assertThatThrownBy(() -> upload("../../etc", png("a.png"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> upload("short", png("a.png"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 휴지통에_든_메모에는_넣을_수_없다() {
        memo.setDeletedAt(NOW);

        assertThatThrownBy(() -> upload(ID, png("a.png")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("휴지통");
    }

    @Test
    void 남의_메모에는_넣을_수_없다() {
        assertThatThrownBy(() -> service.upload(99L, ID, png("a.png"), thumb(), 10, 10, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 한_장_크기를_넘으면_받지_않는다() {
        byte[] big = new byte[1001];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        assertThatThrownBy(() -> upload(ID, new MockMultipartFile("file", "a.png", "image/png", big)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("너무 큽니다");
    }

    @Test
    void 섬네일에_원본을_실어_한도를_피할_수_없다() {
        byte[] big = new byte[300 * 1024];
        System.arraycopy(JPEG, 0, big, 0, JPEG.length);

        assertThatThrownBy(() -> service.upload(MEMO, ID, png("a.png"),
                new MockMultipartFile("thumb", "t.jpg", "image/jpeg", big), 10, 10, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("섬네일");
    }

    @Test
    void 메모_하나에_넣을_수_있는_장_수가_있다() {
        when(imageRepository.countForMemo(ME, MEMO)).thenReturn(3L);

        assertThatThrownBy(() -> upload(ID, png("a.png")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3장까지");
    }

    @Test
    void 사람마다_쓸_수_있는_용량이_있다() {
        when(imageRepository.usedBytes(ME)).thenReturn(4995L);

        assertThatThrownBy(() -> upload(ID, png("a.png")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("다 썼습니다");
    }

    @Test
    void 파일_이름에서_경로와_쓸_수_없는_글자를_걷어_낸다() {
        assertThat(MemoImageService.cleanName("C:\\Users\\kim\\Desktop\\화면:1.png")).isEqualTo("화면_1.png");
        assertThat(MemoImageService.cleanName("../../a.png")).isEqualTo("a.png");
        assertThat(MemoImageService.cleanName("   ")).isNull();
    }

    // ── 내려주기 ─────────────────────────────────────────────

    @Test
    void 서명이_맞으면_내려주고_확장자는_실제_형식을_따른다() {
        MemoImage image = stored(ID, ME);
        image.setContentType("image/webp");
        image.setOriginalName("휴대폰 사진.HEIC.jpg");
        when(imageRepository.findById(ID)).thenReturn(Optional.of(image));
        when(blobRepository.findBytes(ID)).thenReturn(PNG);
        long exp = signer.expiry();

        MemoImageService.ImageFile file = service.read(ID, false, exp, signer.sign(ID, exp));

        assertThat(file.contentType()).isEqualTo("image/webp");
        assertThat(file.filename()).isEqualTo("휴대폰 사진.HEIC.webp");
        assertThat(file.maxAgeSeconds()).isPositive();
    }

    @Test
    void 섬네일의_형식은_바이트에서_읽는다() {
        when(imageRepository.findById(ID)).thenReturn(Optional.of(stored(ID, ME)));
        when(blobRepository.findThumb(ID)).thenReturn(JPEG);
        long exp = signer.expiry();

        assertThat(service.read(ID, true, exp, signer.sign(ID, exp)).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void 서명이_틀리면_있는지조차_알리지_않는다() {
        when(imageRepository.findById(ID)).thenReturn(Optional.of(stored(ID, ME)));
        long exp = signer.expiry();

        assertThatThrownBy(() -> service.read(ID, false, exp, "forged")).isInstanceOf(NotFoundException.class);
        verify(blobRepository, never()).findBytes(any());
    }

    // ── 가리키는 그림 ────────────────────────────────────────

    @Test
    void 토큰이_없으면_참조를_모두_지운다() {
        memo.setContent("그림 없는 글");

        service.syncRefs(memo);

        verify(imageRepository).clearRefs(MEMO);
        verify(imageRepository, never()).addRefs(anyLong(), anyLong(), any());
    }

    @Test
    void 본문의_토큰으로_참조를_다시_맞춘다() {
        memo.setContent("![a](img:k3F9aQ2xZ7)\n![b](img:Qm81LxV0pa)");

        service.syncRefs(memo);

        verify(imageRepository).clearRefsExcept(MEMO, List.of("k3F9aQ2xZ7", "Qm81LxV0pa"));
        verify(imageRepository).addRefs(MEMO, ME, List.of("k3F9aQ2xZ7", "Qm81LxV0pa"));
        verify(imageRepository).adopt(ME, List.of("k3F9aQ2xZ7", "Qm81LxV0pa"));
    }

    @Test
    void 본문_순서대로_주고_없는_그림은_뺀다() {
        when(imageRepository.findByOwnerIdAndIdIn(any(), any()))
                .thenReturn(List.of(stored("Qm81LxV0pa", ME), stored("k3F9aQ2xZ7", ME)));

        List<MemoDtos.ImageView> views = service.viewsFor(ME,
                "![a](img:k3F9aQ2xZ7)\n![없음](img:Za7cR2nWq4)\n![b](img:Qm81LxV0pa)");

        assertThat(views).extracting(MemoDtos.ImageView::id).containsExactly("k3F9aQ2xZ7", "Qm81LxV0pa");
    }

    @Test
    void 하루_지난_고아_그림만_지운다() {
        service.collectGarbage(ME);

        verify(imageRepository).markOrphans(ME, NOW);
        verify(imageRepository).deleteOrphansBefore(ME, Instant.parse("2026-11-19T09:00:00Z"));
    }

    // ── 사용량 ───────────────────────────────────────────────

    @Test
    void 사용량은_정리를_먼저_하고_센다() {
        when(imageRepository.countByOwnerId(ME)).thenReturn(4L);
        when(imageRepository.usedBytes(ME)).thenReturn(3200L);
        when(imageRepository.countByOwnerIdAndOrphanedAtIsNotNull(ME)).thenReturn(1L);
        when(imageRepository.orphanedBytes(ME)).thenReturn(700L);
        when(imageRepository.heaviestMemos(ME, 5)).thenReturn(List.of(usageRow(MEMO, "배포", true, 3, 2500)));

        MemoDtos.ImageUsage usage = service.usage();

        // 하루 지난 것을 먼저 치워야 이미 지울 때가 된 그림이 쓰는 공간으로 잡히지 않는다
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(imageRepository);
        order.verify(imageRepository).deleteOrphansBefore(any(), any());
        order.verify(imageRepository).usedBytes(ME);

        assertThat(usage.count()).isEqualTo(4);
        assertThat(usage.bytes()).isEqualTo(3200);
        assertThat(usage.quotaBytes()).isEqualTo(5000);
        assertThat(usage.maxBytes()).isEqualTo(1000);
        assertThat(usage.maxPerMemo()).isEqualTo(3);
        assertThat(usage.orphanCount()).isEqualTo(1);
        assertThat(usage.orphanBytes()).isEqualTo(700);
        assertThat(usage.memos()).containsExactly(new MemoDtos.MemoImageUsage(MEMO, "배포", true, 3, 2500));
    }

    // ── zip 내보내기 ─────────────────────────────────────────

    @Test
    void zip_에는_본문과_그림이_그림_줄_번호대로_들어간다() throws Exception {
        memo.setTitle("로그인 화면: 오류");
        memo.setContent("""
                첫 줄
                ![로그인 버튼 가려짐](img:k3F9aQ2xZ7)
                ![지워진 그림](img:Za7cR2nWq4)
                ![콘솔 401](img:Qm81LxV0pa) 그리고 다시 ![로그인 버튼 가려짐](img:k3F9aQ2xZ7)""");
        MemoImage console = stored("Qm81LxV0pa", ME);
        console.setContentType("image/webp");
        when(imageRepository.findByOwnerIdAndIdIn(any(), any()))
                .thenReturn(List.of(stored("k3F9aQ2xZ7", ME), console));
        when(blobRepository.findBytes("k3F9aQ2xZ7")).thenReturn(PNG);
        when(blobRepository.findBytes("Qm81LxV0pa")).thenReturn(JPEG);

        MemoImageService.ZipPlan plan = service.prepareZip(MEMO);

        assertThat(plan.filename()).isEqualTo("로그인 화면_ 오류.zip");
        assertThat(plan.textName()).isEqualTo("로그인 화면_ 오류.txt");
        // 찾을 수 없는 그림도 번호 하나를 차지한다 — 화면의 '그림 3' 이 파일 03 이다
        assertThat(plan.images()).extracting(MemoImageService.ZipImage::path)
                .containsExactly("images/01-로그인-버튼-가려짐.png", "images/03-콘솔-401.webp");
        assertThat(new String(plan.text(), StandardCharsets.UTF_8)).isEqualTo(
                "첫 줄\r\n"
                        + "![로그인 버튼 가려짐](images/01-로그인-버튼-가려짐.png)\r\n"
                        + "[그림: 지워진 그림]\r\n"
                        + "![콘솔 401](images/03-콘솔-401.webp) 그리고 다시 ![로그인 버튼 가려짐](images/01-로그인-버튼-가려짐.png)");

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        service.writeZip(plan, out);

        java.util.Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> methods = new java.util.HashMap<>();
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(out.toByteArray()), StandardCharsets.UTF_8)) {
            for (java.util.zip.ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                entries.put(e.getName(), zip.readAllBytes());
                methods.put(e.getName(), e.getMethod());
            }
        }
        assertThat(entries.keySet()).containsExactly(
                "로그인 화면_ 오류.txt", "images/01-로그인-버튼-가려짐.png", "images/03-콘솔-401.webp");
        assertThat(entries.get("로그인 화면_ 오류.txt")).isEqualTo(plan.text());
        assertThat(entries.get("images/01-로그인-버튼-가려짐.png")).isEqualTo(PNG);
        assertThat(entries.get("images/03-콘솔-401.webp")).isEqualTo(JPEG);
        // 그림은 이미 압축돼 있어 다시 줄이지 않는다
        assertThat(methods.get("images/01-로그인-버튼-가려짐.png")).isEqualTo(java.util.zip.ZipEntry.STORED);
        assertThat(methods.get("로그인 화면_ 오류.txt")).isEqualTo(java.util.zip.ZipEntry.DEFLATED);
    }

    @Test
    void 설명이_없으면_올릴_때의_파일_이름으로_둘_다_없으면_번호만() {
        memo.setContent("![](img:k3F9aQ2xZ7)\n![ ](img:Qm81LxV0pa)");
        MemoImage named = stored("k3F9aQ2xZ7", ME);
        named.setOriginalName("배포 전 화면.png");
        when(imageRepository.findByOwnerIdAndIdIn(any(), any())).thenReturn(List.of(named, stored("Qm81LxV0pa", ME)));

        MemoImageService.ZipPlan plan = service.prepareZip(MEMO);

        assertThat(plan.images()).extracting(MemoImageService.ZipImage::path)
                .containsExactly("images/01-배포-전-화면.png", "images/02.png");
        assertThat(new String(plan.text(), StandardCharsets.UTF_8))
                .isEqualTo("![](images/01-배포-전-화면.png)\r\n![](images/02.png)");
    }

    @Test
    void 준비한_뒤_지워진_그림은_건너뛴다() throws Exception {
        memo.setContent("![a](img:k3F9aQ2xZ7)");
        when(imageRepository.findByOwnerIdAndIdIn(any(), any())).thenReturn(List.of(stored("k3F9aQ2xZ7", ME)));
        MemoImageService.ZipPlan plan = service.prepareZip(MEMO);

        when(blobRepository.findBytes("k3F9aQ2xZ7")).thenReturn(null);

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        service.writeZip(plan, out);

        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(out.toByteArray()), StandardCharsets.UTF_8)) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("메모.txt");
            assertThat(zip.getNextEntry()).isNull();
        }
    }

    @Test
    void 남의_메모는_zip_으로_내보낼_수_없다() {
        assertThatThrownBy(() -> service.prepareZip(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void zip_안_파일_이름은_경로와_마크다운을_깨는_글자를_걷는다() {
        assertThat(MemoImageService.slug("로그인 버튼 가려짐")).isEqualTo("로그인-버튼-가려짐");
        assertThat(MemoImageService.slug("../../etc/passwd")).isEqualTo("etc-passwd");
        assertThat(MemoImageService.slug("버튼 (가려짐) #2 [최종] 50%")).isEqualTo("버튼-가려짐-2-최종-50");
        assertThat(MemoImageService.slug("C:\\temp\\a*b")).isEqualTo("C-temp-a-b");
        assertThat(MemoImageService.slug("   ")).isEmpty();
        assertThat(MemoImageService.slug(null)).isEmpty();
        // 글자 수로 자른다. 한글이 바이트 중간에서 잘리지 않는다
        assertThat(MemoImageService.slug("가".repeat(60))).hasSize(40);
    }

    // ── 도우미 ───────────────────────────────────────────────

    private static MemoImageRepository.MemoUsageRow usageRow(Long id, String title, boolean trashed, long images, long bytes) {
        return new MemoImageRepository.MemoUsageRow() {
            public Long getMemoId() { return id; }
            public String getTitle() { return title; }
            public Boolean getTrashed() { return trashed; }
            public Long getImages() { return images; }
            public Long getBytes() { return bytes; }
        };
    }

    private MemoDtos.ImageView upload(String id, MockMultipartFile file) {
        return service.upload(MEMO, id, file, thumb(), 640, 400, file.getOriginalFilename());
    }

    private static MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG);
    }

    private static MockMultipartFile thumb() {
        return new MockMultipartFile("thumb", "thumb.jpg", "image/jpeg", JPEG);
    }

    private static MemoImage stored(String id, Long owner) {
        MemoImage image = new MemoImage();
        image.setId(id);
        image.setOwnerId(owner);
        image.setMemoId(MEMO);
        image.setContentType("image/png");
        image.setByteSize(PNG.length);
        image.setWidth(640);
        image.setHeight(400);
        image.setCreatedAt(NOW);
        return image;
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }
}
