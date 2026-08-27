package com.example.myapi.filter;

import com.example.myapi.config.MyapiSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * REST(/api/*) API 키 검증 필터(옵션형).
 *
 * <ul>
 *   <li>{@code myapi.api-key} 가 비어 있으면 검증 생략 — 기존 클라이언트(내장 대시보드·HARU
 *       life_info 도구 등) 무영향(fail-open). .25 는 키를 비운 채 배포한다.</li>
 *   <li>키가 설정되면 {@code /api/*} 요청에 헤더 {@code X-Myapi-Key} 를 요구한다.</li>
 *   <li>정적 리소스·헬스체크(/actuator/*)·루트(/)는 보호 대상이 아니다.</li>
 * </ul>
 *
 * <p>{@link HttpServletRequest#getServletPath()} 는 context-path(/myapi) 가 제거된 경로라
 * 로컬(context-path 없음)·프록시(/myapi) 배포 모두 동일하게 {@code /api/...} 로 매칭된다.</p>
 */
@Component
@Order(0) // UserIdentificationFilter(@Order(1)) 보다 먼저: 미인증 요청은 UUID 발급 전에 차단
public class ApiKeyRestFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-Myapi-Key";

    private final MyapiSecurityProperties props;

    public ApiKeyRestFilter(MyapiSecurityProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getServletPath();
        boolean protectedPath = path != null && path.startsWith("/api/");
        if (!props.isAuthEnabled() || !protectedPath) {
            chain.doFilter(req, res);
            return;
        }
        String provided = req.getHeader(API_KEY_HEADER);
        if (provided == null || provided.isBlank()) {
            provided = req.getParameter("apiKey");
        }
        if (props.isValidKey(provided)) {
            chain.doFilter(req, res);
            return;
        }
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":\"unauthorized\"}");
    }
}
