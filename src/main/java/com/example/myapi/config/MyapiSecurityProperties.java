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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
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
