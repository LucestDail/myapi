package com.example.myapi.service;

import com.example.myapi.entity.AlertRule;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 조건 판정 + 시간대 조건.
 *
 * <p>시간은 전부 명시적으로 넘긴다 — {@code ZonedDateTime.now()} 에 기대면 테스트가 실행 시각에
 * 따라 갈린다(밤에 돌리면 "영업시간" 테스트가 조용히 반대 결과를 낸다).</p>
 */
class AlertConditionsTest {

    private AlertRule rule(String conditionType, double threshold) {
        AlertRule r = new AlertRule();
        r.setType("test");
        r.setConditionType(conditionType);
        r.setThreshold(threshold);
        return r;
    }

    private ZonedDateTime at(DayOfWeek day, int hour, int minute) {
        // 2026-09-14 는 월요일. 거기서 요일만큼 밀어 원하는 요일을 만든다.
        return ZonedDateTime.of(2026, 9, 14, hour, minute, 0, 0, AlertConditions.ALERT_ZONE)
                .plusDays(day.getValue() - DayOfWeek.MONDAY.getValue());
    }

    // ==================== 임계값 ====================

    @Test
    void above_below_equals_는_기존과_같게_동작한다() {
        assertTrue(AlertConditions.matches("above", 10.0, 5.0));
        assertFalse(AlertConditions.matches("above", 5.0, 5.0));
        assertTrue(AlertConditions.matches("below", 1.0, 5.0));
        assertFalse(AlertConditions.matches("below", 5.0, 5.0));
        assertTrue(AlertConditions.matches("equals", 5.0, 5.0));
        assertFalse(AlertConditions.matches("equals", 5.1, 5.0));
    }

    @Test
    void at_least_와_at_most_는_경계를_포함한다() {
        assertTrue(AlertConditions.matches("at_least", 3.0, 3.0));
        assertTrue(AlertConditions.matches("at_least", 4.0, 3.0));
        assertFalse(AlertConditions.matches("at_least", 2.0, 3.0));

        assertTrue(AlertConditions.matches("at_most", 3.0, 3.0));
        assertTrue(AlertConditions.matches("at_most", 2.0, 3.0));
        assertFalse(AlertConditions.matches("at_most", 4.0, 3.0));
    }

    @Test
    void 모르는_조건타입은_발동하지_않는다() {
        assertFalse(AlertConditions.matches("between", 10.0, 5.0));
        assertFalse(AlertConditions.matches(null, 10.0, 5.0));
    }

    // ==================== 시간대 ====================

    @Test
    void 시간창이_비면_언제나_허용() {
        AlertRule r = rule("above", 1);
        assertTrue(AlertConditions.withinActiveWindow(r, at(DayOfWeek.SUNDAY, 3, 0)));
    }

    @Test
    void 영업시간_09시부터_18시까지만_허용한다() {
        AlertRule r = rule("above", 1);
        r.setActiveFrom("09:00");
        r.setActiveTo("18:00");

        assertFalse(AlertConditions.withinActiveWindow(r, at(DayOfWeek.MONDAY, 8, 59)));
        assertTrue(AlertConditions.withinActiveWindow(r, at(DayOfWeek.MONDAY, 9, 0)), "시작 시각은 포함");
        assertTrue(AlertConditions.withinActiveWindow(r, at(DayOfWeek.MONDAY, 17, 59)));
        assertFalse(AlertConditions.withinActiveWindow(r, at(DayOfWeek.MONDAY, 18, 0)), "끝 시각은 제외");
        assertFalse(AlertConditions.withinActiveWindow(r, at(DayOfWeek.MONDAY, 23, 0)));
    }

    @Test
    void 자정을_넘는_구간도_허용한다() {
        AlertRule r = rule("above", 1);
        r.setActiveFrom("22:00");
        r.setActiveTo("06:00");

        assertTrue(AlertConditions.withinActiveWindow(r, at(DayOfWeek.MONDAY, 23, 0)));
        assertTrue(AlertConditions.withinActiveWindow(r, at(DayOfWeek.MONDAY, 2, 0)));
        assertFalse(AlertConditions.withinActiveWindow(r, at(DayOfWeek.MONDAY, 12, 0)));
        assertFalse(AlertConditions.withinActiveWindow(r, at(DayOfWeek.MONDAY, 6, 0)));
    }

    @Test
    void 시작과_끝이_같으면_24시간이다() {
        AlertRule r = rule("above", 1);
        r.setActiveFrom("09:00");
        r.setActiveTo("09:00");
        assertTrue(AlertConditions.withinActiveWindow(r, at(DayOfWeek.MONDAY, 3, 0)));
        assertTrue(AlertConditions.withinActiveWindow(r, at(DayOfWeek.MONDAY, 15, 0)));
    }

    @Test
    void 요일_제한이_걸리면_그_요일에만_허용한다() {
        AlertRule r = rule("above", 1);
        r.setActiveDays("MON,TUE,WED,THU,FRI");

        assertTrue(AlertConditions.withinActiveWindow(r, at(DayOfWeek.MONDAY, 12, 0)));
        assertTrue(AlertConditions.withinActiveWindow(r, at(DayOfWeek.FRIDAY, 12, 0)));
        assertFalse(AlertConditions.withinActiveWindow(r, at(DayOfWeek.SATURDAY, 12, 0)));
        assertFalse(AlertConditions.withinActiveWindow(r, at(DayOfWeek.SUNDAY, 12, 0)));
    }

    @Test
    void 요일은_한글과_소문자도_받는다() {
        assertEquals(java.util.Set.of(DayOfWeek.MONDAY, DayOfWeek.SATURDAY),
                AlertConditions.parseDays("월,토"));
        assertEquals(java.util.Set.of(DayOfWeek.WEDNESDAY),
                AlertConditions.parseDays("wed"));
    }

    @Test
    void 깨진_시간표기는_알림을_막지_않는다() {
        // fail-open: 표기 오타 하나로 알림이 영영 안 오는 쪽이 더 나쁘다.
        AlertRule r = rule("above", 1);
        r.setActiveFrom("아홉시");
        r.setActiveTo("18:00");
        assertTrue(AlertConditions.withinActiveWindow(r, at(DayOfWeek.MONDAY, 3, 0)));
    }

    @Test
    void 요일을_하나도_못_읽으면_매일로_본다() {
        AlertRule r = rule("above", 1);
        r.setActiveDays("무요일,없는요일");
        assertTrue(AlertConditions.withinActiveWindow(r, at(DayOfWeek.SUNDAY, 12, 0)));
    }

    // ==================== 합산 ====================

    @Test
    void 임계값을_넘어도_시간창_밖이면_발동하지_않는다() {
        AlertRule r = rule("above", 5);
        r.setActiveFrom("09:00");
        r.setActiveTo("18:00");

        assertTrue(AlertConditions.shouldFire(r, 10.0, at(DayOfWeek.MONDAY, 10, 0)));
        assertFalse(AlertConditions.shouldFire(r, 10.0, at(DayOfWeek.MONDAY, 22, 0)),
                "임계값은 넘었지만 영업시간이 아니다");
    }

    @Test
    void 임계값이_없는_규칙은_터지지_않고_발동만_안_한다() {
        AlertRule r = new AlertRule();
        r.setId(1L);
        r.setType("test");
        r.setConditionType("above");
        r.setThreshold(null);
        assertFalse(AlertConditions.shouldFire(r, 10.0, at(DayOfWeek.MONDAY, 10, 0)));
    }

    @Test
    void 시간창_안이라도_임계값을_못_넘으면_발동하지_않는다() {
        AlertRule r = rule("above", 5);
        r.setActiveFrom("09:00");
        r.setActiveTo("18:00");
        assertFalse(AlertConditions.shouldFire(r, 1.0, at(DayOfWeek.MONDAY, 10, 0)));
    }
}
