package com.example.myapi.controller;

import com.example.myapi.dto.dashboard.DashboardConfig;
import com.example.myapi.dto.dashboard.DashboardData;
import com.example.myapi.service.DashboardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.*;

/**
 * 대시보드 SSE 컨트롤러
 * 실시간으로 주식, 날씨, 뉴스, 시스템 정보를 스트리밍
 */
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);
    private static final long SSE_TIMEOUT = 0L; // 무한 타임아웃
    
    private final DashboardService dashboardService;
    private final ObjectMapper objectMapper;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public DashboardController(DashboardService dashboardService, ObjectMapper objectMapper) {
        this.dashboardService = dashboardService;
        this.objectMapper = objectMapper;
        startDataBroadcaster();
    }

    /**
     * SSE 스트림 연결
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("SSE connection completed. Active connections: {}", emitters.size());
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.info("SSE connection timed out. Active connections: {}", emitters.size());
        });

        emitter.onError(e -> {
            emitters.remove(emitter);
            log.warn("SSE connection error: {}. Active connections: {}", e.getMessage(), emitters.size());
        });

        log.info("New SSE connection established. Active connections: {}", emitters.size());

        // 연결 즉시 전체 데이터 전송 (비동기로 처리하여 연결 지연 방지)
        CompletableFuture.runAsync(() -> {
            try {
                // 초기 연결 확인을 위한 heartbeat 먼저 전송
                emitter.send(SseEmitter.event()
                        .name("connected")
                        .data("{\"status\":\"connected\"}"));
                
                // 약간의 지연 후 전체 데이터 전송
                Thread.sleep(100);
                
                DashboardData fullData = dashboardService.getFullData();
                emitter.send(SseEmitter.event()
                        .name("dashboard")
                        .data(objectMapper.writeValueAsString(fullData)));
            } catch (Exception e) {
                log.error("Failed to send initial data: {}", e.getMessage());
            }
        });

        return emitter;
    }

    /**
     * 현재 설정 조회
     */
    @GetMapping("/config")
    public ResponseEntity<DashboardConfig> getConfig() {
        return ResponseEntity.ok(dashboardService.getConfig());
    }

    /**
     * 설정 업데이트
     */
    @PostMapping("/config")
    public ResponseEntity<DashboardConfig> updateConfig(@RequestBody DashboardConfig config) {
        dashboardService.updateConfig(config);
        
        // 설정 변경 시 모든 클라이언트에 즉시 새 데이터 전송
        broadcastFullData();
        
        return ResponseEntity.ok(dashboardService.getConfig());
    }

    /**
     * 현재 데이터 스냅샷 조회 (비 SSE)
     */
    @GetMapping("/data")
    public ResponseEntity<DashboardData> getData() {
        return ResponseEntity.ok(dashboardService.getFullData());
    }

    /**
     * 주기적 데이터 브로드캐스트 시작
     * - 주식: 60초마다
     * - 날씨: 60초마다
     * - 뉴스: 5분마다
     * - 시스템: 5초마다
     */
    private void startDataBroadcaster() {
        // 시스템 데이터 (5초마다)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                broadcastSystemData();
            } catch (Exception e) {
                log.error("Error broadcasting system data: {}", e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS);

        // 전체 데이터 (60초마다)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                broadcastFullData();
            } catch (Exception e) {
                log.error("Error broadcasting full data: {}", e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    private void broadcastFullData() {
        if (emitters.isEmpty()) return;

        DashboardData data = dashboardService.getFullData();
        broadcast("dashboard", data);
    }

    private void broadcastSystemData() {
        if (emitters.isEmpty()) return;

        DashboardData data = DashboardData.system(dashboardService.getSystemData());
        broadcast("system", data);
    }

    private void broadcast(String eventName, DashboardData data) {
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(objectMapper.writeValueAsString(data)));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);
    }
}
