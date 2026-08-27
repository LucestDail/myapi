package com.example.myapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.TestCorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 중앙 CORS 설정 검증.
 *
 * <p>실제 {@link CorsRegistry} 에 매핑을 등록시킨 뒤 결과 {@link CorsConfiguration} 을
 * 꺼내 허용 origin/메서드와 origin 허용/차단 로직을 직접 검증한다(전체 컨텍스트 불필요).</p>
 */
class CorsConfigTest {

    private CorsConfiguration configureAndGet() {
        MyapiSecurityProperties props = new MyapiSecurityProperties();
        CorsConfig cfg = new CorsConfig(props);
        TestCorsRegistry registry = new TestCorsRegistry();
        cfg.addCorsMappings(registry);
        Map<String, CorsConfiguration> map = registry.exposeConfigurations();
        // /api/** 매핑이 존재해야 한다 (SSE 스트림 포함 모든 /api 하위 커버)
        CorsConfiguration api = map.get("/api/**");
        assertNotNull(api, "/api/** CORS 매핑이 등록되어야 한다");
        return api;
    }

    @Test
    void api_전체에_매핑이_적용된다() {
        CorsConfiguration api = configureAndGet();
        assertTrue(api.getAllowedMethods().containsAll(
                java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")));
        assertEquals(Boolean.TRUE, api.getAllowCredentials());
    }

    @Test
    void 홈랩_origin은_허용된다() {
        CorsConfiguration api = configureAndGet();
        assertEquals("http://192.168.11.25", api.checkOrigin("http://192.168.11.25"));
        assertEquals("https://oshhome.duckdns.org:8030",
                api.checkOrigin("https://oshhome.duckdns.org:8030"));
        assertEquals("http://oshhome.duckdns.org:8030",
                api.checkOrigin("http://oshhome.duckdns.org:8030"));
        assertEquals("http://180.70.85.99:8030",
                api.checkOrigin("http://180.70.85.99:8030"));
    }

    @Test
    void 로컬호스트는_임의포트_허용된다() {
        CorsConfiguration api = configureAndGet();
        assertEquals("http://localhost:5173", api.checkOrigin("http://localhost:5173"));
        assertEquals("http://localhost:8080", api.checkOrigin("http://localhost:8080"));
        assertEquals("http://127.0.0.1:3000", api.checkOrigin("http://127.0.0.1:3000"));
    }

    @Test
    void 임의_외부_origin은_차단된다() {
        CorsConfiguration api = configureAndGet();
        // 와일드카드 제거 확인: 허용목록에 없는 origin은 null(차단)
        assertNull(api.checkOrigin("https://evil.example.com"));
        assertNull(api.checkOrigin("http://attacker.io"));
        // 다른 스킴/포트도 정확히 일치해야 함
        assertNull(api.checkOrigin("http://oshhome.duckdns.org:9999"));
    }

    @Test
    void env_오버라이드가_반영된다() {
        MyapiSecurityProperties props = new MyapiSecurityProperties();
        props.getCors().setAllowedOrigins(new java.util.ArrayList<>(
                java.util.List.of("https://custom.example.com")));
        CorsConfig cfg = new CorsConfig(props);
        TestCorsRegistry registry = new TestCorsRegistry();
        cfg.addCorsMappings(registry);
        CorsConfiguration api = registry.exposeConfigurations().get("/api/**");
        assertNotNull(api);
        assertEquals("https://custom.example.com",
                api.checkOrigin("https://custom.example.com"));
        // 오버라이드로 기본 홈랩 origin은 더 이상 허용되지 않음
        assertNull(api.checkOrigin("http://192.168.11.25"));
    }
}
