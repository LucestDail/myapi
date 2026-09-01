package com.example.myapi.dto;

import com.example.myapi.dto.finnhub.FinnhubQuoteResponse;
import com.example.myapi.dto.weather.OpenWeatherResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 외부 API 클라이언트 DTO의 Jackson 역직렬화 검증.
 * RestTemplate 이 실제로 수행하는 매핑(@JsonProperty 별칭, 미지 필드 무시, 누락 필드)을
 * 실제 응답 형태의 JSON 문자열로 결정적으로 확인한다.
 */
class DtoJsonParsingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ── Finnhub /quote ────────────────────────────────────────────
    @Test
    void Finnhub_quote_축약키를_필드로_매핑() throws Exception {
        String json = "{\"c\":190.5,\"d\":2.5,\"dp\":1.33,\"h\":191.0,\"l\":188.0,\"o\":189.0,\"pc\":188.0,\"t\":1700000000}";
        FinnhubQuoteResponse q = mapper.readValue(json, FinnhubQuoteResponse.class);
        assertEquals(190.5, q.currentPrice());
        assertEquals(2.5, q.change());
        assertEquals(1.33, q.percentChange());
        assertEquals(191.0, q.highPrice());
        assertEquals(188.0, q.lowPrice());
        assertEquals(189.0, q.openPrice());
        assertEquals(188.0, q.previousClose());
        assertEquals(1700000000L, q.timestamp());
    }

    @Test
    void Finnhub_알수없는_티커는_모든값_0() throws Exception {
        // Finnhub 은 미존재 티커에 대해 0 으로 채운 페이로드를 반환한다.
        String json = "{\"c\":0,\"d\":null,\"dp\":null,\"h\":0,\"l\":0,\"o\":0,\"pc\":0,\"t\":0}";
        FinnhubQuoteResponse q = mapper.readValue(json, FinnhubQuoteResponse.class);
        assertEquals(0.0, q.currentPrice());
        assertNull(q.change());
        assertNull(q.percentChange());
    }

    @Test
    void Finnhub_누락필드는_null() throws Exception {
        String json = "{\"c\":100.0}";
        FinnhubQuoteResponse q = mapper.readValue(json, FinnhubQuoteResponse.class);
        assertEquals(100.0, q.currentPrice());
        assertNull(q.previousClose());
        assertNull(q.timestamp());
    }

    // ── OpenWeather /data/2.5/weather ─────────────────────────────
    @Test
    void OpenWeather_중첩구조_파싱() throws Exception {
        String json = "{"
                + "\"coord\":{\"lon\":126.97,\"lat\":37.56},"
                + "\"weather\":[{\"id\":800,\"main\":\"Clear\",\"description\":\"clear sky\",\"icon\":\"01d\"}],"
                + "\"main\":{\"temp\":293.15,\"feels_like\":296.15,\"temp_min\":290.0,\"temp_max\":295.0,\"pressure\":1013,\"humidity\":55},"
                + "\"wind\":{\"speed\":3.5,\"deg\":180},"
                + "\"clouds\":{\"all\":20},"
                + "\"sys\":{\"country\":\"KR\",\"sunrise\":1700000000,\"sunset\":1700040000},"
                + "\"name\":\"Seoul\",\"visibility\":10000,\"dt\":1700000500,\"timezone\":32400}";
        OpenWeatherResponse r = mapper.readValue(json, OpenWeatherResponse.class);
        assertEquals("Seoul", r.name());
        assertEquals(1, r.weather().size());
        assertEquals("Clear", r.weather().get(0).main());
        assertEquals(293.15, r.main().temp());
        assertEquals(55, r.main().humidity());
        assertEquals(3.5, r.wind().speed());
        assertEquals(20, r.clouds().all());
        assertEquals("KR", r.sys().country());
        assertNull(r.main().sea_level());   // 옵셔널 필드 누락 → null
    }

    @Test
    void OpenWeather_미지의필드는_무시된다() throws Exception {
        // @JsonIgnoreProperties(ignoreUnknown = true) 동작 검증
        String json = "{"
                + "\"weather\":[{\"id\":801,\"main\":\"Clouds\",\"description\":\"few\",\"icon\":\"02d\",\"extraField\":\"x\"}],"
                + "\"main\":{\"temp\":280.0,\"feels_like\":278.0,\"temp_min\":279.0,\"temp_max\":281.0,\"pressure\":1000,\"humidity\":80},"
                + "\"wind\":{\"speed\":1.0,\"deg\":90},"
                + "\"clouds\":{\"all\":40},"
                + "\"sys\":{\"country\":\"KR\",\"sunrise\":1,\"sunset\":2},"
                + "\"name\":\"X\",\"visibility\":5000,\"dt\":1,\"timezone\":0,"
                + "\"base\":\"stations\",\"cod\":200,\"unmapped\":{\"nested\":true}}";
        OpenWeatherResponse r = mapper.readValue(json, OpenWeatherResponse.class);
        assertNotNull(r);
        assertEquals("Clouds", r.weather().get(0).main());
        assertEquals(280.0, r.main().temp());
        assertTrue(r.visibility() == 5000);
    }
}
