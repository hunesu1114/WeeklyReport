package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {
}
