package com.example.myapi.dto.weather;

/**
 * Represents a city with coordinates for weather lookup
 * Korean major cities only
 */
public record City(
        String name,
        String nameKo,
        String country,
        double lat,
        double lon
) {
    // 10 Major Korean Cities
    public static final City SEOUL = new City("Seoul", "서울", "KR", 37.5665, 126.9780);
    public static final City BUSAN = new City("Busan", "부산", "KR", 35.1796, 129.0756);
    public static final City INCHEON = new City("Incheon", "인천", "KR", 37.4563, 126.7052);
    public static final City DAEGU = new City("Daegu", "대구", "KR", 35.8714, 128.6014);
    public static final City DAEJEON = new City("Daejeon", "대전", "KR", 36.3504, 127.3845);
    public static final City GWANGJU = new City("Gwangju", "광주", "KR", 35.1595, 126.8526);
    public static final City SUWON = new City("Suwon", "수원", "KR", 37.2636, 127.0286);
    public static final City ULSAN = new City("Ulsan", "울산", "KR", 35.5384, 129.3114);
    public static final City JEJU = new City("Jeju", "제주", "KR", 33.4996, 126.5312);
    public static final City CHANGWON = new City("Changwon", "창원", "KR", 35.2280, 128.6811);

    public static City[] getMajorCities() {
        return new City[]{
                SEOUL, BUSAN, INCHEON, DAEGU, DAEJEON,
                GWANGJU, SUWON, ULSAN, JEJU, CHANGWON
        };
    }
}
