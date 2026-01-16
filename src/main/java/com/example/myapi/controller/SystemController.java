package com.example.myapi.controller;

import com.example.myapi.dto.system.SystemStatusResponse;
import com.example.myapi.service.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemStatusService systemStatusService;

    public SystemController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
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
}
