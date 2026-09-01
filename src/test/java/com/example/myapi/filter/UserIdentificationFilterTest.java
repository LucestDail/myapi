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
        // 신규 사용자 → 프로필 저장 시도
        verify(repo).save(any(UserProfile.class));
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
        verify(repo).save(any(UserProfile.class));
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
