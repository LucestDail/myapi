package com.example.myapi.controller;

import com.example.myapi.dto.productivity.TimerDto;
import com.example.myapi.service.TimerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 타이머/포모도로 API 컨트롤러
 */
@RestController
@RequestMapping("/api/timer")
@CrossOrigin(origins = "*")
public class TimerController {

    private final TimerService timerService;

    public TimerController(TimerService timerService) {
        this.timerService = timerService;
    }

    /**
     * 타이머 조회
     */
    @GetMapping("/{type}")
    public ResponseEntity<TimerDto> getTimer(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable String type) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return timerService.getTimer(effectiveUserId, type)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 타이머 생성/초기화
     */
    @PostMapping("/{type}")
    public ResponseEntity<TimerDto> createTimer(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable String type,
            @RequestBody Map<String, Integer> body) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        int duration = body.getOrDefault("durationSeconds", 300); // 기본 5분
        return ResponseEntity.ok(timerService.createTimer(effectiveUserId, type, duration));
    }

    /**
     * 타이머 시작
     */
    @PostMapping("/{type}/start")
    public ResponseEntity<TimerDto> startTimer(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable String type) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(timerService.startTimer(effectiveUserId, type));
    }

    /**
     * 타이머 일시정지
     */
    @PostMapping("/{type}/pause")
    public ResponseEntity<TimerDto> pauseTimer(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable String type) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(timerService.pauseTimer(effectiveUserId, type));
    }

    /**
     * 타이머 정지/리셋
     */
    @PostMapping("/{type}/stop")
    public ResponseEntity<TimerDto> stopTimer(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable String type) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(timerService.stopTimer(effectiveUserId, type));
    }

    // ==================== 포모도로 전용 ====================

    /**
     * 포모도로 타이머 생성
     */
    @PostMapping("/pomodoro/init")
    public ResponseEntity<TimerDto> initPomodoro(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(timerService.createPomodoro(effectiveUserId));
    }

    /**
     * 포모도로 완료
     */
    @PostMapping("/pomodoro/complete")
    public ResponseEntity<TimerDto> completePomodoro(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(timerService.completePomodoro(effectiveUserId));
    }

    /**
     * 다음 포모도로 시작
     */
    @PostMapping("/pomodoro/next")
    public ResponseEntity<TimerDto> nextPomodoro(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(timerService.startNextPomodoro(effectiveUserId));
    }
}
