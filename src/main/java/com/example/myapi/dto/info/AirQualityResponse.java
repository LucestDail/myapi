package com.example.myapi.dto.info;

import java.time.Instant;

/**
 * 미세먼지/대기질 정보 DTO
 */
public record AirQualityResponse(
        String location,
        Integer pm10,           // PM10 농도
        Integer pm25,           // PM2.5 농도
        String pm10Grade,       // "좋음", "보통", "나쁨", "매우나쁨"
        String pm25Grade,
        Integer aqi,            // Air Quality Index (통합 지수)
        String overallGrade,
        String recommendation,
        Instant measuredAt
) {
    public static AirQualityResponse of(String location, Integer pm10, Integer pm25, Instant measuredAt) {
        String pm10Grade = gradeFromPm10(pm10);
        String pm25Grade = gradeFromPm25(pm25);
        int aqi = calculateAqi(pm10, pm25);
        String overallGrade = aqi > 150 ? "매우나쁨" : aqi > 100 ? "나쁨" : aqi > 50 ? "보통" : "좋음";
        String recommendation = getRecommendation(aqi);
        
        return new AirQualityResponse(location, pm10, pm25, pm10Grade, pm25Grade, 
                aqi, overallGrade, recommendation, measuredAt);
    }

    private static String gradeFromPm10(Integer pm10) {
        if (pm10 == null) return "알수없음";
        if (pm10 <= 30) return "좋음";
        if (pm10 <= 80) return "보통";
        if (pm10 <= 150) return "나쁨";
        return "매우나쁨";
    }

    private static String gradeFromPm25(Integer pm25) {
        if (pm25 == null) return "알수없음";
        if (pm25 <= 15) return "좋음";
        if (pm25 <= 35) return "보통";
        if (pm25 <= 75) return "나쁨";
        return "매우나쁨";
    }

    private static int calculateAqi(Integer pm10, Integer pm25) {
        // 간단한 AQI 계산 (실제는 더 복잡한 공식 사용)
        int pm10Aqi = pm10 != null ? (int)(pm10 * 0.8) : 0;
        int pm25Aqi = pm25 != null ? (int)(pm25 * 2.0) : 0;
        return Math.max(pm10Aqi, pm25Aqi);
    }

    private static String getRecommendation(int aqi) {
        if (aqi <= 50) return "야외 활동하기 좋은 날씨입니다.";
        if (aqi <= 100) return "민감군은 장시간 야외 활동 주의가 필요합니다.";
        if (aqi <= 150) return "외출 시 마스크 착용을 권장합니다.";
        return "가급적 외출을 자제하고, 외출 시 반드시 마스크를 착용하세요.";
    }
}
