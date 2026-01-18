package com.example.myapi.dto.alert;

import com.example.myapi.entity.AlertRule;

import java.time.Instant;

/**
 * 알림 규칙 DTO
 */
public record AlertRuleDto(
        Long id,
        String type,         // "stock_price", "stock_change", "cpu", "memory", "weather"
        String target,       // ticker symbol, city name, etc.
        String conditionType, // "above", "below", "equals"
        Double threshold,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static AlertRuleDto from(AlertRule entity) {
        return new AlertRuleDto(
                entity.getId(),
                entity.getType(),
                entity.getTarget(),
                entity.getConditionType(),
                entity.getThreshold(),
                entity.getEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public AlertRule toEntity(String userId) {
        AlertRule entity = new AlertRule();
        entity.setUserId(userId);
        entity.setType(type);
        entity.setTarget(target);
        entity.setConditionType(conditionType);
        entity.setThreshold(threshold);
        entity.setEnabled(enabled != null ? enabled : true);
        return entity;
    }
}
