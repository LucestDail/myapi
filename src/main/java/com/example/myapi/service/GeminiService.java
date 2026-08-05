package com.example.myapi.service;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class GeminiService {

    @Value("${gemini.api.model:gemini-3-flash-preview}")
    private String modelName;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.gateway.base-url:}")
    private String gatewayBaseUrl;

    @Value("${gemini.gateway.token:}")
    private String gatewayToken;

    @Value("${gemini.gateway.service-id:myapi}")
    private String gatewayServiceId;

    private Client client;

    @PostConstruct
    void init() {
        client = buildClient();
        if (gatewayMode()) {
            log.info("[gemini] gateway mode: {} (service-id={})", normalizedGatewayBaseUrl(), gatewayServiceId);
        }
    }

    private boolean gatewayMode() {
        return gatewayBaseUrl != null && !gatewayBaseUrl.isBlank();
    }

    private String normalizedGatewayBaseUrl() {
        return gatewayBaseUrl.replaceAll("/+$", "");
    }

    private Client buildClient() {
        Client.Builder builder = Client.builder();
        if (gatewayMode()) {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("x-service-id", gatewayServiceId);
            if (gatewayToken != null && !gatewayToken.isBlank()) {
                headers.put("x-gateway-token", gatewayToken);
            }
            return builder
                    .apiKey(apiKey == null || apiKey.isBlank() ? "via-gateway" : apiKey)
                    .httpOptions(HttpOptions.builder()
                            .baseUrl(normalizedGatewayBaseUrl())
                            .headers(headers)
                            .build())
                    .build();
        }
        return builder.apiKey(apiKey).build();
    }

    /**
     * Generate content using Gemini API with streaming
     */
    public String generateContentStream(String prompt, Map<String, Object> settings, String systemInstruction) {
        try {
            GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();

            if (systemInstruction != null && !systemInstruction.isEmpty()) {
                configBuilder.systemInstruction(
                    Content.fromParts(Part.fromText(systemInstruction))
                );
            }

            if (settings != null) {
                if (settings.containsKey("temperature")) {
                    Object temp = settings.get("temperature");
                    if (temp instanceof Number) {
                        configBuilder.temperature(((Number) temp).floatValue());
                    }
                }

                if (settings.containsKey("topP")) {
                    Object topP = settings.get("topP");
                    if (topP instanceof Number) {
                        configBuilder.topP(((Number) topP).floatValue());
                    }
                }

                if (settings.containsKey("topK")) {
                    Object topK = settings.get("topK");
                    if (topK instanceof Number) {
                        configBuilder.topK(Float.valueOf(((Number) topK).floatValue()));
                    }
                }

                if (settings.containsKey("presencePenalty")) {
                    Object presencePenalty = settings.get("presencePenalty");
                    if (presencePenalty instanceof Number) {
                        configBuilder.presencePenalty(((Number) presencePenalty).floatValue());
                    }
                }

                if (settings.containsKey("frequencyPenalty")) {
                    Object frequencyPenalty = settings.get("frequencyPenalty");
                    if (frequencyPenalty instanceof Number) {
                        configBuilder.frequencyPenalty(((Number) frequencyPenalty).floatValue());
                    }
                }
            }

            GenerateContentConfig config = configBuilder.build();

            StringBuilder fullResponse = new StringBuilder();

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
