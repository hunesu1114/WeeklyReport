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

    // ── 도우미 ───────────────────────────────────────────────

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
