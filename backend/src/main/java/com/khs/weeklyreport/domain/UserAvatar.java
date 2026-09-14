package com.khs.weeklyreport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 프로필 사진의 바이트.
 *
 * <p>app_user 와 한 몸이지만 테이블을 나눈다. 인증 필터가 요청마다 app_user 를
 * 읽는데, 거기에 사진이 붙어 있으면 아무도 보지 않는 수십 KB 를 매번 끌고 온다.
 * 사진은 이미지 주소를 부를 때만 읽으면 된다.
 */
@Entity
@Table(name = "user_avatar")
@Getter
@Setter
public class UserAvatar {

    /** app_user.id 를 그대로 쓴다. 사람 한 명에 사진 한 장이다. */
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "bytes", nullable = false)
    private byte[] bytes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserAvatar of(Long userId, byte[] bytes) {
        UserAvatar avatar = new UserAvatar();
        avatar.setUserId(userId);
        avatar.setBytes(bytes);
        avatar.setUpdatedAt(Instant.now());
        return avatar;
    }
}
