package com.example.myapi.filter;

import com.example.myapi.entity.UserProfile;
import com.example.myapi.repository.UserProfileRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 사용자 식별 필터
 * X-User-Id 헤더를 통해 사용자 식별
 * 헤더가 없으면 새 UUID 생성하여 응답 헤더에 포함
 */
@Component
@Order(1)
public class UserIdentificationFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(UserIdentificationFilter.class);
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ID_ATTRIBUTE = "userId";

    private final UserProfileRepository userProfileRepository;

    public UserIdentificationFilter(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 정적 리소스는 스킵
        String path = httpRequest.getRequestURI();
        if (isStaticResource(path)) {
            chain.doFilter(request, response);
            return;
        }

        String userId = httpRequest.getHeader(USER_ID_HEADER);
        boolean isNewUser = false;

        if (userId == null || userId.isBlank()) {
            userId = UUID.randomUUID().toString();
            isNewUser = true;
            log.debug("New user created: {}", userId);
        }

        // 사용자 프로필 조회 또는 생성 (에러 발생 시 무시하고 계속 진행)
        // DB 작업을 최소화하고, 에러가 발생해도 요청 처리는 계속
        try {
            final String finalUserId = userId;
            
            // 새 사용자인 경우에만 프로필 생성 시도
            if (isNewUser) {
                try {
                    UserProfile existing = userProfileRepository.findByUserId(userId).orElse(null);
                    if (existing == null) {
                        UserProfile newProfile = new UserProfile(finalUserId);
                        userProfileRepository.save(newProfile);
                    }
                } catch (org.springframework.dao.CannotAcquireLockException e) {
                    // SQLite BUSY 에러는 무시 (나중에 재시도됨)
                } catch (Exception e) {
                    // 기타 DB 에러도 무시
                }
            } else {
                // 기존 사용자의 경우, 마지막 활동 시간 업데이트는 10분 이상 지났을 때만 시도
                try {
                    UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
                    if (profile != null && profile.getLastActive() != null) {
                        long minutesSinceLastActive = java.time.Duration.between(
                                profile.getLastActive(), java.time.Instant.now()).toMinutes();
                        if (minutesSinceLastActive >= 10) {
                            profile.updateLastActive();
                            userProfileRepository.save(profile);
                        }
                    }
                } catch (org.springframework.dao.CannotAcquireLockException e) {
                    // SQLite BUSY 에러는 무시
                } catch (Exception e) {
                    // 기타 DB 에러도 무시
                }
            }
        } catch (Exception e) {
            // 모든 DB 에러는 무시하고 요청 처리는 계속
        }

        // 요청 속성에 userId 저장
        httpRequest.setAttribute(USER_ID_ATTRIBUTE, userId);

        // 응답 헤더에 userId 포함
        httpResponse.setHeader(USER_ID_HEADER, userId);

        chain.doFilter(request, response);
    }

    private boolean isStaticResource(String path) {
        // 정적 파일 확장자
        if (path.endsWith(".html") ||
            path.endsWith(".css") ||
            path.endsWith(".js") ||
            path.endsWith(".png") ||
            path.endsWith(".jpg") ||
            path.endsWith(".ico") ||
            path.endsWith(".woff") ||
            path.endsWith(".woff2") ||
            path.endsWith(".json") ||
            path.endsWith(".map") ||
            path.endsWith(".svg") ||
            path.equals("/")) {
            return true;
        }
        
        // 브라우저/DevTools 특수 경로
        if (path.startsWith("/.well-known") ||
            path.startsWith("/favicon") ||
            path.startsWith("/robots.txt") ||
            path.startsWith("/sitemap")) {
            return true;
        }
        
        return false;
    }
}
