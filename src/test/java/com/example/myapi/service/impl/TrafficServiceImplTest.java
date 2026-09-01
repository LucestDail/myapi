package com.example.myapi.service.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TrafficServiceImpl 검증. 캐시 프리셋으로 네트워크(renew)를 우회하고,
 * 캐시 반환/빈객체 구조만 결정적으로 확인한다.
 */
class TrafficServiceImplTest {

    private TrafficServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TrafficServiceImpl();
    }

    @Test
    void 신선한캐시가_있으면_그대로_반환한다() {
        JsonObject cached = new JsonObject();
        cached.addProperty("marker", "cached");
        ReflectionTestUtils.setField(service, "cachedTrafficData", cached);
        ReflectionTestUtils.setField(service, "lastUpdateTime", System.currentTimeMillis());

        assertSame(cached, service.getTrafficInfo());
    }

    @Test
    void createEmptyTrafficJson_은_body_items_구조를_가진다() {
        JsonObject empty = ReflectionTestUtils.invokeMethod(service, "createEmptyTrafficJson");
        assertNotNull(empty);
        assertTrue(empty.has("body"));
        JsonObject body = empty.getAsJsonObject("body");
        assertTrue(body.has("items"));
        JsonArray items = body.getAsJsonArray("items");
        assertTrue(items.isEmpty());
    }
}
