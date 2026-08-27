package com.example.myapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * myapi 자체 보안 설정.
 *
 * <p>{@code myapi.api-key} 가 설정되면 {@link com.example.myapi.filter.ApiKeyRestFilter}
 * 가 {@code /api/*} 요청에 대해 헤더 {@code X-Myapi-Key} 를 검증한다. 비어 있으면(기본값)
 * 검증을 생략해 기존 소비자(내장 대시보드·HARU life_info 도구 등)에 영향이 없다(fail-open).</p>
 */
@ConfigurationProperties(prefix = "myapi")
public class MyapiSecurityProperties {

    /** REST /api/* 보호에 쓰는 API 키. 비어 있으면 검증 비활성(전체 통과). */
    private String apiKey = "";

    /** CORS 설정(허용 origin 목록 등). {@link com.example.myapi.config.CorsConfig} 에서 사용. */
    private final Cors cors = new Cors();

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public Cors getCors() {
        return cors;
    }

    /**
     * CORS 세부 설정.
     *
     * <p>기본 {@code allowedOrigins} 는 홈랩 생태계 origin(개발 localhost + .25 LAN +
     * 외부 도메인/공인IP:8030)으로 좁혀져 있으며, env {@code MYAPI_CORS_ALLOWED_ORIGINS}
     * (콤마 구분)로 오버라이드/확장할 수 있다.</p>
     */
    public static class Cors {

        /** 교차 출처 허용 origin 목록. 콤마 구분 env {@code MYAPI_CORS_ALLOWED_ORIGINS} 로 오버라이드. */
        private java.util.List<String> allowedOrigins = new java.util.ArrayList<>(java.util.List.of(
                // 개발 (localhost / 127.0.0.1 — 임의 포트 허용 위해 패턴 사용)
                "http://localhost:*",
                "http://127.0.0.1:*",
                // 홈랩 LAN 메인 서버 (.25)
                "http://192.168.11.25",
                // 외부 노출 (공인IP / DuckDNS 도메인, SKB가 80/443 차단 → :8030)
                "http://180.70.85.99:8030",
                "http://oshhome.duckdns.org:8030",
                "https://oshhome.duckdns.org:8030"
        ));

        public java.util.List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(java.util.List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    /** 키가 설정되어 있으면 검증 활성. */
    public boolean isAuthEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 상수시간 비교로 제공 키가 설정 키와 일치하는지 검사. */
    public boolean isValidKey(String provided) {
        if (!isAuthEnabled() || provided == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
