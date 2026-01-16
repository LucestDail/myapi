package com.example.myapi.service;

import com.example.myapi.config.FinnhubProperties;
import com.example.myapi.dto.finnhub.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Service for Finnhub API integration.
 * Base URL: https://finnhub.io/api/v1
 * Rate limit: 30 calls/second
 * Authentication: token parameter or X-Finnhub-Token header
 */
@Service
public class FinnhubService {

    private final RestTemplate restTemplate;
    private final FinnhubProperties properties;

    public FinnhubService(RestTemplate restTemplate, FinnhubProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * Get real-time quote data for a stock
     * GET /quote?symbol=AAPL
     */
    public FinnhubQuoteResponse getQuote(String symbol) {
        String url = buildUrl("/quote")
                .queryParam("symbol", symbol)
                .toUriString();
        return get(url, FinnhubQuoteResponse.class);
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
