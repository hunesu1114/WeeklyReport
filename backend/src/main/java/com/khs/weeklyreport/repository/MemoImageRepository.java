package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.MemoImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 그림 정보와, 메모가 그림을 가리키는 줄(memo_image_ref).
 *
 * <p>가리키는 줄은 엔티티로 두지 않는다. 저장할 때마다 본문의 토큰으로 통째로 다시
 * 맞추기만 하므로, 엔티티를 읽어 와 비교하는 것보다 쿼리 두 줄이 단순하고 가볍다.
 *
 * <p>바꾸는 쿼리는 앞서 고친 엔티티를 먼저 내보낸다(flushAutomatically). 메모를 저장하고
 * 곧바로 이 쿼리를 부르는데, 그 사이에 메모가 아직 DB 에 없으면 참조를 걸 수 없다.
 */
public interface MemoImageRepository extends JpaRepository<MemoImage, String> {

    List<MemoImage> findByOwnerIdAndIdIn(Long ownerId, Collection<String> ids);

    @Query("select coalesce(sum(i.byteSize), 0) from MemoImage i where i.ownerId = :ownerId")
    long usedBytes(@Param("ownerId") Long ownerId);

    long countByOwnerId(Long ownerId);

    // ── 사용량 ───────────────────────────────────────────────

    /** 아무도 가리키지 않아 곧 지워질 것. 본문에서 뺐거나, 올렸는데 아직 저장 전인 그림이다. */
    long countByOwnerIdAndOrphanedAtIsNotNull(Long ownerId);

    @Query("select coalesce(sum(i.byteSize), 0) from MemoImage i where i.ownerId = :ownerId and i.orphanedAt is not null")
    long orphanedBytes(@Param("ownerId") Long ownerId);

    /** 메모 하나가 가리키는 그림. 같은 그림을 두 메모가 가리키면 두 곳에 다 센다. */
    interface MemoUsageRow {
        Long getMemoId();

        String getTitle();

        Boolean getTrashed();

        Long getImages();

        Long getBytes();
    }

    /** 그림을 많이 쓰는 메모. 공간이 모자랄 때 어디를 비울지 보라고 마이페이지에 보인다. */
    @Query(nativeQuery = true, value = """
            select m.id as "memoId", m.title as "title", (m.deleted_at is not null) as "trashed",
                   count(*) as "images", coalesce(sum(i.byte_size), 0) as "bytes"
            from memo_image_ref r
            join memo_image i on i.id = r.image_id
            join memo m on m.id = r.memo_id
            where i.owner_id = :ownerId
            group by m.id, m.title, m.deleted_at
            order by "bytes" desc, m.id desc
            limit :limit
            """)
    List<MemoUsageRow> heaviestMemos(@Param("ownerId") Long ownerId, @Param("limit") int limit);

    /**
     * 이 메모의 그림 수. 이 메모가 가리키는 것과, 이 메모에 올렸는데 아직 아무도 가리키지 않는 것
     * (저장 전이거나 방금 지운 것)을 함께 센다.
     */
    @Query(nativeQuery = true, value = """
            select count(*) from memo_image i
            where i.owner_id = :ownerId
              and (exists (select 1 from memo_image_ref r where r.image_id = i.id and r.memo_id = :memoId)
                   or (i.memo_id = :memoId
                       and not exists (select 1 from memo_image_ref r where r.image_id = i.id)))
            """)
    long countForMemo(@Param("ownerId") Long ownerId, @Param("memoId") Long memoId);

    // ── 가리키는 줄 ──────────────────────────────────────────

    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = "delete from memo_image_ref where memo_id = :memoId")
    int clearRefs(@Param("memoId") Long memoId);

    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true,
            value = "delete from memo_image_ref where memo_id = :memoId and image_id not in (:ids)")
    int clearRefsExcept(@Param("memoId") Long memoId, @Param("ids") Collection<String> ids);

    /** 내 그림만 건다. 남의 그림 아이디를 본문에 적어도 걸리지 않는다. */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into memo_image_ref (memo_id, image_id)
            select :memoId, i.id from memo_image i
            where i.owner_id = :ownerId and i.id in (:ids)
            on conflict do nothing
            """)
    int addRefs(@Param("memoId") Long memoId, @Param("ownerId") Long ownerId, @Param("ids") Collection<String> ids);

    /** 다시 쓰이게 된 그림은 정리 대상에서 뺀다. 지웠던 토큰을 Ctrl+Z 로 되살린 경우다. */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            update memo_image set orphaned_at = null
            where owner_id = :ownerId and id in (:ids) and orphaned_at is not null
            """)
    int adopt(@Param("ownerId") Long ownerId, @Param("ids") Collection<String> ids);

    // ── 정리 ─────────────────────────────────────────────────

    /**
     * 아무도 가리키지 않는데 아직 표시가 없는 그림에 지금 시각을 적는다.
     *
     * <p>메모를 영구 삭제하면 가리키는 줄이 DB 에서 함께 지워져(on delete cascade) 표시를
     * 남길 틈이 없다. 저장 경로마다 챙기는 대신, 정리할 때 한 번 훑어 표시한다.
     */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            update memo_image i set orphaned_at = :now
            where i.owner_id = :ownerId and i.orphaned_at is null
              and not exists (select 1 from memo_image_ref r where r.image_id = i.id)
            """)
    int markOrphans(@Param("ownerId") Long ownerId, @Param("now") Instant now);

    /** 표시한 지 오래된 것을 지운다. 바이트는 DB 가 함께 지운다(on delete cascade). */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true,
            value = "delete from memo_image where owner_id = :ownerId and orphaned_at < :before")
    int deleteOrphansBefore(@Param("ownerId") Long ownerId, @Param("before") Instant before);
}
