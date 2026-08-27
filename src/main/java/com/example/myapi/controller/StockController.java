package com.example.myapi.controller;

import com.example.myapi.dto.stock.StockQuote;
import com.example.myapi.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 생태계 주가 단일화용 정규화 엔드포인트.
 *
 * <pre>
 * GET /api/stocks/quote?symbol=삼성전자&market=KR   → 네이버 키리스
 * GET /api/stocks/quote?symbol=AAPL&market=US       → Finnhub(기존 재사용)
 * </pre>
 *
 * 응답: {@link StockQuote} — {symbol,name,price,previousClose,change,changePct,currency,market}
 */
@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/quote")
    public ResponseEntity<StockQuote> getQuote(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "US") String market) {
        StockQuote quote = stockService.getQuote(symbol, market);
        return quote == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(quote);
    }
}
