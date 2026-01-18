package com.example.myapi.dto.alert;

import java.time.Instant;

/**
 * 실시간 알림 이벤트 DTO
 */
public record AlertEventDto(
        String type,
        String message,
        String severity,
        String target,
        Double currentValue,
        Double threshold,
        Instant timestamp
) {
    public static AlertEventDto create(String type, String message, String severity) {
        return new AlertEventDto(type, message, severity, null, null, null, Instant.now());
    }

    public static AlertEventDto create(String type, String message, String severity, 
                                        String target, Double currentValue, Double threshold) {
        return new AlertEventDto(type, message, severity, target, currentValue, threshold, Instant.now());
    }
}
