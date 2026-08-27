package org.springframework.web.servlet.config.annotation;

import org.springframework.web.cors.CorsConfiguration;

import java.util.Map;

/**
 * 테스트 전용: {@link CorsRegistry#getCorsConfigurations()} 가 protected 라서
 * 같은 패키지에서 공개 접근자로 노출하기 위한 서브클래스.
 */
public class TestCorsRegistry extends CorsRegistry {
    public Map<String, CorsConfiguration> exposeConfigurations() {
        return getCorsConfigurations();
    }
}
