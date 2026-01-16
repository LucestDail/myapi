package com.example.myapi.dto.finnhub;

/**
 * Finnhub Market/Company News Response
 * GET /news?category=general
 * GET /company-news?symbol=AAPL&from=...&to=...
 */
public record MarketNewsResponse(
        String category,
        Long datetime,
        String headline,
        Long id,
        String image,
        String related,
        String source,
        String summary,
        String url
) {}
