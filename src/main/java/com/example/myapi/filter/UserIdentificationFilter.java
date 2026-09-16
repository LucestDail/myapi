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
            
            // 🔴 **자동 생성한 익명 ID 는 저장하지 않는다**(2026-09-16).
            //
            // 종전에는 헤더가 없으면 `UUID.randomUUID()` 로 새 id 를 만들고 그것을
            // **프로필 테이블에 넣었다.** 그런데 그 id 는 응답 헤더로만 돌려줄 뿐이라,
            // 클라이언트가 그것을 들고 다시 오지 않으면 **한 번 쓰고 버려진다.**
            //
            // 실측(2026-09-16, `.25` 운영 DB):
            //   user_profiles  **7,361,599 행** · 고유 user_id **7,361,613개**
            //   dashboard.db   **772.8MB** (빈 페이지 0 — 전부 실사용)
            //   대조군: user_settings 5행 · todos 0행 · system_history 10,921행
            //
            // 즉 **개인용 대시보드 하나에 사용자 736만 명**이 쌓여 있었다. 비용은 둘이다:
            //   ① 무한 증가 — 헤더 없는 요청이 올 때마다 한 행씩
            //   ② 🔴 **가장 뜨거운 경로에 쓰기가 붙는다** — 커넥션 풀이 **1**이라
            //      모든 DB 접근이 직렬화되는데(README 「동시성 한계」 참고),
            //      `/api/system/status`(로그 기준 46,734회)가 헤더 없이 오면
            //      매번 이 INSERT 가 그 한 줄에 선다.
            //
            // ⇒ **클라이언트가 자기 id 를 들고 왔을 때만 저장한다.** 생성한 id 는
            //    응답 헤더로 계속 돌려주므로, 클라이언트가 그것을 채택하면 다음
            //    요청부터는 `isNewUser == false` 로 들어와 정상적으로 기록된다.
            if (isNewUser) {
                // 저장하지 않는다. (기록 자체가 필요해지면 "클라이언트가 한 번이라도
                // 재사용한 id" 만 남기는 방식으로 다시 설계할 것 — 요청마다 새 행을
                // 만드는 방식으로는 돌아가지 않는다.)
                log.debug("익명 요청 — 프로필을 만들지 않는다: {}", finalUserId);
            } else {
                // 기존 사용자의 경우, 마지막 활동 시간 업데이트는 10분 이상 지났을 때만 시도
                try {
                    UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);

                    // 🔴 클라이언트가 **자기 id 를 들고 왔는데 프로필이 없으면 만든다**.
                    //
                    // 종전에는 이 분기가 *기존 프로필 갱신* 만 했다. 그래서 위에서 익명
                    // 저장을 끄고 나니 **어떤 경로로도 프로필이 생기지 않는 상태**가 됐다 —
                    // 내가 위 주석에 "채택하면 다음 요청부터 정상 기록된다" 고 적어 놓고
                    // 실제로는 안 되는, 딱 그 모양이었다(2026-09-16 검증에서 잡았다).
                    //
                    // ⚠️ 익명과 달리 여기는 **무한 증가하지 않는다** — id 를 만들어 내는
                    //    쪽이 클라이언트이고, 같은 id 로 다시 오면 이 분기는 갱신만 한다.
                    if (profile == null) {
                        userProfileRepository.save(new UserProfile(finalUserId));
                    } else if (profile.getLastActive() != null) {
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
