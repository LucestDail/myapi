package com.example.myapi.dto.settings;

import java.util.List;

/**
 * 사용자 설정 DTO
 * 전체 설정 구조를 정의
 */
public record UserSettingsDto(
        MediaSettings media,
        StockSettings stocks,
        NewsSettings news,
        WeatherSettings weather,
        SystemSettings system,
        GeneralSettings general
) {
    /**
     * 미디어 설정
     */
    public record MediaSettings(
            List<String> youtubeUrls,
            int playOrder,  // 0: sequential, 1: random
            boolean autoPlay,
            String autoStartTime,
            String autoStopTime
    ) {
        public static MediaSettings defaultSettings() {
            return new MediaSettings(
                    List.of("https://www.youtube.com/watch?v=jfKfPfyJRdk"),
                    0,
                    true,
                    null,
                    null
            );
        }
    }

    /**
     * 주식 설정
     */
    public record StockSettings(
            List<TickerConfig> tickers,
            List<String> favorites,
            String sortBy,      // "default", "symbol", "change", "percentChange"
            String sortOrder,   // "asc", "desc"
            String filter,      // "all", "up", "down"
            double filterThreshold,
            AlertSettings alerts
    ) {
        public record TickerConfig(
                String symbol,
                String name
        ) {}

        public record AlertSettings(
                boolean enabled,
                double priceChangeThreshold,
                double percentChangeThreshold
        ) {
            public static AlertSettings defaultSettings() {
                return new AlertSettings(false, 0, 5.0);
            }
        }

        public static StockSettings defaultSettings() {
            return new StockSettings(
                    List.of(
                            new TickerConfig("SPY", "S&P500"),
                            new TickerConfig("QLD", "NAS2X"),
                            new TickerConfig("NVDA", "NVIDIA"),
                            new TickerConfig("TSLA", "TESLA")
                    ),
                    List.of(),
                    "default",
                    "desc",
                    "all",
                    0,
                    AlertSettings.defaultSettings()
            );
        }
    }

    /**
     * 뉴스 설정
     */
    public record NewsSettings(
            List<String> sources,
            List<String> includeKeywords,
            List<String> excludeKeywords,
            boolean autoSlide,
            int slideIntervalSeconds
    ) {
        public static NewsSettings defaultSettings() {
            return new NewsSettings(
                    List.of("yahoo", "yonhap"),
                    List.of(),
                    List.of(),
                    false,
                    5
            );
        }
    }

    /**
     * 날씨 설정
     */
    public record WeatherSettings(
            List<String> favoriteCities,
            int displayCount,
            boolean showAlerts
    ) {
        public static WeatherSettings defaultSettings() {
            return new WeatherSettings(
                    List.of(),
                    10,
                    true
            );
        }
    }

    /**
     * 시스템 설정
     */
    public record SystemSettings(
            int cpuWarningThreshold,
            int cpuDangerThreshold,
            int memoryWarningThreshold,
            int memoryDangerThreshold,
            boolean enableAlerts
    ) {
        public static SystemSettings defaultSettings() {
            return new SystemSettings(70, 90, 70, 90, true);
        }
    }

    /**
     * 일반 설정
     */
    public record GeneralSettings(
            boolean autoSave,
            boolean restoreStateOnRefresh,
            String theme,
            SectionStates sectionStates
    ) {
        public record SectionStates(
                boolean stocksCollapsed,
                boolean weatherCollapsed,
                boolean newsCollapsed,
                boolean systemCollapsed
        ) {
            public static SectionStates defaultStates() {
                return new SectionStates(false, false, false, false);
            }
        }

        public static GeneralSettings defaultSettings() {
            return new GeneralSettings(
                    true,
                    true,
                    "dark",
                    SectionStates.defaultStates()
            );
        }
    }

    /**
     * 기본 설정 생성
     */
    public static UserSettingsDto defaultSettings() {
        return new UserSettingsDto(
                MediaSettings.defaultSettings(),
                StockSettings.defaultSettings(),
                NewsSettings.defaultSettings(),
                WeatherSettings.defaultSettings(),
                SystemSettings.defaultSettings(),
                GeneralSettings.defaultSettings()
        );
    }
}
