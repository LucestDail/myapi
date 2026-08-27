package com.example.myapi.filter;

import com.example.myapi.config.MyapiSecurityProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyRestFilterTest {

    private MyapiSecurityProperties propsWithKey(String key) {
        MyapiSecurityProperties p = new MyapiSecurityProperties();
        p.setApiKey(key);
        return p;
    }

    private static class FlagChain implements FilterChain {
        boolean passed = false;
        @Override public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) { passed = true; }
    }

    private MockHttpServletRequest req(String servletPath, String headerKey, String queryKey) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", servletPath);
        r.setServletPath(servletPath);
        if (headerKey != null) r.addHeader(ApiKeyRestFilter.API_KEY_HEADER, headerKey);
        if (queryKey != null) r.setParameter("apiKey", queryKey);
        return r;
    }

    @Test
    void 키_미설정이면_전체통과_fail_open() throws Exception {
        var filter = new ApiKeyRestFilter(new MyapiSecurityProperties()); // apiKey 빈값
        var chain = new FlagChain();
        var res = new MockHttpServletResponse();
        filter.doFilter(req("/api/finnhub/quote", null, null), res, chain);
        assertTrue(chain.passed, "키 미설정이면 검증 없이 통과해야 한다(기존 클라 무영향)");
        assertEquals(200, res.getStatus());
    }

    @Test
    void 키설정시_api경로_키없으면_401() throws Exception {
        var filter = new ApiKeyRestFilter(propsWithKey("SECRET"));
        var chain = new FlagChain();
        var res = new MockHttpServletResponse();
        filter.doFilter(req("/api/finnhub/quote", null, null), res, chain);
        assertFalse(chain.passed);
        assertEquals(401, res.getStatus());
    }

    @Test
    void 키설정시_올바른키_헤더면_통과() throws Exception {
        var filter = new ApiKeyRestFilter(propsWithKey("SECRET"));
        var chain = new FlagChain();
        var res = new MockHttpServletResponse();
        filter.doFilter(req("/api/finnhub/quote", "SECRET", null), res, chain);
        assertTrue(chain.passed);
    }

    @Test
    void 키설정시_틀린키_헤더면_401() throws Exception {
        var filter = new ApiKeyRestFilter(propsWithKey("SECRET"));
        var chain = new FlagChain();
        var res = new MockHttpServletResponse();
        filter.doFilter(req("/api/finnhub/quote", "WRONG", null), res, chain);
        assertFalse(chain.passed);
        assertEquals(401, res.getStatus());
    }

    @Test
    void 키설정시_올바른키_쿼리면_통과() throws Exception {
        var filter = new ApiKeyRestFilter(propsWithKey("SECRET"));
        var chain = new FlagChain();
        filter.doFilter(req("/api/stocks/quote", null, "SECRET"), new MockHttpServletResponse(), chain);
        assertTrue(chain.passed);
    }

    @Test
    void 키설정이어도_비api경로는_개방() throws Exception {
        var filter = new ApiKeyRestFilter(propsWithKey("SECRET"));
        var chain = new FlagChain();
        var res = new MockHttpServletResponse();
        filter.doFilter(req("/actuator/health", null, null), res, chain);
        assertTrue(chain.passed);
        assertEquals(200, res.getStatus());
    }

    @Test
    void 키설정이어도_정적_루트는_개방() throws Exception {
        var filter = new ApiKeyRestFilter(propsWithKey("SECRET"));
        var chain = new FlagChain();
        var res = new MockHttpServletResponse();
        filter.doFilter(req("/index.html", null, null), res, chain);
        assertTrue(chain.passed);
        assertEquals(200, res.getStatus());
    }
}
