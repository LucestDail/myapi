package com.example.myapi.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 사용자 프로필 엔티티
 * 브라우저 UUID 기반 사용자 식별
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_active", nullable = false)
    private Instant lastActive;

    public UserProfile() {
    }

    public UserProfile(String userId) {
        this.userId = userId;
        this.createdAt = Instant.now();
        this.lastActive = Instant.now();
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastActive() {
        return lastActive;
    }

    public void setLastActive(Instant lastActive) {
        this.lastActive = lastActive;
    }

    public void updateLastActive() {
        this.lastActive = Instant.now();
    }
}
