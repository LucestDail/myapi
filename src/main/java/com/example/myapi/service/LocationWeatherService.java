package com.example.myapi.service;

import com.example.myapi.dto.location.LocationWeatherResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 현재 위치 날씨 서비스 (wttr.in 기반)
 * 60초마다 자동 갱신, 캐시된 데이터 반환
 */
@Service
public class LocationWeatherService {

    private static final Logger log = LoggerFactory.getLogger(LocationWeatherService.class);
    private static final String WTTR_URL = "https://wttr.in?format=4";

    private final RestTemplate restTemplate;
    private final AtomicReference<LocationWeatherResponse> cachedWeather = new AtomicReference<>();

    public LocationWeatherService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing location weather data...");
        refreshLocationWeather();
    }

    /**
     * 60초마다 자동 갱신
     */
    @Scheduled(fixedRate = 60000)
    public void scheduledRefresh() {
        log.debug("Refreshing location weather data...");
        refreshLocationWeather();
    }

    /**
     * wttr.in에서 날씨 정보 가져오기
     */
    private void refreshLocationWeather() {
        try {
            String response = restTemplate.getForObject(WTTR_URL, String.class);
            if (response != null && !response.isBlank()) {
                // "Tokyang-gu, South Korea: 🌫  🌡️+4°C 🌬️↘7km/h" 형식
                String location = "Unknown";
                if (response.contains(":")) {
                    location = response.substring(0, response.indexOf(":")).trim();
                }
                
                LocationWeatherResponse weather = new LocationWeatherResponse(
                        location,
                        response,
                        response,
                        Instant.now()
                );
                cachedWeather.set(weather);
                log.info("Location weather updated: {}", location);
            }
        } catch (Exception e) {
            log.error("Failed to fetch location weather: {}", e.getMessage());
        }
    }

    /**
     * 캐시된 날씨 정보 반환
     */
    public LocationWeatherResponse getLocationWeather() {
        LocationWeatherResponse cached = cachedWeather.get();
        if (cached == null) {
            return new LocationWeatherResponse(
                    "Unknown",
                    "날씨 정보를 가져오는 중...",
                    "",
                    Instant.now()
            );
        }
        return cached;
    }
}
