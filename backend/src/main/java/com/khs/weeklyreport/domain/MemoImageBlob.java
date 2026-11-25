package com.khs.weeklyreport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/**
 * 그림의 바이트와 섬네일.
 *
 * <p>{@link MemoImage} 와 한 몸이지만 테이블을 나눈다. 메모를 열 때마다 그림 정보를
 * 읽는데, 거기에 바이트가 붙어 있으면 아무도 보지 않는 수 MB 를 끌고 온다.
 */
@Entity
@Table(name = "memo_image_blob")
@Getter
@Setter
public class MemoImageBlob implements Persistable<String> {

    @Id
    @Column(name = "image_id", length = 16)
    private String imageId;

    @Column(name = "bytes", nullable = false)
    private byte[] bytes;

    @Column(name = "thumb", nullable = false)
    private byte[] thumb;

    @Transient
    private boolean fresh = true;

    public static MemoImageBlob of(String imageId, byte[] bytes, byte[] thumb) {
        MemoImageBlob blob = new MemoImageBlob();
        blob.setImageId(imageId);
        blob.setBytes(bytes);
        blob.setThumb(thumb);
        return blob;
    }

    @Override
    public String getId() {
        return imageId;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        fresh = false;
    }
}
