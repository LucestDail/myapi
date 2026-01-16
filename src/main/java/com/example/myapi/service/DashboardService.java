package com.example.myapi.service;

import com.example.myapi.dto.dashboard.DashboardConfig;
import com.example.myapi.dto.dashboard.DashboardConfig.TickerConfig;
import com.example.myapi.dto.dashboard.DashboardData;
import com.example.myapi.dto.dashboard.DashboardData.*;
import com.example.myapi.dto.finnhub.FinnhubQuoteResponse;
import com.example.myapi.dto.rss.RssFeedResponse;
import com.example.myapi.dto.system.SystemStatusResponse;
import com.example.myapi.dto.weather.CityWeatherResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 대시보드 데이터 통합 서비스
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final FinnhubService finnhubService;
    private final WeatherService weatherService;
    private final RssService rssService;
    private final SystemStatusService systemStatusService;

    // 현재 설정 저장 (인메모리)
    private final AtomicReference<DashboardConfig> currentConfig = 
            new AtomicReference<>(DashboardConfig.defaultConfig());

    public DashboardService(
            FinnhubService finnhubService,
            WeatherService weatherService,
            RssService rssService,
            SystemStatusService systemStatusService) {
        this.finnhubService = finnhubService;
        this.weatherService = weatherService;
        this.rssService = rssService;
        this.systemStatusService = systemStatusService;
    }

    // ==================== 설정 관리 ====================

    public DashboardConfig getConfig() {
        return currentConfig.get();
    }

    public void updateConfig(DashboardConfig config) {
        currentConfig.set(config);
        log.info("Dashboard config updated: {} tickers, youtube: {}", 
                config.tickers().size(), config.youtubeUrl());
    }

    // ==================== 주식 데이터 ====================

    public StocksData getStocksData() {
        DashboardConfig config = currentConfig.get();
        List<StockQuote> quotes = new ArrayList<>();

        for (TickerConfig ticker : config.tickers()) {
            try {
                FinnhubQuoteResponse response = finnhubService.getQuote(ticker.symbol());
                quotes.add(StockQuote.from(ticker.symbol(), ticker.name(), response));
            } catch (Exception e) {
                log.warn("Failed to get quote for {}: {}", ticker.symbol(), e.getMessage());
                quotes.add(StockQuote.from(ticker.symbol(), ticker.name(), null));
            }
        }

        return new StocksData(quotes, Instant.now());
    }

    // ==================== 날씨 데이터 ====================

    public List<WeatherData> getWeatherData() {
        List<CityWeatherResponse> allWeather = weatherService.getAllWeather();
        return allWeather.stream()
                .map(WeatherData::from)
                .toList();
    }

    // ==================== 뉴스 데이터 ====================

    public NewsData getNewsData() {
        // Yahoo Finance 뉴스 (첫 번째 티커 기준)
        List<NewsItem> yahooNews = new ArrayList<>();
        DashboardConfig config = currentConfig.get();
        
        if (!config.tickers().isEmpty()) {
            String firstSymbol = config.tickers().get(0).symbol();
            try {
                RssFeedResponse yahooFeed = rssService.getYahooStock(firstSymbol);
                if (yahooFeed != null && yahooFeed.items() != null) {
                    yahooNews = yahooFeed.items().stream()
                            .map(NewsItem::from)
                            .toList();
                }
            } catch (Exception e) {
                log.warn("Failed to get Yahoo news: {}", e.getMessage());
            }
        }

        // 연합뉴스
        List<NewsItem> yonhapNews = new ArrayList<>();
        try {
            RssFeedResponse yonhapFeed = rssService.getYonhapAll();
            if (yonhapFeed != null && yonhapFeed.items() != null) {
                yonhapNews = yonhapFeed.items().stream()
                        .map(NewsItem::from)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Failed to get Yonhap news: {}", e.getMessage());
        }

        return new NewsData(yahooNews, yonhapNews, Instant.now());
    }

    // ==================== 시스템 데이터 ====================

    public SystemData getSystemData() {
        SystemStatusResponse status = systemStatusService.getSystemStatus();
        return new SystemData(
                status.systemCpuLoad(),
                status.memoryUsagePercent(),
                status.usedPhysicalMemory(),
                status.totalPhysicalMemory(),
                status.heapUsagePercent(),
                status.heapUsed(),
                status.heapMax(),
                status.threadCount(),
                status.gcCount(),
                status.gcTime(),
                status.uptimeMillis()
        );
    }

    // ==================== 전체 데이터 ====================

    public DashboardData getFullData() {
        return DashboardData.full(
                getStocksData(),
                getWeatherData(),
                getNewsData(),
                getSystemData()
        );
    }
}
