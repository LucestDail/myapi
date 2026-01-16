package com.example.myapi.dto.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * OpenWeatherMap API Response
 * GET /data/2.5/weather?lat={lat}&lon={lon}&appid={API key}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenWeatherResponse(
        Coord coord,
        List<Weather> weather,
        Main main,
        Wind wind,
        Clouds clouds,
        Sys sys,
        String name,
        int visibility,
        long dt,
        int timezone
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Coord(double lon, double lat) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Weather(int id, String main, String description, String icon) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Main(
            double temp,
            double feels_like,
            double temp_min,
            double temp_max,
            int pressure,
            int humidity,
            Integer sea_level,
            Integer grnd_level
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Wind(double speed, int deg, Double gust) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Clouds(int all) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sys(String country, long sunrise, long sunset) {}
}
