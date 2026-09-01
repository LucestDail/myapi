package com.example.myapi.dto.weather;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CityWeatherResponse.from() 매핑 로직 검증(순수 함수, 네트워크 무관).
 * 켈빈→섭씨 변환/반올림, weather 리스트 비어있음, clouds null 경계.
 */
class CityWeatherResponseTest {

    private OpenWeatherResponse response(List<OpenWeatherResponse.Weather> weather,
                                         double temp, double feels,
                                         OpenWeatherResponse.Clouds clouds) {
        return new OpenWeatherResponse(
                new OpenWeatherResponse.Coord(126.97, 37.56),
                weather,
                new OpenWeatherResponse.Main(temp, feels, temp - 1, temp + 1, 1013, 55, null, null),
                new OpenWeatherResponse.Wind(3.5, 180, null),
                clouds,
                new OpenWeatherResponse.Sys("KR", 1_700_000_000L, 1_700_040_000L),
                "Seoul", 10000, 1_700_000_500L, 32400
        );
    }

    @Test
    void 정상매핑_켈빈을_섭씨로_변환한다() {
        var owr = response(
                List.of(new OpenWeatherResponse.Weather(800, "Clear", "clear sky", "01d")),
                293.15, 296.15, new OpenWeatherResponse.Clouds(20));
        Instant now = Instant.now();

        CityWeatherResponse r = CityWeatherResponse.from(City.SEOUL, owr, now);

        assertEquals("Seoul", r.city());
        assertEquals("서울", r.cityKo());
        assertEquals("KR", r.country());
        assertEquals("Clear", r.weather());
        assertEquals("clear sky", r.description());
        assertEquals("01d", r.icon());
        assertEquals(293.15, r.temperature());
        assertEquals(20.0, r.temperatureCelsius());   // 293.15 - 273.15
        assertEquals(23.0, r.feelsLikeCelsius());      // 296.15 - 273.15
        assertEquals(55, r.humidity());
        assertEquals(1013, r.pressure());
        assertEquals(20, r.cloudiness());
        assertEquals(now, r.fetchedAt());
        assertEquals(Instant.ofEpochSecond(1_700_000_000L), r.sunrise());
    }

    @Test
    void 섭씨변환은_소수2자리로_반올림한다() {
        var owr = response(
                List.of(new OpenWeatherResponse.Weather(800, "Clear", "clear", "01d")),
                300.0, 300.0, new OpenWeatherResponse.Clouds(0));
        CityWeatherResponse r = CityWeatherResponse.from(City.BUSAN, owr, Instant.now());
        assertEquals(26.85, r.temperatureCelsius()); // (300-273.15)=26.85
    }

    @Test
    void weather리스트가_비면_날씨필드는_null() {
        var owr = response(List.of(), 280.0, 280.0, new OpenWeatherResponse.Clouds(50));
        CityWeatherResponse r = CityWeatherResponse.from(City.JEJU, owr, Instant.now());
        assertNull(r.weather());
        assertNull(r.description());
        assertNull(r.icon());
    }

    @Test
    void clouds가_null이면_cloudiness는_0() {
        var owr = response(
                List.of(new OpenWeatherResponse.Weather(800, "Clear", "clear", "01d")),
                290.0, 290.0, null);
        CityWeatherResponse r = CityWeatherResponse.from(City.DAEGU, owr, Instant.now());
        assertEquals(0, r.cloudiness());
    }
}
