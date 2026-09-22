package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.Memo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /*
     * 깃발은 엔티티로 저장하지 않고 이 쿼리로 바꾼다.
     *
     * 엔티티를 고쳐 save 하면 감사(auditing)가 updated_at 을 따라 올린다. 그러면 글을
     * 한 글자도 고치지 않았는데 목록에서 맨 위로 튀어 오르고 "방금 저장됨"으로 보인다.
     * 별을 눌렀을 뿐인데 목록이 뒤섞이는 셈이라, 고친 시각은 그대로 두고 깃발만 바꾼다.
     */

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Memo m set m.pinned = :value where m.id = :id and m.owner.id = :ownerId")
    void updatePinned(@Param("id") Long id, @Param("ownerId") Long ownerId, @Param("value") boolean value);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Memo m set m.favorite = :value where m.id = :id and m.owner.id = :ownerId")
    void updateFavorite(@Param("id") Long id, @Param("ownerId") Long ownerId, @Param("value") boolean value);
}
