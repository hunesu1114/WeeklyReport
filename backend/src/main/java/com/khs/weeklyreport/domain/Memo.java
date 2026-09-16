package com.khs.weeklyreport.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 메모 한 장.
 *
 * <p>내용은 서식 없는 텍스트다. 글꼴 설정은 메모장처럼 글 전체에 하나가 걸리므로
 * 본문에 태그가 섞이지 않고, 그대로 txt 로 내보낼 수 있다.
 */
@Entity
@Table(name = "memo")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Memo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    /** 비어 있으면 최상위(폴더 없음). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private MemoFolder folder;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content = "";

    @Column(name = "font_family", length = 60)
    private String fontFamily;

    @Column(name = "font_size")
    private Integer fontSize;

    @Column(name = "font_color", length = 20)
    private String fontColor;

    @Column(name = "word_wrap", nullable = false)
    private boolean wordWrap = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long folderId() {
        return folder == null ? null : folder.getId();
    }

    /** 목록에 띄울 한 줄 미리보기. 본문 첫 줄이 제목보다 내용을 잘 알려줄 때가 많다. */
    public String preview() {
        if (content == null || content.isBlank()) return "";
        String first = content.strip().lines().findFirst().orElse("");
        return first.length() > 80 ? first.substring(0, 80) + "…" : first;
    }
}
