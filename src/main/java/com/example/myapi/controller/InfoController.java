package com.example.myapi.controller;

import com.example.myapi.dto.info.AirQualityResponse;
import com.example.myapi.dto.info.ExchangeRateResponse;
import com.example.myapi.dto.info.HolidayResponse;
import com.example.myapi.dto.info.SunTimesResponse;
import com.example.myapi.service.LifeInfoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 생활정보 API 컨트롤러
 */
@RestController
@RequestMapping("/api/info")
@CrossOrigin(origins = "*")
public class InfoController {

    private final LifeInfoService lifeInfoService;

    public InfoController(LifeInfoService lifeInfoService) {
        this.lifeInfoService = lifeInfoService;
    }

    /**
     * 환율 정보 조회
     * GET /api/info/exchange?base=USD
     */
    @GetMapping("/exchange")
    public ResponseEntity<ExchangeRateResponse> getExchangeRates(
            @RequestParam(defaultValue = "USD") String base) {
        return ResponseEntity.ok(lifeInfoService.getExchangeRates(base));
    }

    /**
     * 미세먼지 정보 조회
     * GET /api/info/air-quality?location=Seoul
     */
    @GetMapping("/air-quality")
    public ResponseEntity<AirQualityResponse> getAirQuality(
            @RequestParam(defaultValue = "Seoul") String location) {
        return ResponseEntity.ok(lifeInfoService.getAirQuality(location));
    }

    /**
     * 일출/일몰 시간 조회
     * GET /api/info/sun-times?lat=37.5665&lon=126.9780&location=Seoul
     */
    @GetMapping("/sun-times")
    public ResponseEntity<SunTimesResponse> getSunTimes(
            @RequestParam(defaultValue = "37.5665") double lat,
            @RequestParam(defaultValue = "126.9780") double lon,
            @RequestParam(defaultValue = "Seoul") String location) {
        return ResponseEntity.ok(lifeInfoService.getSunTimes(lat, lon, location));
    }

    /**
     * 공휴일 정보 조회
     * GET /api/info/holidays?year=2024&month=1
     */
    @GetMapping("/holidays")
    public ResponseEntity<HolidayResponse> getHolidays(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        int targetYear = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(lifeInfoService.getHolidays(targetYear, month));
    }

    /**
     * 오늘이 공휴일인지 확인
     * GET /api/info/holidays/today
     */
    @GetMapping("/holidays/today")
    public ResponseEntity<Map<String, Object>> isTodayHoliday() {
        boolean isHoliday = lifeInfoService.isTodayHoliday();
        HolidayResponse.Holiday nextHoliday = lifeInfoService.getNextHoliday();
        
        return ResponseEntity.ok(Map.of(
                "today", LocalDate.now().toString(),
                "isHoliday", isHoliday,
                "nextHoliday", nextHoliday != null ? Map.of(
                        "date", nextHoliday.date().toString(),
                        "name", nextHoliday.name()
                ) : Map.of()
        ));
    }

    /**
     * 종합 생활정보 조회
     * GET /api/info/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(defaultValue = "Seoul") String location,
            @RequestParam(defaultValue = "37.5665") double lat,
            @RequestParam(defaultValue = "126.9780") double lon) {
        return ResponseEntity.ok(Map.of(
                "exchange", lifeInfoService.getExchangeRates("USD"),
                "airQuality", lifeInfoService.getAirQuality(location),
                "sunTimes", lifeInfoService.getSunTimes(lat, lon, location),
                "holiday", Map.of(
                        "isToday", lifeInfoService.isTodayHoliday(),
                        "next", lifeInfoService.getNextHoliday()
                )
        ));
    }
}
