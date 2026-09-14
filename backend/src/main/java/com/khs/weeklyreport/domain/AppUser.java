package com.khs.weeklyreport.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "app_user")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    /** BCrypt 해시. 평문은 어디에도 저장하지 않는다. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /**
     * 프로필 사진의 형식. 바이트는 {@link UserAvatar} 에 따로 둔다 —
     * 로그인한 사람은 요청마다 이 엔티티를 읽는데, 사진까지 딸려 오면
     * 매 요청이 수십 KB 씩 무거워진다.
     */
    @Column(name = "avatar_type", length = 50)
    private String avatarType;

    /** 사진이 바뀔 때마다 오른다. 브라우저 캐시를 끊는 데 쓴다. */
    @Column(name = "avatar_version", nullable = false)
    private long avatarVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean hasAvatar() {
        return avatarType != null;
    }
}
