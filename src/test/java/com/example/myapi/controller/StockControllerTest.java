package com.example.myapi.controller;

import com.example.myapi.dto.stock.StockQuote;
import com.example.myapi.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * StockController 웹 계층 검증(standalone MockMvc).
 * 파라미터 바인딩(기본 market=US)·404/200 분기·필수 파라미터 누락(400) 확인.
 */
class StockControllerTest {

    private StockService stockService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        stockService = mock(StockService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StockController(stockService)).build();
    }

    @Test
    void quote_정상조회_200과_본문() throws Exception {
        when(stockService.getQuote("AAPL", "US"))
                .thenReturn(new StockQuote("AAPL", "AAPL", 190.5, 188.0, 2.5, 1.33, "USD", "US"));
        mockMvc.perform(get("/api/stocks/quote").param("symbol", "AAPL").param("market", "US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.price").value(190.5));
    }

    @Test
    void quote_market기본값은_US() throws Exception {
        when(stockService.getQuote(eq("AAPL"), eq("US")))
                .thenReturn(new StockQuote("AAPL", "AAPL", 1.0, 1.0, 0.0, 0.0, "USD", "US"));
        mockMvc.perform(get("/api/stocks/quote").param("symbol", "AAPL"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> market = ArgumentCaptor.forClass(String.class);
        verify(stockService).getQuote(eq("AAPL"), market.capture());
        org.junit.jupiter.api.Assertions.assertEquals("US", market.getValue());
    }

    @Test
    void quote_서비스가_null이면_404() throws Exception {
        when(stockService.getQuote("ZZZZ", "US")).thenReturn(null);
        mockMvc.perform(get("/api/stocks/quote").param("symbol", "ZZZZ"))
                .andExpect(status().isNotFound());
    }

    @Test
    void quote_symbol누락이면_400() throws Exception {
        mockMvc.perform(get("/api/stocks/quote"))
                .andExpect(status().isBadRequest());
    }
}
