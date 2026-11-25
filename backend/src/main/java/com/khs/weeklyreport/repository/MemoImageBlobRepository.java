package com.khs.weeklyreport.repository;

import com.khs.weeklyreport.domain.MemoImageBlob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 그림 바이트. 원본과 섬네일을 따로 읽는다 — 그림 줄은 섬네일만 수십 장을 부르는데,
 * 한 줄을 통째로 읽으면 장마다 원본 수 MB 가 딸려 온다.
 */
public interface MemoImageBlobRepository extends JpaRepository<MemoImageBlob, String> {

    @Query("select b.bytes from MemoImageBlob b where b.imageId = :id")
    byte[] findBytes(@Param("id") String id);

    @Query("select b.thumb from MemoImageBlob b where b.imageId = :id")
    byte[] findThumb(@Param("id") String id);
}
