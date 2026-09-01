package com.example.myapi.service;

import com.example.myapi.config.FinnhubProperties;
import com.example.myapi.dto.finnhub.FinnhubQuoteResponse;
import com.example.myapi.dto.finnhub.MarketNewsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FinnhubService 단위 테스트. RestTemplate 를 목으로 대체해 네트워크 없이
 * 캐싱/정규화/널바디/예외전파/URL구성 을 확인한다.
 */
class FinnhubServiceTest {

    private RestTemplate restTemplate;
    private FinnhubService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        FinnhubProperties props = new FinnhubProperties();
        props.setBaseUrl("https://finnhub.io/api/v1");
        props.setApiKey("TESTKEY");
        service = new FinnhubService(restTemplate, props);
    }

    private void stubQuote(FinnhubQuoteResponse body) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(FinnhubQuoteResponse.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    @Test
    void getQuote_정상응답을_반환하고_심볼을_대문자로_정규화한다() {
        FinnhubQuoteResponse body = new FinnhubQuoteResponse(190.5, 2.5, 1.33, 191.0, 188.0, 189.0, 188.0, 0L);
        stubQuote(body);

        FinnhubQuoteResponse r = service.getQuote("aapl");
        assertSame(body, r);

        // URL 에 대문자 심볼과 token 파라미터가 포함되어야 한다
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(url.capture(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(FinnhubQuoteResponse.class));
        org.junit.jupiter.api.Assertions.assertTrue(url.getValue().contains("symbol=AAPL"));
        org.junit.jupiter.api.Assertions.assertTrue(url.getValue().contains("token=TESTKEY"));
    }

    @Test
    void getQuote_두번째호출은_캐시에서_반환한다() {
        FinnhubQuoteResponse body = new FinnhubQuoteResponse(100.0, 1.0, 1.0, 101.0, 99.0, 100.0, 99.0, 0L);
        stubQuote(body);

        service.getQuote("MSFT");
        service.getQuote("msft"); // 정규화 후 동일 캐시 키

        // API 는 최초 1회만 호출되어야 한다
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(FinnhubQuoteResponse.class));
    }

    @Test
    void getQuote_바디가_null이면_null이고_캐시하지_않는다() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(FinnhubQuoteResponse.class)))
                .thenReturn(ResponseEntity.ok(null));

        assertNull(service.getQuote("ZZZZ"));
        service.getQuote("ZZZZ");
        // null 은 캐시되지 않으므로 매번 API 재호출
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(FinnhubQuoteResponse.class));
    }

    @Test
    void getQuote_RestTemplate예외는_전파된다() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(FinnhubQuoteResponse.class)))
                .thenThrow(new RestClientException("timeout"));
        assertThrows(RestClientException.class, () -> service.getQuote("AAPL"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getMarketNews_리스트를_반환한다() {
        List<MarketNewsResponse> news = List.of(
                new MarketNewsResponse("general", 0L, "headline", 1L, "img", "related", "src", "summary", "url"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(news));

        List<MarketNewsResponse> r = service.getMarketNews("general");
        assertEquals(1, r.size());
        assertEquals("headline", r.get(0).headline());
    }

    @Test
    void getCacheStatus_캐시크기를_보고한다() {
        stubQuote(new FinnhubQuoteResponse(10.0, 0.0, 0.0, 10.0, 10.0, 10.0, 10.0, 0L));
        service.getQuote("AAPL");
        var status = service.getCacheStatus();
        assertEquals(1, status.get("cacheSize"));
        assertEquals(60L, status.get("cacheTtlSeconds"));
    }
}
