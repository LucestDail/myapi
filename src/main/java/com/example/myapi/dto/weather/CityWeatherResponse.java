package com.example.myapi.dto.weather;

import java.time.Instant;

/**
 * Simplified weather response for API consumers
 */
public record CityWeatherResponse(
        String city,
        String cityKo,
        String country,
        double lat,
        double lon,
        String weather,
        String description,
        String icon,
        double temperature,        // Kelvin
        double temperatureCelsius, // Celsius
        double feelsLike,          // Kelvin
        double feelsLikeCelsius,   // Celsius
        double tempMin,
        double tempMax,
        int humidity,              // %
        int pressure,              // hPa
        double windSpeed,          // m/s
        int windDeg,
        int cloudiness,            // %
        int visibility,            // meters
        Instant sunrise,
        Instant sunset,
        Instant fetchedAt
) {
    public static CityWeatherResponse from(City city, OpenWeatherResponse response, Instant fetchedAt) {
        var weather = response.weather().isEmpty() ? null : response.weather().get(0);
        var main = response.main();
        var wind = response.wind();
        var sys = response.sys();

        return new CityWeatherResponse(
                city.name(),
                city.nameKo(),
                city.country(),
                city.lat(),
                city.lon(),
                weather != null ? weather.main() : null,
                weather != null ? weather.description() : null,
                weather != null ? weather.icon() : null,
                main.temp(),
                kelvinToCelsius(main.temp()),
                main.feels_like(),
                kelvinToCelsius(main.feels_like()),
                main.temp_min(),
                main.temp_max(),
                main.humidity(),
                main.pressure(),
                wind.speed(),
                wind.deg(),
                response.clouds() != null ? response.clouds().all() : 0,
                response.visibility(),
                Instant.ofEpochSecond(sys.sunrise()),
                Instant.ofEpochSecond(sys.sunset()),
                fetchedAt
        );
    }

    private static double kelvinToCelsius(double kelvin) {
        return Math.round((kelvin - 273.15) * 100.0) / 100.0;
    }
}
