package com.example.myapi.service;

import com.example.myapi.dto.weather.CityWeatherResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * WeatherService 캐시 조회 로직 검증. @PostConstruct init() 을 호출하지 않으므로
 * 네트워크 없이 캐시 읽기/대소문자 무시/상태 보고만 결정적으로 확인한다.
 */
class WeatherServiceTest {

    private WeatherService service;

    @BeforeEach
    void setUp() {
        service = new WeatherService(mock(RestTemplate.class), null);
    }

    @SuppressWarnings("unchecked")
    private void seed(String key, CityWeatherResponse resp) {
        Map<String, CityWeatherResponse> cache =
                (Map<String, CityWeatherResponse>) ReflectionTestUtils.getField(service, "weatherCache");
        cache.put(key, resp);
    }

    private CityWeatherResponse dummy(String city) {
        return new CityWeatherResponse(city, city, "KR", 0, 0, "Clear", "clear", "01d",
                293.15, 20.0, 293.15, 20.0, 290, 295, 50, 1013, 3.0, 180, 10, 10000,
                Instant.now(), Instant.now(), Instant.now());
    }

    @Test
    void 빈캐시에서_전체조회는_빈리스트() {
        assertTrue(service.getAllWeather().isEmpty());
    }

    @Test
    void 빈캐시에서_도시조회는_Optional_empty() {
        assertFalse(service.getWeatherByCity("seoul").isPresent());
    }

    @Test
    void 도시조회는_대소문자를_무시한다() {
        seed("seoul", dummy("Seoul")); // 캐시는 소문자 키로 저장됨
        assertTrue(service.getWeatherByCity("SEOUL").isPresent());
        assertTrue(service.getWeatherByCity("Seoul").isPresent());
        assertEquals("Seoul", service.getWeatherByCity("seoul").get().city());
    }

    @Test
    void null_도시명은_NPE() {
        assertThrows(NullPointerException.class, () -> service.getWeatherByCity(null));
    }

    @Test
    void 사용가능도시목록은_주요_10개도시() {
        var cities = service.getAvailableCities();
        assertEquals(10, cities.size());
        assertTrue(cities.contains("Seoul"));
        assertTrue(cities.contains("Jeju"));
    }

    @Test
    void 캐시상태_초기값은_never() {
        Map<String, Object> status = service.getCacheStatus();
        assertEquals(0, status.get("cachedCities"));
        assertEquals(10, status.get("totalCities"));
        assertEquals("never", status.get("lastUpdated"));
    }
}
