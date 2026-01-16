package com.example.myapi.controller;

import com.example.myapi.dto.location.LocationWeatherResponse;
import com.example.myapi.service.LocationWeatherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/location")
public class LocationController {

    private final LocationWeatherService locationWeatherService;

    public LocationController(LocationWeatherService locationWeatherService) {
        this.locationWeatherService = locationWeatherService;
    }

    /**
     * 현재 위치 날씨 정보 (캐시)
     * GET /api/location/weather
     */
    @GetMapping("/weather")
    public LocationWeatherResponse getLocationWeather() {
        return locationWeatherService.getLocationWeather();
    }
}
