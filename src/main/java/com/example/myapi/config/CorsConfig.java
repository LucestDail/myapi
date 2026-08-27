package com.example.myapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 중앙 CORS 설정.
 *
 * <p>기존에 각 컨트롤러에 흩어져 있던 {@code @CrossOrigin(origins = "*")} (와일드카드,
 * 보안 취약)를 제거하고 이곳에서 {@code /api/**} 전체에 대해 일괄 적용한다.
 * SSE 스트리밍 엔드포인트(/api/dashboard/stream, /api/social/*​/stream, /api/alerts/stream)도
 * 동일하게 {@code /api/**} 매핑으로 커버된다.</p>
 *
 * <p>허용 origin 은 {@link MyapiSecurityProperties.Cors#getAllowedOrigins()} (기본=홈랩 생태계
 * origin, env {@code MYAPI_CORS_ALLOWED_ORIGINS} 로 오버라이드)에서 가져온다. 포트 와일드카드
 * (예: {@code http://localhost:*})와 {@code allowCredentials(true)} 를 함께 쓰기 위해
 * {@code allowedOriginPatterns} 를 사용한다.</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final MyapiSecurityProperties props;

    public CorsConfig(MyapiSecurityProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        props.getCors().getAllowedOrigins().toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
