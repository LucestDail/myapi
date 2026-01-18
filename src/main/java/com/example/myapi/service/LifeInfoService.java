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

        try {
            // API 호출
            String encodedKey = URLEncoder.encode(airKoreaProperties.getApiKey(), StandardCharsets.UTF_8);
            int year = LocalDate.now().getYear();
            
            String url = String.format(
                "%s/getUlfptcaAlarmInfo?serviceKey=%s&returnType=json&numOfRows=100&pageNo=1&year=%d",
                airKoreaProperties.getBaseUrl(), encodedKey, year
            );
            
            log.debug("Calling Air Korea API: {}", url);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode items = root.path("response").path("body").path("items");
                
                Integer pm10 = null;
                Integer pm25 = null;
                
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String districtName = item.path("districtName").asText("");
                        String itemCode = item.path("itemCode").asText("");
                        
                        // 지역명 매칭 (서울, 경기 등)
                        if (districtName.contains(normalizeLocation(location)) || 
                            normalizeLocation(location).contains(districtName)) {
                            
                            // 경보 발령 중인지 확인 (clearVal이 없으면 발령 중)
                            String clearVal = item.path("clearVal").asText("");
                            if (clearVal.isEmpty() || clearVal.equals("-")) {
                                // 발령 중 - 농도값 추출
                                String issueVal = item.path("issueVal").asText("0");
                                int concentration = parseConcentration(issueVal);
                                
                                if ("PM10".equals(itemCode)) {
                                    pm10 = concentration;
                                } else if ("PM25".equals(itemCode)) {
                                    pm25 = concentration;
                                }
                            }
                        }
                    }
                }
                
                // 경보 데이터가 없으면 (정상 상태) 양호한 기본값 사용
                if (pm10 == null && pm25 == null) {
                    pm10 = 35;  // 보통 수준
                    pm25 = 20;
                    log.debug("No active air quality alerts for {}, using default values", location);
                }
                
                AirQualityResponse result = AirQualityResponse.of(location, 
                    pm10 != null ? pm10 : 35, 
                    pm25 != null ? pm25 : 20, 
                    Instant.now());
                airQualityCache.put(location, result);
                return result;
            }
        } catch (Exception e) {
            log.error("Failed to fetch air quality from API: {}", e.getMessage());
        }

        // API 실패 시 기본값 반환
        return AirQualityResponse.of(location, 40, 25, Instant.now());
    }

    /**
     * 지역명 정규화
     */
    private String normalizeLocation(String location) {
        // "서울특별시" -> "서울", "경기도" -> "경기" 등
        return location.replace("특별시", "")
                      .replace("광역시", "")
                      .replace("도", "")
                      .replace("시", "")
                      .trim();
    }

    /**
     * 농도 문자열 파싱
     */
    private int parseConcentration(String value) {
        try {
            // "123㎍/㎥" 또는 "123" 형식 처리
            String numStr = value.replaceAll("[^0-9]", "");
            return numStr.isEmpty() ? 0 : Integer.parseInt(numStr);
        } catch (NumberFormatException e) {
            return 0;
        }
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
