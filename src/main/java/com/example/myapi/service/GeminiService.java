package com.example.myapi.service;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class GeminiService {

    @Value("${gemini.api.model:gemini-3-flash-preview}")
    private String modelName;
    
    @Value("${gemini.api.key:}")
    private String apiKey;

    /**
     * Generate content using Gemini API with streaming
     */
    public String generateContentStream(String prompt, Map<String, Object> settings, String systemInstruction) {
        try {
            // API 키로 클라이언트 생성 (Builder 패턴 사용)
            Client client = Client.builder()
                    .apiKey(apiKey)
                    .build();
            
            // GenerateContentConfig 생성 (설정값 적용)
            GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();
            
            // 시스템 프롬프트 설정
            if (systemInstruction != null && !systemInstruction.isEmpty()) {
                configBuilder.systemInstruction(
                    Content.fromParts(Part.fromText(systemInstruction))
                );
            }
            
            if (settings != null) {
                // Temperature (0.0-2.0)
                if (settings.containsKey("temperature")) {
                    Object temp = settings.get("temperature");
                    if (temp instanceof Number) {
                        configBuilder.temperature(((Number) temp).floatValue());
                    }
                }
                
                // Top P (0.0-1.0)
                if (settings.containsKey("topP")) {
                    Object topP = settings.get("topP");
                    if (topP instanceof Number) {
                        configBuilder.topP(((Number) topP).floatValue());
                    }
                }
                
                // Top K (Float로 변환)
                if (settings.containsKey("topK")) {
                    Object topK = settings.get("topK");
                    if (topK instanceof Number) {
                        configBuilder.topK(Float.valueOf(((Number) topK).floatValue()));
                    }
                }
                
                // Presence Penalty
                if (settings.containsKey("presencePenalty")) {
                    Object presencePenalty = settings.get("presencePenalty");
                    if (presencePenalty instanceof Number) {
                        configBuilder.presencePenalty(((Number) presencePenalty).floatValue());
                    }
                }
                
                // Frequency Penalty
                if (settings.containsKey("frequencyPenalty")) {
                    Object frequencyPenalty = settings.get("frequencyPenalty");
                    if (frequencyPenalty instanceof Number) {
                        configBuilder.frequencyPenalty(((Number) frequencyPenalty).floatValue());
                    }
                }
            }
            
            GenerateContentConfig config = configBuilder.build();
            
            StringBuilder fullResponse = new StringBuilder();
            
            // 스트리밍으로 응답 받기
            ResponseStream<GenerateContentResponse> responseStream = 
                client.models.generateContentStream(modelName, prompt, config);
            
            try {
                for (GenerateContentResponse response : responseStream) {
                    if (response.text() != null) {
                        fullResponse.append(response.text());
                    }
                }
            } finally {
                if (responseStream != null) {
                    try {
                        responseStream.close();
                    } catch (Exception closeException) {
                        log.warn("Error closing response stream: {}", closeException.getMessage());
                    }
                }
            }
            
            return fullResponse.toString();
            
        } catch (Exception e) {
            log.error("Error generating content with Gemini API: {}", e.getMessage(), e);
            return "AI 서비스 호출 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.";
        }
    }

    /**
     * Generate content using Gemini API (non-streaming, fallback)
     */
    public String generateContent(String prompt, Map<String, Object> settings, String systemInstruction) {
        return generateContentStream(prompt, settings, systemInstruction);
    }
}
