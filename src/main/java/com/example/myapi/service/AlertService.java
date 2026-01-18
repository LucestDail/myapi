package com.example.myapi.service;

import com.example.myapi.dto.alert.AlertEventDto;
import com.example.myapi.dto.alert.AlertLogDto;
import com.example.myapi.dto.alert.AlertRuleDto;
import com.example.myapi.entity.AlertLog;
import com.example.myapi.entity.AlertRule;
import com.example.myapi.repository.AlertLogRepository;
import com.example.myapi.repository.AlertRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 알림 서비스
 * 알림 규칙 관리 및 실시간 알림 전송
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    
    private final AlertRuleRepository ruleRepository;
    private final AlertLogRepository logRepository;
    private final ObjectMapper objectMapper;
    
    private final List<SseEmitter> alertEmitters = new CopyOnWriteArrayList<>();

    public AlertService(AlertRuleRepository ruleRepository, 
                       AlertLogRepository logRepository,
                       ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    // ==================== 알림 규칙 관리 ====================

    public List<AlertRuleDto> getRules(String userId) {
        return ruleRepository.findByUserId(userId).stream()
                .map(AlertRuleDto::from)
                .toList();
    }

    public AlertRuleDto createRule(String userId, AlertRuleDto dto) {
        AlertRule entity = dto.toEntity(userId);
        return AlertRuleDto.from(ruleRepository.save(entity));
    }

    @Transactional
    public AlertRuleDto updateRule(String userId, Long ruleId, AlertRuleDto dto) {
        AlertRule entity = ruleRepository.findById(ruleId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + ruleId));
        
        entity.setType(dto.type());
        entity.setTarget(dto.target());
        entity.setConditionType(dto.conditionType());
        entity.setThreshold(dto.threshold());
        if (dto.enabled() != null) {
            entity.setEnabled(dto.enabled());
        }
        
        return AlertRuleDto.from(ruleRepository.save(entity));
    }

    @Transactional
    public void deleteRule(String userId, Long ruleId) {
        ruleRepository.findById(ruleId)
                .filter(r -> r.getUserId().equals(userId))
                .ifPresent(ruleRepository::delete);
    }

    @Transactional
    public void toggleRule(String userId, Long ruleId, boolean enabled) {
        ruleRepository.findById(ruleId)
                .filter(r -> r.getUserId().equals(userId))
                .ifPresent(r -> {
                    r.setEnabled(enabled);
                    ruleRepository.save(r);
                });
    }

    // ==================== 알림 로그 관리 ====================

    public List<AlertLogDto> getLogs(String userId, int page, int size) {
        return logRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .getContent().stream()
                .map(AlertLogDto::from)
                .toList();
    }

    public List<AlertLogDto> getUnreadLogs(String userId) {
        return logRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId).stream()
                .map(AlertLogDto::from)
                .toList();
    }

    public long getUnreadCount(String userId) {
        return logRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(String userId, Long logId) {
        logRepository.findById(logId)
                .filter(l -> l.getUserId().equals(userId))
                .ifPresent(l -> {
                    l.setIsRead(true);
                    logRepository.save(l);
                });
    }

    @Transactional
    public void markAllAsRead(String userId) {
        logRepository.markAllAsRead(userId);
    }

    // ==================== 알림 발생 ====================

    /**
     * 알림 생성 및 실시간 전송
     */
    @Transactional
    public void triggerAlert(String userId, AlertEventDto event) {
        // 로그 저장
        AlertLog logEntry = new AlertLog(userId, event.type(), event.message(), event.severity());
        logRepository.save(logEntry);
        
        // 실시간 전송
        broadcastAlert(event);
        
        log.info("Alert triggered for user {}: {}", userId, event.message());
    }

    /**
     * 조건 검사 후 알림 발생
     */
    public void checkAndTrigger(String userId, String type, String target, double currentValue) {
        List<AlertRule> rules = ruleRepository.findByUserIdAndEnabled(userId, true).stream()
                .filter(r -> r.getType().equals(type))
                .filter(r -> target == null || r.getTarget() == null || r.getTarget().equals(target))
                .toList();

        for (AlertRule rule : rules) {
            boolean triggered = switch (rule.getConditionType()) {
                case "above" -> currentValue > rule.getThreshold();
                case "below" -> currentValue < rule.getThreshold();
                case "equals" -> Math.abs(currentValue - rule.getThreshold()) < 0.001;
                default -> false;
            };

            if (triggered) {
                String message = buildAlertMessage(rule, currentValue);
                String severity = determineSeverity(type, rule.getConditionType(), currentValue, rule.getThreshold());
                
                AlertEventDto event = AlertEventDto.create(type, message, severity, 
                        target, currentValue, rule.getThreshold());
                triggerAlert(userId, event);
            }
        }
    }

    private String buildAlertMessage(AlertRule rule, double currentValue) {
        String targetInfo = rule.getTarget() != null ? rule.getTarget() + " " : "";
        return String.format("%s%s: %.2f (임계값: %s %.2f)", 
                targetInfo, rule.getType(), currentValue, 
                rule.getConditionType(), rule.getThreshold());
    }

    private String determineSeverity(String type, String condition, double current, double threshold) {
        double diff = Math.abs(current - threshold);
        double ratio = diff / threshold;
        
        if (ratio > 0.2) return "danger";
        if (ratio > 0.1) return "warning";
        return "info";
    }

    // ==================== SSE 알림 스트림 ====================

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(0L); // 무한 타임아웃
        alertEmitters.add(emitter);

        emitter.onCompletion(() -> alertEmitters.remove(emitter));
        emitter.onTimeout(() -> alertEmitters.remove(emitter));
        emitter.onError(e -> alertEmitters.remove(emitter));

        return emitter;
    }

    private void broadcastAlert(AlertEventDto event) {
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : alertEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("alert")
                        .data(objectMapper.writeValueAsString(event)));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }

        alertEmitters.removeAll(deadEmitters);
    }

    // ==================== 정리 작업 ====================

    /**
     * 오래된 알림 로그 정리 (매일 자정)
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupOldLogs() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        logRepository.deleteOlderThan(cutoff);
        log.info("Cleaned up alert logs older than 30 days");
    }
}
