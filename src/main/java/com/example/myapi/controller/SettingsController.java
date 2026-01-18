package com.example.myapi.controller;

import com.example.myapi.dto.settings.UserSettingsDto;
import com.example.myapi.service.UserSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 사용자 설정 API 컨트롤러
 */
@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*")
public class SettingsController {

    private final UserSettingsService settingsService;

    public SettingsController(UserSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * 전체 설정 조회
     */
    @GetMapping
    public ResponseEntity<UserSettingsDto> getSettings(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.ok(UserSettingsDto.defaultSettings());
        }
        return ResponseEntity.ok(settingsService.getSettings(effectiveUserId));
    }

    /**
     * 전체 설정 저장
     */
    @PutMapping
    public ResponseEntity<UserSettingsDto> saveSettings(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody UserSettingsDto settings) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(settingsService.saveSettings(effectiveUserId, settings));
    }

    /**
     * 섹션별 설정 업데이트
     */
    @PatchMapping("/{section}")
    public ResponseEntity<UserSettingsDto> updateSection(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable String section,
            @RequestBody Map<String, Object> sectionData) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(settingsService.updateSection(effectiveUserId, section, sectionData));
    }

    /**
     * 설정 초기화
     */
    @DeleteMapping
    public ResponseEntity<UserSettingsDto> resetSettings(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String effectiveUserId = userId != null ? userId : headerUserId;
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(settingsService.resetSettings(effectiveUserId));
    }
}
