package com.example.myapi.dto.stock;

/**
 * 정규화된 주가 응답. 국내(네이버)·해외(Finnhub) 소스를 공통 형태로 노출한다.
 *
 * @param symbol        종목 코드/티커 (국내=6자리 코드, 해외=대문자 티커)
 * @param name          종목명(가능한 경우; 없으면 symbol)
 * @param price         현재가/최근 체결가(마감 시 종가)
 * @param previousClose 전일 종가
 * @param change        전일 대비 등락(price - previousClose)
 * @param changePct     전일 대비 등락률(%)
 * @param currency      통화(KRW/USD)
 * @param market        시장 구분("KR"|"US")
 */
public record StockQuote(
        String symbol,
        String name,
        Double price,
        Double previousClose,
        Double change,
        Double changePct,
        String currency,
        String market
) {}
