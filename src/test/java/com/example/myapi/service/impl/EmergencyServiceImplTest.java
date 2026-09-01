package com.example.myapi.service.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EmergencyServiceImpl 검증. 네트워크 유발(renew)을 피하기 위해 캐시를 미리 세팅하고,
 * 순수 파싱/빈객체 생성 로직만 결정적으로 확인한다.
 */
class EmergencyServiceImplTest {

    private EmergencyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmergencyServiceImpl();
    }

    @Test
    void 신선한캐시가_있으면_네트워크없이_그대로_반환한다() {
        JsonObject cached = new JsonObject();
        cached.add("items", new JsonArray());
        ReflectionTestUtils.setField(service, "cachedEmergencyData", cached);
        ReflectionTestUtils.setField(service, "lastUpdateTime", System.currentTimeMillis());

        assertSame(cached, service.getEmergencyInfo());
    }

    @Test
    void createEmptyEmergencyJson_는_빈items배열을_가진다() {
        JsonObject empty = ReflectionTestUtils.invokeMethod(service, "createEmptyEmergencyJson");
        assertTrue(empty.has("items"));
        assertEquals(0, empty.getAsJsonArray("items").size());
    }

    @Test
    void parse_JsonNull은_아무것도_추가하지_않는다() {
        JsonArray target = new JsonArray();
        ReflectionTestUtils.invokeMethod(service, "parseEmergencyDataFromString", JsonNull.INSTANCE, target);
        assertEquals(0, target.size());
    }

    @Test
    void parse_body배열이_있는_JsonObject를_펼친다() {
        JsonObject obj = JsonParser.parseString("{\"body\":[{\"MSG_CN\":\"a\"},{\"MSG_CN\":\"b\"}]}")
                .getAsJsonObject();
        JsonArray target = new JsonArray();
        ReflectionTestUtils.invokeMethod(service, "parseEmergencyDataFromString", obj, target);
        assertEquals(2, target.size());
    }

    @Test
    void parse_JSON문자열_프리미티브도_body를_추출한다() {
        JsonPrimitive strPrimitive = new JsonPrimitive("{\"body\":[{\"MSG_CN\":\"x\"}]}");
        JsonArray target = new JsonArray();
        ReflectionTestUtils.invokeMethod(service, "parseEmergencyDataFromString", strPrimitive, target);
        assertEquals(1, target.size());
    }

    @Test
    void parse_body없는객체는_추가없이_무시한다() {
        JsonObject obj = JsonParser.parseString("{\"header\":{\"code\":\"00\"}}").getAsJsonObject();
        JsonArray target = new JsonArray();
        ReflectionTestUtils.invokeMethod(service, "parseEmergencyDataFromString", obj, target);
        assertEquals(0, target.size());
    }

    @Test
    void parse_숫자프리미티브는_예외없이_무시한다() {
        JsonArray target = new JsonArray();
        ReflectionTestUtils.invokeMethod(service, "parseEmergencyDataFromString", new JsonPrimitive(42), target);
        assertEquals(0, target.size());
    }
}
