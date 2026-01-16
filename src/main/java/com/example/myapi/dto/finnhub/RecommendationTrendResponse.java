package com.example.myapi.dto.finnhub;

/**
 * Finnhub Recommendation Trends Response
 * GET /stock/recommendation?symbol=AAPL
 */
public record RecommendationTrendResponse(
        Integer buy,
        Integer hold,
        String period,
        Integer sell,
        Integer strongBuy,
        Integer strongSell,
        String symbol
) {}
