package com.example.myapi.controller;

import com.example.myapi.dto.dashboard.DashboardConfig;
import com.example.myapi.dto.dashboard.DashboardData;
import com.example.myapi.dto.info.AirQualityResponse;
import com.example.myapi.dto.info.ExchangeRateResponse;
import com.example.myapi.dto.info.HolidayResponse;
import com.example.myapi.dto.info.SunTimesResponse;
import com.example.myapi.dto.rss.RssFeedResponse;
import com.example.myapi.dto.rss.RssItem;
import com.example.myapi.entity.News;
import com.example.myapi.service.*;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/ai-report")
@CrossOrigin(origins = "*")
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
    private RssService rssService;

    @Autowired
    private LifeInfoService lifeInfoService;

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
    public String generateReport(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Boolean> topics = (Map<String, Boolean>) request.getOrDefault("topics", new HashMap<>());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> settings = (Map<String, Object>) request.getOrDefault("settings", new HashMap<>());
            
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

            // 시스템 프롬프트 생성 (페르소나 및 작성 스타일)
            StringBuilder systemPromptBuilder = new StringBuilder();
            systemPromptBuilder.append("당신은 경제·금융·시사 정보를 종합적으로 분석하고 전달하는 전문 리포트 작가예요.\n\n");
            systemPromptBuilder.append("## 페르소나\n");
            systemPromptBuilder.append("- 독자들이 하루를 시작하기 전에 세상의 흐름을 한눈에 파악할 수 있도록 돕는 친근한 안내자\n");
            systemPromptBuilder.append("- 복잡한 정보를 쉽고 읽기 편하게 전달하는 전문가\n");
            systemPromptBuilder.append("- 데이터의 의미와 맥락을 종합적으로 해석하여 인사이트를 제공하는 분석가\n\n");
            systemPromptBuilder.append("## 작성 원칙 (반드시 지켜야 할 사항)\n");
            systemPromptBuilder.append("1. **절대 줄 단위로 나열하지 말 것**: 개별 뉴스나 데이터를 '- 제목: ...' 형태로 나열하지 말고, 관련된 내용들을 종합하여 하나의 문단(2-4문장)으로 구성해요.\n");
            systemPromptBuilder.append("2. **섹션별 종합 분석 필수**: 같은 주제의 여러 뉴스나 데이터가 있으면, 그것들의 공통점, 차이점, 흐름, 의미를 파악하여 종합적으로 설명해요. 예: '오늘은 다양한 가격, 수치, 전망의 오르내림이 풍성하게 담겨 있어요. 만약 '저번에 오르더니 이번엔 좀 내리네?' 같은 흐름이 머릿속에 그려진다면, 아주 잘하고 계신 거예요.'\n");
            systemPromptBuilder.append("3. **읽기 편한 구조**: 이모지(📊, ⏰, 📆, 🥔, 🌳, ✨, 🍯, 👂, 💼, ⚙️, 🗞️, 🚩 등)로 섹션을 구분하고, 각 섹션은 2-4문단으로 구성해요.\n");
            systemPromptBuilder.append("4. **맥락과 인사이트 제공**: 단순히 '무엇'이 아니라 '왜', '어떤 의미인지', '앞으로 어떻게 될지'를 함께 설명해요.\n");
            systemPromptBuilder.append("5. **문체**: 해요체 사용, 능동형 문장, 긍정적 표현, 캐주얼한 경어, 한자어 풀어쓰기\n\n");
            systemPromptBuilder.append("## 리포트 구조 예시\n");
            systemPromptBuilder.append("```\n");
            systemPromptBuilder.append("📊 증시 UP&DOWN\n");
            systemPromptBuilder.append("13일(현지 시각) 뉴욕 증시는 하락세로 마감했어요. JP모건체이스의 기대 이하 실적과 파월 연준 의장 수사를 둘러싼 논란이 악재로 작용했어요. 서버용 CPU에 대한 수요가 크다는 소식에 인텔과 AMD의 주가는 급등했어요. 14일 어제 코스피는 사상 처음으로 4,700선을 돌파해 4,723.10로 마감했어요. 데이터센터향 전력 기기 수요 증가에 대한 기대감으로 LS일렉트릭 주가가 급등했어요.\n\n");
            systemPromptBuilder.append("✨ 금융시장 동향\n");
            systemPromptBuilder.append("원-달러 환율이 1,470원까지 다시 오르자 정부가 대책 마련에 나섰어요. 수출기업의 외환거래를 점검하고, 은행의 달러예금 금리 인하를 유도해 국내로의 달러 유입을 늘린다는 방침이에요.\n\n");
            systemPromptBuilder.append("```\n\n");
            systemPromptBuilder.append("위 예시처럼, 데이터를 나열하지 말고 종합적으로 분석하여 문단 단위로 작성해주세요.\n");

            // 현재 날짜/시간 정보
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시");
            String currentDateTime = now.format(formatter);
            
            // 데이터 프롬프트 생성
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("다음은 ").append(currentDateTime).append(" 시점의 대시보드 데이터예요. 위 지침에 따라 종합적인 리포트를 작성해주세요.\n");
            promptBuilder.append("리포트 제목이나 시작 부분에 '").append(currentDateTime).append(" 리포트'라고 명시해주세요.\n\n");

            // 캐시된 대시보드 데이터를 한 번에 가져오기 (외부 API 호출 최소화)
            DashboardData cachedDashboardData = null;
            if (includeStocks || includeYahooFinance || includeYonhapNews || includeWeather || includeSystem) {
                try {
                    cachedDashboardData = dashboardService.getFullData();
                } catch (Exception e) {
                    log.warn("Failed to get cached dashboard data: {}", e.getMessage());
                }
            }

            // 1. 뉴스 데이터 (DB) - DB에서 직접 조회 (캐시된 데이터)
            if (includeNews) {
                List<News> recentNews = newsService.getAllNews();
                if (recentNews != null && !recentNews.isEmpty()) {
                    int newsCount = Math.min(recentNews.size(), 50);
                    promptBuilder.append("## 뉴스 데이터 (DB)\n\n");
                    for (int i = 0; i < newsCount; i++) {
                        News news = recentNews.get(i);
                        promptBuilder.append("- 제목: ").append(news.getNewsTitle() != null ? news.getNewsTitle() : "").append("\n");
                        String content = news.getNewsContents() != null ? news.getNewsContents() : "";
                        if (content.length() > 500) content = content.substring(0, 500) + "...";
                        promptBuilder.append("  내용: ").append(content).append("\n\n");
                    }
                    promptBuilder.append("\n");
                }
            }

            // 2. 주식 정보 (캐시된 데이터 사용)
            if (includeStocks && cachedDashboardData != null && cachedDashboardData.stocks() != null) {
                DashboardData.StocksData stocksData = cachedDashboardData.stocks();
                if (stocksData.quotes() != null && !stocksData.quotes().isEmpty()) {
                    promptBuilder.append("## 주식 정보\n\n");
                    for (DashboardData.StockQuote quote : stocksData.quotes()) {
                        promptBuilder.append("- ").append(quote.symbol()).append(" (").append(quote.name() != null ? quote.name() : "").append("): ");
                        if (quote.currentPrice() != null) {
                            promptBuilder.append("현재가 ").append(quote.currentPrice());
                            if (quote.percentChange() != null) {
                                promptBuilder.append(", ").append(quote.percentChange() >= 0 ? "+" : "").append(String.format("%.2f", quote.percentChange())).append("%");
                            }
                        }
                        promptBuilder.append("\n");
                    }
                    promptBuilder.append("\n");
                }
            }

            // 3. 야후 파이낸스 뉴스 (모든 티커의 뉴스 수집)
            if (includeYahooFinance) {
                try {
                    DashboardConfig config = dashboardService.getConfig();
                    List<RssItem> allYahooNews = new ArrayList<>();
                    
                    // 모든 티커의 뉴스 수집
                    for (DashboardConfig.TickerConfig ticker : config.tickers()) {
                        try {
                            RssFeedResponse yahooFeed = rssService.getYahooStock(ticker.symbol());
                            if (yahooFeed != null && yahooFeed.items() != null) {
                                allYahooNews.addAll(yahooFeed.items());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to get Yahoo news for {}: {}", ticker.symbol(), e.getMessage());
                        }
                    }
                    
                    // 중복 제거 및 제한 (최신순)
                    if (!allYahooNews.isEmpty()) {
                        promptBuilder.append("## 야후 파이낸스 뉴스 (모든 티커)\n\n");
                        allYahooNews.stream()
                                .distinct()
                                .limit(30)
                                .forEach(item -> 
                                    promptBuilder.append("- [").append(item.source() != null ? item.source() : "").append("] ")
                                        .append(item.title() != null ? item.title() : "").append("\n"));
                        promptBuilder.append("\n");
                    }
                } catch (Exception e) {
                    log.warn("Failed to get Yahoo Finance news for all tickers: {}", e.getMessage());
                }
            }

            // 4. 연합뉴스 (캐시된 데이터 사용)
            if (includeYonhapNews && cachedDashboardData != null && cachedDashboardData.news() != null) {
                DashboardData.NewsData newsData = cachedDashboardData.news();
                if (newsData.yonhapNews() != null && !newsData.yonhapNews().isEmpty()) {
                    promptBuilder.append("## 연합뉴스\n\n");
                    for (DashboardData.NewsItem item : newsData.yonhapNews().stream().limit(20).collect(Collectors.toList())) {
                        promptBuilder.append("- ").append(item.title() != null ? item.title() : "").append("\n");
                    }
                    promptBuilder.append("\n");
                }
            }

            // 5. 날씨 데이터 (캐시된 데이터 사용)
            if (includeWeather && cachedDashboardData != null && cachedDashboardData.weather() != null) {
                List<DashboardData.WeatherData> weatherList = cachedDashboardData.weather();
                if (!weatherList.isEmpty()) {
                    promptBuilder.append("## 전국 주요 도시 날씨 정보\n\n");
                    for (DashboardData.WeatherData weather : weatherList) {
                        promptBuilder.append("- ").append(weather.cityKo() != null ? weather.cityKo() : weather.city()).append(": ");
                        promptBuilder.append(weather.temperatureCelsius()).append("°C, ").append(weather.humidity()).append("% 습도, ").append(weather.weather() != null ? weather.weather() : "").append("\n");
                    }
                    promptBuilder.append("\n");
                }
            }

            // 6. 생활 정보 (환율, 미세먼지, 일출/일몰, 공휴일)
            if (includeLifeInfo) {
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

            // 7. 교통돌발상황
            if (includeTraffic) {
                JsonObject trafficData = trafficService.getTrafficInfo();
                if (trafficData != null && trafficData.has("body") && trafficData.getAsJsonObject("body").has("items")) {
                    promptBuilder.append("## 교통돌발상황 정보\n\n");
                    promptBuilder.append(trafficData.toString()).append("\n\n");
                }
            }

            // 8. 긴급재난문자
            if (includeEmergency) {
                JsonObject emergencyData = emergencyService.getEmergencyInfo();
                if (emergencyData != null) {
                    promptBuilder.append("## 긴급재난문자 정보\n\n");
                    promptBuilder.append(emergencyData.toString()).append("\n\n");
                }
            }

            // 9. 시스템 정보 (캐시된 데이터 사용)
            if (includeSystem && cachedDashboardData != null && cachedDashboardData.system() != null) {
                DashboardData.SystemData systemData = cachedDashboardData.system();
                promptBuilder.append("## 시스템 정보\n\n");
                promptBuilder.append("CPU 사용률: ").append(String.format("%.2f", systemData.cpuUsage())).append("%\n");
                promptBuilder.append("메모리 사용률: ").append(String.format("%.2f", systemData.memoryUsagePercent())).append("%\n");
                promptBuilder.append("JVM Heap 사용률: ").append(String.format("%.2f", systemData.heapUsagePercent())).append("%\n");
                promptBuilder.append("스레드 수: ").append(systemData.threadCount()).append("\n");
                promptBuilder.append("가동시간: ").append(systemData.uptimeMillis() / 1000 / 60).append("분\n\n");
            }

            String systemInstruction = systemPromptBuilder.toString();
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
}
