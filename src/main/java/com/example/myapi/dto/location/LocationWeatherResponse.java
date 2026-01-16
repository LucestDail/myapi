package com.example.myapi.dto.location;

import java.time.Instant;

/**
 * 현재 위치 날씨 응답 (wttr.in 기반)
 */
public record LocationWeatherResponse(
        String location,
        String weather,
        String rawResponse,
        Instant fetchedAt
) {}
