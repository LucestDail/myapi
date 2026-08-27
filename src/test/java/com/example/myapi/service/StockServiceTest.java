package com.example.myapi.service;

import com.example.myapi.dto.finnhub.FinnhubQuoteResponse;
import com.example.myapi.dto.stock.StockQuote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * StockService 단위 테스트. 네트워크 의존(KR 네이버) 경로는 통합/수동 검증으로 남기고,
 * 여기서는 US 경로(Finnhub 매핑)와 입력 검증만 결정적으로 확인한다.
 */
class StockServiceTest {

    private final FinnhubService finnhub = mock(FinnhubService.class);
    private final StockService svc = new StockService(finnhub, new ObjectMapper());

    @Test
    void US경로는_Finnhub를_정규화매핑() {
        when(finnhub.getQuote("AAPL")).thenReturn(
                new FinnhubQuoteResponse(190.5, 2.5, 1.33, 191.0, 188.0, 189.0, 188.0, 0L));
        StockQuote q = svc.getQuote("aapl", "US"); // 소문자 → 대문자 정규화
        assertEquals("AAPL", q.symbol());
        assertEquals("US", q.market());
        assertEquals("USD", q.currency());
        assertEquals(190.5, q.price());
        assertEquals(188.0, q.previousClose());
        assertEquals(2.5, q.change());
        assertEquals(1.33, q.changePct());
    }

    @Test
    void market_기본값은_US() {
        when(finnhub.getQuote("NVDA")).thenReturn(
                new FinnhubQuoteResponse(120.0, -1.0, -0.83, 122.0, 119.0, 121.0, 121.0, 0L));
        StockQuote q = svc.getQuote("NVDA", null);
        assertEquals("US", q.market());
        assertEquals(120.0, q.price());
    }

    @Test
    void Finnhub_현재가0이면_null() {
        when(finnhub.getQuote("ZZZZ")).thenReturn(
                new FinnhubQuoteResponse(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L));
        assertNull(svc.getQuote("ZZZZ", "US"));
    }

    @Test
    void Finnhub_null이면_null() {
        when(finnhub.getQuote("ZZZZ")).thenReturn(null);
        assertNull(svc.getQuote("ZZZZ", "US"));
    }

    @Test
    void 빈_symbol은_예외() {
        assertThrows(IllegalArgumentException.class, () -> svc.getQuote("  ", "US"));
        assertThrows(IllegalArgumentException.class, () -> svc.getQuote(null, "KR"));
    }
}
