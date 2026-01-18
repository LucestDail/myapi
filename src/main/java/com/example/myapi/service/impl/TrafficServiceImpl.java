package com.example.myapi.service.impl;

import com.example.myapi.service.TrafficService;
import com.example.myapi.util.HttpUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TrafficServiceImpl implements TrafficService {

    @org.springframework.beans.factory.annotation.Value("${traffic.api.url:https://openapi.its.go.kr:9443/eventInfo}")
    private String trafficApiUrl;
    
    @org.springframework.beans.factory.annotation.Value("${traffic.api.key:}")
    private String trafficApiKey;

    private JsonObject cachedTrafficData = null;
    private volatile long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 300000; // 5분

    @Override
    public JsonObject getTrafficInfo() {
        long currentTime = System.currentTimeMillis();
        
        // 캐시가 없거나 갱신 시간이 지났으면 업데이트
        if (cachedTrafficData == null || (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS)) {
            renewTrafficData();
        }
        
        return cachedTrafficData != null ? cachedTrafficData : createEmptyTrafficJson();
    }

    @Override
    public void renewTrafficData() {
        try {
            String trafficInfo = new HttpUtil().executeGet(
                trafficApiUrl + "?apiKey=" + trafficApiKey + "&type=all&eventType=all&getType=json"
            );
            
            if (trafficInfo != null && !trafficInfo.trim().isEmpty()) {
                JsonObject jsonObject = JsonParser.parseString(trafficInfo).getAsJsonObject();
                cachedTrafficData = jsonObject;
                lastUpdateTime = System.currentTimeMillis();
                log.debug("Successfully updated traffic data");
            } else {
                log.warn("Traffic info is null or empty, keeping existing cache");
            }
        } catch (Exception e) {
            log.error("Error renewing traffic data: {}", e.getMessage());
            if (cachedTrafficData == null) {
                cachedTrafficData = createEmptyTrafficJson();
            }
        }
    }

    private JsonObject createEmptyTrafficJson() {
        JsonObject jsonObject = new JsonObject();
        JsonObject body = new JsonObject();
        JsonArray items = new JsonArray();
        body.add("items", items);
        jsonObject.add("body", body);
        return jsonObject;
    }
}
