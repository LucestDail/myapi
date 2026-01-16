package com.example.myapi.dto.finnhub;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Finnhub Quote Response
 * GET /quote?symbol=AAPL
 */
public record FinnhubQuoteResponse(
        @JsonProperty("c") Double currentPrice,
        @JsonProperty("d") Double change,
        @JsonProperty("dp") Double percentChange,
        @JsonProperty("h") Double highPrice,
        @JsonProperty("l") Double lowPrice,
        @JsonProperty("o") Double openPrice,
        @JsonProperty("pc") Double previousClose,
        @JsonProperty("t") Long timestamp
) {}
