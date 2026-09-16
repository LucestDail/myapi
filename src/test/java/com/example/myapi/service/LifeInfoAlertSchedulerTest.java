package com.example.myapi.service;

import com.example.myapi.dto.info.AirQualityResponse;
import com.example.myapi.dto.info.ExchangeRateResponse;
import com.example.myapi.entity.AlertRule;
import com.example.myapi.repository.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 주기 검사의 <b>바닥 비용</b>을 못박는다.
 *
 * <p>이 저장소는 SQLite 이고 커넥션 풀이 1이라 모든 DB 접근이 직렬화된다. 아무도 안 쓰는
 * 기능이 10분마다 외부 API 를 두드리고 결과를 쓰면 그대로 다른 요청의 지연이 된다.</p>
 */
class LifeInfoAlertSchedulerTest {

    private AlertRuleRepository ruleRepository;
    private LifeInfoService lifeInfoService;
    private AlertIntegrationService integrationService;
    private LifeInfoAlertScheduler scheduler;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(AlertRuleRepository.class);
        lifeInfoService = mock(LifeInfoService.class);
        integrationService = mock(AlertIntegrationService.class);
        scheduler = new LifeInfoAlertScheduler(ruleRepository, lifeInfoService, integrationService);
    }

    private AlertRule rule(String type, String target) {
        AlertRule r = new AlertRule();
        r.setUserId("u1");
        r.setType(type);
        r.setTarget(target);
        r.setConditionType("above");
        r.setThreshold(1.0);
        r.setEnabled(true);
        return r;
    }

    @Test
    void 규칙이_없으면_외부_API_를_한_번도_부르지_않는다() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of());

        scheduler.checkLifeInfoAlerts();

        verifyNoInteractions(lifeInfoService);
        verifyNoInteractions(integrationService);
    }

    @Test
    void 다른_종류의_규칙만_있어도_외부_API_를_안_부른다() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule("stock_price", "SPY")));

        scheduler.checkLifeInfoAlerts();

        verifyNoInteractions(lifeInfoService);
    }

    @Test
    void 꺼_두면_규칙이_있어도_돌지_않는다() {
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.checkLifeInfoAlerts();

        verifyNoInteractions(ruleRepository);
        verifyNoInteractions(lifeInfoService);
    }

    @Test
    void 환율_규칙이_있으면_조회해_검사로_넘긴다() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule("exchange_rate", "USD/KRW")));
        ExchangeRateResponse rates = new ExchangeRateResponse("USD", Instant.now(), Map.of("KRW", 1400.0));
        when(lifeInfoService.getExchangeRates("USD")).thenReturn(rates);

        scheduler.checkLifeInfoAlerts();

        verify(lifeInfoService).getExchangeRates("USD");
        verify(integrationService).checkExchangeRateAlerts(rates);
        verify(lifeInfoService, never()).getAirQuality(anyString());
    }

    @Test
    void 같은_기준통화를_쓰는_규칙이_여럿이면_조회는_한_번이다() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(
                rule("exchange_rate", "USD/KRW"),
                rule("exchange_rate", "USD/JPY"),
                rule("exchange_rate", "JPY"),       // 기준통화를 안 밝힘 → 기본 USD
                rule("exchange_rate", "EUR/KRW")));

        when(lifeInfoService.getExchangeRates(anyString()))
                .thenReturn(new ExchangeRateResponse("USD", Instant.now(), Map.of("KRW", 1400.0)));

        scheduler.checkLifeInfoAlerts();

        verify(lifeInfoService, times(1)).getExchangeRates("USD");
        verify(lifeInfoService, times(1)).getExchangeRates("EUR");
    }

    @Test
    void 대기질_규칙의_지역별로_한_번씩_조회한다() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(
                rule("air_grade", "서울"),
                rule("air_pm10", "서울"),
                rule("air_pm25", "부산")));
        when(lifeInfoService.getAirQuality(anyString()))
                .thenReturn(AirQualityResponse.of("서울", 30, 10, Instant.now()));

        scheduler.checkLifeInfoAlerts();

        verify(lifeInfoService, times(1)).getAirQuality("서울");
        verify(lifeInfoService, times(1)).getAirQuality("부산");
        verify(integrationService, times(2)).checkAirQualityAlerts(any());
    }

    @Test
    void 조회에_실패해도_다음_회차를_위해_예외가_새지_않는다() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule("air_grade", "서울")));
        when(lifeInfoService.getAirQuality("서울")).thenThrow(new RuntimeException("포털 응답 없음"));

        scheduler.checkLifeInfoAlerts(); // 던지면 테스트 실패

        verify(integrationService, never()).checkAirQualityAlerts(any());
    }

    @Test
    void 기준통화를_안_밝힌_규칙은_기본_통화에_묶인다() {
        // "KRW" 만 적으면 "USD/KRW" 로 본다. 조회를 통화 수만큼 늘리지 않는다.
        assertEquals(Set.of("USD"), LifeInfoAlertScheduler.basesOf(List.of(
                rule("exchange_rate", "KRW"),
                rule("exchange_rate", "JPY"))));
    }

    @Test
    void target_이_비면_기본_지역과_기본_기준통화를_쓴다() {
        assertEquals(Set.of(LifeInfoAlertScheduler.DEFAULT_REGION),
                LifeInfoAlertScheduler.regionsOf(List.of(rule("air_grade", null))));
        assertEquals(Set.of(LifeInfoAlertScheduler.DEFAULT_BASE),
                LifeInfoAlertScheduler.basesOf(List.of(rule("exchange_rate", null))));
    }
}
