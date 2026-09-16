package com.example.myapi.service;

import com.example.myapi.dto.alert.AlertEventDto;
import com.example.myapi.dto.info.AirQualityResponse;
import com.example.myapi.dto.info.ExchangeRateResponse;
import com.example.myapi.entity.AlertRule;
import com.example.myapi.repository.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 환율·미세먼지 알림과 시간대 조건·채널이 실제 발송 경로까지 이어지는지 확인한다.
 *
 * <p>순수 클래스({@link AlertConditions})만 테스트하면 "판정은 맞는데 안 불린다" 를 못 잡는다.
 * 여기서는 규칙 저장소부터 {@code AlertService.triggerAlert} 까지의 배선을 본다.</p>
 */
class AlertIntegrationServiceTest {

    private AlertRuleRepository ruleRepository;
    private AlertService alertService;
    private AlertIntegrationService service;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(AlertRuleRepository.class);
        alertService = mock(AlertService.class);
        service = new AlertIntegrationService(ruleRepository, alertService);
    }

    private AlertRule rule(long id, String type, String target, String cond, double threshold) {
        AlertRule r = new AlertRule();
        r.setId(id);
        r.setUserId("u1");
        r.setType(type);
        r.setTarget(target);
        r.setConditionType(cond);
        r.setThreshold(threshold);
        r.setEnabled(true);
        return r;
    }

    /** 지금이 반드시 <b>바깥</b>인 시간창. 실행 시각과 무관하게 성립한다. */
    private void setWindowExcludingNow(AlertRule r) {
        LocalTime now = ZonedDateTime.now(AlertConditions.ALERT_ZONE).toLocalTime();
        r.setActiveFrom(hhmm(now.plusHours(2)));
        r.setActiveTo(hhmm(now.plusHours(3)));
    }

    /** 지금이 반드시 <b>안</b>인 시간창. */
    private void setWindowIncludingNow(AlertRule r) {
        LocalTime now = ZonedDateTime.now(AlertConditions.ALERT_ZONE).toLocalTime();
        r.setActiveFrom(hhmm(now.minusHours(1)));
        r.setActiveTo(hhmm(now.plusHours(1)));
    }

    private String hhmm(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private ExchangeRateResponse rates(String base, Map<String, Double> map) {
        return new ExchangeRateResponse(base, Instant.now(), map);
    }

    // ==================== 환율 알림 ====================

    @Test
    void 환율이_임계값을_넘으면_알림이_나간다() {
        AlertRule r = rule(1, "exchange_rate", "USD/KRW", "above", 1400.0);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        service.checkExchangeRateAlerts(rates("USD", Map.of("KRW", 1450.0)));

        ArgumentCaptor<AlertEventDto> captor = ArgumentCaptor.forClass(AlertEventDto.class);
        verify(alertService).triggerAlert(eqUser(), captor.capture());
        AlertEventDto event = captor.getValue();
        assertEquals("exchange_rate", event.type());
        assertEquals("USD/KRW", event.target());
        assertEquals(1450.0, event.currentValue());
        assertTrue(event.message().contains("환율"), "메시지: " + event.message());
    }

    @Test
    void 환율이_임계값_아래면_알림이_없다() {
        AlertRule r = rule(1, "exchange_rate", "USD/KRW", "above", 1400.0);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        service.checkExchangeRateAlerts(rates("USD", Map.of("KRW", 1350.0)));

        verify(alertService, never()).triggerAlert(anyString(), any());
    }

    @Test
    void 통화코드만_적으면_응답의_기준통화를_쓴다() {
        AlertRule r = rule(1, "exchange_rate", "JPY", "below", 10.0);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        service.checkExchangeRateAlerts(rates("USD", Map.of("JPY", 9.0)));

        verify(alertService).triggerAlert(anyString(), any());
    }

    @Test
    void 기준통화가_다르면_환산하지_않고_건너뛴다() {
        // 임의로 환산하면 지어낸 숫자로 알림을 보내게 된다.
        AlertRule r = rule(1, "exchange_rate", "EUR/KRW", "above", 1.0);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        service.checkExchangeRateAlerts(rates("USD", Map.of("KRW", 1450.0)));

        verify(alertService, never()).triggerAlert(anyString(), any());
    }

    @Test
    void 응답에_그_통화가_없으면_건너뛴다() {
        AlertRule r = rule(1, "exchange_rate", "USD/CHF", "above", 1.0);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        service.checkExchangeRateAlerts(rates("USD", Map.of("KRW", 1450.0)));

        verify(alertService, never()).triggerAlert(anyString(), any());
    }

    @Test
    void 통화쌍_표기를_읽는다() {
        assertEquals("USD", AlertIntegrationService.splitCurrencyPair("usd/krw", "EUR")[0]);
        assertEquals("KRW", AlertIntegrationService.splitCurrencyPair("usd/krw", "EUR")[1]);
        assertEquals("EUR", AlertIntegrationService.splitCurrencyPair("krw", "EUR")[0]);
        org.junit.jupiter.api.Assertions.assertNull(AlertIntegrationService.splitCurrencyPair("", "EUR"));
        org.junit.jupiter.api.Assertions.assertNull(AlertIntegrationService.splitCurrencyPair("/KRW", "EUR"));
    }

    // ==================== 미세먼지 알림 ====================

    @Test
    void 미세먼지_통합등급이_나쁨_이상이면_알림이_나간다() {
        AlertRule r = rule(2, AlertIntegrationService.TYPE_AIR_GRADE, "서울", "at_least", 3.0);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        // pm10 160 → AQI 128 → 통합 "나쁨"
        service.checkAirQualityAlerts(AirQualityResponse.of("서울", 160, 40, Instant.now()));

        ArgumentCaptor<AlertEventDto> captor = ArgumentCaptor.forClass(AlertEventDto.class);
        verify(alertService).triggerAlert(eqUser(), captor.capture());
        assertTrue(captor.getValue().message().contains("나쁨"),
                "메시지: " + captor.getValue().message());
    }

    @Test
    void 미세먼지가_보통이면_알림이_없다() {
        AlertRule r = rule(2, AlertIntegrationService.TYPE_AIR_GRADE, "서울", "at_least", 3.0);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        // pm10 80 → AQI 64 → 통합 "보통"
        service.checkAirQualityAlerts(AirQualityResponse.of("서울", 80, 25, Instant.now()));

        verify(alertService, never()).triggerAlert(anyString(), any());
    }

    /**
     * 🔴 통합 등급과 항목별 등급은 같지 않다 — PM10 120 은 PM10 등급 "나쁨" 이지만
     * 통합 등급은 "보통"(AQI 96)이다. 이 차이를 모르면 "나쁨 알림을 켰는데 안 온다" 가 된다.
     * 항목별로 보고 싶으면 {@code air_pm10_grade} 를 쓴다.
     */
    @Test
    void PM10_등급과_통합_등급은_다르게_판정된다() {
        AlertRule overall = rule(2, AlertIntegrationService.TYPE_AIR_GRADE, "서울", "at_least", 3.0);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(overall));
        service.checkAirQualityAlerts(AirQualityResponse.of("서울", 120, 30, Instant.now()));
        verify(alertService, never()).triggerAlert(anyString(), any());

        AlertRule pm10Grade = rule(4, "air_pm10_grade", "서울", "at_least", 3.0);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(pm10Grade));
        service.checkAirQualityAlerts(AirQualityResponse.of("서울", 120, 30, Instant.now()));

        ArgumentCaptor<AlertEventDto> captor = ArgumentCaptor.forClass(AlertEventDto.class);
        verify(alertService).triggerAlert(eqUser(), captor.capture());
        assertTrue(captor.getValue().message().contains("PM10 등급 나쁨"),
                "메시지: " + captor.getValue().message());
    }

    @Test
    void 대기질을_못_가져오면_알림을_보내지_않는다() {
        // unavailable 은 "알수없음" 이다. 0 으로 읽어 "좋음" 알림을 보내면 지어내는 것이다.
        AlertRule grade = rule(2, AlertIntegrationService.TYPE_AIR_GRADE, "서울", "at_least", 3.0);
        AlertRule pm10 = rule(3, "air_pm10", "서울", "below", 500.0);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(grade, pm10));

        service.checkAirQualityAlerts(AirQualityResponse.unavailable("서울"));

        verify(alertService, never()).triggerAlert(anyString(), any());
    }

    @Test
    void PM10_수치_규칙도_동작한다() {
        AlertRule r = rule(3, "air_pm10", "서울", "above", 80.0);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        service.checkAirQualityAlerts(AirQualityResponse.of("서울", 120, 30, Instant.now()));

        ArgumentCaptor<AlertEventDto> captor = ArgumentCaptor.forClass(AlertEventDto.class);
        verify(alertService).triggerAlert(eqUser(), captor.capture());
        assertEquals(120.0, captor.getValue().currentValue());
    }

    @Test
    void 다른_지역_규칙은_반응하지_않는다() {
        AlertRule r = rule(2, AlertIntegrationService.TYPE_AIR_GRADE, "부산", "at_least", 1.0);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        service.checkAirQualityAlerts(AirQualityResponse.of("서울", 120, 40, Instant.now()));

        verify(alertService, never()).triggerAlert(anyString(), any());
    }

    @Test
    void 등급_문자열을_순위로_바꾼다() {
        assertEquals(1.0, AlertIntegrationService.gradeRank("좋음"));
        assertEquals(3.0, AlertIntegrationService.gradeRank("나쁨"));
        assertEquals(4.0, AlertIntegrationService.gradeRank("매우나쁨"));
        org.junit.jupiter.api.Assertions.assertNull(AlertIntegrationService.gradeRank("알수없음"));
        org.junit.jupiter.api.Assertions.assertNull(AlertIntegrationService.gradeRank(null));
    }

    // ==================== 시간대 조건 ====================

    @Test
    void 시간창_밖이면_임계값을_넘어도_알림이_없다() {
        AlertRule r = rule(1, "exchange_rate", "USD/KRW", "above", 1400.0);
        setWindowExcludingNow(r);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        service.checkExchangeRateAlerts(rates("USD", Map.of("KRW", 1450.0)));

        verify(alertService, never()).triggerAlert(anyString(), any());
    }

    @Test
    void 시간창_안이면_알림이_나간다() {
        AlertRule r = rule(1, "exchange_rate", "USD/KRW", "above", 1400.0);
        setWindowIncludingNow(r);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        service.checkExchangeRateAlerts(rates("USD", Map.of("KRW", 1450.0)));

        verify(alertService).triggerAlert(anyString(), any());
    }

    // ==================== 채널 ====================

    @Test
    void 규칙의_채널이_이벤트에_실린다() {
        AlertRule r = rule(1, "exchange_rate", "USD/KRW", "above", 1400.0);
        r.setChannels("browser");
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        service.checkExchangeRateAlerts(rates("USD", Map.of("KRW", 1450.0)));

        ArgumentCaptor<AlertEventDto> captor = ArgumentCaptor.forClass(AlertEventDto.class);
        verify(alertService).triggerAlert(anyString(), captor.capture());
        assertEquals(List.of("sse", "browser"), captor.getValue().channels());
    }

    @Test
    void 채널을_안_정한_규칙은_SSE_만_실린다() {
        AlertRule r = rule(1, "exchange_rate", "USD/KRW", "above", 1400.0);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        service.checkExchangeRateAlerts(rates("USD", Map.of("KRW", 1450.0)));

        ArgumentCaptor<AlertEventDto> captor = ArgumentCaptor.forClass(AlertEventDto.class);
        verify(alertService).triggerAlert(anyString(), captor.capture());
        assertEquals(List.of("sse"), captor.getValue().channels());
    }

    // ==================== 쿨다운 ====================

    @Test
    void 쿨다운_안에서는_같은_규칙이_다시_안_울린다() {
        AlertRule r = rule(1, "exchange_rate", "USD/KRW", "above", 1400.0);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        service.checkExchangeRateAlerts(rates("USD", Map.of("KRW", 1450.0)));
        service.checkExchangeRateAlerts(rates("USD", Map.of("KRW", 1460.0)));
        service.checkExchangeRateAlerts(rates("USD", Map.of("KRW", 1470.0)));

        verify(alertService, times(1)).triggerAlert(anyString(), any());
    }

    private String eqUser() {
        return org.mockito.ArgumentMatchers.eq("u1");
    }
}
