package com.example.myapi.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 시스템 히스토리 엔티티
 * 1분 단위로 시스템 메트릭 저장
 */
@Entity
@Table(name = "system_history")
public class SystemHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "cpu_usage")
    private Double cpuUsage;

    @Column(name = "memory_usage_percent")
    private Double memoryUsagePercent;

    @Column(name = "memory_used")
    private Long memoryUsed;

    @Column(name = "memory_total")
    private Long memoryTotal;

    @Column(name = "heap_usage_percent")
    private Double heapUsagePercent;

    @Column(name = "heap_used")
    private Long heapUsed;

    @Column(name = "heap_max")
    private Long heapMax;

    @Column(name = "thread_count")
    private Integer threadCount;

    @Column(name = "gc_count")
    private Long gcCount;

    @Column(name = "gc_time")
    private Long gcTime;

    public SystemHistory() {
    }

    @PrePersist
    public void onCreate() {
        if (this.timestamp == null) {
            this.timestamp = Instant.now();
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(Double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public Double getMemoryUsagePercent() {
        return memoryUsagePercent;
    }

    public void setMemoryUsagePercent(Double memoryUsagePercent) {
        this.memoryUsagePercent = memoryUsagePercent;
    }

    public Long getMemoryUsed() {
        return memoryUsed;
    }

    public void setMemoryUsed(Long memoryUsed) {
        this.memoryUsed = memoryUsed;
    }

    public Long getMemoryTotal() {
        return memoryTotal;
    }

    public void setMemoryTotal(Long memoryTotal) {
        this.memoryTotal = memoryTotal;
    }

    public Double getHeapUsagePercent() {
        return heapUsagePercent;
    }

    public void setHeapUsagePercent(Double heapUsagePercent) {
        this.heapUsagePercent = heapUsagePercent;
    }

    public Long getHeapUsed() {
        return heapUsed;
    }

    public void setHeapUsed(Long heapUsed) {
        this.heapUsed = heapUsed;
    }

    public Long getHeapMax() {
        return heapMax;
    }

    public void setHeapMax(Long heapMax) {
        this.heapMax = heapMax;
    }

    public Integer getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(Integer threadCount) {
        this.threadCount = threadCount;
    }

    public Long getGcCount() {
        return gcCount;
    }

    public void setGcCount(Long gcCount) {
        this.gcCount = gcCount;
    }

    public Long getGcTime() {
        return gcTime;
    }

    public void setGcTime(Long gcTime) {
        this.gcTime = gcTime;
    }
}
