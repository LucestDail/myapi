package com.example.myapi.filter;

import com.example.myapi.entity.UserProfile;
import com.example.myapi.repository.UserProfileRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserIdentificationFilter 검증. 리포지토리 목으로 DB 없이
 * UUID 발급/헤더 에코/정적리소스 스킵/DB예외 무해화 를 확인한다.
 */
class UserIdentificationFilterTest {

    private UserProfileRepository repo;
    private UserIdentificationFilter filter;

    @BeforeEach
    void setUp() {
        repo = mock(UserProfileRepository.class);
        filter = new UserIdentificationFilter(repo);
    }

    private static class FlagChain implements FilterChain {
        boolean passed = false;
        @Override public void doFilter(ServletRequest r, ServletResponse s) { passed = true; }
    }

    @Test
    void 헤더없으면_UUID를_생성해_응답헤더와_요청속성에_넣는다() throws Exception {
        when(repo.findByUserId(any())).thenReturn(Optional.empty());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/weather");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FlagChain chain = new FlagChain();

        filter.doFilter(req, res, chain);

        assertTrue(chain.passed);
        String userId = res.getHeader(UserIdentificationFilter.USER_ID_HEADER);
        assertNotNull(userId);
        assertEquals(userId, req.getAttribute(UserIdentificationFilter.USER_ID_ATTRIBUTE));

        // 🔴 **저장하지 않는다**(2026-09-16 계약 변경).
        //
        // 종전에는 여기서 `verify(repo).save(...)` 로 *"신규 사용자 → 프로필 저장"* 을
        // 고정하고 있었다. 그런데 그 id 는 응답 헤더로만 돌려줄 뿐이라 클라이언트가
        // 들고 다시 오지 않으면 **한 번 쓰고 버려진다.**
        //
        // 실측(`.25` 운영 DB): user_profiles **7,361,599 행** · 고유 id 7,361,613개 ·
        // `dashboard.db` **772.8MB**(빈 페이지 0). 대조군은 user_settings 5행·todos 0행.
        // 개인용 대시보드 하나에 **사용자 736만 명**이 쌓여 있었다.
        //
        // ⚠️ 더 나쁜 것은 지연이다 — 커넥션 풀이 **1**이라 모든 DB 접근이 직렬화되는데,
        //    가장 많이 불리는 `/api/system/status`(46,734회)가 헤더 없이 오면
        //    **매번 이 INSERT 가 그 한 줄에 선다.**
        verify(repo, never()).save(any(UserProfile.class));
    }

    @Test
    void 헤더를_들고_오면_프로필이_없을때_만든다() throws Exception {
        // 🔴 이 테스트가 없어서 한 번 놓쳤다(2026-09-16).
        //
        // 익명 저장을 끄고 나니 **어떤 경로로도 프로필이 생기지 않는 상태**가 됐는데,
        // 필터 주석에는 "클라이언트가 id 를 채택하면 다음 요청부터 정상 기록된다" 고
        // 적혀 있었다. **선언과 실물이 달랐고**, 라이브에서 요청을 실제로 태워 보고서야
        // 잡았다. 단위 테스트가 그 축을 안 보고 있었다.
        when(repo.findByUserId(any())).thenReturn(Optional.empty());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/system/status");
        req.addHeader(UserIdentificationFilter.USER_ID_HEADER, "client-supplied-id");

        filter.doFilter(req, new MockHttpServletResponse(), new FlagChain());

        verify(repo).save(any(UserProfile.class));
    }

    @Test
    void 익명요청이_반복돼도_저장하지_않는다() throws Exception {
        when(repo.findByUserId(any())).thenReturn(Optional.empty());

        for (int i = 0; i < 50; i++) {
            filter.doFilter(new MockHttpServletRequest("GET", "/api/system/status"),
                    new MockHttpServletResponse(), new FlagChain());
        }

        // 종전 동작이라면 여기서 50행이 쌓인다. 그게 736만이 된 경위다.
        verify(repo, never()).save(any(UserProfile.class));
    }

    @Test
    void 빈헤더도_신규사용자로_취급한다() throws Exception {
        when(repo.findByUserId(any())).thenReturn(Optional.empty());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/weather");
        req.addHeader(UserIdentificationFilter.USER_ID_HEADER, "   ");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FlagChain chain = new FlagChain();

        filter.doFilter(req, res, chain);

        assertTrue(chain.passed);
        assertNotNull(res.getHeader(UserIdentificationFilter.USER_ID_HEADER));
        // 빈 헤더도 익명이다 — 위와 같은 이유로 저장하지 않는다.
        verify(repo, never()).save(any(UserProfile.class));
    }

    @Test
    void 기존헤더는_그대로_에코되고_신규저장은_없다() throws Exception {
        UserProfile existing = new UserProfile("user-123");
        existing.setLastActive(Instant.now()); // 최근 활동 → 갱신 저장 없음
        when(repo.findByUserId("user-123")).thenReturn(Optional.of(existing));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/weather");
        req.addHeader(UserIdentificationFilter.USER_ID_HEADER, "user-123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FlagChain chain = new FlagChain();

        filter.doFilter(req, res, chain);

        assertTrue(chain.passed);
        assertEquals("user-123", res.getHeader(UserIdentificationFilter.USER_ID_HEADER));
        assertEquals("user-123", req.getAttribute(UserIdentificationFilter.USER_ID_ATTRIBUTE));
        verify(repo, never()).save(any());
    }

    @Test
    void 기존사용자_10분초과면_활동시간을_갱신저장한다() throws Exception {
        UserProfile old = new UserProfile("user-old");
        old.setLastActive(Instant.now().minusSeconds(60 * 20)); // 20분 전
        when(repo.findByUserId("user-old")).thenReturn(Optional.of(old));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/weather");
        req.addHeader(UserIdentificationFilter.USER_ID_HEADER, "user-old");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new FlagChain());

        verify(repo).save(old);
    }

    @Test
    void 정적리소스는_사용자처리를_건너뛴다() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/index.html");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FlagChain chain = new FlagChain();

        filter.doFilter(req, res, chain);

        assertTrue(chain.passed);
        assertNull(res.getHeader(UserIdentificationFilter.USER_ID_HEADER));
        verify(repo, never()).findByUserId(any());
    }

    @Test
    void DB예외가_나도_요청은_계속_진행된다() throws Exception {
        when(repo.findByUserId(any())).thenThrow(new RuntimeException("db down"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/weather");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FlagChain chain = new FlagChain();

        filter.doFilter(req, res, chain); // 예외 전파 없이 통과해야 한다

        assertTrue(chain.passed);
        assertNotNull(res.getHeader(UserIdentificationFilter.USER_ID_HEADER));
    }
}
