package com.example.myapi.controller;

import com.example.myapi.dto.alert.AlertEventDto;
import com.example.myapi.dto.dashboard.DashboardConfig;
import com.example.myapi.dto.dashboard.DashboardData;
import com.example.myapi.service.AlertIntegrationService;
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
import java.util.concurrent.ConcurrentHashMap;

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
    private final AlertIntegrationService alertIntegrationService;
    private final ObjectMapper objectMapper;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    // 사용자 ID와 emitter 매핑 (향후 개선용)
    private final ConcurrentHashMap<SseEmitter, String> emitterUserMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public DashboardController(DashboardService dashboardService, 
                              AlertIntegrationService alertIntegrationService,
                              ObjectMapper objectMapper) {
        this.dashboardService = dashboardService;
        this.alertIntegrationService = alertIntegrationService;
        this.objectMapper = objectMapper;
        startDataBroadcaster();
    }

    /**
     * SSE 스트림 연결 (사용자별)
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "userId", required = false) String paramUserId) {
        // 우선순위: RequestAttribute > RequestHeader > RequestParam
        String effectiveUserId = userId != null ? userId : (headerUserId != null ? headerUserId : paramUserId);
        if (effectiveUserId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.add(emitter);
        emitterUserMap.put(emitter, effectiveUserId);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            emitterUserMap.remove(emitter);
            log.info("SSE connection completed. Active connections: {}", emitters.size());
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitterUserMap.remove(emitter);
            log.info("SSE connection timed out. Active connections: {}", emitters.size());
        });

        emitter.onError(e -> {
            emitters.remove(emitter);
            emitterUserMap.remove(emitter);
            log.warn("SSE connection error: {}. Active connections: {}", e.getMessage(), emitters.size());
        });

        log.info("New SSE connection established for user: {}. Active connections: {}", effectiveUserId, emitters.size());

        // 연결 즉시 전체 데이터 전송 (비동기로 처리하여 연결 지연 방지)
        final String finalUserId = effectiveUserId;
        CompletableFuture.runAsync(() -> {
            try {
                // 초기 연결 확인을 위한 heartbeat 먼저 전송
                emitter.send(SseEmitter.event()
                        .name("connected")
                        .data("{\"status\":\"connected\"}"));
                
                // 약간의 지연 후 전체 데이터 전송
                Thread.sleep(100);
                
                DashboardData fullData = dashboardService.getFullData(finalUserId);
                emitter.send(SseEmitter.event()
                        .name("dashboard")
                        .data(objectMapper.writeValueAsString(fullData)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitters.remove(emitter);
            } catch (Exception e) {
                // Client disconnected during initial data send - just remove
                emitters.remove(emitter);
                log.debug("Initial data send failed (client disconnected): {}", e.getMessage());
            }
        });

        return emitter;
    }

    /**
     * 현재 설정 조회 (사용자별)
     */
    @GetMapping("/config")
    public ResponseEntity<DashboardConfig> getConfig(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(dashboardService.getConfig(effectiveUserId));
    }

    /**
     * 설정 업데이트 (사용자별)
     */
    @PostMapping("/config")
    public ResponseEntity<DashboardConfig> updateConfig(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody DashboardConfig config) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        DashboardConfig savedConfig = dashboardService.updateConfig(effectiveUserId, config);
        
        // 설정 변경 시 해당 사용자에게 즉시 새 데이터 전송 (사용자별 브로드캐스트)
        // SSE 재연결 시에도 데이터를 받지만, 즉시 반영을 위해 브로드캐스트도 수행
        CompletableFuture.runAsync(() -> {
            try {
                // 잠시 대기하여 SSE 재연결이 완료될 시간 제공
                Thread.sleep(300);
                broadcastFullDataForUser(effectiveUserId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.debug("Failed to broadcast after config update: {}", e.getMessage());
            }
        });
        
        return ResponseEntity.ok(savedConfig);
    }

    /**
     * 현재 데이터 스냅샷 조회 (비 SSE, 사용자별)
     */
    @GetMapping("/data")
    public ResponseEntity<DashboardData> getData(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(dashboardService.getFullData(effectiveUserId));
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

    /**
     * 전체 데이터 브로드캐스트 (모든 사용자에게 각자의 설정에 맞는 데이터 전송)
     */
    private void broadcastFullData() {
        if (emitters.isEmpty()) return;
        
        // 각 사용자별로 개인화된 데이터 브로드캐스트
        for (SseEmitter emitter : emitters) {
            String userId = emitterUserMap.get(emitter);
            if (userId == null) continue;
            
            try {
                DashboardData data = dashboardService.getFullData(userId);
                broadcastToEmitter(emitter, "dashboard", data);
                
                // 알림 조건 검사 (한 번만 수행)
                if (emitter == emitters.get(0) && data.stocks() != null && data.stocks().quotes() != null) {
                    alertIntegrationService.checkStockAlerts(data.stocks().quotes());
                }
                if (emitter == emitters.get(0) && data.weather() != null) {
                    alertIntegrationService.checkWeatherAlerts(data.weather());
                }
            } catch (Exception e) {
                log.debug("Failed to broadcast to user {}: {}", userId, e.getMessage());
            }
        }
    }

    /**
     * 특정 사용자에게 전체 데이터 브로드캐스트
     */
    private void broadcastFullDataForUser(String userId) {
        if (emitters.isEmpty()) {
            log.debug("No active emitters for user {} broadcast", userId);
            return;
        }

        try {
            DashboardData data = dashboardService.getFullData(userId);
            
            int sentCount = 0;
            // 해당 사용자의 모든 emitter에 전송
            for (SseEmitter emitter : emitters) {
                String emitterUserId = emitterUserMap.get(emitter);
                if (userId.equals(emitterUserId)) {
                    log.debug("Broadcasting data to user {} emitter, stocks: {}", 
                            userId, data.stocks() != null && data.stocks().quotes() != null 
                            ? data.stocks().quotes().size() : 0);
                    broadcastToEmitter(emitter, "dashboard", data);
                    sentCount++;
                }
            }
            
            if (sentCount == 0) {
                log.warn("No active emitter found for user {} to broadcast data. Active emitters: {}", 
                        userId, emitters.size());
            } else {
                log.debug("Successfully broadcasted data to {} emitter(s) for user {}", sentCount, userId);
            }

            // 알림 조건 검사
            if (data.stocks() != null && data.stocks().quotes() != null) {
                alertIntegrationService.checkStockAlerts(data.stocks().quotes());
            }
            if (data.weather() != null) {
                alertIntegrationService.checkWeatherAlerts(data.weather());
            }
        } catch (Exception e) {
            log.error("Error broadcasting data to user {}: {}", userId, e.getMessage(), e);
        }
    }

    private void broadcastSystemData() {
        if (emitters.isEmpty()) return;

        DashboardData.SystemData systemData = dashboardService.getSystemData();
        DashboardData data = DashboardData.system(systemData);
        
        // 시스템 데이터는 공통이므로 모든 사용자에게 브로드캐스트
        broadcast("system", data);

        // 시스템 알림 조건 검사
        try {
            alertIntegrationService.checkSystemAlerts(systemData);
        } catch (Exception e) {
            log.debug("System alert check error: {}", e.getMessage());
        }
    }

    /**
     * 단일 emitter에 이벤트 전송
     */
    private void broadcastToEmitter(SseEmitter emitter, String eventName, DashboardData data) {
        if (data == null) return;
        
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Failed to serialize data: {}", e.getMessage());
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(jsonData));
            
            // 데이터 전송 로그 (디버깅용)
            if ("dashboard".equals(eventName) && data.stocks() != null) {
                log.debug("Sent dashboard data via SSE: {} stocks", data.stocks().quotes().size());
            }
        } catch (Exception e) {
            // 클라이언트 연결 끊김 - 제거 대상으로 표시
            log.debug("Failed to send SSE event: {}", e.getMessage());
            emitters.remove(emitter);
            emitterUserMap.remove(emitter);
        }
    }

    /**
     * 모든 emitter에 동일한 데이터 브로드캐스트 (시스템 데이터 등 공통 데이터용)
     */
    private void broadcast(String eventName, DashboardData data) {
        if (emitters.isEmpty() || data == null) return;
        
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Failed to serialize data: {}", e.getMessage());
            return;
        }

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(jsonData));
            } catch (Exception e) {
                // Any error means client disconnected - just mark for removal
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            deadEmitters.forEach(emitterUserMap::remove);
            log.debug("Removed {} dead emitters. Active: {}", deadEmitters.size(), emitters.size());
        }
    }

    /**
     * 알림 브로드캐스트
     */
    public void broadcastAlert(AlertEventDto alert) {
        if (emitters.isEmpty()) return;

        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(alert);
        } catch (Exception e) {
            log.error("Failed to serialize alert: {}", e.getMessage());
            return;
        }

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("alert")
                        .data(jsonData));
            } catch (Exception e) {
                // Any error means client disconnected - just mark for removal
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            log.debug("Removed {} dead emitters after alert. Active: {}", deadEmitters.size(), emitters.size());
        }
    }

    /**
     * 현재 연결 수 조회
     */
    @GetMapping("/connections")
    public ResponseEntity<Integer> getConnectionCount() {
        return ResponseEntity.ok(emitters.size());
    }
}
