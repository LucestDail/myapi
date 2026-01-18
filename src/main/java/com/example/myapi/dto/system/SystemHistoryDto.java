package com.example.myapi.dto.system;

import com.example.myapi.entity.SystemHistory;

import java.time.Instant;

/**
 * 시스템 히스토리 DTO
 */
public record SystemHistoryDto(
        Long id,
        Instant timestamp,
        Double cpuUsage,
        Double memoryUsagePercent,
        Long memoryUsed,
        Long memoryTotal,
        Double heapUsagePercent,
        Long heapUsed,
        Long heapMax,
        Integer threadCount,
        Long gcCount,
        Long gcTime
) {
    public static SystemHistoryDto from(SystemHistory entity) {
        return new SystemHistoryDto(
                entity.getId(),
                entity.getTimestamp(),
                entity.getCpuUsage(),
                entity.getMemoryUsagePercent(),
                entity.getMemoryUsed(),
                entity.getMemoryTotal(),
                entity.getHeapUsagePercent(),
                entity.getHeapUsed(),
                entity.getHeapMax(),
                entity.getThreadCount(),
                entity.getGcCount(),
                entity.getGcTime()
        );
    }
}
