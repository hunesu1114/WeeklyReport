package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** 아이디는 대소문자를 구분하지 않는다. */
    @Query("select u from AppUser u where lower(u.username) = lower(:username)")
    Optional<AppUser> findByUsername(@Param("username") String username);

    @Query("select count(u) > 0 from AppUser u where lower(u.username) = lower(:username)")
    boolean existsByUsername(@Param("username") String username);

    /**
     * 초대할 사람 찾기. 아이디나 표시 이름으로 검색한다.
     * {@code pattern} 은 항상 소문자 LIKE 패턴으로 넘긴다 — null 을 바인딩하면
     * PostgreSQL 이 타입을 추론하지 못한다.
     */
    @Query("""
            select u from AppUser u
            where u.enabled = true
              and (lower(u.username) like :pattern or lower(u.displayName) like :pattern)
            order by u.displayName asc
            """)
    List<AppUser> search(@Param("pattern") String pattern);
}
