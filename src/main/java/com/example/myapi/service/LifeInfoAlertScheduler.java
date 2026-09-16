package com.example.myapi.service;

import com.example.myapi.dto.info.AirQualityResponse;
import com.example.myapi.dto.info.ExchangeRateResponse;
import com.example.myapi.entity.AlertRule;
import com.example.myapi.repository.AlertRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 환율·미세먼지 알림 주기 검사.
 *
 * <p>주식·날씨·시스템 알림은 대시보드 <b>요청</b>이 들어올 때 곁다리로 검사된다
 * ({@code DashboardController}). 환율·미세먼지는 대시보드 스트림에 실려 오지 않아 같은 자리에
 * 얹을 수 없고, 그렇다고 요청 경로에서 외부 API 를 또 부르면 응답이 그만큼 느려진다.
 * 그래서 주기 작업으로 뺐다.</p>
 *
 * <p>🔴 <b>규칙이 하나도 없으면 외부 호출도 DB 쓰기도 하지 않는다.</b> 이 저장소는 SQLite 이고
 * 커넥션 풀이 1이라 모든 DB 접근이 직렬화된다 — 아무도 안 쓰는 기능이 10분마다 공공 API 를
 * 두드리고 결과를 쓰는 일이 없어야 한다. 활성 규칙 조회(읽기 한 번)가 이 작업의 바닥 비용이고,
 * 쓰기는 <b>실제로 알림이 발동할 때만</b> 일어난다.</p>
 */
@Service
public class LifeInfoAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(LifeInfoAlertScheduler.class);

    /** 규칙이 target 을 비워 둔 경우의 기본 지역. {@code LifeInfoService} 의 기본값과 맞춘다. */
    static final String DEFAULT_REGION = "서울";

    /** 규칙이 통화쌍을 안 밝힌 경우의 기준 통화. */
    static final String DEFAULT_BASE = "USD";

    private final AlertRuleRepository alertRuleRepository;
    private final LifeInfoService lifeInfoService;
    private final AlertIntegrationService alertIntegrationService;

    @Value("${myapi.alerts.lifeinfo.enabled:true}")
    private boolean enabled = true;

    public LifeInfoAlertScheduler(AlertRuleRepository alertRuleRepository,
                                  LifeInfoService lifeInfoService,
                                  AlertIntegrationService alertIntegrationService) {
        this.alertRuleRepository = alertRuleRepository;
        this.lifeInfoService = lifeInfoService;
        this.alertIntegrationService = alertIntegrationService;
    }

    /**
     * 기본 10분. 환율·대기질은 분 단위로 요동치지 않고, 쿨다운이 1분이라 더 자주 볼 이유가 없다.
     */
    @Scheduled(
            initialDelayString = "${myapi.alerts.lifeinfo.initial-delay-ms:60000}",
            fixedRateString = "${myapi.alerts.lifeinfo.interval-ms:600000}")
    public void checkLifeInfoAlerts() {
        if (!enabled) {
            log.debug("[alert] 생활정보 알림 검사가 꺼져 있음(myapi.alerts.lifeinfo.enabled=false)");
            return;
        }
        try {
            List<AlertRule> rules = alertRuleRepository.findByEnabledTrue();
            List<AlertRule> exchangeRules = rules.stream()
                    .filter(r -> AlertIntegrationService.TYPE_EXCHANGE_RATE.equals(r.getType()))
                    .toList();
            List<AlertRule> airRules = rules.stream()
                    .filter(r -> r.getType() != null && r.getType().startsWith("air_"))
                    .toList();

            if (exchangeRules.isEmpty() && airRules.isEmpty()) {
                // "검사 안 함" 과 "통과" 를 구분해 남긴다.
                log.debug("[alert] 환율·미세먼지 규칙이 없어 외부 조회를 생략함");
                return;
            }

            checkExchangeRules(exchangeRules);
            checkAirRules(airRules);

            log.info("[alert] 생활정보 알림 검사 완료 — 환율 규칙 {}건, 대기질 규칙 {}건",
                    exchangeRules.size(), airRules.size());
        } catch (Exception e) {
            // 주기 작업이 예외로 죽으면 다음 주기가 안 돈다. 삼키되 조용하지는 않게.
            log.warn("[alert] 생활정보 알림 검사 실패: {}", e.getMessage(), e);
        }
    }

    private void checkExchangeRules(List<AlertRule> rules) {
        for (String base : basesOf(rules)) {
            ExchangeRateResponse rates = lifeInfoService.getExchangeRates(base);
            if (rates == null) {
                log.warn("[alert] 환율({}) 을 가져오지 못해 이번 회차 검사를 건너뜀", base);
                continue;
            }
            alertIntegrationService.checkExchangeRateAlerts(rates);
        }
    }

    private void checkAirRules(List<AlertRule> rules) {
        for (String region : regionsOf(rules)) {
            AirQualityResponse air = lifeInfoService.getAirQuality(region);
            if (air == null) {
                log.warn("[alert] 대기질({}) 을 가져오지 못해 이번 회차 검사를 건너뜀", region);
                continue;
            }
            alertIntegrationService.checkAirQualityAlerts(air);
        }
    }

    /** 규칙들이 요구하는 기준 통화 집합. 같은 base 를 여러 규칙이 써도 외부 호출은 한 번. */
    static Set<String> basesOf(List<AlertRule> rules) {
        Set<String> bases = new LinkedHashSet<>();
        for (AlertRule rule : rules) {
            String[] pair = AlertIntegrationService.splitCurrencyPair(rule.getTarget(), DEFAULT_BASE);
            bases.add(pair != null ? pair[0] : DEFAULT_BASE);
        }
        return bases;
    }

    /** 규칙들이 요구하는 지역 집합. */
    static Set<String> regionsOf(List<AlertRule> rules) {
        Set<String> regions = new LinkedHashSet<>();
        for (AlertRule rule : rules) {
            String target = rule.getTarget();
            regions.add(target == null || target.isBlank() ? DEFAULT_REGION : target.trim());
        }
        return regions;
    }
}
