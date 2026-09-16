package com.example.myapi.dto.alert;

import com.example.myapi.entity.AlertRule;
import com.example.myapi.service.AlertChannels;

import java.time.Instant;

/**
 * 알림 규칙 DTO
 */
public record AlertRuleDto(
        Long id,
        String type,         // "stock_price", "cpu", "exchange_rate", "air_pm10", "air_grade" ...
        String target,       // ticker symbol, city name, "USD/KRW", 지역명 ...
        String conditionType, // "above", "below", "equals", "at_least", "at_most"
        Double threshold,
        Boolean enabled,
        String activeFrom,   // "09:00" — 비어 있으면 시간 제한 없음
        String activeTo,     // "18:00"
        String activeDays,   // "MON,TUE,WED,THU,FRI" — 비어 있으면 매일
        String channels,     // "sse" 또는 "sse,browser"
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
                entity.getActiveFrom(),
                entity.getActiveTo(),
                entity.getActiveDays(),
                AlertChannels.normalize(entity.getChannels()),
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
        applyScheduleTo(entity);
        return entity;
    }

    /**
     * 시간대 조건·채널을 엔티티에 반영한다. 생성/수정 양쪽이 같은 경로를 쓰도록 한 곳에 둔다
     * (수정에서만 빠뜨리면 "저장은 됐는데 반영이 안 되는" 모양이 된다).
     */
    public void applyScheduleTo(AlertRule entity) {
        entity.setActiveFrom(blankToNull(activeFrom));
        entity.setActiveTo(blankToNull(activeTo));
        entity.setActiveDays(blankToNull(activeDays));
        entity.setChannels(AlertChannels.normalize(channels));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
