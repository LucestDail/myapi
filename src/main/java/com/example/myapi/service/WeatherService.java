package com.example.myapi.service;

import com.example.myapi.config.OpenWeatherProperties;
import com.example.myapi.dto.weather.City;
import com.example.myapi.dto.weather.CityWeatherResponse;
import com.example.myapi.dto.weather.OpenWeatherResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weather service that caches weather data for major cities.
 * Auto-refreshes every 1 minute via scheduler.
 * User requests only return cached data.
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final RestTemplate restTemplate;
    private final OpenWeatherProperties properties;
    private final Map<String, CityWeatherResponse> weatherCache = new ConcurrentHashMap<>();
    private Instant lastUpdated = null;

    public WeatherService(RestTemplate restTemplate, OpenWeatherProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * Initialize weather data on startup
     */
    @PostConstruct
    public void init() {
        log.info("Initializing weather data for {} major cities...", City.getMajorCities().length);
        refreshAllWeatherData();
    }

    /**
     * Refresh weather data every 1 minute
     */
    @Scheduled(fixedRate = 60000) // 60 seconds
    public void scheduledRefresh() {
        log.info("Scheduled refresh: Updating weather data for all cities...");
        refreshAllWeatherData();
    }

    /**
     * Fetch weather data for all major cities
     */
    private void refreshAllWeatherData() {
        City[] cities = City.getMajorCities();
        int successCount = 0;

        for (City city : cities) {
            try {
                CityWeatherResponse weather = fetchWeatherForCity(city);
                if (weather != null) {
                    weatherCache.put(city.name().toLowerCase(), weather);
                    successCount++;
                }
                // Small delay to avoid rate limiting (60 calls/min = 1 call/sec max)
                Thread.sleep(100);
            } catch (Exception e) {
                log.error("Failed to fetch weather for {}: {}", city.name(), e.getMessage());
            }
        }

        lastUpdated = Instant.now();
        log.info("Weather data refreshed: {}/{} cities updated", successCount, cities.length);
    }

    /**
     * Fetch weather data for a single city from OpenWeatherMap API
     */
    private CityWeatherResponse fetchWeatherForCity(City city) {
        String url = UriComponentsBuilder
                .fromHttpUrl(properties.getBaseUrl() + "/data/2.5/weather")
                .queryParam("lat", city.lat())
                .queryParam("lon", city.lon())
                .queryParam("appid", properties.getApiKey())
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<OpenWeatherResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, OpenWeatherResponse.class
        );

        if (response.getBody() != null) {
            return CityWeatherResponse.from(city, response.getBody(), Instant.now());
        }
        return null;
    }

    /**
     * Get cached weather for a specific city
     */
    public Optional<CityWeatherResponse> getWeatherByCity(String cityName) {
        return Optional.ofNullable(weatherCache.get(cityName.toLowerCase()));
    }

    /**
     * Get cached weather for all cities
     */
    public List<CityWeatherResponse> getAllWeather() {
        return new ArrayList<>(weatherCache.values());
    }

    /**
     * Get list of available cities
     */
    public List<String> getAvailableCities() {
        return Arrays.stream(City.getMajorCities())
                .map(City::name)
                .toList();
    }

    /**
     * Get cache status
     */
    public Map<String, Object> getCacheStatus() {
        return Map.of(
                "cachedCities", weatherCache.size(),
                "totalCities", City.getMajorCities().length,
                "lastUpdated", lastUpdated != null ? lastUpdated.toString() : "never",
                "availableCities", getAvailableCities()
        );
    }
}
