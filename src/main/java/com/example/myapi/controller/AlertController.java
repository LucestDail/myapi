package com.example.myapi.controller;

import com.example.myapi.dto.alert.AlertLogDto;
import com.example.myapi.dto.alert.AlertRuleDto;
import com.example.myapi.service.AlertService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 알림 API 컨트롤러
 */
@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "*")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    // ==================== 알림 규칙 ====================

    @GetMapping("/rules")
    public ResponseEntity<List<AlertRuleDto>> getRules(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(alertService.getRules(effectiveUserId));
    }

    @PostMapping("/rules")
    public ResponseEntity<AlertRuleDto> createRule(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody AlertRuleDto dto) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(alertService.createRule(effectiveUserId, dto));
    }

    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<AlertRuleDto> updateRule(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable Long ruleId,
            @RequestBody AlertRuleDto dto) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(alertService.updateRule(effectiveUserId, ruleId, dto));
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable Long ruleId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        alertService.deleteRule(effectiveUserId, ruleId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/rules/{ruleId}/toggle")
    public ResponseEntity<Void> toggleRule(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable Long ruleId,
            @RequestBody Map<String, Boolean> body) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        alertService.toggleRule(effectiveUserId, ruleId, body.getOrDefault("enabled", true));
        return ResponseEntity.ok().build();
    }

    // ==================== 알림 로그 ====================

    @GetMapping("/logs")
    public ResponseEntity<List<AlertLogDto>> getLogs(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(alertService.getLogs(effectiveUserId, page, size));
    }

    @GetMapping("/logs/unread")
    public ResponseEntity<List<AlertLogDto>> getUnreadLogs(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(alertService.getUnreadLogs(effectiveUserId));
    }

    @GetMapping("/logs/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(Map.of("count", alertService.getUnreadCount(effectiveUserId)));
    }

    @PostMapping("/logs/{logId}/read")
    public ResponseEntity<Void> markAsRead(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable Long logId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        alertService.markAsRead(effectiveUserId, logId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logs/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        alertService.markAllAsRead(effectiveUserId);
        return ResponseEntity.ok().build();
    }

    // ==================== SSE 스트림 ====================

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlerts() {
        return alertService.createEmitter();
    }
}
