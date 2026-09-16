package com.example.myapi.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * AI 리포트 이력.
 *
 * <p>본문은 수 KB 수준이라 목록 조회에서 통째로 끌어오면 커넥션 하나짜리 SQLite 에서 비싸다.
 * 그래서 목록용 {@code summary}(앞부분 발췌)를 따로 저장하고, 전문은 상세 조회에서만 읽는다.</p>
 */
@Entity
@Table(name = "report_history", indexes = {
        @Index(name = "idx_report_history_user_created", columnList = "user_id,created_at")
})
public class ReportHistory {

    /** 사용자가 직접 생성 버튼을 눌러 만든 리포트. */
    public static final String TYPE_MANUAL = "manual";

    /** 스케줄러가 매일 저녁 만든 리포트. */
    public static final String TYPE_DAILY = "daily";

    /** 스케줄러가 직전 일간 리포트들을 비교해 만든 주간 트렌드 리포트. */
    public static final String TYPE_WEEKLY = "weekly";

    /** 목록에 실을 발췌 길이. */
    public static final int SUMMARY_MAX_CHARS = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "report_type", nullable = false, length = 20)
    private String reportType;

    @Column(name = "summary", length = 400)
    private String summary;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ReportHistory() {
    }

    public ReportHistory(String userId, String reportType, String content) {
        this.userId = userId;
        this.reportType = reportType;
        this.content = content;
        this.summary = summarize(content);
        this.createdAt = Instant.now();
    }

    /** 앞부분을 한 줄로 눌러 담는다. 줄바꿈이 섞이면 목록이 깨져 보인다. */
    public static String summarize(String content) {
        if (content == null) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= SUMMARY_MAX_CHARS ? flat : flat.substring(0, SUMMARY_MAX_CHARS) + "…";
    }

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.summary == null) {
            this.summary = summarize(this.content);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
