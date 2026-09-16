package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.MemoFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemoFolderRepository extends JpaRepository<MemoFolder, Long> {

    /**
     * 내 폴더 전부. 트리는 화면에서 조립한다 —
     * 개인 폴더가 수백 개가 될 일은 없어서 한 번에 읽는 편이 단순하다.
     */
    @Query("""
            select f from MemoFolder f
            where f.owner.id = :ownerId
            order by f.sortOrder asc, f.name asc, f.id asc
            """)
    List<MemoFolder> findMine(@Param("ownerId") Long ownerId);

    Optional<MemoFolder> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerId(Long ownerId);
}
