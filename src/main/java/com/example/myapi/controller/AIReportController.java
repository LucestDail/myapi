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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    /**
     * 생성 파라미터는 서버가 정한다(2026-09-26).
     *
     * <p>왜 화면에서 뺐나: Temperature·Top P·Top K·Penalty 를 사용자에게 입력받고 있었는데,
     * 무엇을 넣어야 하는지 판단할 근거가 화면에 없었다. 잘못 넣으면 리포트 품질만 나빠지고
     * 되돌릴 방법도 안내되지 않았다. 게다가 게이트웨이가 OpenRouter 로 번역해 넘기므로
     * 일부 값은 그대로 전달되지도 않는다 — 조정한 만큼 반영된다는 보장이 없는 입력이었다.
     */
    private static final Map<String, Object> FIXED_SETTINGS = Map.of(
            "temperature", 1.0,
            "topP", 0.95,
            "topK", 40,
            "presencePenalty", 0.0,
            "frequencyPenalty", 0.0);

    /** SSE 생성은 오래 걸린다(수 초~수십 초). 요청 스레드를 붙잡지 않는다. */
    private final ExecutorService streamExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "ai-report-stream");
                t.setDaemon(true);
                return t;
            });

    private static final long STREAM_TIMEOUT_MS = 300_000L;

    public AIReportController(AiReportService aiReportService, ReportHistoryService historyService) {
        this.aiReportService = aiReportService;
        this.historyService = historyService;
    }

    /**
     * 리포트 생성 — <b>실제 진행 상황</b>을 SSE 로 흘린다.
     *
     * <p>종전 {@code /generate} 는 다 만들 때까지 응답이 없어서, 화면이 2초짜리 타이머로
     * 가짜 진행률을 돌리고 있었다. 서버가 뭘 하는지와 무관해 수집이 길어져도 "거의 다 됐어요"
     * 가 떠 있었고, 실패해도 마지막 단계에서 멈춘 것처럼 보였다.
     *
     * <p>이벤트: {@code stage}(현재 단계) · {@code delta}(본문 조각) · {@code done} · {@code error}.
     * delta 는 줄바꿈이 섞이므로 JSON 으로 감싼다 — 날것으로 보내면 SSE 프레이밍이 깨진다.
     */
    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateReportStream(
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody Map<String, Object> request) {

        String resolved = headerUserId != null ? headerUserId : userId;
        final String effectiveUserId = (resolved == null || resolved.isBlank()) ? "default" : resolved;

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.debug("AI report stream error: {}", e.getMessage()));

        @SuppressWarnings("unchecked")
        Map<String, Boolean> topicsMap = (Map<String, Boolean>) request.getOrDefault("topics", new HashMap<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> clientSnapshot = (Map<String, Object>) request.get("snapshot");

        streamExecutor.execute(() -> {
            try {
                sendEvent(emitter, "stage", "요청 접수");

                String report = aiReportService.generate(
                        effectiveUserId,
                        AiReportService.Topics.from(topicsMap),
                        FIXED_SETTINGS,
                        clientSnapshot,
                        label -> sendEvent(emitter, "stage", label),
                        delta -> sendEvent(emitter, "delta", delta));

                if (saveManual) {
                    sendEvent(emitter, "stage", "이력 저장");
                    historyService.save(effectiveUserId, ReportHistory.TYPE_MANUAL, report);
                }

                JsonObject done = new JsonObject();
                done.addProperty("report", report);
                emitter.send(SseEmitter.event().name("done").data(done.toString(), MediaType.APPLICATION_JSON));
                emitter.complete();

            } catch (Exception e) {
                log.error("Error streaming AI report: {}", e.getMessage(), e);
                JsonObject err = new JsonObject();
                err.addProperty("message", "리포트 생성 중 오류가 발생했어요.");
                err.addProperty("detail", String.valueOf(e.getMessage()));
                try {
                    emitter.send(SseEmitter.event().name("error").data(err.toString(), MediaType.APPLICATION_JSON));
                } catch (Exception ignored) {
                    // 클라이언트가 이미 끊었다 — 더 할 수 있는 게 없다
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    /** 끊긴 클라이언트 때문에 생성이 죽지 않도록 전송 실패는 삼킨다. */
    private void sendEvent(SseEmitter emitter, String name, String payload) {
        JsonObject obj = new JsonObject();
        obj.addProperty(name.equals("delta") ? "t" : "label", payload);
        try {
            emitter.send(SseEmitter.event().name(name).data(obj.toString(), MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.debug("SSE send failed ({}): {}", name, e.getMessage());
        }
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
