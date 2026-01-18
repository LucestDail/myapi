package com.example.myapi.controller;

import com.example.myapi.dto.system.SystemHistoryDto;
import com.example.myapi.dto.system.SystemStatusResponse;
import com.example.myapi.service.SystemHistoryService;
import com.example.myapi.service.SystemStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system")
@CrossOrigin(origins = "*")
public class SystemController {

    private final SystemStatusService systemStatusService;
    private final SystemHistoryService historyService;

    public SystemController(SystemStatusService systemStatusService,
                           SystemHistoryService historyService) {
        this.systemStatusService = systemStatusService;
        this.historyService = historyService;
    }

    /**
     * 서버 시스템 상태 조회
     * GET /api/system/status
     * 
     * CPU, 메모리, 디스크, JVM, GC, 스레드 등 정보 반환
     */
    @GetMapping("/status")
    public SystemStatusResponse getSystemStatus() {
        return systemStatusService.getSystemStatus();
    }

    /**
     * 시스템 히스토리 조회
     * GET /api/system/history?period=1h|24h|7d
     */
    @GetMapping("/history")
    public ResponseEntity<List<SystemHistoryDto>> getHistory(
            @RequestParam(defaultValue = "1h") String period) {
        List<SystemHistoryDto> history = historyService.getHistory(period).stream()
                .map(SystemHistoryDto::from)
                .toList();
        return ResponseEntity.ok(history);
    }

    /**
     * 최근 시스템 히스토리 조회
     * GET /api/system/history/recent?count=60
     */
    @GetMapping("/history/recent")
    public ResponseEntity<List<SystemHistoryDto>> getRecentHistory(
            @RequestParam(defaultValue = "60") int count) {
        List<SystemHistoryDto> history = historyService.getRecentHistory(count).stream()
                .map(SystemHistoryDto::from)
                .toList();
        return ResponseEntity.ok(history);
    }
}
