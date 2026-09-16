package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.Memo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemoRepository extends JpaRepository<Memo, Long> {

    /** 목록용. 본문까지 실어 오지만 개인 메모 규모에서는 문제가 되지 않는다. */
    @Query("""
            select m from Memo m
            where m.owner.id = :ownerId
            order by m.updatedAt desc, m.id desc
            """)
    List<Memo> findMine(@Param("ownerId") Long ownerId);

    Optional<Memo> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerIdAndFolderId(Long ownerId, Long folderId);

    @Query("select count(m) from Memo m where m.owner.id = :ownerId")
    long countMine(@Param("ownerId") Long ownerId);
}
