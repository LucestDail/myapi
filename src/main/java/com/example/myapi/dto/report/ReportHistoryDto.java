package com.example.myapi.dto.report;

import com.example.myapi.entity.ReportHistory;

import java.time.Instant;

/**
 * 리포트 이력 DTO.
 *
 * <p>{@link #listItem} 은 본문을 뺀다 — 목록 20건에 전문을 실으면 수십 KB 를 SQLite 커넥션
 * 하나로 끌어오게 된다. 전문이 필요하면 상세 조회({@link #full})를 쓴다.</p>
 */
public record ReportHistoryDto(
        Long id,
        String reportType,
        String summary,
        String content,   // 목록에서는 null
        Instant createdAt
) {
    public static ReportHistoryDto listItem(ReportHistory entity) {
        return new ReportHistoryDto(
                entity.getId(),
                entity.getReportType(),
                entity.getSummary(),
                null,
                entity.getCreatedAt());
    }

    public static ReportHistoryDto full(ReportHistory entity) {
        return new ReportHistoryDto(
                entity.getId(),
                entity.getReportType(),
                entity.getSummary(),
                entity.getContent(),
                entity.getCreatedAt());
    }
}
