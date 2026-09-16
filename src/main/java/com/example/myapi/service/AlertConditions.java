package com.example.myapi.service;

import com.example.myapi.entity.AlertRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * 알림 규칙 판정의 단일 정본.
 *
 * <p>🔴 왜 따로 뺐나: 같은 {@code switch (conditionType)} 가 {@link AlertService} 와
 * {@link AlertIntegrationService} 에 <b>두 벌</b>로 복사돼 있었다. 한쪽에만 조건을 더하면
 * "규칙은 저장됐는데 어떤 경로로 들어오느냐에 따라 발동이 다른" 상태가 되고, 그건 사용자에게
 * 보이지 않는다. 시간대 조건을 넣으면서 한쪽만 고칠 위험이 실제로 있었으므로 먼저 합쳤다.
 * 복제가 다시 생기지 않도록 {@code AlertConditionSingleSourceTest} 가 소스를 훑는다.</p>
 *
 * <p>판정 불능(모르는 조건 타입 · 깨진 시간 표기)은 <b>조용히 넘기지 않는다</b>.
 * 조건 타입을 모르면 발동하지 않고(fail-closed — 지어낸 기준으로 알림을 보내는 것보다 낫다),
 * 시간창 표기가 깨졌으면 시간 제한 없이 발동한다(fail-open — 표기 오타 때문에 알림이
 * 영영 안 오는 쪽이 더 나쁘다). 어느 쪽이든 {@code warn} 을 남긴다.</p>
 */
public final class AlertConditions {

    private static final Logger log = LoggerFactory.getLogger(AlertConditions.class);

    /**
     * 시간대 조건("영업시간에만")의 기준 시간대. 서버 기본 시간대에 기대면 배포 환경이 UTC 일 때
     * 사용자가 적은 09:00 이 18:00 로 어긋난다. 이 서비스는 국내용이므로 고정한다.
     */
    public static final ZoneId ALERT_ZONE = ZoneId.of("Asia/Seoul");

    private AlertConditions() {
    }

    /**
     * 임계값 비교. {@code at_least}/{@code at_most} 는 등급형 조건("나쁨 이상")을 위해 추가됐다.
     *
     * @return 모르는 조건 타입이면 false
     */
    public static boolean matches(String conditionType, double value, double threshold) {
        if (conditionType == null) {
            log.warn("[alert] 조건 타입이 비어 있어 발동하지 않음 (value={}, threshold={})", value, threshold);
            return false;
        }
        return switch (conditionType) {
            case "above" -> value > threshold;
            case "below" -> value < threshold;
            case "equals" -> Math.abs(value - threshold) < 0.001;
            case "at_least" -> value >= threshold;
            case "at_most" -> value <= threshold;
            default -> {
                log.warn("[alert] 모르는 조건 타입 '{}' — 발동하지 않음 (value={}, threshold={})",
                        conditionType, value, threshold);
                yield false;
            }
        };
    }

    /**
     * 시간대 조건. {@code activeFrom}/{@code activeTo} 가 비어 있으면 항상 허용한다.
     *
     * <p>구간은 [from, to) 이다. from &gt; to 이면 자정을 넘는 구간(22:00~06:00)으로 해석하고,
     * from == to 이면 "24시간"으로 본다.</p>
     */
    public static boolean withinActiveWindow(AlertRule rule, ZonedDateTime now) {
        return withinDays(rule.getActiveDays(), now.getDayOfWeek())
                && withinHours(rule.getActiveFrom(), rule.getActiveTo(), now.toLocalTime());
    }

    private static boolean withinHours(String fromText, String toText, LocalTime now) {
        if (isBlank(fromText) || isBlank(toText)) {
            return true;
        }
        LocalTime from = parseTime(fromText);
        LocalTime to = parseTime(toText);
        if (from == null || to == null) {
            return true; // fail-open: 표기가 깨졌다고 알림을 영영 막지 않는다(위에서 warn 남김)
        }
        if (from.equals(to)) {
            return true; // 24시간
        }
        if (from.isBefore(to)) {
            return !now.isBefore(from) && now.isBefore(to);
        }
        return !now.isBefore(from) || now.isBefore(to); // 자정을 넘는 구간
    }

    private static boolean withinDays(String activeDays, DayOfWeek today) {
        if (isBlank(activeDays)) {
            return true;
        }
        Set<DayOfWeek> allowed = parseDays(activeDays);
        if (allowed.isEmpty()) {
            return true; // fail-open: 전부 해석 실패(위에서 warn 남김)
        }
        return allowed.contains(today);
    }

    /** "MON,TUE" / "mon tue" / "월,화" 를 받는다. 해석 못 한 토큰은 warn 후 버린다. */
    static Set<DayOfWeek> parseDays(String activeDays) {
        Set<DayOfWeek> allowed = EnumSet.noneOf(DayOfWeek.class);
        for (String token : activeDays.split("[,\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            DayOfWeek day = toDayOfWeek(token.trim());
            if (day == null) {
                log.warn("[alert] 요일 '{}' 을 해석하지 못해 무시함 (activeDays={})", token, activeDays);
                continue;
            }
            allowed.add(day);
        }
        return allowed;
    }

    private static DayOfWeek toDayOfWeek(String token) {
        String upper = token.toUpperCase(Locale.ROOT);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day.name().equals(upper) || day.name().startsWith(upper) && upper.length() >= 3) {
                return day;
            }
        }
        return switch (token) {
            case "월" -> DayOfWeek.MONDAY;
            case "화" -> DayOfWeek.TUESDAY;
            case "수" -> DayOfWeek.WEDNESDAY;
            case "목" -> DayOfWeek.THURSDAY;
            case "금" -> DayOfWeek.FRIDAY;
            case "토" -> DayOfWeek.SATURDAY;
            case "일" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private static LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text.trim());
        } catch (Exception e) {
            log.warn("[alert] 시간 표기 '{}' 를 해석하지 못해 시간대 조건을 건너뜀 (HH:mm 형식이어야 함)", text);
            return null;
        }
    }

    /** 임계값과 시간대 조건을 모두 만족해야 발동한다. */
    public static boolean shouldFire(AlertRule rule, double value, ZonedDateTime now) {
        if (rule.getThreshold() == null) {
            // 컬럼은 NOT NULL 이지만 손으로 넣은 행이 있을 수 있다. 터뜨리지 말고 알린다.
            log.warn("[alert] 규칙 {} 에 임계값이 없어 발동하지 않음 (type={})",
                    rule.getId(), rule.getType());
            return false;
        }
        return matches(rule.getConditionType(), value, rule.getThreshold())
                && withinActiveWindow(rule, now);
    }

    public static boolean shouldFire(AlertRule rule, double value) {
        return shouldFire(rule, value, ZonedDateTime.now(ALERT_ZONE));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
