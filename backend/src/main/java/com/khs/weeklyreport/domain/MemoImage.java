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

import java.time.Instant;

/**
 * 메모에 넣은 그림 한 장의 정보. 바이트는 {@link MemoImageBlob} 에 따로 둔다.
 *
 * <p>아이디는 브라우저가 정한다. 그래서 {@link Persistable} 로 "새 것"임을 직접 알린다 —
 * 그러지 않으면 저장소가 아이디가 채워진 것을 보고 merge 로 처리해, 같은 아이디의
 * 남의 그림을 소리 없이 덮어쓸 수 있다. persist 는 같은 아이디가 있으면 실패한다.
 */
@Entity
@Table(name = "memo_image")
@Getter
@Setter
public class MemoImage implements Persistable<String> {

    @Id
    @Column(name = "id", length = 16)
    private String id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** 처음 올린 메모. 메모당 장 수를 셀 때 쓴다. 메모가 영구 삭제되면 비워진다. */
    @Column(name = "memo_id")
    private Long memoId;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Column(name = "original_name", length = 200)
    private String originalName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 어느 메모에서도 가리키지 않게 된 시각. 하루가 지나면 지운다. */
    @Column(name = "orphaned_at")
    private Instant orphanedAt;

    @Transient
    private boolean fresh = true;

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
