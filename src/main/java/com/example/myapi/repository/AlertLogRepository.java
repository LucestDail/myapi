package com.example.myapi.repository;

import com.example.myapi.entity.AlertLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AlertLogRepository extends JpaRepository<AlertLog, Long> {
    List<AlertLog> findByUserIdOrderByCreatedAtDesc(String userId);
    Page<AlertLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    List<AlertLog> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(String userId);
    long countByUserIdAndIsReadFalse(String userId);
    
    @Modifying
    @Query("UPDATE AlertLog a SET a.isRead = true WHERE a.userId = :userId")
    void markAllAsRead(String userId);
    
    @Modifying
    @Query("DELETE FROM AlertLog a WHERE a.createdAt < :before")
    void deleteOlderThan(Instant before);
}
