package com.example.myapi.controller;

import com.example.myapi.dto.rss.RssFeedResponse;
import com.example.myapi.service.RssService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * RSS Feed Controller
 * 
 * All feeds are cached for 1 minute per URL.
 * First call fetches from source, subsequent calls within 1 minute return cache.
 */
@RestController
@RequestMapping("/api/rss")
public class RssController {

    private final RssService rssService;

    public RssController(RssService rssService) {
        this.rssService = rssService;
    }

    // ==================== Reuters ====================

    /**
     * Reuters Top News
     * GET /api/rss/reuters/top
     */
    @GetMapping("/reuters/top")
    public RssFeedResponse getReutersTop() {
        return rssService.getReutersTop();
    }

    /**
     * Reuters Business News
     * GET /api/rss/reuters/business
     */
    @GetMapping("/reuters/business")
    public RssFeedResponse getReutersBusiness() {
        return rssService.getReutersBusiness();
    }

    /**
     * Reuters Technology News
     * GET /api/rss/reuters/tech
     */
    @GetMapping("/reuters/tech")
    public RssFeedResponse getReutersTech() {
        return rssService.getReutersTech();
    }

    // ==================== Yahoo Finance ====================

    /**
     * Yahoo Finance Market News
     * GET /api/rss/yahoo/market
     */
    @GetMapping("/yahoo/market")
    public RssFeedResponse getYahooMarket() {
        return rssService.getYahooMarket();
    }

    /**
     * Yahoo Finance Stock News by Symbol
     * GET /api/rss/yahoo/stock?symbol=AAPL
     */
    @GetMapping("/yahoo/stock")
    public RssFeedResponse getYahooStock(@RequestParam String symbol) {
        return rssService.getYahooStock(symbol);
    }

    // ==================== Yonhap (Korean News) ====================

    /**
     * Yonhap All News
     * GET /api/rss/yonhap/all
     */
    @GetMapping("/yonhap/all")
    public RssFeedResponse getYonhapAll() {
        return rssService.getYonhapAll();
    }

    /**
     * Yonhap Economy News
     * GET /api/rss/yonhap/economy
     */
    @GetMapping("/yonhap/economy")
    public RssFeedResponse getYonhapEconomy() {
        return rssService.getYonhapEconomy();
    }

    /**
     * Yonhap Politics News
     * GET /api/rss/yonhap/politics
     */
    @GetMapping("/yonhap/politics")
    public RssFeedResponse getYonhapPolitics() {
        return rssService.getYonhapPolitics();
    }

    /**
     * Yonhap IT/Science News
     * GET /api/rss/yonhap/it
     */
    @GetMapping("/yonhap/it")
    public RssFeedResponse getYonhapIT() {
        return rssService.getYonhapIT();
    }

    // ==================== Custom ====================

    /**
     * Custom RSS Feed by URL
     * GET /api/rss/custom?url=https://example.com/feed.xml
     */
    @GetMapping("/custom")
    public RssFeedResponse getCustomFeed(@RequestParam String url) {
        return rssService.getCustomFeed(url);
    }

    // ==================== Cache Status ====================

    /**
     * Get cache status
     * GET /api/rss/cache/status
     */
    @GetMapping("/cache/status")
    public Map<String, Object> getCacheStatus() {
        return rssService.getCacheStatus();
    }
}
