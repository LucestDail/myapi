package com.example.myapi.dto.finnhub;

/**
 * Finnhub Company Profile 2 Response
 * GET /stock/profile2?symbol=AAPL
 */
public record CompanyProfileResponse(
        String country,
        String currency,
        String exchange,
        String finnhubIndustry,
        String ipo,
        String logo,
        Double marketCapitalization,
        String name,
        String phone,
        Double shareOutstanding,
        String ticker,
        String weburl
) {}
