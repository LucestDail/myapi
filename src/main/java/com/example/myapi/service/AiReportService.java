package com.example.myapi.service;

import com.example.myapi.dto.dashboard.DashboardData;
import com.example.myapi.dto.info.AirQualityResponse;
import com.example.myapi.dto.info.ExchangeRateResponse;
import com.example.myapi.dto.info.HolidayResponse;
import com.example.myapi.dto.info.SunTimesResponse;
import com.example.myapi.entity.News;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 리포트 생성.
 *
 * <p>원래 {@code AIReportController} 안에 있던 프롬프트 조립·Gemini 호출을 그대로 옮긴 것이다
 * (로직 변경 없음). 옮긴 이유는 하나 — <b>일간 자동 리포트 스케줄러가 같은 경로를 써야</b> 하기
 * 때문이다. 컨트롤러 안에 두면 스케줄러는 프롬프트를 새로 짜야 하고, 그러면 두 벌이 서서히
 * 갈라져 "화면 리포트와 자동 리포트가 다른 것을 본다".</p>
 */
@Slf4j
@Service
public class AiReportService {

    private static final Duration CLIENT_SNAPSHOT_TTL = Duration.ofMinutes(15);
    private static final int MAX_NEWS_ITEMS = 25;
    private static final int MAX_NEWS_CONTENT_CHARS = 280;

    private final NewsService newsService;
    private final TrafficService trafficService;
    private final EmergencyService emergencyService;
    private final GeminiService geminiService;
    private final DashboardService dashboardService;
    private final LifeInfoService lifeInfoService;
    private final ObjectMapper objectMapper;

    public AiReportService(NewsService newsService,
                           TrafficService trafficService,
                           EmergencyService emergencyService,
                           GeminiService geminiService,
                           DashboardService dashboardService,
                           LifeInfoService lifeInfoService,
                           ObjectMapper objectMapper) {
        this.newsService = newsService;
        this.trafficService = trafficService;
        this.emergencyService = emergencyService;
        this.geminiService = geminiService;
        this.dashboardService = dashboardService;
        this.lifeInfoService = lifeInfoService;
        this.objectMapper = objectMapper;
    }

    /** 리포트에 담을 토픽. 화면이 보내는 map 과 스케줄러의 기본값을 한 형태로 받는다. */
    public record Topics(
            boolean news,
            boolean weather,
            boolean traffic,
            boolean emergency,
            boolean stocks,
            boolean yahooFinance,
            boolean yonhapNews,
            boolean lifeInfo,
            boolean system) {

        public static final List<String> NAMES = List.of(
                "news", "weather", "traffic", "emergency", "stocks",
                "yahooFinance", "yonhapNews", "lifeInfo", "system");

        public static Topics from(Map<String, Boolean> map) {
            Map<String, Boolean> m = map != null ? map : Map.of();
            return new Topics(
                    m.getOrDefault("news", true),
                    m.getOrDefault("weather", true),
                    m.getOrDefault("traffic", true),
                    m.getOrDefault("emergency", true),
                    m.getOrDefault("stocks", true),
                    m.getOrDefault("yahooFinance", true),
                    m.getOrDefault("yonhapNews", true),
                    m.getOrDefault("lifeInfo", true),
                    m.getOrDefault("system", true));
        }

        public static Topics all() {
            return from(null);
        }
    }

    /**
     * 리포트 생성. 실패는 삼키지 않고 던진다 — 호출자(컨트롤러는 사용자 안내, 스케줄러는
     * "이력에 남기지 않음")가 각자 다르게 처리해야 한다.
     */
    public String generate(String userId, Topics topics, Map<String, Object> settings,
                           Map<String, Object> clientSnapshot) {
        String prompt = buildPrompt(userId, topics, clientSnapshot);
        return geminiService.generateContent(prompt, settings, buildSystemPrompt());
    }

    /**
     * 위와 같되 <b>실제 진행 단계</b>를 흘려보낸다.
     *
     * <p>왜 필요했나: 화면의 진행률이 2초짜리 타이머로 돌아가는 가짜였다. 서버가 뭘 하는지와
     * 무관해서, 수집이 오래 걸려도 "거의 다 됐어요" 가 떠 있고 실패해도 눈치챌 수 없었다.
     * 여기서 내보내는 단계는 전부 실제로 그 일이 끝난 뒤에 찍힌다.
     *
     * @param onStage 단계 라벨(수집원 이름 등). null 이면 무시
     * @param onDelta 본문 조각. Gemini 스트림이 오는 대로 그대로 흘린다. null 이면 무시
     */
    public String generate(String userId, Topics topics, Map<String, Object> settings,
                           Map<String, Object> clientSnapshot,
                           Consumer<String> onStage, Consumer<String> onDelta) {
        String prompt = buildPrompt(userId, topics, clientSnapshot, onStage);
        if (onStage != null) {
            onStage.accept("AI 모델 호출 (프롬프트 " + prompt.length() + "자)");
        }
        return geminiService.generateContentStream(prompt, settings, buildSystemPrompt(), onDelta);
    }

    /**
     * 이미 만들어진 프롬프트로 리포트를 생성한다(주간 트렌드 분석처럼 대시보드 데이터가 아니라
     * 지난 리포트를 재료로 쓰는 경우). 페르소나·문체 지침은 일간 리포트와 같은 것을 쓴다 —
     * 같은 사람이 쓴 글처럼 읽혀야 한다.
     */
    public String generateTrend(String prompt) {
        return geminiService.generateContent(prompt, Map.of(), buildSystemPrompt());
    }

    /** 진행 콜백 없이 쓰던 기존 호출부를 위한 것. */
    public String buildPrompt(String userId, Topics topics, Map<String, Object> clientSnapshot) {
        return buildPrompt(userId, topics, clientSnapshot, null);
    }

    private static void stage(Consumer<String> onStage, String label) {
        if (onStage != null) {
            onStage.accept(label);
        }
    }

    /** 프롬프트 조립만. Gemini 없이 검증할 수 있도록 분리해 둔다. */
    public String buildPrompt(String userId, Topics topics, Map<String, Object> clientSnapshot,
                              Consumer<String> onStage) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시");
        String currentDateTime = now.format(formatter);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("다음은 ").append(currentDateTime).append(" 시점의 대시보드 데이터예요. 위 지침에 따라 종합적인 리포트를 작성해주세요.\n");
        promptBuilder.append("리포트 제목이나 시작 부분에 '").append(currentDateTime).append(" 리포트'라고 명시해주세요.\n\n");

        boolean needNews = topics.yahooFinance() || topics.yonhapNews();

        DashboardService.ReportBundle bundle = dashboardService.getReportBundle(
                userId, topics.stocks(), topics.weather(), needNews, topics.system());
        bundle = mergeClientSnapshot(bundle, clientSnapshot);

        log.info("AI report context for user {} — serverCache={}, clientSnapshot={}, stocks={}, yahooNews={}",
                userId,
                bundle.fromCache(),
                clientSnapshot != null,
                bundle.stocks() != null && bundle.stocks().quotes() != null ? bundle.stocks().quotes().size() : 0,
                bundle.news() != null && bundle.news().yahooNews() != null ? bundle.news().yahooNews().size() : 0);

        if (topics.news()) {
            stage(onStage, "뉴스 수집");
            appendDbNews(promptBuilder);
        }
        if (topics.stocks() && bundle.stocks() != null) {
            stage(onStage, "주가 수집");
            appendStocks(promptBuilder, bundle.stocks());
        }
        if (topics.yahooFinance() && bundle.news() != null) {
            stage(onStage, "야후 파이낸스 수집");
            appendYahooNews(promptBuilder, bundle.news(), clientSnapshot);
        }
        if (topics.yonhapNews() && bundle.news() != null) {
            stage(onStage, "연합뉴스 수집");
            appendYonhapNews(promptBuilder, bundle.news());
        }
        if (topics.weather() && bundle.weather() != null) {
            stage(onStage, "날씨 수집");
            appendWeather(promptBuilder, bundle.weather());
        }
        if (topics.lifeInfo()) {
            stage(onStage, "생활 정보 수집");
            appendLifeInfo(promptBuilder);
        }
        if (topics.traffic()) {
            stage(onStage, "교통 돌발상황 수집");
            appendTraffic(promptBuilder);
        }
        if (topics.emergency()) {
            stage(onStage, "긴급재난문자 수집");
            appendEmergency(promptBuilder);
        }
        if (topics.system() && bundle.system() != null) {
            stage(onStage, "시스템 정보 수집");
            appendSystem(promptBuilder, bundle.system());
        }

        return promptBuilder.toString();
    }

    public String buildSystemPrompt() {
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

    DashboardService.ReportBundle mergeClientSnapshot(
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
        newsData.yonhapNews().stream().limit(MAX_NEWS_ITEMS).forEach(item ->
                promptBuilder.append("- ").append(item.title() != null ? item.title() : "").append("\n"));
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
