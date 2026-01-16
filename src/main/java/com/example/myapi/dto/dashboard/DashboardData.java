package com.example.myapi.dto.dashboard;

import com.example.myapi.dto.finnhub.FinnhubQuoteResponse;
import com.example.myapi.dto.rss.RssItem;
import com.example.myapi.dto.weather.CityWeatherResponse;

import java.time.Instant;
import java.util.List;

/**
 * Dashboard SSE 데이터 전송용 DTO
 */
public record DashboardData(
        String type,                              // "stocks", "weather", "news", "system", "full"
        Instant timestamp,
        StocksData stocks,
        List<WeatherData> weather,
        NewsData news,
        SystemData system
) {
    public static DashboardData stocks(StocksData stocks) {
        return new DashboardData("stocks", Instant.now(), stocks, null, null, null);
    }

    public static DashboardData weather(List<WeatherData> weather) {
        return new DashboardData("weather", Instant.now(), null, weather, null, null);
    }

    public static DashboardData news(NewsData news) {
        return new DashboardData("news", Instant.now(), null, null, news, null);
    }

    public static DashboardData system(SystemData system) {
        return new DashboardData("system", Instant.now(), null, null, null, system);
    }

    public static DashboardData full(StocksData stocks, List<WeatherData> weather, NewsData news, SystemData system) {
        return new DashboardData("full", Instant.now(), stocks, weather, news, system);
    }

    // 주식 데이터
    public record StocksData(
            List<StockQuote> quotes,
            Instant fetchedAt
    ) {}

    public record StockQuote(
            String symbol,
            String name,
            Double currentPrice,
            Double change,
            Double percentChange,
            Double highPrice,
            Double lowPrice,
            Double openPrice,
            Double previousClose
    ) {
        public static StockQuote from(String symbol, String name, FinnhubQuoteResponse response) {
            if (response == null) {
                return new StockQuote(symbol, name, null, null, null, null, null, null, null);
            }
            return new StockQuote(
                    symbol,
                    name,
                    response.currentPrice(),
                    response.change(),
                    response.percentChange(),
                    response.highPrice(),
                    response.lowPrice(),
                    response.openPrice(),
                    response.previousClose()
            );
        }
    }

    // 날씨 데이터
    public record WeatherData(
            String city,
            String cityKo,
            double temperatureCelsius,
            int humidity,
            String weather,
            String icon
    ) {
        public static WeatherData from(CityWeatherResponse response) {
            return new WeatherData(
                    response.city(),
                    response.cityKo(),
                    response.temperatureCelsius(),
                    response.humidity(),
                    response.weather(),
                    response.icon()
            );
        }
    }

    // 뉴스 데이터
    public record NewsData(
            List<NewsItem> yahooNews,
            List<NewsItem> yonhapNews,
            Instant fetchedAt
    ) {}

    public record NewsItem(
            String title,
            String link,
            String source,
            String pubDate
    ) {
        public static NewsItem from(RssItem item) {
            return new NewsItem(
                    item.title(),
                    item.link(),
                    item.source(),
                    item.pubDate()
            );
        }
    }

    // 시스템 데이터
    public record SystemData(
            double cpuUsage,
            double memoryUsagePercent,
            long memoryUsed,
            long memoryTotal,
            double heapUsagePercent,
            long heapUsed,
            long heapMax,
            int threadCount,
            long gcCount,
            long gcTime,
            long uptimeMillis
    ) {}
}
