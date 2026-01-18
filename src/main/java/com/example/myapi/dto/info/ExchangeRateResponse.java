package com.example.myapi.dto.info;

import java.time.Instant;
import java.util.Map;

/**
 * 환율 정보 DTO
 */
public record ExchangeRateResponse(
        String base,
        Instant timestamp,
        Map<String, Double> rates
) {
    public static ExchangeRateResponse of(String base, Map<String, Double> rates) {
        return new ExchangeRateResponse(base, Instant.now(), rates);
    }
}
