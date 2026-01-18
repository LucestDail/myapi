package com.example.myapi.service;

import com.example.myapi.dto.alert.AlertEventDto;
import com.example.myapi.dto.dashboard.DashboardData.StockQuote;
import com.example.myapi.dto.dashboard.DashboardData.SystemData;
import com.example.myapi.dto.dashboard.DashboardData.WeatherData;
import com.example.myapi.entity.AlertRule;
import com.example.myapi.repository.AlertRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 알림 통합 서비스
 * 데이터 변경 시 알림 조건 검사 및 발송
 */
@Service
public class AlertIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(AlertIntegrationService.class);

    private final AlertRuleRepository alertRuleRepository;
    private final AlertService alertService;

    // 마지막 알림 발송 시간 추적 (중복 알림 방지)
    private final Map<String, Long> lastAlertTime = new HashMap<>();
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

    private boolean matchesTarget(AlertRule rule, String target) {
        return rule.getTarget() == null || 
               rule.getTarget().isEmpty() || 
               rule.getTarget().equalsIgnoreCase(target);
    }

    private boolean checkCondition(AlertRule rule, double value) {
        return switch (rule.getConditionType()) {
            case "above" -> value > rule.getThreshold();
            case "below" -> value < rule.getThreshold();
            case "equals" -> Math.abs(value - rule.getThreshold()) < 0.001;
            default -> false;
        };
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
                rule.getType(), message, severity, target, value, rule.getThreshold());
        
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
        String conditionText = switch (rule.getConditionType()) {
            case "above" -> "초과";
            case "below" -> "미만";
            case "equals" -> "도달";
            default -> "";
        };

        String typeText = switch (rule.getType()) {
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

        return String.format("%s %s %.2f%s %s (임계값: %.2f%s)", 
                target, typeText, value, unit, conditionText, rule.getThreshold(), unit);
    }
}
