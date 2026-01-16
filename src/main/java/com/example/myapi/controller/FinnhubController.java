package com.example.myapi.controller;

import com.example.myapi.dto.finnhub.*;
import com.example.myapi.service.FinnhubService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finnhub")
public class FinnhubController {

    private final FinnhubService finnhubService;

    public FinnhubController(FinnhubService finnhubService) {
        this.finnhubService = finnhubService;
    }

    /**
     * Get real-time quote for a stock
     * GET /api/finnhub/quote?symbol=AAPL
     */
    @GetMapping("/quote")
    public FinnhubQuoteResponse getQuote(@RequestParam String symbol) {
        return finnhubService.getQuote(symbol);
    }

    /**
     * Get company profile
     * GET /api/finnhub/profile?symbol=AAPL
     */
    @GetMapping("/profile")
    public CompanyProfileResponse getCompanyProfile(@RequestParam String symbol) {
        return finnhubService.getCompanyProfile(symbol);
    }

    /**
     * Get market news
     * GET /api/finnhub/news?category=general
     * Categories: general, forex, crypto, merger
     */
    @GetMapping("/news")
    public List<MarketNewsResponse> getMarketNews(@RequestParam(defaultValue = "general") String category) {
        return finnhubService.getMarketNews(category);
    }

    /**
     * Get company news
     * GET /api/finnhub/company-news?symbol=AAPL&from=2024-01-01&to=2024-01-31
     */
    @GetMapping("/company-news")
    public List<MarketNewsResponse> getCompanyNews(
            @RequestParam String symbol,
            @RequestParam String from,
            @RequestParam String to) {
        return finnhubService.getCompanyNews(symbol, from, to);
    }

    /**
     * Get analyst recommendation trends
     * GET /api/finnhub/recommendation?symbol=AAPL
     */
    @GetMapping("/recommendation")
    public List<RecommendationTrendResponse> getRecommendationTrends(@RequestParam String symbol) {
        return finnhubService.getRecommendationTrends(symbol);
    }

    /**
     * Get basic financials (P/E, 52-week high/low, etc.)
     * GET /api/finnhub/financials?symbol=AAPL
     */
    @GetMapping("/financials")
    public BasicFinancialsResponse getBasicFinancials(@RequestParam String symbol) {
        return finnhubService.getBasicFinancials(symbol);
    }

    /**
     * Get company peers
     * GET /api/finnhub/peers?symbol=AAPL
     */
    @GetMapping("/peers")
    public List<String> getCompanyPeers(@RequestParam String symbol) {
        return finnhubService.getCompanyPeers(symbol);
    }

    /**
     * Get cache status
     * GET /api/finnhub/cache/status
     */
    @GetMapping("/cache/status")
    public Map<String, Object> getCacheStatus() {
        return finnhubService.getCacheStatus();
    }
}
