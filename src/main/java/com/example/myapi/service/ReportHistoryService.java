package com.example.myapi.service;

import com.example.myapi.dto.report.ReportHistoryDto;
import com.example.myapi.entity.ReportHistory;
import com.example.myapi.repository.ReportHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 리포트 이력 저장·조회·보관 정책.
 *
 * <h3>왜 상한이 두 개인가</h3>
 * <p>이력은 아무도 지우지 않으면 무한히 자란다. 이 저장소는 그 사고를 이미 겪었다 —
 * 익명 프로필이 요청마다 쌓여 736만 행이 됐다. 그래서 상한을 <b>둘 다</b> 건다:</p>
 * <ul>
 *   <li><b>사용자당 개수</b>({@code myapi.report.history.max-per-user}, 기본 50) — 저장할 때마다
 *       확인한다. 스케줄러가 꺼져 있어도 이 상한만은 반드시 지켜진다.</li>
 *   <li><b>보관 기간</b>({@code myapi.report.history.retention-days}, 기본 90) — 하루 한 번
 *       주기 작업에서 지운다. 개수 상한에 못 미치는 사용자의 오래된 이력을 치우는 쪽이다.</li>
 * </ul>
 *
 * <p>개수 상한만 저장 경로에 둔 이유: SQLite 커넥션 풀이 1이라 요청 경로의 DB 작업을 늘리면
 * 그대로 응답 지연이 된다. 개수 확인은 인덱스를 탄 {@code COUNT} 한 번이고, 삭제는 상한을
 * 넘겼을 때만(=보통 한 건) 일어난다. 기간 기반 전수 삭제처럼 훑는 작업은 주기로 뺐다.</p>
 */
@Service
public class ReportHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ReportHistoryService.class);

    private final ReportHistoryRepository repository;

    @Value("${myapi.report.history.max-per-user:50}")
    private int maxPerUser = 50;

    @Value("${myapi.report.history.retention-days:90}")
    private int retentionDays = 90;

    public ReportHistoryService(ReportHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * 리포트 한 건을 저장하고 사용자당 개수 상한을 강제한다.
     *
     * @return 저장된 이력. 본문이 비면 저장하지 않고 {@code null} — 리포트 생성이 실패해
     *         안내 문구만 남은 것을 이력에 쌓으면 나중에 트렌드 분석이 그 문구를 읽는다.
     */
    @Transactional
    public ReportHistory save(String userId, String reportType, String content) {
        if (userId == null || userId.isBlank()) {
            log.warn("[report] userId 가 없어 이력을 저장하지 않음 (type={})", reportType);
            return null;
        }
        if (content == null || content.isBlank()) {
            log.warn("[report] 본문이 비어 이력을 저장하지 않음 (user={}, type={})", userId, reportType);
            return null;
        }
        ReportHistory saved = repository.save(new ReportHistory(userId, reportType, content));
        enforceCountLimit(userId);
        return saved;
    }

    /** 사용자당 보관 개수 상한. 넘친 만큼 오래된 것부터 지운다. */
    @Transactional
    public int enforceCountLimit(String userId) {
        if (maxPerUser <= 0) {
            log.warn("[report] max-per-user 가 {} 이라 개수 상한을 적용하지 않음", maxPerUser);
            return 0;
        }
        long count = repository.countByUserId(userId);
        if (count <= maxPerUser) {
            return 0;
        }
        int surplus = (int) (count - maxPerUser);
        List<Long> victims = repository.findIdsOldestFirst(userId, PageRequest.of(0, surplus));
        if (victims.isEmpty()) {
            return 0;
        }
        int deleted = repository.deleteByIdIn(victims);
        log.info("[report] 이력 보관 상한({}건) 초과 — 사용자 {} 의 오래된 {}건 삭제",
                maxPerUser, userId, deleted);
        return deleted;
    }

    /**
     * 보관 기간을 넘긴 이력 정리. 매일 03시 — 알림 로그 정리(자정)와 시간을 겹치지 않게 둔다
     * (커넥션이 하나라 같은 시각에 몰면 서로 기다린다).
     */
    @Scheduled(cron = "${myapi.report.history.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public int cleanupExpired() {
        if (retentionDays <= 0) {
            log.warn("[report] retention-days 가 {} 이라 기간 기반 정리를 건너뜀", retentionDays);
            return 0;
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = repository.deleteOlderThan(cutoff);
        // 0건이어도 남긴다 — "검사 안 함" 과 "지울 게 없음" 은 다르다.
        log.info("[report] {}일 지난 이력 {}건 삭제 (기준 {})", retentionDays, deleted, cutoff);
        return deleted;
    }

    public List<ReportHistoryDto> list(String userId, int page, int size) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .getContent().stream()
                .map(ReportHistoryDto::listItem)
                .toList();
    }

    public List<ReportHistoryDto> listByType(String userId, String reportType, int page, int size) {
        return repository.findByUserIdAndReportTypeOrderByCreatedAtDesc(
                        userId, reportType, PageRequest.of(page, size))
                .getContent().stream()
                .map(ReportHistoryDto::listItem)
                .toList();
    }

    public Optional<ReportHistoryDto> get(String userId, Long id) {
        return repository.findByIdAndUserId(id, userId).map(ReportHistoryDto::full);
    }

    /** 주간 트렌드 분석이 쓰는 원본 — 최근 {@code days} 일 안의 일간 리포트. */
    public List<ReportHistory> recentDaily(String userId, int days) {
        Instant after = Instant.now().minus(days, ChronoUnit.DAYS);
        return repository.findByUserIdAndReportTypeAndCreatedAtAfterOrderByCreatedAtDesc(
                userId, ReportHistory.TYPE_DAILY, after);
    }

    int getMaxPerUser() {
        return maxPerUser;
    }

    void setMaxPerUser(int maxPerUser) {
        this.maxPerUser = maxPerUser;
    }

    void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}
