package com.example.myapi.dto.productivity;

import com.example.myapi.entity.Timer;

import java.time.Instant;

/**
 * 타이머 DTO
 */
public record TimerDto(
        Long id,
        String type,              // "timer", "pomodoro"
        Integer durationSeconds,
        Integer remainingSeconds,
        Instant startedAt,
        String status,            // "idle", "running", "paused", "completed"
        Integer pomodoroCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static TimerDto from(Timer entity) {
        return new TimerDto(
                entity.getId(),
                entity.getType(),
                entity.getDurationSeconds(),
                entity.getRemainingSeconds(),
                entity.getStartedAt(),
                entity.getStatus(),
                entity.getPomodoroCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public Timer toEntity(String userId) {
        Timer entity = new Timer(userId, type, durationSeconds);
        entity.setRemainingSeconds(remainingSeconds != null ? remainingSeconds : durationSeconds);
        entity.setStatus(status != null ? status : "idle");
        entity.setPomodoroCount(pomodoroCount != null ? pomodoroCount : 0);
        return entity;
    }
}
