package com.example.myapi.controller;

import com.example.myapi.service.EmergencyService;
import com.example.myapi.service.NewsService;
import com.example.myapi.service.TrafficService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/social")
public class SocialController {

    @Autowired
    private NewsService newsService;

    @Autowired
    private TrafficService trafficService;

    @Autowired
    private EmergencyService emergencyService;

    @GetMapping(value = "/news", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getNews() {
        try {
            JsonArray newsArray = newsService.getCachedNews();
            JsonObject result = new JsonObject();
            JsonObject data = new JsonObject();
            data.add("items", newsArray);
            result.add("data", data);
            return result.toString();
        } catch (Exception e) {
            log.error("Error getting news: {}", e.getMessage());
            JsonObject result = new JsonObject();
            JsonObject data = new JsonObject();
            JsonArray emptyArray = new JsonArray();
            data.add("items", emptyArray);
            result.add("data", data);
            return result.toString();
        }
    }

    @GetMapping(value = "/traffic", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getTraffic() {
        try {
            JsonObject trafficData = trafficService.getTrafficInfo();
            return trafficData != null ? trafficData.toString() : "{}";
        } catch (Exception e) {
            log.error("Error getting traffic info: {}", e.getMessage());
            JsonObject result = new JsonObject();
            JsonObject body = new JsonObject();
            JsonArray items = new JsonArray();
            body.add("items", items);
            result.add("body", body);
            return result.toString();
        }
    }

    @GetMapping(value = "/emergency", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getEmergency() {
        try {
            JsonObject emergencyData = emergencyService.getEmergencyInfo();
            return emergencyData != null ? emergencyData.toString() : "{}";
        } catch (Exception e) {
            log.error("Error getting emergency info: {}", e.getMessage());
            JsonObject result = new JsonObject();
            JsonArray items = new JsonArray();
            result.add("items", items);
            return result.toString();
        }
    }

    @org.springframework.beans.factory.annotation.Value("${traffic.api.url:https://openapi.its.go.kr:9443/eventInfo}")
    private String trafficApiUrl;
    
    @org.springframework.beans.factory.annotation.Value("${traffic.api.key:}")
    private String trafficApiKey;

    @GetMapping(value = "/traffic/raw", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getTrafficRaw() {
        try {
            String rawResponse = new com.example.myapi.util.HttpUtil().executeGet(
                trafficApiUrl + "?apiKey=" + trafficApiKey + "&type=all&eventType=all&getType=json"
            );
            JsonObject result = new JsonObject();
            result.addProperty("raw", rawResponse != null ? rawResponse : "");
            return result.toString();
        } catch (Exception e) {
            log.error("Error getting raw traffic data: {}", e.getMessage());
            JsonObject result = new JsonObject();
            result.addProperty("raw", "");
            result.addProperty("error", e.getMessage());
            return result.toString();
        }
    }

    @org.springframework.beans.factory.annotation.Value("${emergency.api.serviceKey:}")
    private String emergencyServiceKey;
    
    @org.springframework.beans.factory.annotation.Value("${emergency.api.url:https://www.safetydata.go.kr/V2/api/DSSP-IF-00247}")
    private String emergencyApiUrl;

    @GetMapping(value = "/emergency/raw", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getEmergencyRaw() {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd");
            java.util.TimeZone seoul = java.util.TimeZone.getTimeZone("Asia/Seoul");
            sdf.setTimeZone(seoul);
            String strToday = sdf.format(System.currentTimeMillis());
            String strYesterday = sdf.format(System.currentTimeMillis() - 24 * 60 * 60 * 1000);

            String todayRaw = new com.example.myapi.util.HttpUtil().executeGet(
                emergencyApiUrl + "?serviceKey=" + emergencyServiceKey + "&crtDt=" + strToday
            );
            String yesterdayRaw = new com.example.myapi.util.HttpUtil().executeGet(
                emergencyApiUrl + "?serviceKey=" + emergencyServiceKey + "&crtDt=" + strYesterday
            );

            JsonObject result = new JsonObject();
            result.addProperty("today", todayRaw != null ? todayRaw : "");
            result.addProperty("yesterday", yesterdayRaw != null ? yesterdayRaw : "");
            return result.toString();
        } catch (Exception e) {
            log.error("Error getting raw emergency data: {}", e.getMessage());
            JsonObject result = new JsonObject();
            result.addProperty("today", "");
            result.addProperty("yesterday", "");
            result.addProperty("error", e.getMessage());
            return result.toString();
        }
    }

    private static final long SSE_TIMEOUT = 3600000L; // 1 hour
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    @GetMapping(value = "/news/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNews() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        emitter.onCompletion(() -> log.debug("News SSE connection completed"));
        emitter.onTimeout(() -> log.debug("News SSE connection timed out"));
        emitter.onError(e -> log.error("News SSE connection error: {}", e.getMessage()));

        // Send initial data
        try {
            JsonObject result = new JsonObject();
            JsonArray newsArray = newsService.getCachedNews();
            JsonObject data = new JsonObject();
            data.add("items", newsArray);
            result.add("data", data);
            emitter.send(SseEmitter.event().data(result.toString()));
        } catch (Exception e) {
            log.error("Error sending initial news data: {}", e.getMessage());
        }

        // Schedule periodic updates
        scheduler.scheduleAtFixedRate(() -> {
            try {
                JsonObject result = new JsonObject();
                JsonArray newsArray = newsService.getCachedNews();
                JsonObject data = new JsonObject();
                data.add("items", newsArray);
                result.add("data", data);
                emitter.send(SseEmitter.event().data(result.toString()));
            } catch (IOException e) {
                log.error("Error sending news update: {}", e.getMessage());
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("Error in news stream: {}", e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS);

        return emitter;
    }

    @GetMapping(value = "/traffic/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTraffic() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        emitter.onCompletion(() -> log.debug("Traffic SSE connection completed"));
        emitter.onTimeout(() -> log.debug("Traffic SSE connection timed out"));
        emitter.onError(e -> log.error("Traffic SSE connection error: {}", e.getMessage()));

        // Send initial data
        try {
            JsonObject trafficData = trafficService.getTrafficInfo();
            emitter.send(SseEmitter.event().data(trafficData != null ? trafficData.toString() : "{}"));
        } catch (Exception e) {
            log.error("Error sending initial traffic data: {}", e.getMessage());
        }

        // Schedule periodic updates
        scheduler.scheduleAtFixedRate(() -> {
            try {
                JsonObject trafficData = trafficService.getTrafficInfo();
                emitter.send(SseEmitter.event().data(trafficData != null ? trafficData.toString() : "{}"));
            } catch (IOException e) {
                log.error("Error sending traffic update: {}", e.getMessage());
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("Error in traffic stream: {}", e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS);

        return emitter;
    }

    @GetMapping(value = "/emergency/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEmergency() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        emitter.onCompletion(() -> log.debug("Emergency SSE connection completed"));
        emitter.onTimeout(() -> log.debug("Emergency SSE connection timed out"));
        emitter.onError(e -> log.error("Emergency SSE connection error: {}", e.getMessage()));

        // Send initial data
        try {
            JsonObject emergencyData = emergencyService.getEmergencyInfo();
            emitter.send(SseEmitter.event().data(emergencyData != null ? emergencyData.toString() : "{}"));
        } catch (Exception e) {
            log.error("Error sending initial emergency data: {}", e.getMessage());
        }

        // Schedule periodic updates
        scheduler.scheduleAtFixedRate(() -> {
            try {
                JsonObject emergencyData = emergencyService.getEmergencyInfo();
                emitter.send(SseEmitter.event().data(emergencyData != null ? emergencyData.toString() : "{}"));
            } catch (IOException e) {
                log.error("Error sending emergency update: {}", e.getMessage());
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("Error in emergency stream: {}", e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS);

        return emitter;
    }
}
