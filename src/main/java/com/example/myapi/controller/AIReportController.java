package com.example.myapi.controller;

import com.example.myapi.dto.report.ReportHistoryDto;
import com.example.myapi.entity.ReportHistory;
import com.example.myapi.service.AiReportService;
import com.example.myapi.service.ReportHistoryService;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ai-report")
public class AIReportController {

    private final AiReportService aiReportService;
    private final ReportHistoryService historyService;

    /**
     * 화면에서 만든 리포트도 이력에 남길지. 요청 경로에 INSERT 한 번(+상한 초과 시 DELETE)이
     * 늘어난다 — SQLite 커넥션이 하나라 무시할 수 없는 비용이지만, Gemini 호출이 이미 수 초라
     * 상대적으로 작고 <b>사용자가 명시적으로 누른 한 번</b>에만 일어난다(익명 요청마다 쌓이던
     * 프로필 사고와 다른 점이 이것이다). 그래도 끌 수 있게 열어 둔다.
     */
    @Value("${myapi.report.history.save-manual:true}")
    private boolean saveManual = true;

    public AIReportController(AiReportService aiReportService, ReportHistoryService historyService) {
        this.aiReportService = aiReportService;
        this.historyService = historyService;
    }

    /**
     * 데이터 토픽 목록 조회
     */
    @GetMapping(value = "/topics", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getTopics() {
        JsonObject result = new JsonObject();
        JsonObject data = new JsonObject();
        JsonObject topics = new JsonObject();
        for (String key : AiReportService.Topics.NAMES) {
            topics.addProperty(key, true);
        }
        data.add("topics", topics);
        result.add("data", data);
        return result.toString();
    }

    /**
     * AI 보고서 생성 (RAG 방식 - 모든 선택된 데이터를 컨텍스트로 전달)
     */
    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public String generateReport(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody Map<String, Object> request) {
        String effectiveUserId = headerUserId != null ? headerUserId : userId;
        if (effectiveUserId == null || effectiveUserId.isBlank()) {
            effectiveUserId = "default";
            log.warn("AI report request without userId — using default tickers");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Boolean> topics = (Map<String, Boolean>) request.getOrDefault("topics", new HashMap<>());

            @SuppressWarnings("unchecked")
            Map<String, Object> settings = (Map<String, Object>) request.getOrDefault("settings", new HashMap<>());

            @SuppressWarnings("unchecked")
            Map<String, Object> clientSnapshot = (Map<String, Object>) request.get("snapshot");

            String report = aiReportService.generate(
                    effectiveUserId, AiReportService.Topics.from(topics), settings, clientSnapshot);

            if (saveManual) {
                historyService.save(effectiveUserId, ReportHistory.TYPE_MANUAL, report);
            }

            JsonObject result = new JsonObject();
            JsonObject data = new JsonObject();
            data.addProperty("report", report);
            result.add("data", data);
            return result.toString();

        } catch (Exception e) {
            log.error("Error generating AI report: {}", e.getMessage(), e);
            JsonObject result = new JsonObject();
            JsonObject data = new JsonObject();
            data.addProperty("report", "리포트 생성 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.");
            data.addProperty("error", e.getMessage());
            result.add("data", data);
            return result.toString();
        }
    }

    // ==================== 리포트 이력 ====================

    /**
     * 이력 목록. 본문은 싣지 않는다(발췌만) — 목록 한 번에 전문 20건을 끌어오면
     * 커넥션 하나짜리 SQLite 에서 그대로 지연이 된다.
     *
     * @param type 비우면 전체, {@code daily}/{@code weekly}/{@code manual} 로 거를 수 있다
     */
    @GetMapping("/history")
    public ResponseEntity<List<ReportHistoryDto>> getHistory(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String effectiveUserId = headerUserId != null ? headerUserId : userId;
        if (effectiveUserId == null || effectiveUserId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(type == null || type.isBlank()
                ? historyService.list(effectiveUserId, Math.max(page, 0), safeSize)
                : historyService.listByType(effectiveUserId, type, Math.max(page, 0), safeSize));
    }

    /** 이력 상세(전문). 남의 이력은 못 읽는다 — 조회 조건에 userId 가 함께 들어간다. */
    @GetMapping("/history/{id}")
    public ResponseEntity<ReportHistoryDto> getHistoryDetail(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable Long id) {
        String effectiveUserId = headerUserId != null ? headerUserId : userId;
        if (effectiveUserId == null || effectiveUserId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return historyService.get(effectiveUserId, id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
