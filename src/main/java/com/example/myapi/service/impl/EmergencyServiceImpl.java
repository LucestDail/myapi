package com.example.myapi.service.impl;

import com.example.myapi.service.EmergencyService;
import com.example.myapi.util.HttpUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

@Slf4j
@Service
public class EmergencyServiceImpl implements EmergencyService {

    @org.springframework.beans.factory.annotation.Value("${emergency.api.serviceKey:}")
    private String emergencyServiceKey;
    
    @org.springframework.beans.factory.annotation.Value("${emergency.api.url:https://www.safetydata.go.kr/V2/api/DSSP-IF-00247}")
    private String emergencyApiUrl;

    private JsonObject cachedEmergencyData = null;
    private volatile long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 300000; // 5분

    @Override
    public JsonObject getEmergencyInfo() {
        long currentTime = System.currentTimeMillis();
        
        // 캐시가 없거나 갱신 시간이 지났으면 업데이트
        if (cachedEmergencyData == null || (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS)) {
            renewEmergencyData();
        }
        
        return cachedEmergencyData != null ? cachedEmergencyData : createEmptyEmergencyJson();
    }

    @Override
    public void renewEmergencyData() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            TimeZone seoul = TimeZone.getTimeZone("Asia/Seoul");
            sdf.setTimeZone(seoul);
            String strToday = sdf.format(System.currentTimeMillis());
            String strYesterday = sdf.format(System.currentTimeMillis() - 24 * 60 * 60 * 1000);

            String strEmergencyInfo = new HttpUtil().executeGet(
                emergencyApiUrl + "?serviceKey=" + emergencyServiceKey + "&crtDt=" + strToday
            );
            String strEmergencyInfoYesterday = new HttpUtil().executeGet(
                emergencyApiUrl + "?serviceKey=" + emergencyServiceKey + "&crtDt=" + strYesterday
            );
            
            // 두 JSON 데이터를 하나로 합치기
            JsonObject combinedJson = new JsonObject();
            JsonArray emergencyArray = new JsonArray();
            
            // 오늘 데이터 파싱 및 추가
            if (strEmergencyInfo != null && !strEmergencyInfo.trim().isEmpty()) {
                JsonObject todayJson = JsonParser.parseString(strEmergencyInfo).getAsJsonObject();
                if (todayJson.has("body") && !todayJson.get("body").isJsonNull()) {
                    JsonArray todayItems = todayJson.getAsJsonArray("body");
                    for (JsonElement item : todayItems) {
                        emergencyArray.add(item);
                    }
                }
            }
            
            // 어제 데이터 파싱 및 추가
            if (strEmergencyInfoYesterday != null && !strEmergencyInfoYesterday.trim().isEmpty()) {
                JsonObject yesterdayJson = JsonParser.parseString(strEmergencyInfoYesterday).getAsJsonObject();
                if (yesterdayJson.has("body") && !yesterdayJson.get("body").isJsonNull()) {
                    JsonArray yesterdayItems = yesterdayJson.getAsJsonArray("body");
                    for (JsonElement item : yesterdayItems) {
                        emergencyArray.add(item);
                    }
                }
            }
            
            // 합쳐진 데이터를 새로운 JSON 객체에 추가
            combinedJson.add("items", emergencyArray);
            cachedEmergencyData = combinedJson;
            lastUpdateTime = System.currentTimeMillis();
            log.debug("Successfully updated emergency data with {} items", emergencyArray.size());
            
        } catch (Exception e) {
            log.error("Error renewing emergency data: {}", e.getMessage());
            if (cachedEmergencyData == null) {
                cachedEmergencyData = createEmptyEmergencyJson();
            }
        }
    }

    private JsonObject createEmptyEmergencyJson() {
        JsonObject jsonObject = new JsonObject();
        JsonArray items = new JsonArray();
        jsonObject.add("items", items);
        return jsonObject;
    }
}
