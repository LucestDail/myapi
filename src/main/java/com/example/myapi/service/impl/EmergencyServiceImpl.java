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

            String rawResponse = new HttpUtil().executeGet(
                emergencyApiUrl + "?serviceKey=" + emergencyServiceKey + "&crtDt=" + strToday + "&numOfRows=30"
            );
            
            // 두 JSON 데이터를 하나로 합치기
            JsonObject combinedJson = new JsonObject();
            JsonArray emergencyArray = new JsonArray();
            
            // 응답이 {today: "...", yesterday: "..."} 형태인지 확인
            if (rawResponse != null && !rawResponse.trim().isEmpty()) {
                try {
                    // 먼저 외부 JSON 파싱 (today/yesterday 구조 확인)
                    JsonObject outerJson = JsonParser.parseString(rawResponse).getAsJsonObject();
                    
                    if (outerJson.has("today") || outerJson.has("yesterday")) {
                        // today/yesterday 구조인 경우
                        parseEmergencyDataFromString(outerJson.get("today"), emergencyArray);
                        parseEmergencyDataFromString(outerJson.get("yesterday"), emergencyArray);
                    } else if (outerJson.has("body")) {
                        // 직접 body 구조인 경우 (기존 방식)
                        JsonArray items = outerJson.getAsJsonArray("body");
                        for (JsonElement item : items) {
                            emergencyArray.add(item);
                        }
                    }
                } catch (Exception e) {
                    // 파싱 실패 시 기존 방식으로 시도
                    log.warn("Failed to parse as outer JSON, trying direct parsing: {}", e.getMessage());
                    JsonObject directJson = JsonParser.parseString(rawResponse).getAsJsonObject();
                    if (directJson.has("body") && !directJson.get("body").isJsonNull()) {
                        JsonArray items = directJson.getAsJsonArray("body");
                        for (JsonElement item : items) {
                            emergencyArray.add(item);
                        }
                    }
                }
            }
            
            // 별도로 오늘과 어제 데이터를 각각 요청하는 방식도 지원
            if (emergencyArray.size() == 0) {
                String strEmergencyInfo = new HttpUtil().executeGet(
                    emergencyApiUrl + "?serviceKey=" + emergencyServiceKey + "&crtDt=" + strToday + "&numOfRows=30"
                );
                String strEmergencyInfoYesterday = new HttpUtil().executeGet(
                    emergencyApiUrl + "?serviceKey=" + emergencyServiceKey + "&crtDt=" + strYesterday + "&numOfRows=30"
                );
                
                // 오늘 데이터 파싱 및 추가
                if (strEmergencyInfo != null && !strEmergencyInfo.trim().isEmpty()) {
                    parseEmergencyDataFromString(JsonParser.parseString(strEmergencyInfo), emergencyArray);
                }
                
                // 어제 데이터 파싱 및 추가
                if (strEmergencyInfoYesterday != null && !strEmergencyInfoYesterday.trim().isEmpty()) {
                    parseEmergencyDataFromString(JsonParser.parseString(strEmergencyInfoYesterday), emergencyArray);
                }
            }
            
            // 각 항목을 프론트엔드가 사용하기 쉬운 형식으로 변환
            JsonArray formattedArray = new JsonArray();
            for (JsonElement element : emergencyArray) {
                JsonObject item = element.getAsJsonObject();
                JsonObject formattedItem = new JsonObject();
                
                // 필드 매핑 및 변환
                formattedItem.addProperty("msg", item.has("MSG_CN") ? item.get("MSG_CN").getAsString() : "");
                formattedItem.addProperty("locationName", item.has("RCPTN_RGN_NM") ? item.get("RCPTN_RGN_NM").getAsString() : "");
                formattedItem.addProperty("createDate", item.has("CRT_DT") ? item.get("CRT_DT").getAsString() : "");
                formattedItem.addProperty("registerDate", item.has("REG_YMD") ? item.get("REG_YMD").getAsString() : "");
                formattedItem.addProperty("emergencyStep", item.has("EMRG_STEP_NM") ? item.get("EMRG_STEP_NM").getAsString() : "");
                formattedItem.addProperty("category", item.has("DST_SE_NM") ? item.get("DST_SE_NM").getAsString() : "");
                formattedItem.addProperty("serialNumber", item.has("SN") ? item.get("SN").getAsLong() : 0);
                formattedItem.addProperty("modifyDate", item.has("MDFCN_YMD") ? item.get("MDFCN_YMD").getAsString() : "");
                
                formattedArray.add(formattedItem);
            }
            
            // 합쳐진 데이터를 새로운 JSON 객체에 추가
            combinedJson.add("items", formattedArray);
            cachedEmergencyData = combinedJson;
            lastUpdateTime = System.currentTimeMillis();
            log.debug("Successfully updated emergency data with {} items", formattedArray.size());
            
        } catch (Exception e) {
            log.error("Error renewing emergency data: {}", e.getMessage(), e);
            if (cachedEmergencyData == null) {
                cachedEmergencyData = createEmptyEmergencyJson();
            }
        }
    }
    
    /**
     * JSON 문자열이나 JsonElement에서 emergency 데이터를 파싱하여 배열에 추가
     */
    private void parseEmergencyDataFromString(JsonElement element, JsonArray targetArray) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        
        try {
            JsonObject jsonObj;
            
            // 문자열인 경우 JSON으로 파싱
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String jsonString = element.getAsString();
                if (jsonString == null || jsonString.trim().isEmpty()) {
                    return;
                }
                jsonObj = JsonParser.parseString(jsonString).getAsJsonObject();
            } else if (element.isJsonObject()) {
                jsonObj = element.getAsJsonObject();
            } else {
                return;
            }
            
            // body 배열 추출
            if (jsonObj.has("body") && !jsonObj.get("body").isJsonNull()) {
                JsonArray bodyArray = jsonObj.getAsJsonArray("body");
                for (JsonElement item : bodyArray) {
                    targetArray.add(item);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse emergency data from element: {}", e.getMessage());
        }
    }

    private JsonObject createEmptyEmergencyJson() {
        JsonObject jsonObject = new JsonObject();
        JsonArray items = new JsonArray();
        jsonObject.add("items", items);
        return jsonObject;
    }
}
