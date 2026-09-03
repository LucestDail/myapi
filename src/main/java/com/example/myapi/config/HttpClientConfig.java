package com.example.myapi.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class HttpClientConfig {

    /**
     * 외부 API 호출용 공용 RestTemplate.
     *
     * <p>⚠️ 타임아웃이 없으면 외부 API가 응답하지 않을 때 요청 스레드가 무한 대기한다.
     * 실제로 공공데이터포털이 느려졌을 때 대기질 조회가 10초 넘게 매달려 호출자(HARU)가
     * 먼저 끊는 일이 있었다(2026-09-03). 외부 의존은 반드시 상한을 둔다.</p>
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(8))
                .build();
    }
}
