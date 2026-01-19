package com.example.myapi.service;

import com.example.myapi.config.FinnhubProperties;
import com.example.myapi.dto.finnhub.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for Finnhub API integration.
 * Base URL: https://finnhub.io/api/v1
 * Rate limit: 30 calls/second
 * 
 * 캐싱 전략:
 * - 티커별 Quote 데이터 캐싱 (60초 TTL)
 * - 전역 호출 쿨타임: 캐시된 티커는 즉시 반환, 60초 후 백그라운드 갱신
 */
@Service
public class FinnhubService {

    private static final Logger log = LoggerFactory.getLogger(FinnhubService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final RestTemplate restTemplate;
    private final FinnhubProperties properties;

    // Quote 캐시: symbol -> (response, fetchedAt)
    private final Map<String, CachedQuote> quoteCache = new ConcurrentHashMap<>();

    public FinnhubService(RestTemplate restTemplate, FinnhubProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * 캐시된 Quote 엔트리
     */
    private record CachedQuote(FinnhubQuoteResponse response, Instant fetchedAt) {
    }

    /**
     * 60초마다 캐시된 모든 티커 갱신
     */
    @Scheduled(fixedRate = 60000)
    public void refreshCachedQuotes() {
        if (quoteCache.isEmpty()) {
            return;
        }
        log.info("Refreshing {} cached stock quotes...", quoteCache.size());
        
        for (String symbol : quoteCache.keySet()) {
            try {
                FinnhubQuoteResponse fresh = fetchQuoteFromApi(symbol);
                if (fresh != null) {
                    quoteCache.put(symbol, new CachedQuote(fresh, Instant.now()));
                }
                // API rate limit 방지를 위한 딜레이
                Thread.sleep(100);
            } catch (Exception e) {
                log.error("Failed to refresh quote for {}: {}", symbol, e.getMessage());
            }
        }
        log.info("Stock quotes refresh completed");
    }

    /**
     * Get real-time quote data for a stock (캐시 적용)
     * GET /quote?symbol=AAPL
     * 
     * 1. 캐시에 있고 만료 안됨 → 캐시 반환
     * 2. 캐시에 없음 → API 호출 후 캐시 저장
     * 3. 캐시 만료됨 → 캐시 반환 (백그라운드에서 갱신됨)
     */
    public FinnhubQuoteResponse getQuote(String symbol) {
        String normalizedSymbol = symbol.toUpperCase().trim();
        CachedQuote cached = quoteCache.get(normalizedSymbol);

        // 캐시에 있으면 바로 반환 (만료 여부 상관없이 - 백그라운드에서 갱신됨)
        if (cached != null) {
            return cached.response();
        }

        // 캐시에 없으면 API 호출
        log.info("Fetching quote for {} (not in cache)", normalizedSymbol);
        FinnhubQuoteResponse response = fetchQuoteFromApi(normalizedSymbol);
        if (response != null) {
            quoteCache.put(normalizedSymbol, new CachedQuote(response, Instant.now()));
        }
        return response;
    }

    /**
     * API에서 직접 Quote 조회
     */
    private FinnhubQuoteResponse fetchQuoteFromApi(String symbol) {
        String url = buildUrl("/quote")
                .queryParam("symbol", symbol)
                .toUriString();
        return get(url, FinnhubQuoteResponse.class);
    }

    /**
     * 캐시 상태 조회
     */
    public Map<String, Object> getCacheStatus() {
        return Map.of(
                "cachedSymbols", quoteCache.keySet(),
                "cacheSize", quoteCache.size(),
                "cacheTtlSeconds", CACHE_TTL.getSeconds()
        );
    }

    /**
     * Get company profile information
     * GET /stock/profile2?symbol=AAPL
     */
    public CompanyProfileResponse getCompanyProfile(String symbol) {
        String url = buildUrl("/stock/profile2")
                .queryParam("symbol", symbol)
                .toUriString();
        return get(url, CompanyProfileResponse.class);
    }

    /**
     * Get latest market news
     * GET /news?category=general
     * Categories: general, forex, crypto, merger
     */
    public List<MarketNewsResponse> getMarketNews(String category) {
        String url = buildUrl("/news")
                .queryParam("category", category)
                .toUriString();
        return getList(url, new ParameterizedTypeReference<>() {});
    }

    /**
     * Get company news
     * GET /company-news?symbol=AAPL&from=2024-01-01&to=2024-01-31
     */
    public List<MarketNewsResponse> getCompanyNews(String symbol, String from, String to) {
        String url = buildUrl("/company-news")
                .queryParam("symbol", symbol)
                .queryParam("from", from)
                .queryParam("to", to)
                .toUriString();
        return getList(url, new ParameterizedTypeReference<>() {});
    }

    /**
     * Get analyst recommendation trends
     * GET /stock/recommendation?symbol=AAPL
     */
    public List<RecommendationTrendResponse> getRecommendationTrends(String symbol) {
        String url = buildUrl("/stock/recommendation")
                .queryParam("symbol", symbol)
                .toUriString();
        return getList(url, new ParameterizedTypeReference<>() {});
    }

    /**
     * Get company basic financials (P/E ratio, 52-week high/low, etc.)
     * GET /stock/metric?symbol=AAPL&metric=all
     */
    public BasicFinancialsResponse getBasicFinancials(String symbol) {
        String url = buildUrl("/stock/metric")
                .queryParam("symbol", symbol)
                .queryParam("metric", "all")
                .toUriString();
        return get(url, BasicFinancialsResponse.class);
    }

    /**
     * Get company peers
     * GET /stock/peers?symbol=AAPL
     */
    public List<String> getCompanyPeers(String symbol) {
        String url = buildUrl("/stock/peers")
                .queryParam("symbol", symbol)
                .toUriString();
        return getList(url, new ParameterizedTypeReference<>() {});
    }

    private UriComponentsBuilder buildUrl(String path) {
        return UriComponentsBuilder
                .fromHttpUrl(properties.getBaseUrl() + path)
                .queryParam("token", properties.getApiKey());
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Finnhub-Token", properties.getApiKey());
        return headers;
    }

    private <T> T get(String url, Class<T> responseType) {
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
        return response.getBody();
    }

    private <T> List<T> getList(String url, ParameterizedTypeReference<List<T>> responseType) {
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<List<T>> response = restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
        return response.getBody();
    }
}
