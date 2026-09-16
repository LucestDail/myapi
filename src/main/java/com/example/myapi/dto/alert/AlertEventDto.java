package com.example.myapi.dto.alert;

import com.example.myapi.service.AlertChannels;

import java.time.Instant;
import java.util.List;

/**
 * 실시간 알림 이벤트 DTO
 *
 * <p>{@code channels} 는 화면이 무엇까지 해야 하는지를 알려 준다. 항상 SSE 로 내려가고,
 * {@code browser} 가 들어 있으면 브라우저가 OS 데스크톱 알림까지 띄운다.</p>
 */
public record AlertEventDto(
        String type,
        String message,
        String severity,
        String target,
        Double currentValue,
        Double threshold,
        List<String> channels,
        Instant timestamp
) {
    public static AlertEventDto create(String type, String message, String severity) {
        return new AlertEventDto(type, message, severity, null, null, null,
                AlertChannels.parse(null), Instant.now());
    }

    public static AlertEventDto create(String type, String message, String severity,
                                        String target, Double currentValue, Double threshold) {
        return new AlertEventDto(type, message, severity, target, currentValue, threshold,
                AlertChannels.parse(null), Instant.now());
    }

    public static AlertEventDto create(String type, String message, String severity,
                                        String target, Double currentValue, Double threshold,
                                        String rawChannels) {
        return new AlertEventDto(type, message, severity, target, currentValue, threshold,
                AlertChannels.parse(rawChannels), Instant.now());
    }
}
