package com.example.myapi.service;

import com.example.myapi.dto.alert.AlertEventDto;
import com.example.myapi.dto.dashboard.DashboardData.StockQuote;
import com.example.myapi.dto.dashboard.DashboardData.SystemData;
import com.example.myapi.dto.dashboard.DashboardData.WeatherData;
import com.example.myapi.dto.info.AirQualityResponse;
import com.example.myapi.dto.info.ExchangeRateResponse;
import com.example.myapi.entity.AlertRule;
import com.example.myapi.repository.AlertRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 알림 통합 서비스
 * 데이터 변경 시 알림 조건 검사 및 발송
 */
@Service
public class AlertIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(AlertIntegrationService.class);

    /** 환율 알림 규칙 타입. target 은 "USD/KRW" 또는 통화코드만("KRW", base 는 USD 로 본다). */
    public static final String TYPE_EXCHANGE_RATE = "exchange_rate";

    /**
     * 미세먼지 <b>통합</b> 등급 알림 타입. 값은 등급 순위(1 좋음 ~ 4 매우나쁨)이고
     * "나쁨 이상" 은 {@code at_least} + 임계값 3 이다.
     *
     * <p>⚠️ 이 타입이 보는 것은 {@code overallGrade}(통합대기지수 기준)다. PM10 농도 등급과
     * 통합 등급은 <b>같지 않다</b> — 기존 {@code AirQualityResponse} 에서 PM10 120 은 PM10 등급
     * "나쁨" 이지만 통합 등급은 "보통" 이다(AQI 96). 항목별 등급을 보려면
     * {@code air_pm10_grade}/{@code air_pm25_grade} 를 쓴다.</p>
     */
    public static final String TYPE_AIR_GRADE = "air_grade";

    private final AlertRuleRepository alertRuleRepository;
    private final AlertService alertService;

    /**
     * 마지막 알림 발송 시간 추적 (중복 알림 방지).
     *
     * <p>🔴 {@code HashMap} 이었다. 원래도 SSE 브로드캐스트 스레드 여러 개가 같이 썼고,
     * 생활정보 스케줄러까지 같은 맵을 쓰게 되면서 확실히 깨진다(동시 rehash 는 무한 루프까지 간다).
     * 크기는 규칙 수에 묶여 있어 증가 걱정은 없다.</p>
     */
    private final Map<String, Long> lastAlertTime = new ConcurrentHashMap<>();
    private static final long ALERT_COOLDOWN_MS = 60000; // 1분

    public AlertIntegrationService(AlertRuleRepository alertRuleRepository,
                                   AlertService alertService) {
        this.alertRuleRepository = alertRuleRepository;
        this.alertService = alertService;
    }

    /**
     * 주식 데이터 변경 시 알림 검사
     */
    public void checkStockAlerts(List<StockQuote> quotes) {
        List<AlertRule> enabledRules = alertRuleRepository.findByEnabledTrue().stream()
                .filter(r -> r.getType().startsWith("stock_"))
                .toList();

        for (StockQuote quote : quotes) {
            if (quote.currentPrice() == null) continue;

            for (AlertRule rule : enabledRules) {
                if (!matchesTarget(rule, quote.symbol())) continue;

                double value = switch (rule.getType()) {
                    case "stock_price" -> quote.currentPrice();
                    case "stock_change" -> quote.change() != null ? quote.change() : 0;
                    case "stock_percent" -> quote.percentChange() != null ? quote.percentChange() : 0;
                    default -> 0;
                };

                if (checkCondition(rule, value)) {
                    triggerAlertWithCooldown(rule, quote.symbol(), value);
                }
            }
        }
    }

    /**
     * 시스템 데이터 변경 시 알림 검사
     */
    public void checkSystemAlerts(SystemData system) {
        List<AlertRule> enabledRules = alertRuleRepository.findByEnabledTrue().stream()
                .filter(r -> r.getType().equals("cpu") || r.getType().equals("memory") || r.getType().equals("heap"))
                .toList();

        for (AlertRule rule : enabledRules) {
            double value = switch (rule.getType()) {
                case "cpu" -> system.cpuUsage();
                case "memory" -> system.memoryUsagePercent();
                case "heap" -> system.heapUsagePercent();
                default -> 0;
            };

            if (checkCondition(rule, value)) {
                triggerAlertWithCooldown(rule, rule.getType().toUpperCase(), value);
            }
        }
    }

    /**
     * 날씨 데이터 변경 시 알림 검사
     */
    public void checkWeatherAlerts(List<WeatherData> weatherList) {
        List<AlertRule> enabledRules = alertRuleRepository.findByEnabledTrue().stream()
                .filter(r -> r.getType().equals("weather_temp") || r.getType().equals("weather_humidity"))
                .toList();

        for (WeatherData weather : weatherList) {
            for (AlertRule rule : enabledRules) {
                if (!matchesTarget(rule, weather.city())) continue;

                double value = switch (rule.getType()) {
                    case "weather_temp" -> weather.temperatureCelsius();
                    case "weather_humidity" -> weather.humidity();
                    default -> 0;
                };

                if (checkCondition(rule, value)) {
                    triggerAlertWithCooldown(rule, weather.cityKo() != null ? weather.cityKo() : weather.city(), value);
                }
            }

            // 극한 온도 자동 알림
            if (weather.temperatureCelsius() < -15) {
                triggerWeatherWarning(weather, "한파 경보", "danger");
            } else if (weather.temperatureCelsius() > 35) {
                triggerWeatherWarning(weather, "폭염 경보", "danger");
            } else if (weather.temperatureCelsius() < -10 || weather.temperatureCelsius() > 33) {
                triggerWeatherWarning(weather, "기온 주의", "warning");
            }
        }
    }

    /**
     * 환율 알림 검사. 규칙 하나당 통화쌍 하나를 본다.
     *
     * <p>target 표기는 두 가지를 받는다: {@code "USD/KRW"}(base/quote) 또는 {@code "KRW"}(base 는
     * 응답의 base 를 그대로 쓴다). 응답 base 와 규칙이 요구한 base 가 다르면 <b>환산하지 않고
     * 건너뛴다</b> — 환산을 지어내면 잘못된 숫자로 알림을 보내게 된다.</p>
     */
    public void checkExchangeRateAlerts(ExchangeRateResponse response) {
        if (response == null || response.rates() == null || response.rates().isEmpty()) {
            return;
        }
        List<AlertRule> rules = alertRuleRepository.findByEnabledTrue().stream()
                .filter(r -> TYPE_EXCHANGE_RATE.equals(r.getType()))
                .toList();

        for (AlertRule rule : rules) {
            String[] pair = splitCurrencyPair(rule.getTarget(), response.base());
            if (pair == null) {
                log.warn("[alert] 환율 규칙 {} 의 target '{}' 을 통화쌍으로 읽지 못해 건너뜀",
                        rule.getId(), rule.getTarget());
                continue;
            }
            if (!pair[0].equalsIgnoreCase(response.base())) {
                log.debug("[alert] 환율 규칙 {}: 기준통화 {} != 응답 base {} — 건너뜀",
                        rule.getId(), pair[0], response.base());
                continue;
            }
            Double rate = response.rates().get(pair[1].toUpperCase(Locale.ROOT));
            if (rate == null) {
                log.debug("[alert] 환율 규칙 {}: 응답에 {} 가 없어 건너뜀", rule.getId(), pair[1]);
                continue;
            }
            if (checkCondition(rule, rate)) {
                triggerAlertWithCooldown(rule, pair[0] + "/" + pair[1], rate);
            }
        }
    }

    /**
     * 미세먼지 알림 검사. 수치형({@code air_pm10}·{@code air_pm25}·{@code air_aqi})과
     * 등급형({@code air_grade}) 둘 다 본다.
     *
     * <p>"나쁨 이상" 은 등급형 + {@code at_least} + 임계값 3 으로 표현한다. 등급 문자열을
     * 직접 비교하지 않고 순위로 바꾸는 이유는, 임계값 비교 엔진이 이미 있는데 문자열 전용
     * 경로를 새로 만들면 시간대 조건·쿨다운을 또 한 번 붙여야 하기 때문이다.</p>
     *
     * <p>값을 못 가져온 경우(등급 "알수없음", 농도 null)는 <b>발동하지 않는다</b> —
     * {@link AirQualityResponse#unavailable} 가 이미 "지어내지 않는다" 를 지키고 있으므로
     * 여기서 0 으로 읽어 "좋음" 알림을 보내면 그 규율을 깨뜨린다.</p>
     */
    public void checkAirQualityAlerts(AirQualityResponse air) {
        if (air == null) {
            return;
        }
        List<AlertRule> rules = alertRuleRepository.findByEnabledTrue().stream()
                .filter(r -> r.getType() != null && r.getType().startsWith("air_"))
                .toList();

        for (AlertRule rule : rules) {
            if (!matchesTarget(rule, air.location())) continue;

            Double value = switch (rule.getType()) {
                case "air_pm10" -> air.pm10() != null ? air.pm10().doubleValue() : null;
                case "air_pm25" -> air.pm25() != null ? air.pm25().doubleValue() : null;
                case "air_aqi" -> air.aqi() != null ? air.aqi().doubleValue() : null;
                case TYPE_AIR_GRADE -> gradeRank(air.overallGrade());
                case "air_pm10_grade" -> gradeRank(air.pm10Grade());
                case "air_pm25_grade" -> gradeRank(air.pm25Grade());
                default -> null;
            };

            if (value == null) {
                log.debug("[alert] 대기질 규칙 {}({}): 값을 못 가져와 건너뜀", rule.getId(), rule.getType());
                continue;
            }
            if (checkCondition(rule, value)) {
                triggerAlertWithCooldown(rule, air.location(), value);
            }
        }
    }

    /** 등급 문자열 → 순위. 모르는 값("알수없음" 포함)은 null(=발동 안 함). */
    static Double gradeRank(String grade) {
        if (grade == null) {
            return null;
        }
        return switch (grade.trim()) {
            case "좋음" -> 1.0;
            case "보통" -> 2.0;
            case "나쁨" -> 3.0;
            case "매우나쁨" -> 4.0;
            default -> null;
        };
    }

    /** "USD/KRW" → [USD, KRW], "KRW" → [defaultBase, KRW]. 읽지 못하면 null. */
    static String[] splitCurrencyPair(String target, String defaultBase) {
        if (target == null || target.isBlank()) {
            return null;
        }
        String trimmed = target.trim();
        if (trimmed.contains("/")) {
            String[] parts = trimmed.split("/", 2);
            if (parts[0].isBlank() || parts[1].isBlank()) {
                return null;
            }
            return new String[]{parts[0].trim().toUpperCase(Locale.ROOT), parts[1].trim().toUpperCase(Locale.ROOT)};
        }
        if (defaultBase == null || defaultBase.isBlank()) {
            return null;
        }
        return new String[]{defaultBase.trim().toUpperCase(Locale.ROOT), trimmed.toUpperCase(Locale.ROOT)};
    }

    private boolean matchesTarget(AlertRule rule, String target) {
        return rule.getTarget() == null || 
               rule.getTarget().isEmpty() || 
               rule.getTarget().equalsIgnoreCase(target);
    }

    /**
     * 임계값 + 시간대 조건. 판정은 {@link AlertConditions} 한 곳에만 있다 —
     * 여기에 다시 {@code switch} 를 쓰면 {@code AlertConditionSingleSourceTest} 가 깨진다.
     */
    private boolean checkCondition(AlertRule rule, double value) {
        return AlertConditions.shouldFire(rule, value);
    }

    private void triggerAlertWithCooldown(AlertRule rule, String target, double value) {
        String key = rule.getId() + "_" + target;
        long now = System.currentTimeMillis();
        Long lastTime = lastAlertTime.get(key);

        if (lastTime != null && (now - lastTime) < ALERT_COOLDOWN_MS) {
            return; // 쿨다운 중
        }

        lastAlertTime.put(key, now);

        String severity = determineSeverity(rule, value);
        String message = buildAlertMessage(rule, target, value);
        
        AlertEventDto event = AlertEventDto.create(
                rule.getType(), message, severity, target, value, rule.getThreshold(), rule.getChannels());

        alertService.triggerAlert(rule.getUserId(), event);
    }

    private void triggerWeatherWarning(WeatherData weather, String warning, String severity) {
        String key = "weather_warning_" + weather.city();
        long now = System.currentTimeMillis();
        Long lastTime = lastAlertTime.get(key);

        if (lastTime != null && (now - lastTime) < ALERT_COOLDOWN_MS * 10) { // 10분 쿨다운
            return;
        }

        lastAlertTime.put(key, now);

        String city = weather.cityKo() != null ? weather.cityKo() : weather.city();
        String message = String.format("%s: %s (%.1f°C)", city, warning, weather.temperatureCelsius());
        
        AlertEventDto event = AlertEventDto.create("weather_alert", message, severity, 
                weather.city(), weather.temperatureCelsius(), null);
        
        // 모든 활성 규칙의 사용자에게 전송 (전역 알림)
        alertService.triggerAlert("system", event);
    }

    private String determineSeverity(AlertRule rule, double value) {
        double diff = Math.abs(value - rule.getThreshold());
        double ratio = rule.getThreshold() != 0 ? diff / Math.abs(rule.getThreshold()) : diff;

        if (rule.getType().contains("percent") && Math.abs(value) > 10) return "danger";
        if (ratio > 0.2) return "danger";
        if (ratio > 0.1) return "warning";
        return "info";
    }

    private String buildAlertMessage(AlertRule rule, String target, double value) {
        // 문구 전용 표. 발동 여부는 AlertConditions 가 정하고 여기서는 사람이 읽을 말만 고른다.
        String conditionText = switch (rule.getConditionType()) {
            case "above" -> "초과";
            case "below" -> "미만";
            case "equals" -> "도달";
            case "at_least" -> "이상";
            case "at_most" -> "이하";
            default -> "";
        };

        if (rule.getType() != null && rule.getType().endsWith("_grade")) {
            String what = switch (rule.getType()) {
                case "air_pm10_grade" -> "PM10 등급";
                case "air_pm25_grade" -> "PM2.5 등급";
                default -> "미세먼지";
            };
            return String.format("%s %s %s (기준: %s %s)",
                    target, what, gradeText(value), gradeText(rule.getThreshold()), conditionText);
        }

        String typeText = switch (rule.getType()) {
            case TYPE_EXCHANGE_RATE -> "환율";
            case "air_pm10" -> "PM10";
            case "air_pm25" -> "PM2.5";
            case "air_aqi" -> "통합대기지수";
            case "stock_price" -> "주가";
            case "stock_change" -> "변동금액";
            case "stock_percent" -> "변동률";
            case "cpu" -> "CPU 사용률";
            case "memory" -> "메모리 사용률";
            case "heap" -> "힙 메모리";
            case "weather_temp" -> "온도";
            case "weather_humidity" -> "습도";
            default -> rule.getType();
        };

        String unit = rule.getType().contains("percent") || rule.getType().contains("cpu") ||
                      rule.getType().contains("memory") || rule.getType().contains("heap") ||
                      rule.getType().contains("humidity") ? "%" : "";
        if (rule.getType().contains("temp")) unit = "°C";
        if (rule.getType().equals("stock_price")) unit = "$";
        if (rule.getType().equals("air_pm10") || rule.getType().equals("air_pm25")) unit = "㎍/㎥";

        return String.format("%s %s %.2f%s %s (임계값: %.2f%s)",
                target, typeText, value, unit, conditionText, rule.getThreshold(), unit);
    }

    /** 등급 순위 → 사람이 읽는 말. {@link #gradeRank} 의 역. */
    static String gradeText(double rank) {
        int r = (int) Math.round(rank);
        return switch (r) {
            case 1 -> "좋음";
            case 2 -> "보통";
            case 3 -> "나쁨";
            case 4 -> "매우나쁨";
            default -> "등급 " + r;
        };
    }
}
