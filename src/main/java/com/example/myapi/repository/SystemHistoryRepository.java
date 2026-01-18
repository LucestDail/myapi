package com.example.myapi.repository;

import com.example.myapi.entity.SystemHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SystemHistoryRepository extends JpaRepository<SystemHistory, Long> {
    List<SystemHistory> findByTimestampAfterOrderByTimestampAsc(Instant after);
    List<SystemHistory> findByTimestampBetweenOrderByTimestampAsc(Instant start, Instant end);
    
    @Modifying
    @Query("DELETE FROM SystemHistory s WHERE s.timestamp < :before")
    void deleteOlderThan(Instant before);
}
