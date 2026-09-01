package com.example.myapi.controller;

import com.example.myapi.dto.weather.CityWeatherResponse;
import com.example.myapi.service.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WeatherController 웹 계층 검증(standalone MockMvc, 컨텍스트/DB/필터 불필요).
 * 경로변수 매핑, 404/200 분기, JSON 직렬화를 확인한다.
 */
class WeatherControllerTest {

    private WeatherService weatherService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        weatherService = mock(WeatherService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WeatherController(weatherService)).build();
    }

    private CityWeatherResponse seoul() {
        return new CityWeatherResponse("Seoul", "서울", "KR", 37.56, 126.97, "Clear", "clear sky", "01d",
                293.15, 20.0, 293.15, 20.0, 290, 295, 55, 1013, 3.0, 180, 20, 10000,
                Instant.now(), Instant.now(), Instant.now());
    }

    @Test
    void 도시조회_존재하면_200과_본문() throws Exception {
        when(weatherService.getWeatherByCity("seoul")).thenReturn(Optional.of(seoul()));
        mockMvc.perform(get("/api/weather/seoul"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Seoul"))
                .andExpect(jsonPath("$.temperatureCelsius").value(20.0));
    }

    @Test
    void 도시조회_없으면_404() throws Exception {
        when(weatherService.getWeatherByCity("atlantis")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/weather/atlantis"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 전체조회_리스트를_반환한다() throws Exception {
        when(weatherService.getAllWeather()).thenReturn(List.of(seoul()));
        mockMvc.perform(get("/api/weather"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].city").value("Seoul"));
    }

    @Test
    void 도시목록을_반환한다() throws Exception {
        when(weatherService.getAvailableCities()).thenReturn(List.of("Seoul", "Busan"));
        mockMvc.perform(get("/api/weather/cities/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("Seoul"));
    }
}
