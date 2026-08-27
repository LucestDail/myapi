package com.example.myapi.service;

import com.example.myapi.dto.finnhub.FinnhubQuoteResponse;
import com.example.myapi.dto.stock.StockQuote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 정규화된 주가 조회 서비스.
 *
 * <ul>
 *   <li><b>국내(KR)</b>: 네이버 파이낸스 키리스 엔드포인트. 6자리 코드(005930) 또는 종목명(삼성전자)을
 *       자동완성(ac.stock.naver.com)으로 코드 해석 → polling(polling.finance.naver.com)으로 시세 조회.
 *       my-computer {@code StockQuoteTool} 방식을 이식했다. 모든 국내 상장 종목 커버.</li>
 *   <li><b>해외(US)</b>: 기존 {@link FinnhubService} 재사용(티커=대문자, 60초 캐시).</li>
 * </ul>
 *
 * <p>국내 시세는 {@link FinnhubService} 와 동일하게 60초 TTL 캐시를 적용한다(중복 조회 시 즉시 반환).</p>
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String NAVER_AC = "https://ac.stock.naver.com/ac?target=stock&st=1&q=";
    private static final String NAVER_POLL = "https://polling.finance.naver.com/api/realtime/domestic/stock/";

    private final FinnhubService finnhubService;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // 국내 시세 캐시: code -> (quote, fetchedAt)
    private final Map<String, CachedQuote> krCache = new ConcurrentHashMap<>();

    private record CachedQuote(StockQuote quote, Instant fetchedAt) {}

    public StockService(FinnhubService finnhubService, ObjectMapper objectMapper) {
        this.finnhubService = finnhubService;
        this.objectMapper = objectMapper;
    }

    /**
     * 정규화된 주가 조회.
     *
     * @param symbol 종목명/코드/티커 (예: 삼성전자, 005930, AAPL)
     * @param market "KR" 또는 "US" (대소문자 무시, 기본 US)
     * @return 정규화 응답. 조회 실패 시 {@code null}.
     */
    public StockQuote getQuote(String symbol, String market) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        String in = symbol.trim();
        boolean kr = market != null && market.equalsIgnoreCase("KR");
        return kr ? getKrQuote(in) : getUsQuote(in);
    }

    // ── 국내(네이버) ──────────────────────────────────────────────
    private StockQuote getKrQuote(String symbolOrName) {
        String code = resolveKrCode(symbolOrName);
        if (code == null) {
            log.info("[stock] KR code not resolved for '{}'", symbolOrName);
            return null;
        }
        CachedQuote cached = krCache.get(code);
        if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return cached.quote();
        }
        StockQuote quote = naverQuote(code);
        if (quote != null) {
            krCache.put(code, new CachedQuote(quote, Instant.now()));
        } else if (cached != null) {
            return cached.quote(); // 갱신 실패 시 만료된 캐시라도 반환
        }
        return quote;
    }

    /** 이름/코드 → 6자리 숫자 코드. */
    private String resolveKrCode(String in) {
        if (in.matches("\\d{6}")) {
            return in;
        }
        if (in.matches("(?i)\\d{6}\\.(KS|KQ)")) {
            return in.substring(0, 6);
        }
        try {
            String body = get(NAVER_AC + URLEncoder.encode(in, StandardCharsets.UTF_8));
            if (body == null) return null;
            JsonNode items = objectMapper.readTree(body).path("items");
            if (!items.isArray() || items.isEmpty()) return null;
            String firstNumeric = null;
            String needle = in.replaceAll("\\s+", "");
            for (JsonNode it : items) {
                String c = it.path("code").asText("");
                String nm = it.path("name").asText("");
                if (!c.matches("\\d{6}")) continue; // ETF 등 비숫자 코드 제외
                if (firstNumeric == null) firstNumeric = c;
                if (nm.replaceAll("\\s+", "").equalsIgnoreCase(needle)) {
                    return c; // 정확 일치 우선
                }
            }
            return firstNumeric;
        } catch (Exception e) {
            log.debug("[stock] naver resolve fail {}: {}", in, e.getMessage());
            return null;
        }
    }

    private StockQuote naverQuote(String code) {
        try {
            String body = get(NAVER_POLL + code);
            if (body == null) return null;
            JsonNode datas = objectMapper.readTree(body).path("datas");
            if (!datas.isArray() || datas.isEmpty()) return null;
            JsonNode d = datas.get(0);
            String name = d.path("stockName").asText(code);
            Double close = parseNum(d.path("closePrice").asText(""));
            if (close == null) return null;
            Double diff = parseNum(d.path("compareToPreviousClosePrice").asText(""));
            Double ratio = parseNum(d.path("fluctuationsRatio").asText(""));
            String dir = d.path("compareToPreviousPrice").path("text").asText(""); // 상승/하락/보합
            boolean down = dir.contains("하락");
            Double signedDiff = diff == null ? null : (down ? -Math.abs(diff) : Math.abs(diff));
            Double signedRatio = ratio == null ? null : (down ? -Math.abs(ratio) : Math.abs(ratio));
            Double prevClose = (close != null && signedDiff != null) ? close - signedDiff : null;
            return new StockQuote(code, name, close, prevClose, signedDiff, signedRatio, "KRW", "KR");
        } catch (Exception e) {
            log.debug("[stock] naver quote fail {}: {}", code, e.getMessage());
            return null;
        }
    }

    // ── 해외(Finnhub) ────────────────────────────────────────────
    private StockQuote getUsQuote(String symbol) {
        String ticker = symbol.toUpperCase();
        FinnhubQuoteResponse r = finnhubService.getQuote(ticker);
        if (r == null || r.currentPrice() == null || r.currentPrice() == 0.0) {
            return null;
        }
        return new StockQuote(
                ticker,
                ticker,
                r.currentPrice(),
                r.previousClose(),
                r.change(),
                r.percentChange(),
                "USD",
                "US");
    }

    // ── 공용 ─────────────────────────────────────────────────────
    private String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            log.info("[stock] HTTP {} for {}", res.statusCode(), url);
            return null;
        }
        return res.body();
    }

    /** 네이버 응답 수치는 콤마 포함 문자열("70,000")일 수 있어 정규화 후 파싱. */
    private static Double parseNum(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.valueOf(s.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
