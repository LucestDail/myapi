package com.example.myapi.service;

import com.example.myapi.dto.settings.UserSettingsDto;
import com.example.myapi.entity.UserProfile;
import com.example.myapi.entity.UserSettings;
import com.example.myapi.repository.UserProfileRepository;
import com.example.myapi.repository.UserSettingsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 사용자 설정 관리 서비스
 */
@Service
public class UserSettingsService {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsService.class);

    private final UserSettingsRepository settingsRepository;
    private final UserProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    public UserSettingsService(
            UserSettingsRepository settingsRepository,
            UserProfileRepository profileRepository,
            ObjectMapper objectMapper) {
        this.settingsRepository = settingsRepository;
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 사용자 설정 조회
     */
    public UserSettingsDto getSettings(String userId) {
        return settingsRepository.findByUserId(userId)
                .map(this::parseSettings)
                .orElse(UserSettingsDto.defaultSettings());
    }

    /**
     * 사용자 설정 저장
     */
    @Transactional
    public UserSettingsDto saveSettings(String userId, UserSettingsDto settings) {
        // 사용자 프로필 확인/생성
        ensureUserProfile(userId);

        String json = serializeSettings(settings);
        
        UserSettings entity = settingsRepository.findByUserId(userId)
                .orElse(new UserSettings(userId, json));
        
        entity.setSettingsJson(json);
        settingsRepository.save(entity);
        
        log.info("Settings saved for user: {}", userId);
        return settings;
    }

    /**
     * 섹션별 설정 업데이트
     */
    @Transactional
    public UserSettingsDto updateSection(String userId, String section, Object sectionData) {
        UserSettingsDto current = getSettings(userId);
        UserSettingsDto updated = switch (section) {
            case "media" -> new UserSettingsDto(
                    objectMapper.convertValue(sectionData, UserSettingsDto.MediaSettings.class),
                    current.stocks(), current.news(), current.weather(), current.system(), current.general()
            );
            case "stocks" -> new UserSettingsDto(
                    current.media(),
                    objectMapper.convertValue(sectionData, UserSettingsDto.StockSettings.class),
                    current.news(), current.weather(), current.system(), current.general()
            );
            case "news" -> new UserSettingsDto(
                    current.media(), current.stocks(),
                    objectMapper.convertValue(sectionData, UserSettingsDto.NewsSettings.class),
                    current.weather(), current.system(), current.general()
            );
            case "weather" -> new UserSettingsDto(
                    current.media(), current.stocks(), current.news(),
                    objectMapper.convertValue(sectionData, UserSettingsDto.WeatherSettings.class),
                    current.system(), current.general()
            );
            case "system" -> new UserSettingsDto(
                    current.media(), current.stocks(), current.news(), current.weather(),
                    objectMapper.convertValue(sectionData, UserSettingsDto.SystemSettings.class),
                    current.general()
            );
            case "general" -> new UserSettingsDto(
                    current.media(), current.stocks(), current.news(), current.weather(), current.system(),
                    objectMapper.convertValue(sectionData, UserSettingsDto.GeneralSettings.class)
            );
            default -> throw new IllegalArgumentException("Unknown section: " + section);
        };
        
        return saveSettings(userId, updated);
    }

    /**
     * 사용자 설정 초기화
     */
    @Transactional
    public UserSettingsDto resetSettings(String userId) {
        settingsRepository.findByUserId(userId)
                .ifPresent(settingsRepository::delete);
        return UserSettingsDto.defaultSettings();
    }

    /**
     * 사용자 프로필 확인/생성
     */
    private void ensureUserProfile(String userId) {
        if (!profileRepository.existsById(userId)) {
            profileRepository.save(new UserProfile(userId));
        }
    }

    /**
     * 설정 JSON 파싱
     */
    private UserSettingsDto parseSettings(UserSettings entity) {
        try {
            return objectMapper.readValue(entity.getSettingsJson(), UserSettingsDto.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse settings for user {}: {}", entity.getUserId(), e.getMessage());
            return UserSettingsDto.defaultSettings();
        }
    }

    /**
     * 설정 직렬화
     */
    private String serializeSettings(UserSettingsDto settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize settings", e);
        }
    }
}
