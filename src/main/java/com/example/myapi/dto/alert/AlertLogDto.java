package com.example.myapi.dto.alert;

import com.example.myapi.entity.AlertLog;

import java.time.Instant;

/**
 * 알림 로그 DTO
 */
public record AlertLogDto(
        Long id,
        String type,
        String message,
        String severity,
        Boolean isRead,
        Instant createdAt
) {
    public static AlertLogDto from(AlertLog entity) {
        return new AlertLogDto(
                entity.getId(),
                entity.getType(),
                entity.getMessage(),
                entity.getSeverity(),
                entity.getIsRead(),
                entity.getCreatedAt()
        );
    }
}
