package com.example.myapi.dto.info;

import java.time.LocalDate;
import java.util.List;

/**
 * 공휴일 정보 DTO
 */
public record HolidayResponse(
        int year,
        int month,
        List<Holiday> holidays
) {
    public record Holiday(
            LocalDate date,
            String name,
            boolean isHoliday,
            String type  // "national", "substitute", "special"
    ) {}

    public static HolidayResponse of(int year, int month, List<Holiday> holidays) {
        return new HolidayResponse(year, month, holidays);
    }

    // 한국 공휴일 하드코딩 (실제는 API 사용)
    public static List<Holiday> getKoreanHolidays(int year) {
        return List.of(
                new Holiday(LocalDate.of(year, 1, 1), "신정", true, "national"),
                new Holiday(LocalDate.of(year, 3, 1), "삼일절", true, "national"),
                new Holiday(LocalDate.of(year, 5, 5), "어린이날", true, "national"),
                new Holiday(LocalDate.of(year, 6, 6), "현충일", true, "national"),
                new Holiday(LocalDate.of(year, 8, 15), "광복절", true, "national"),
                new Holiday(LocalDate.of(year, 10, 3), "개천절", true, "national"),
                new Holiday(LocalDate.of(year, 10, 9), "한글날", true, "national"),
                new Holiday(LocalDate.of(year, 12, 25), "크리스마스", true, "national")
                // 음력 공휴일(설날, 추석 등)은 실제 API에서 가져와야 함
        );
    }
}
