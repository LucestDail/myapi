package com.example.myapi.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 알림 규칙 엔티티
 */
@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "type", nullable = false, length = 50)
    private String type; // "stock_price", "stock_change", "cpu", "memory", "weather"

    @Column(name = "target", length = 100)
    private String target; // ticker symbol, city name, etc.

    @Column(name = "condition_type", nullable = false, length = 20)
    private String conditionType; // "above", "below", "equals"

    @Column(name = "threshold", nullable = false)
    private Double threshold;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    /**
     * 시간대 조건 시작("09:00"). {@code activeTo} 와 함께 비어 있으면 시간 제한이 없다.
     * 판정은 {@link com.example.myapi.service.AlertConditions#withinActiveWindow}.
     */
    @Column(name = "active_from", length = 5)
    private String activeFrom;

    /** 시간대 조건 끝("18:00"). 구간은 [from, to) 이고 from &gt; to 면 자정을 넘는다. */
    @Column(name = "active_to", length = 5)
    private String activeTo;

    /** 요일 제한("MON,TUE,WED,THU,FRI"). 비어 있으면 매일. */
    @Column(name = "active_days", length = 60)
    private String activeDays;

    /** 알림 채널("sse" 또는 "sse,browser"). 비어 있으면 SSE 만. {@link com.example.myapi.service.AlertChannels} */
    @Column(name = "channels", length = 60)
    private String channels;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AlertRule() {
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

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getConditionType() {
        return conditionType;
    }

    public void setConditionType(String conditionType) {
        this.conditionType = conditionType;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getActiveFrom() {
        return activeFrom;
    }

    public void setActiveFrom(String activeFrom) {
        this.activeFrom = activeFrom;
    }

    public String getActiveTo() {
        return activeTo;
    }

    public void setActiveTo(String activeTo) {
        this.activeTo = activeTo;
    }

    public String getActiveDays() {
        return activeDays;
    }

    public void setActiveDays(String activeDays) {
        this.activeDays = activeDays;
    }

    public String getChannels() {
        return channels;
    }

    public void setChannels(String channels) {
        this.channels = channels;
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
