package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.Memo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MemoRepository extends JpaRepository<Memo, Long> {

    /** 목록용. 휴지통에 든 것은 뺀다. 본문까지 실어 오지만 개인 메모 규모에서는 문제가 되지 않는다. */
    @Query("""
            select m from Memo m
            where m.owner.id = :ownerId and m.deletedAt is null
            order by m.updatedAt desc, m.id desc
            """)
    List<Memo> findMine(@Param("ownerId") Long ownerId);

    /** 휴지통. 최근에 지운 것부터. */
    @Query("""
            select m from Memo m
            where m.owner.id = :ownerId and m.deletedAt is not null
            order by m.deletedAt desc, m.id desc
            """)
    List<Memo> findTrash(@Param("ownerId") Long ownerId);

    /**
     * 제목과 본문에서 찾는다. 휴지통에 든 것은 찾지 않는다.
     * 패턴은 부르는 쪽이 소문자로 만들고 % _ ! 를 ! 로 막아서 넘긴다.
     */
    @Query("""
            select m from Memo m
            where m.owner.id = :ownerId and m.deletedAt is null
              and (lower(m.title) like :pattern escape '!'
                   or lower(m.content) like :pattern escape '!')
            order by m.updatedAt desc, m.id desc
            """)
    List<Memo> search(@Param("ownerId") Long ownerId, @Param("pattern") String pattern);

    /** 휴지통에 든 것까지 찾는다. 되살리기 · 영구 삭제 · 휴지통 안에서 열어 보기가 쓴다. */
    Optional<Memo> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerIdAndFolderIdAndDeletedAtIsNull(Long ownerId, Long folderId);

    @Query("select count(m) from Memo m where m.owner.id = :ownerId")
    long countMine(@Param("ownerId") Long ownerId);

    /*
     * 깃발과 휴지통은 엔티티로 저장하지 않고 이 쿼리로 바꾼다.
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

    /** 휴지통에 넣거나(시각) 되살린다(null). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Memo m set m.deletedAt = :at where m.id = :id and m.owner.id = :ownerId")
    void updateDeletedAt(@Param("id") Long id, @Param("ownerId") Long ownerId, @Param("at") Instant at);

    /** 휴지통에서 오래된 것을 정리한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Memo m where m.owner.id = :ownerId and m.deletedAt < :before")
    int purgeTrashedBefore(@Param("ownerId") Long ownerId, @Param("before") Instant before);

    /** 휴지통 비우기. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Memo m where m.owner.id = :ownerId and m.deletedAt is not null")
    int purgeTrash(@Param("ownerId") Long ownerId);
}
