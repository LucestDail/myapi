package com.example.myapi.controller;

import com.example.myapi.dto.weather.CityWeatherResponse;
import com.example.myapi.service.WeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /**
     * Get weather for all cached cities
     * GET /api/weather
     */
    @GetMapping
    public List<CityWeatherResponse> getAllWeather() {
        return weatherService.getAllWeather();
    }

    /**
     * Get weather for a specific city
     * GET /api/weather/{city}
     * Example: /api/weather/seoul, /api/weather/tokyo
     */
    @GetMapping("/{city}")
    public ResponseEntity<CityWeatherResponse> getWeatherByCity(@PathVariable String city) {
        return weatherService.getWeatherByCity(city)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get list of available cities
     * GET /api/weather/cities
     */
    @GetMapping("/cities/list")
    public List<String> getAvailableCities() {
        return weatherService.getAvailableCities();
    }

    /**
     * Get cache status
     * GET /api/weather/status
     */
    @GetMapping("/cache/status")
    public Map<String, Object> getCacheStatus() {
        return weatherService.getCacheStatus();
    }
}
