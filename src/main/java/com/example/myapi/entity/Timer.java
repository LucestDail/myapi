package com.example.myapi.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 타이머/포모도로 엔티티
 */
@Entity
@Table(name = "timers")
public class Timer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "type", nullable = false, length = 20)
    private String type; // "timer", "pomodoro"

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "remaining_seconds")
    private Integer remainingSeconds;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // "idle", "running", "paused", "completed"

    @Column(name = "pomodoro_count")
    private Integer pomodoroCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Timer() {
    }

    public Timer(String userId, String type, Integer durationSeconds) {
        this.userId = userId;
        this.type = type;
        this.durationSeconds = durationSeconds;
        this.remainingSeconds = durationSeconds;
        this.status = "idle";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Integer getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(Integer remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPomodoroCount() {
        return pomodoroCount;
    }

    public void setPomodoroCount(Integer pomodoroCount) {
        this.pomodoroCount = pomodoroCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
