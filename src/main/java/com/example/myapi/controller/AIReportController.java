package com.example.myapi.controller;

import com.example.myapi.dto.dashboard.DashboardData;
import com.example.myapi.dto.info.AirQualityResponse;
import com.example.myapi.dto.info.ExchangeRateResponse;
import com.example.myapi.dto.info.HolidayResponse;
import com.example.myapi.dto.info.SunTimesResponse;
import com.example.myapi.entity.News;
import com.example.myapi.service.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/ai-report")
public class AIReportController {

    @Autowired
    private NewsService newsService;

    @Autowired
    private TrafficService trafficService;

    @Autowired
    private EmergencyService emergencyService;

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private LifeInfoService lifeInfoService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Duration CLIENT_SNAPSHOT_TTL = Duration.ofMinutes(15);
    private static final int MAX_NEWS_ITEMS = 25;
    private static final int MAX_NEWS_CONTENT_CHARS = 280;

    /**
     * 데이터 토픽 목록 조회
     */
    @GetMapping(value = "/topics", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getTopics() {
        try {
            JsonObject result = new JsonObject();
            JsonObject data = new JsonObject();
            JsonObject topics = new JsonObject();
            
            // 모든 토픽 기본값 true
            topics.addProperty("news", true);
            topics.addProperty("weather", true);
            topics.addProperty("traffic", true);
            topics.addProperty("emergency", true);
            topics.addProperty("stocks", true);
            topics.addProperty("yahooFinance", true);
            topics.addProperty("yonhapNews", true);
            topics.addProperty("lifeInfo", true);
            topics.addProperty("system", true);
            
            data.add("topics", topics);
            result.add("data", data);
            return result.toString();
        } catch (Exception e) {
            log.error("Error getting topics: {}", e.getMessage());
            JsonObject result = new JsonObject();
            JsonObject data = new JsonObject();
            JsonObject topics = new JsonObject();
            // 모든 토픽 false로 설정
            for (String key : Arrays.asList("news", "weather", "traffic", "emergency", "stocks", "yahooFinance", "yonhapNews", "lifeInfo", "system")) {
                topics.addProperty(key, false);
            }
            data.add("topics", topics);
            result.add("data", data);
            return result.toString();
        }
    }

    /**
     * AI 보고서 생성 (RAG 방식 - 모든 선택된 데이터를 컨텍스트로 전달)
     */
    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public String generateReport(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody Map<String, Object> request) {
        String effectiveUserId = headerUserId != null ? headerUserId : userId;
        if (effectiveUserId == null || effectiveUserId.isBlank()) {
            effectiveUserId = "default";
            log.warn("AI report request without userId — using default tickers");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Boolean> topics = (Map<String, Boolean>) request.getOrDefault("topics", new HashMap<>());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> settings = (Map<String, Object>) request.getOrDefault("settings", new HashMap<>());

            @SuppressWarnings("unchecked")
            Map<String, Object> clientSnapshot = (Map<String, Object>) request.get("snapshot");
            
            // 각 토픽별 포함 여부
            boolean includeNews = topics.getOrDefault("news", true);
            boolean includeWeather = topics.getOrDefault("weather", true);
            boolean includeTraffic = topics.getOrDefault("traffic", true);
            boolean includeEmergency = topics.getOrDefault("emergency", true);
            boolean includeStocks = topics.getOrDefault("stocks", true);
            boolean includeYahooFinance = topics.getOrDefault("yahooFinance", true);
            boolean includeYonhapNews = topics.getOrDefault("yonhapNews", true);
            boolean includeLifeInfo = topics.getOrDefault("lifeInfo", true);
            boolean includeSystem = topics.getOrDefault("system", true);

            String systemInstruction = buildSystemPrompt();

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시");
            String currentDateTime = now.format(formatter);
            
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("다음은 ").append(currentDateTime).append(" 시점의 대시보드 데이터예요. 위 지침에 따라 종합적인 리포트를 작성해주세요.\n");
            promptBuilder.append("리포트 제목이나 시작 부분에 '").append(currentDateTime).append(" 리포트'라고 명시해주세요.\n\n");

            boolean needStocks = includeStocks;
            boolean needWeather = includeWeather;
            boolean needNews = includeYahooFinance || includeYonhapNews;
            boolean needSystem = includeSystem;

            DashboardService.ReportBundle bundle = dashboardService.getReportBundle(
                    effectiveUserId, needStocks, needWeather, needNews, needSystem);
            bundle = mergeClientSnapshot(bundle, clientSnapshot);

            log.info("AI report context for user {} — serverCache={}, clientSnapshot={}, stocks={}, yahooNews={}",
                    effectiveUserId,
                    bundle.fromCache(),
                    clientSnapshot != null,
                    bundle.stocks() != null && bundle.stocks().quotes() != null ? bundle.stocks().quotes().size() : 0,
                    bundle.news() != null && bundle.news().yahooNews() != null ? bundle.news().yahooNews().size() : 0);

            if (includeNews) {
                appendDbNews(promptBuilder);
            }

            if (includeStocks && bundle.stocks() != null) {
                appendStocks(promptBuilder, bundle.stocks());
            }

            if (includeYahooFinance && bundle.news() != null) {
                appendYahooNews(promptBuilder, bundle.news(), clientSnapshot);
            }

            if (includeYonhapNews && bundle.news() != null) {
                appendYonhapNews(promptBuilder, bundle.news());
            }

            if (includeWeather && bundle.weather() != null) {
                appendWeather(promptBuilder, bundle.weather());
            }

            if (includeLifeInfo) {
                appendLifeInfo(promptBuilder);
            }

            if (includeTraffic) {
                appendTraffic(promptBuilder);
            }

            if (includeEmergency) {
                appendEmergency(promptBuilder);
            }

            if (includeSystem && bundle.system() != null) {
                appendSystem(promptBuilder, bundle.system());
            }

            String prompt = promptBuilder.toString();
            String report = geminiService.generateContent(prompt, settings, systemInstruction);

            JsonObject result = new JsonObject();
            JsonObject data = new JsonObject();
            data.addProperty("report", report);
            result.add("data", data);
            return result.toString();

        } catch (Exception e) {
            log.error("Error generating AI report: {}", e.getMessage(), e);
            JsonObject result = new JsonObject();
            JsonObject data = new JsonObject();
            data.addProperty("report", "리포트 생성 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.");
            data.addProperty("error", e.getMessage());
            result.add("data", data);
            return result.toString();
        }
    }

    private String buildSystemPrompt() {
        StringBuilder systemPromptBuilder = new StringBuilder();
        systemPromptBuilder.append("당신은 경제·금융·시사 정보를 종합적으로 분석하고 전달하는 전문 리포트 작가예요.\n\n");
        systemPromptBuilder.append("## 페르소나\n");
        systemPromptBuilder.append("- 독자들이 하루를 시작하기 전에 세상의 흐름을 한눈에 파악할 수 있도록 돕는 친근한 안내자\n");
        systemPromptBuilder.append("- 복잡한 정보를 쉽고 읽기 편하게 전달하는 전문가\n");
        systemPromptBuilder.append("- 데이터의 의미와 맥락을 종합적으로 해석하여 인사이트를 제공하는 분석가\n\n");
        systemPromptBuilder.append("## 작성 원칙 (반드시 지켜야 할 사항)\n");
        systemPromptBuilder.append("1. **절대 줄 단위로 나열하지 말 것**: 개별 뉴스나 데이터를 '- 제목: ...' 형태로 나열하지 말고, 관련된 내용들을 종합하여 하나의 문단(2-4문장)으로 구성해요.\n");
        systemPromptBuilder.append("2. **섹션별 종합 분석 필수**: 같은 주제의 여러 뉴스나 데이터가 있으면, 그것들의 공통점, 차이점, 흐름, 의미를 파악하여 종합적으로 설명해요.\n");
        systemPromptBuilder.append("3. **읽기 편한 구조**: 이모지로 섹션을 구분하고, 각 섹션은 2-4문단으로 구성해요.\n");
        systemPromptBuilder.append("4. **맥락과 인사이트 제공**: 단순히 '무엇'이 아니라 '왜', '어떤 의미인지', '앞으로 어떻게 될지'를 함께 설명해요.\n");
        systemPromptBuilder.append("5. **문체**: 해요체 사용, 능동형 문장, 긍정적 표현, 캐주얼한 경어, 한자어 풀어쓰기\n");
        return systemPromptBuilder.toString();
    }

    private DashboardService.ReportBundle mergeClientSnapshot(
            DashboardService.ReportBundle server,
            Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty() || !isClientSnapshotFresh(snapshot)) {
            return server;
        }

        DashboardData.StocksData stocks = server.stocks();
        if (snapshot.containsKey("stocks")) {
            DashboardData.StocksData clientStocks = objectMapper.convertValue(
                    snapshot.get("stocks"), DashboardData.StocksData.class);
            if (clientStocks != null && clientStocks.quotes() != null && !clientStocks.quotes().isEmpty()) {
                stocks = clientStocks;
            }
        }

        List<DashboardData.WeatherData> weather = server.weather();
        if (snapshot.containsKey("weather")) {
            List<DashboardData.WeatherData> clientWeather = objectMapper.convertValue(
                    snapshot.get("weather"), new TypeReference<>() {});
            if (clientWeather != null && !clientWeather.isEmpty()) {
                weather = clientWeather;
            }
        }

        DashboardData.NewsData news = server.news();
        List<DashboardData.NewsItem> yahooNews = news != null ? news.yahooNews() : null;
        List<DashboardData.NewsItem> yonhapNews = news != null ? news.yonhapNews() : null;

        if (snapshot.containsKey("yahooNews")) {
            List<DashboardData.NewsItem> clientYahoo = objectMapper.convertValue(
                    snapshot.get("yahooNews"), new TypeReference<>() {});
            if (clientYahoo != null && !clientYahoo.isEmpty()) {
                yahooNews = clientYahoo;
            }
        }
        if (snapshot.containsKey("yonhapNews")) {
            List<DashboardData.NewsItem> clientYonhap = objectMapper.convertValue(
                    snapshot.get("yonhapNews"), new TypeReference<>() {});
            if (clientYonhap != null && !clientYonhap.isEmpty()) {
                yonhapNews = clientYonhap;
            }
        }
        if (yahooNews != null || yonhapNews != null) {
            news = new DashboardData.NewsData(
                    yahooNews != null ? yahooNews : List.of(),
                    yonhapNews != null ? yonhapNews : List.of(),
                    Instant.now());
        }

        DashboardData.SystemData system = server.system();
        if (snapshot.containsKey("system")) {
            DashboardData.SystemData clientSystem = objectMapper.convertValue(
                    snapshot.get("system"), DashboardData.SystemData.class);
            if (clientSystem != null) {
                system = clientSystem;
            }
        }

        return new DashboardService.ReportBundle(stocks, weather, news, system, true);
    }

    private boolean isClientSnapshotFresh(Map<String, Object> snapshot) {
        Object fetchedAt = snapshot.get("fetchedAt");
        if (fetchedAt == null) {
            return true;
        }
        try {
            Instant at = Instant.parse(fetchedAt.toString());
            return Duration.between(at, Instant.now()).compareTo(CLIENT_SNAPSHOT_TTL) <= 0;
        } catch (Exception e) {
            return true;
        }
    }

    private void appendDbNews(StringBuilder promptBuilder) {
        List<News> recentNews = newsService.getAllNews();
        if (recentNews == null || recentNews.isEmpty()) {
            return;
        }
        int newsCount = Math.min(recentNews.size(), MAX_NEWS_ITEMS);
        promptBuilder.append("## 뉴스 데이터 (DB)\n\n");
        for (int i = 0; i < newsCount; i++) {
            News news = recentNews.get(i);
            promptBuilder.append("- 제목: ").append(news.getNewsTitle() != null ? news.getNewsTitle() : "").append("\n");
            String content = news.getNewsContents() != null ? news.getNewsContents() : "";
            if (content.length() > MAX_NEWS_CONTENT_CHARS) {
                content = content.substring(0, MAX_NEWS_CONTENT_CHARS) + "...";
            }
            promptBuilder.append("  내용: ").append(content).append("\n\n");
        }
        promptBuilder.append("\n");
    }

    private void appendStocks(StringBuilder promptBuilder, DashboardData.StocksData stocksData) {
        if (stocksData.quotes() == null || stocksData.quotes().isEmpty()) {
            return;
        }
        promptBuilder.append("## 주식 정보 (세션 관심 종목)\n\n");
        if (stocksData.fetchedAt() != null) {
            promptBuilder.append("데이터 수집 시각: ").append(stocksData.fetchedAt()).append("\n\n");
        }
        for (DashboardData.StockQuote quote : stocksData.quotes()) {
            appendStockQuote(promptBuilder, quote);
        }
        promptBuilder.append("\n");
    }

    private void appendStockQuote(StringBuilder promptBuilder, DashboardData.StockQuote quote) {
        promptBuilder.append("- ").append(quote.symbol());
        if (quote.name() != null && !quote.name().isBlank()) {
            promptBuilder.append(" (").append(quote.name()).append(")");
        }
        promptBuilder.append(": ");
        if (quote.currentPrice() != null) {
            promptBuilder.append("현재가 ").append(String.format("%.2f", quote.currentPrice()));
        }
        if (quote.percentChange() != null) {
            promptBuilder.append(", ").append(quote.percentChange() >= 0 ? "+" : "")
                    .append(String.format("%.2f", quote.percentChange())).append("%");
        }
        if (quote.change() != null) {
            promptBuilder.append(" (변동 ").append(String.format("%.2f", quote.change())).append(")");
        }
        if (quote.openPrice() != null) {
            promptBuilder.append(", 시가 ").append(String.format("%.2f", quote.openPrice()));
        }
        if (quote.highPrice() != null && quote.lowPrice() != null) {
            promptBuilder.append(", 고/저 ").append(String.format("%.2f", quote.highPrice()))
                    .append("/").append(String.format("%.2f", quote.lowPrice()));
        }
        if (quote.previousClose() != null) {
            promptBuilder.append(", 전일종가 ").append(String.format("%.2f", quote.previousClose()));
        }
        promptBuilder.append("\n");
    }

    private void appendYahooNews(
            StringBuilder promptBuilder,
            DashboardData.NewsData newsData,
            Map<String, Object> clientSnapshot) {
        List<DashboardData.NewsItem> items = newsData.yahooNews();
        if (items == null || items.isEmpty()) {
            return;
        }
        promptBuilder.append("## 야후 파이낸스 뉴스\n\n");
        items.stream().limit(MAX_NEWS_ITEMS).forEach(item ->
                promptBuilder.append("- [").append(item.source() != null ? item.source() : "Yahoo")
                        .append("] ").append(item.title() != null ? item.title() : "").append("\n"));
        promptBuilder.append("\n");

        if (clientSnapshot != null && clientSnapshot.containsKey("stockNews")) {
            List<Map<String, Object>> stockNews = objectMapper.convertValue(
                    clientSnapshot.get("stockNews"), new TypeReference<>() {});
            if (stockNews != null && !stockNews.isEmpty()) {
                promptBuilder.append("## 관심 종목별 최근 뉴스 (화면 세션)\n\n");
                stockNews.stream().limit(15).forEach(item -> {
                    Object title = item.get("title");
                    Object symbol = item.get("symbol");
                    if (title != null) {
                        promptBuilder.append("- ");
                        if (symbol != null) {
                            promptBuilder.append("[").append(symbol).append("] ");
                        }
                        promptBuilder.append(title).append("\n");
                    }
                });
                promptBuilder.append("\n");
            }
        }
    }

    private void appendYonhapNews(StringBuilder promptBuilder, DashboardData.NewsData newsData) {
        if (newsData.yonhapNews() == null || newsData.yonhapNews().isEmpty()) {
            return;
        }
        promptBuilder.append("## 연합뉴스\n\n");
        for (DashboardData.NewsItem item : newsData.yonhapNews().stream().limit(MAX_NEWS_ITEMS).collect(Collectors.toList())) {
            promptBuilder.append("- ").append(item.title() != null ? item.title() : "").append("\n");
        }
        promptBuilder.append("\n");
    }

    private void appendWeather(StringBuilder promptBuilder, List<DashboardData.WeatherData> weatherList) {
        if (weatherList.isEmpty()) {
            return;
        }
        promptBuilder.append("## 전국 주요 도시 날씨 정보\n\n");
        for (DashboardData.WeatherData weather : weatherList) {
            promptBuilder.append("- ").append(weather.cityKo() != null ? weather.cityKo() : weather.city()).append(": ");
            promptBuilder.append(weather.temperatureCelsius()).append("°C, ")
                    .append(weather.humidity()).append("% 습도, ")
                    .append(weather.weather() != null ? weather.weather() : "").append("\n");
        }
        promptBuilder.append("\n");
    }

    private void appendLifeInfo(StringBuilder promptBuilder) {
        try {
            ExchangeRateResponse exchangeRates = lifeInfoService.getExchangeRates("USD");
            AirQualityResponse airQuality = lifeInfoService.getAirQuality("Seoul");
            SunTimesResponse sunTimes = lifeInfoService.getSunTimes(37.5665, 126.9780, "Seoul");
            HolidayResponse.Holiday nextHoliday = lifeInfoService.getNextHoliday();

            promptBuilder.append("## 생활 정보\n\n");
            if (exchangeRates != null) {
                promptBuilder.append("### 환율 정보\n");
                promptBuilder.append("기준 통화: ").append(exchangeRates.base()).append("\n");
                exchangeRates.rates().forEach((currency, rate) ->
                        promptBuilder.append(currency).append(": ").append(rate).append("\n"));
                promptBuilder.append("\n");
            }
            if (airQuality != null) {
                promptBuilder.append("### 미세먼지 정보\n");
                promptBuilder.append("위치: ").append(airQuality.location()).append("\n");
                promptBuilder.append("PM10: ").append(airQuality.pm10() != null ? airQuality.pm10() : "N/A")
                        .append(" (").append(airQuality.pm10Grade() != null ? airQuality.pm10Grade() : "N/A").append(")\n");
                promptBuilder.append("PM2.5: ").append(airQuality.pm25() != null ? airQuality.pm25() : "N/A")
                        .append(" (").append(airQuality.pm25Grade() != null ? airQuality.pm25Grade() : "N/A").append(")\n");
                promptBuilder.append("통합 지수: ").append(airQuality.aqi() != null ? airQuality.aqi() : "N/A")
                        .append(" (").append(airQuality.overallGrade() != null ? airQuality.overallGrade() : "N/A").append(")\n\n");
            }
            if (sunTimes != null) {
                promptBuilder.append("### 일출/일몰 정보\n");
                promptBuilder.append("위치: ").append(sunTimes.location()).append("\n");
                promptBuilder.append("일출: ").append(sunTimes.sunrise() != null ? sunTimes.sunrise() : "N/A").append("\n");
                promptBuilder.append("일몰: ").append(sunTimes.sunset() != null ? sunTimes.sunset() : "N/A").append("\n");
                if (sunTimes.dayLength() != null) {
                    promptBuilder.append("낮 길이: ").append(sunTimes.dayLength()).append("\n");
                }
                promptBuilder.append("\n");
            }
            if (nextHoliday != null) {
                promptBuilder.append("### 다음 공휴일\n");
                promptBuilder.append(nextHoliday.name()).append(" (").append(nextHoliday.date().toString()).append(")\n\n");
            }
            promptBuilder.append("\n");
        } catch (Exception e) {
            log.warn("Failed to get life info: {}", e.getMessage());
        }
    }

    private void appendTraffic(StringBuilder promptBuilder) {
        JsonObject trafficData = trafficService.getTrafficInfo();
        if (trafficData != null && trafficData.has("body") && trafficData.getAsJsonObject("body").has("items")) {
            promptBuilder.append("## 교통돌발상황 정보\n\n");
            promptBuilder.append(trafficData.toString()).append("\n\n");
        }
    }

    private void appendEmergency(StringBuilder promptBuilder) {
        JsonObject emergencyData = emergencyService.getEmergencyInfo();
        if (emergencyData != null) {
            promptBuilder.append("## 긴급재난문자 정보\n\n");
            promptBuilder.append(emergencyData.toString()).append("\n\n");
        }
    }

    private void appendSystem(StringBuilder promptBuilder, DashboardData.SystemData systemData) {
        promptBuilder.append("## 시스템 정보\n\n");
        promptBuilder.append("CPU 사용률: ").append(String.format("%.2f", systemData.cpuUsage())).append("%\n");
        promptBuilder.append("메모리 사용률: ").append(String.format("%.2f", systemData.memoryUsagePercent())).append("%\n");
        promptBuilder.append("JVM Heap 사용률: ").append(String.format("%.2f", systemData.heapUsagePercent())).append("%\n");
        promptBuilder.append("스레드 수: ").append(systemData.threadCount()).append("\n");
        promptBuilder.append("가동시간: ").append(systemData.uptimeMillis() / 1000 / 60).append("분\n\n");
    }
}
