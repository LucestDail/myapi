package com.example.myapi.dto.finnhub;

import java.util.Map;

/**
 * Finnhub Basic Financials Response
 * GET /stock/metric?symbol=AAPL&metric=all
 */
public record BasicFinancialsResponse(
        Map<String, Object> metric,
        String metricType,
        Map<String, Object> series,
        String symbol
) {}
