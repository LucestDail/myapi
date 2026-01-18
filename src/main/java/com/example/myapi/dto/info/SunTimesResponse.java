package com.example.myapi.dto.info;

import java.time.LocalTime;

/**
 * 일출/일몰 정보 DTO
 */
public record SunTimesResponse(
        String location,
        double latitude,
        double longitude,
        String date,
        LocalTime sunrise,
        LocalTime sunset,
        LocalTime solarNoon,
        Long dayLengthSeconds,
        String dayLength  // "HH:mm:ss" 형식
) {
    public static SunTimesResponse of(String location, double lat, double lon, String date,
                                       LocalTime sunrise, LocalTime sunset, LocalTime solarNoon) {
        long dayLengthSec = sunset.toSecondOfDay() - sunrise.toSecondOfDay();
        if (dayLengthSec < 0) dayLengthSec += 86400; // 자정을 넘는 경우
        
        String dayLengthStr = String.format("%02d:%02d:%02d",
                dayLengthSec / 3600,
                (dayLengthSec % 3600) / 60,
                dayLengthSec % 60);
        
        return new SunTimesResponse(location, lat, lon, date, sunrise, sunset, 
                solarNoon, dayLengthSec, dayLengthStr);
    }
}
