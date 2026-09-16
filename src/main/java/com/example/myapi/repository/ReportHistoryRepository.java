package com.example.myapi.repository;

import com.example.myapi.entity.ReportHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportHistoryRepository extends JpaRepository<ReportHistory, Long> {

    Page<ReportHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<ReportHistory> findByUserIdAndReportTypeOrderByCreatedAtDesc(
            String userId, String reportType, Pageable pageable);

    List<ReportHistory> findByUserIdAndReportTypeAndCreatedAtAfterOrderByCreatedAtDesc(
            String userId, String reportType, Instant after);

    Optional<ReportHistory> findByIdAndUserId(Long id, String userId);

    long countByUserId(String userId);

    /** 보관 개수 상한을 넘겼을 때 버릴 대상(오래된 순). */
    @Query("SELECT r.id FROM ReportHistory r WHERE r.userId = :userId ORDER BY r.createdAt ASC")
    List<Long> findIdsOldestFirst(String userId, Pageable pageable);

    /** 서로 다른 사용자 id 목록. 기간 기반 정리에서 쓰인다. */
    @Query("SELECT DISTINCT r.userId FROM ReportHistory r")
    List<String> findDistinctUserIds();

    @Modifying
    @Query("DELETE FROM ReportHistory r WHERE r.id IN :ids")
    int deleteByIdIn(List<Long> ids);

    @Modifying
    @Query("DELETE FROM ReportHistory r WHERE r.createdAt < :before")
    int deleteOlderThan(Instant before);
}
