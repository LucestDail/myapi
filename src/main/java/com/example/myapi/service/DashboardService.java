package com.example.myapi.service;

import com.example.myapi.dto.dashboard.DashboardConfig;
import com.example.myapi.dto.dashboard.DashboardConfig.TickerConfig;
import com.example.myapi.dto.dashboard.DashboardData;
import com.example.myapi.dto.dashboard.DashboardData.*;
import com.example.myapi.dto.finnhub.FinnhubQuoteResponse;
import com.example.myapi.dto.rss.RssFeedResponse;
import com.example.myapi.dto.settings.UserSettingsDto;
import com.example.myapi.dto.system.SystemStatusResponse;
import com.example.myapi.dto.weather.CityWeatherResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    private final UserSettingsService userSettingsService;

    public DashboardService(
            FinnhubService finnhubService,
            WeatherService weatherService,
            RssService rssService,
            SystemStatusService systemStatusService,
            UserSettingsService userSettingsService) {
        this.finnhubService = finnhubService;
        this.weatherService = weatherService;
        this.rssService = rssService;
        this.systemStatusService = systemStatusService;
        this.userSettingsService = userSettingsService;
    }

    // ==================== 설정 관리 ====================

    /**
     * 사용자별 대시보드 설정 조회
     */
    public DashboardConfig getConfig(String userId) {
        UserSettingsDto settings = userSettingsService.getSettings(userId);
        DashboardConfig config = convertToDashboardConfig(settings);
        log.debug("Retrieved config for user {}: {} tickers", userId, config.tickers().size());
        return config;
    }

    /**
     * 사용자별 대시보드 설정 저장
     */
    public DashboardConfig updateConfig(String userId, DashboardConfig config) {
        UserSettingsDto currentSettings = userSettingsService.getSettings(userId);
        log.debug("Current settings for user {}: {} tickers", userId, 
                currentSettings.stocks().tickers().size());
        
        UserSettingsDto updatedSettings = mergeDashboardConfig(currentSettings, config);
        log.debug("Merged settings for user {}: {} tickers", userId, 
                updatedSettings.stocks().tickers().size());
        
        userSettingsService.saveSettings(userId, updatedSettings);
        log.info("Dashboard config updated for user {}: {} tickers, youtube: {}", 
                userId, config.tickers().size(), config.youtubeUrl());
        
        // 저장 후 다시 조회하여 실제 저장된 값 반환
        UserSettingsDto savedSettings = userSettingsService.getSettings(userId);
        DashboardConfig savedConfig = convertToDashboardConfig(savedSettings);
        log.debug("Retrieved saved config for user {}: {} tickers: {}", 
                userId, savedConfig.tickers().size(),
                savedConfig.tickers().stream()
                        .map(t -> t.symbol())
                        .collect(java.util.stream.Collectors.joining(", ")));
        return savedConfig;
    }

    /**
     * UserSettingsDto를 DashboardConfig로 변환
     */
    private DashboardConfig convertToDashboardConfig(UserSettingsDto settings) {
        // MediaSettings의 첫 번째 URL을 youtubeUrl로 사용
        String youtubeUrl = settings.media().youtubeUrls().isEmpty() 
                ? "https://www.youtube.com/watch?v=jfKfPfyJRdk"
                : settings.media().youtubeUrls().get(0);
        
        // StockSettings의 tickers를 TickerConfig 리스트로 변환
        List<TickerConfig> tickers = settings.stocks().tickers().stream()
                .map(t -> new TickerConfig(t.symbol(), t.name()))
                .collect(Collectors.toList());
        
        return new DashboardConfig(youtubeUrl, tickers);
    }

    /**
     * DashboardConfig를 UserSettingsDto에 병합
     */
    private UserSettingsDto mergeDashboardConfig(UserSettingsDto current, DashboardConfig config) {
        // MediaSettings 업데이트
        List<String> youtubeUrls = config.youtubeUrl() != null 
                ? List.of(config.youtubeUrl())
                : current.media().youtubeUrls();
        
        UserSettingsDto.MediaSettings mediaSettings = new UserSettingsDto.MediaSettings(
                youtubeUrls,
                current.media().playOrder(),
                current.media().autoPlay(),
                current.media().autoStartTime(),
                current.media().autoStopTime()
        );

        // StockSettings 업데이트
        List<UserSettingsDto.StockSettings.TickerConfig> tickers = config.tickers().stream()
                .map(t -> new UserSettingsDto.StockSettings.TickerConfig(t.symbol(), t.name()))
                .collect(Collectors.toList());
        
        UserSettingsDto.StockSettings stockSettings = new UserSettingsDto.StockSettings(
                tickers,
                current.stocks().favorites(),
                current.stocks().sortBy(),
                current.stocks().sortOrder(),
                current.stocks().filter(),
                current.stocks().filterThreshold(),
                current.stocks().alerts()
        );

        return new UserSettingsDto(
                mediaSettings,
                stockSettings,
                current.news(),
                current.weather(),
                current.system(),
                current.general()
        );
    }

    // ==================== 주식 데이터 ====================

    /**
     * 사용자별 주식 데이터 조회
     */
    public StocksData getStocksData(String userId) {
        DashboardConfig config = getConfig(userId);
        List<StockQuote> quotes = new ArrayList<>();

        log.debug("Getting stocks data for user {} with {} tickers: {}", 
                userId, config.tickers().size(), 
                config.tickers().stream().map(TickerConfig::symbol).collect(java.util.stream.Collectors.joining(", ")));

        for (TickerConfig ticker : config.tickers()) {
            try {
                FinnhubQuoteResponse response = finnhubService.getQuote(ticker.symbol());
                quotes.add(StockQuote.from(ticker.symbol(), ticker.name(), response));
            } catch (Exception e) {
                log.warn("Failed to get quote for {}: {}", ticker.symbol(), e.getMessage());
                quotes.add(StockQuote.from(ticker.symbol(), ticker.name(), null));
            }
        }

        log.debug("Retrieved {} stock quotes for user {}", quotes.size(), userId);
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
        // Yahoo Finance 종합 경제 뉴스 (대시보드용)
        List<NewsItem> yahooNews = new ArrayList<>();
        try {
            RssFeedResponse yahooFeed = rssService.getYahooMarket();
            if (yahooFeed != null && yahooFeed.items() != null) {
                yahooNews = yahooFeed.items().stream()
                        .map(NewsItem::from)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Failed to get Yahoo market news: {}", e.getMessage());
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

    /**
     * 사용자별 전체 데이터 조회
     */
    public DashboardData getFullData(String userId) {
        return DashboardData.full(
                getStocksData(userId),
                getWeatherData(),
                getNewsData(),
                getSystemData()
        );
    }
}
