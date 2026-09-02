package com.example.myapi.service;

import com.example.myapi.config.AirKoreaProperties;
import com.example.myapi.dto.info.AirQualityResponse;
import com.example.myapi.dto.info.ExchangeRateResponse;
import com.example.myapi.dto.info.HolidayResponse;
import com.example.myapi.dto.info.SunTimesResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 생활정보 서비스
 * 환율, 미세먼지, 일출/일몰, 공휴일 정보 제공
 */
@Service
public class LifeInfoService {

    private static final Logger log = LoggerFactory.getLogger(LifeInfoService.class);

    private static final String DEFAULT_SIDO = "서울";

    /** 지역명 별칭 -> 에어코리아 sidoName. 정식 명칭을 먼저 검사하도록 순서를 유지한다. */
    private static final Map<String, String> SIDO_BY_ALIAS = new LinkedHashMap<>();
    static {
        SIDO_BY_ALIAS.put("충청북", "충북");
        SIDO_BY_ALIAS.put("충청남", "충남");
        SIDO_BY_ALIAS.put("전라북", "전북");
        SIDO_BY_ALIAS.put("전라남", "전남");
        SIDO_BY_ALIAS.put("경상북", "경북");
        SIDO_BY_ALIAS.put("경상남", "경남");
        for (String sido : List.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
                "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주")) {
            SIDO_BY_ALIAS.put(sido, sido);
        }
        SIDO_BY_ALIAS.put("seoul", "서울");
        SIDO_BY_ALIAS.put("busan", "부산");
        SIDO_BY_ALIAS.put("daegu", "대구");
        SIDO_BY_ALIAS.put("incheon", "인천");
        SIDO_BY_ALIAS.put("gwangju", "광주");
        SIDO_BY_ALIAS.put("daejeon", "대전");
        SIDO_BY_ALIAS.put("ulsan", "울산");
        SIDO_BY_ALIAS.put("sejong", "세종");
        SIDO_BY_ALIAS.put("gyeonggi", "경기");
        SIDO_BY_ALIAS.put("gangwon", "강원");
        SIDO_BY_ALIAS.put("chungbuk", "충북");
        SIDO_BY_ALIAS.put("chungnam", "충남");
        SIDO_BY_ALIAS.put("jeonbuk", "전북");
        SIDO_BY_ALIAS.put("jeonnam", "전남");
        SIDO_BY_ALIAS.put("gyeongbuk", "경북");
        SIDO_BY_ALIAS.put("gyeongnam", "경남");
        SIDO_BY_ALIAS.put("jeju", "제주");
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AirKoreaProperties airKoreaProperties;

    // 캐시
    private final Map<String, ExchangeRateResponse> exchangeRateCache = new ConcurrentHashMap<>();
    private final Map<String, AirQualityResponse> airQualityCache = new ConcurrentHashMap<>();
    private final Map<String, SunTimesResponse> sunTimesCache = new ConcurrentHashMap<>();

    public LifeInfoService(RestTemplate restTemplate, ObjectMapper objectMapper, 
                          AirKoreaProperties airKoreaProperties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.airKoreaProperties = airKoreaProperties;
    }

    // ==================== 환율 ====================

    /**
     * 환율 정보 조회
     */
    public ExchangeRateResponse getExchangeRates(String baseCurrency) {
        String cacheKey = baseCurrency.toUpperCase();
        ExchangeRateResponse cached = exchangeRateCache.get(cacheKey);
        
        if (cached != null) {
            return cached;
        }

        try {
            // ExchangeRate-API (무료) 사용
            String url = String.format("https://api.exchangerate-api.com/v4/latest/%s", baseCurrency);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode ratesNode = root.get("rates");
                
                Map<String, Double> rates = new LinkedHashMap<>();
                // 주요 통화만 선택
                String[] currencies = {"USD", "KRW", "JPY", "EUR", "CNY", "GBP"};
                for (String currency : currencies) {
                    if (ratesNode.has(currency)) {
                        rates.put(currency, ratesNode.get(currency).asDouble());
                    }
                }
                
                ExchangeRateResponse result = ExchangeRateResponse.of(baseCurrency, rates);
                exchangeRateCache.put(cacheKey, result);
                return result;
            }
        } catch (Exception e) {
            log.error("Failed to fetch exchange rates: {}", e.getMessage());
        }

        // 기본값 반환
        return ExchangeRateResponse.of(baseCurrency, Map.of(
                "USD", 1.0,
                "KRW", 1350.0,
                "JPY", 150.0,
                "EUR", 0.92,
                "CNY", 7.2,
                "GBP", 0.79
        ));
    }

    // ==================== 미세먼지 ====================

    /**
     * 미세먼지 경보 정보 조회
     * 공공데이터포털 미세먼지 경보 현황 API 사용
     */
    public AirQualityResponse getAirQuality(String location) {
        AirQualityResponse cached = airQualityCache.get(location);
        if (cached != null) {
            return cached;
        }

        String apiKey = airKoreaProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Air Korea API key is not configured. Air quality unavailable for {}", location);
            return AirQualityResponse.unavailable(location);
        }

        try {
            // 공공데이터포털 API는 인코딩된 키를 직접 사용해야 함
            // YAML에는 디코딩된 키를 저장하고, 여기서 URL 인코딩 수행
            String serviceKey;
            if (apiKey.contains("%")) {
                // 이미 인코딩된 키인 경우 그대로 사용
                serviceKey = apiKey;
            } else {
                // 디코딩된 키를 URL 인코딩 (+ -> %2B, = -> %3D 등)
                serviceKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            }

            String sidoName = toSidoName(location);
            String url = String.format(
                "%s/getCtprvnRltmMesureDnsty?serviceKey=%s&returnType=json&numOfRows=100&pageNo=1&sidoName=%s&ver=1.0",
                airKoreaProperties.getRealtimeBaseUrl(), serviceKey,
                URLEncoder.encode(sidoName, StandardCharsets.UTF_8)
            );

            log.debug("Calling Air Korea realtime API for location: {} (sido={})", location, sidoName);
            // ★ URI.create 필수. URL 문자열을 그대로 넘기면 RestTemplate이 재인코딩해
            //   serviceKey의 %2B가 %252B로 깨지고, 포털이 403 "등록되지 않은 서비스키"를 돌려준다.
            ResponseEntity<String> response = restTemplate.getForEntity(URI.create(url), String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode items = root.path("response").path("body").path("items");

                if (items.isArray() && !items.isEmpty()) {
                    Integer pm10 = null;
                    Integer pm25 = null;

                    // 시/군/구가 지정됐고 같은 이름의 측정소가 있으면 그 측정소 실측값 우선
                    String station = extractStationName(location);
                    if (station != null) {
                        for (JsonNode item : items) {
                            if (station.equals(item.path("stationName").asText(""))) {
                                pm10 = parseMeasuredValue(item.path("pm10Value").asText(""));
                                pm25 = parseMeasuredValue(item.path("pm25Value").asText(""));
                                break;
                            }
                        }
                    }
                    // 없으면 해당 시·도 전 측정소 평균
                    if (pm10 == null && pm25 == null) {
                        pm10 = averageMeasuredValue(items, "pm10Value");
                        pm25 = averageMeasuredValue(items, "pm25Value");
                    }

                    if (pm10 != null || pm25 != null) {
                        AirQualityResponse result = AirQualityResponse.of(location, pm10, pm25, Instant.now());
                        airQualityCache.put(location, result);
                        return result;
                    }
                }

                log.warn("Air Korea returned no usable measurement for {}: {}", location,
                        root.path("response").path("header").path("resultMsg").asText(
                                root.path("OpenAPI_ServiceResponse").path("cmmMsgHeader").path("returnAuthMsg").asText("")));
            }
        } catch (Exception e) {
            log.error("Failed to fetch air quality for {}: {} - {}", location,
                     e.getClass().getSimpleName(), e.getMessage());
        }

        // 추정값을 지어내지 않는다 — 못 가져왔으면 못 가져왔다고 응답한다.
        return AirQualityResponse.unavailable(location);
    }

    /**
     * 지역명 -> 에어코리아 sidoName ("Seoul", "서울특별시", "Seongnam-si, Gyeonggi-do" 등 수용)
     */
    private String toSidoName(String location) {
        if (location == null || location.isBlank()) {
            return DEFAULT_SIDO;
        }
        String key = location.toLowerCase().replaceAll("[\\s,-]", "");
        for (Map.Entry<String, String> entry : SIDO_BY_ALIAS.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return DEFAULT_SIDO;
    }

    /**
     * 지역명에서 측정소명(시/군/구) 추출. 없으면 null.
     */
    private String extractStationName(String location) {
        if (location == null) {
            return null;
        }
        for (String token : location.trim().split("[\\s,]+")) {
            if (token.length() >= 2 && (token.endsWith("구") || token.endsWith("군") || token.endsWith("시"))) {
                return token;
            }
        }
        return null;
    }

    /**
     * 측정값 파싱. 결측("-", 빈값)은 null.
     */
    private Integer parseMeasuredValue(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        try {
            return Math.round(Float.parseFloat(value.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 시·도 내 전 측정소 평균(결측 제외). 유효값이 하나도 없으면 null.
     */
    private Integer averageMeasuredValue(JsonNode items, String field) {
        int sum = 0;
        int count = 0;
        for (JsonNode item : items) {
            Integer value = parseMeasuredValue(item.path(field).asText(""));
            if (value != null) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? null : Math.round((float) sum / count);
    }

    // ==================== 일출/일몰 ====================

    /**
     * 일출/일몰 시간 조회
     */
    public SunTimesResponse getSunTimes(double lat, double lon, String locationName) {
        String cacheKey = String.format("%.2f,%.2f", lat, lon);
        SunTimesResponse cached = sunTimesCache.get(cacheKey);
        
        if (cached != null && cached.date().equals(LocalDate.now().toString())) {
            return cached;
        }

        try {
            String url = String.format(
                    "https://api.sunrise-sunset.org/json?lat=%f&lng=%f&formatted=0",
                    lat, lon);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode results = root.get("results");
                
                if (results != null) {
                    LocalTime sunrise = parseUtcTimeToLocalTime(results.get("sunrise").asText());
                    LocalTime sunset = parseUtcTimeToLocalTime(results.get("sunset").asText());
                    LocalTime solarNoon = parseUtcTimeToLocalTime(results.get("solar_noon").asText());
                    
                    SunTimesResponse result = SunTimesResponse.of(
                            locationName, lat, lon, LocalDate.now().toString(),
                            sunrise, sunset, solarNoon);
                    sunTimesCache.put(cacheKey, result);
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch sun times: {}", e.getMessage());
        }

        // 기본값 (서울 기준 대략적인 값)
        return SunTimesResponse.of(
                locationName, lat, lon, LocalDate.now().toString(),
                LocalTime.of(6, 30), LocalTime.of(18, 30), LocalTime.of(12, 30));
    }

    private LocalTime parseUtcTimeToLocalTime(String utcTime) {
        try {
            // ISO 8601 형식: "2024-01-15T21:30:00+00:00"
            Instant instant = Instant.parse(utcTime);
            // UTC+9 (한국 시간)로 변환
            return instant.atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalTime();
        } catch (Exception e) {
            return LocalTime.NOON;
        }
    }

    // ==================== 공휴일 ====================

    /**
     * 공휴일 정보 조회
     */
    public HolidayResponse getHolidays(int year, Integer month) {
        List<HolidayResponse.Holiday> allHolidays = HolidayResponse.getKoreanHolidays(year);
        
        List<HolidayResponse.Holiday> filtered = month != null
                ? allHolidays.stream()
                        .filter(h -> h.date().getMonthValue() == month)
                        .toList()
                : allHolidays;
        
        return HolidayResponse.of(year, month != null ? month : 0, filtered);
    }

    /**
     * 오늘이 공휴일인지 확인
     */
    public boolean isTodayHoliday() {
        LocalDate today = LocalDate.now();
        List<HolidayResponse.Holiday> holidays = HolidayResponse.getKoreanHolidays(today.getYear());
        return holidays.stream()
                .anyMatch(h -> h.date().equals(today) && h.isHoliday());
    }

    /**
     * 다음 공휴일 조회
     */
    public HolidayResponse.Holiday getNextHoliday() {
        LocalDate today = LocalDate.now();
        List<HolidayResponse.Holiday> holidays = HolidayResponse.getKoreanHolidays(today.getYear());
        
        return holidays.stream()
                .filter(h -> h.date().isAfter(today) || h.date().isEqual(today))
                .findFirst()
                .orElse(null);
    }

    // ==================== 캐시 갱신 ====================

    @Scheduled(fixedRate = 3600000) // 1시간마다
    public void refreshCache() {
        exchangeRateCache.clear();
        airQualityCache.clear();
        // sunTimesCache는 날짜 기반으로 자동 갱신
        log.debug("Life info cache cleared");
    }
}
