package com.example.myapi.service;

import com.example.myapi.dto.system.SystemStatusResponse;
import com.example.myapi.entity.SystemHistory;
import com.example.myapi.repository.SystemHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 시스템 히스토리 서비스
 * 1분 단위로 시스템 메트릭 수집 및 저장
 */
@Service
public class SystemHistoryService {

    private static final Logger log = LoggerFactory.getLogger(SystemHistoryService.class);

    private final SystemHistoryRepository historyRepository;
    private final SystemStatusService systemStatusService;

    public SystemHistoryService(SystemHistoryRepository historyRepository,
                                SystemStatusService systemStatusService) {
        this.historyRepository = historyRepository;
        this.systemStatusService = systemStatusService;
    }

    /**
     * 시스템 메트릭 수집 (1분마다)
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void collectMetrics() {
        try {
            SystemStatusResponse status = systemStatusService.getSystemStatus();
            
            SystemHistory history = new SystemHistory();
            history.setTimestamp(Instant.now());
            history.setCpuUsage(status.systemCpuLoad());
            history.setMemoryUsagePercent(status.memoryUsagePercent());
            history.setMemoryUsed(status.usedPhysicalMemory());
            history.setMemoryTotal(status.totalPhysicalMemory());
            history.setHeapUsagePercent(status.heapUsagePercent());
            history.setHeapUsed(status.heapUsed());
            history.setHeapMax(status.heapMax());
            history.setThreadCount(status.threadCount());
            history.setGcCount(status.gcCount());
            history.setGcTime(status.gcTime());
            
            historyRepository.save(history);
            log.debug("System metrics collected");
        } catch (Exception e) {
            log.error("Failed to collect system metrics: {}", e.getMessage());
        }
    }

    /**
     * 기간별 히스토리 조회
     */
    public List<SystemHistory> getHistory(String period) {
        Instant start = switch (period) {
            case "1h" -> Instant.now().minus(1, ChronoUnit.HOURS);
            case "24h" -> Instant.now().minus(24, ChronoUnit.HOURS);
            case "7d" -> Instant.now().minus(7, ChronoUnit.DAYS);
            default -> Instant.now().minus(1, ChronoUnit.HOURS);
        };
        
        return historyRepository.findByTimestampAfterOrderByTimestampAsc(start);
    }

    /**
     * 시간 범위 지정 히스토리 조회
     */
    public List<SystemHistory> getHistory(Instant start, Instant end) {
        return historyRepository.findByTimestampBetweenOrderByTimestampAsc(start, end);
    }

    /**
     * 최근 N개 데이터 포인트 조회
     */
    public List<SystemHistory> getRecentHistory(int count) {
        List<SystemHistory> all = historyRepository.findByTimestampAfterOrderByTimestampAsc(
                Instant.now().minus(24, ChronoUnit.HOURS));
        
        if (all.size() <= count) return all;
        return all.subList(all.size() - count, all.size());
    }

    /**
     * 오래된 히스토리 정리 (매일 자정)
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupOldHistory() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        historyRepository.deleteOlderThan(cutoff);
        log.info("Cleaned up system history older than 7 days");
    }
}
