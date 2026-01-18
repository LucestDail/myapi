package com.example.myapi.repository;

import com.example.myapi.entity.Timer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TimerRepository extends JpaRepository<Timer, Long> {
    Optional<Timer> findByUserIdAndType(String userId, String type);
    Optional<Timer> findFirstByUserIdOrderByUpdatedAtDesc(String userId);
}
