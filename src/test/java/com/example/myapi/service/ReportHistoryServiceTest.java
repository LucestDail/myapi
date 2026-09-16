package com.example.myapi.service;

import com.example.myapi.entity.ReportHistory;
import com.example.myapi.repository.ReportHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 리포트 이력의 <b>보관 정책</b>을 못박는다.
 *
 * <p>이 저장소는 "지우는 규칙 없이 쌓는" 사고를 이미 겪었다 — 익명 프로필이 요청마다 만들어져
 * 736만 행이 됐다. 이력은 저장 경로가 사람의 명시적 행동이라 그만큼 빠르진 않지만,
 * 상한이 없으면 결국 같은 곳에 도착한다.</p>
 */
class ReportHistoryServiceTest {

    private ReportHistoryRepository repository;
    private ReportHistoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReportHistoryRepository.class);
        service = new ReportHistoryService(repository);
        when(repository.save(any(ReportHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 저장하면_발췌가_함께_만들어진다() {
        when(repository.countByUserId("u1")).thenReturn(1L);

        ReportHistory saved = service.save("u1", ReportHistory.TYPE_DAILY, "오늘의  리포트\n\n내용입니다");

        assertEquals("오늘의 리포트 내용입니다", saved.getSummary(), "줄바꿈·연속 공백은 한 줄로 눌러 담는다");
        assertEquals(ReportHistory.TYPE_DAILY, saved.getReportType());
    }

    @Test
    void 긴_본문의_발췌는_상한에서_잘린다() {
        String long_ = "가".repeat(ReportHistory.SUMMARY_MAX_CHARS + 500);
        String summary = ReportHistory.summarize(long_);
        assertEquals(ReportHistory.SUMMARY_MAX_CHARS + 1, summary.length(), "말줄임표 한 글자 포함");
        assertTrue(summary.endsWith("…"));
    }

    @Test
    void 본문이_비면_저장하지_않는다() {
        // 생성 실패 안내 문구만 남은 것을 쌓으면 나중에 트렌드 분석이 그 문구를 읽는다.
        assertNull(service.save("u1", ReportHistory.TYPE_DAILY, "  "));
        assertNull(service.save("u1", ReportHistory.TYPE_DAILY, null));
        verify(repository, never()).save(any());
    }

    @Test
    void userId_가_없으면_저장하지_않는다() {
        assertNull(service.save(null, ReportHistory.TYPE_MANUAL, "내용"));
        assertNull(service.save("", ReportHistory.TYPE_MANUAL, "내용"));
        verify(repository, never()).save(any());
    }

    // ==================== 개수 상한 ====================

    @Test
    void 상한_안이면_아무것도_지우지_않는다() {
        service.setMaxPerUser(50);
        when(repository.countByUserId("u1")).thenReturn(50L);

        assertEquals(0, service.enforceCountLimit("u1"));
        verify(repository, never()).deleteByIdIn(anyList());
    }

    @Test
    void 상한을_넘으면_넘친_만큼_오래된_것부터_지운다() {
        service.setMaxPerUser(50);
        when(repository.countByUserId("u1")).thenReturn(53L);
        when(repository.findIdsOldestFirst(eq("u1"), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(repository.deleteByIdIn(List.of(1L, 2L, 3L))).thenReturn(3);

        assertEquals(3, service.enforceCountLimit("u1"));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findIdsOldestFirst(eq("u1"), page.capture());
        assertEquals(3, page.getValue().getPageSize(), "넘친 개수만큼만 고른다");
    }

    @Test
    void 저장할_때마다_개수_상한이_강제된다() {
        // 주기 작업이 꺼져 있어도 이 상한만은 반드시 지켜져야 한다.
        service.setMaxPerUser(2);
        when(repository.countByUserId("u1")).thenReturn(3L);
        when(repository.findIdsOldestFirst(eq("u1"), any(Pageable.class))).thenReturn(List.of(9L));
        when(repository.deleteByIdIn(List.of(9L))).thenReturn(1);

        service.save("u1", ReportHistory.TYPE_MANUAL, "내용");

        verify(repository).deleteByIdIn(List.of(9L));
    }

    @Test
    void 상한이_0_이하면_적용하지_않는다() {
        service.setMaxPerUser(0);
        assertEquals(0, service.enforceCountLimit("u1"));
        verify(repository, never()).countByUserId(anyString());
    }

    // ==================== 기간 상한 ====================

    @Test
    void 보관_기간이_지난_이력을_지운다() {
        service.setRetentionDays(90);
        when(repository.deleteOlderThan(any(Instant.class))).thenReturn(7);

        assertEquals(7, service.cleanupExpired());

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteOlderThan(cutoff.capture());
        long days = ChronoUnit.DAYS.between(cutoff.getValue(), Instant.now());
        assertTrue(days >= 89 && days <= 91, "기준 시각이 90일 전이어야 한다: " + days);
    }

    @Test
    void 보관_기간이_0_이하면_기간_정리를_건너뛴다() {
        service.setRetentionDays(0);
        assertEquals(0, service.cleanupExpired());
        verify(repository, never()).deleteOlderThan(any());
    }

    // ==================== 조회 ====================

    @Test
    void 목록은_본문을_싣지_않는다() {
        ReportHistory entity = new ReportHistory("u1", ReportHistory.TYPE_DAILY, "아주 긴 본문");
        entity.setId(1L);
        when(repository.findByUserIdOrderByCreatedAtDesc(eq("u1"), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(entity)));

        var list = service.list("u1", 0, 20);

        assertEquals(1, list.size());
        assertNull(list.get(0).content(), "목록에 전문을 실으면 커넥션 하나짜리 DB 에서 그대로 지연이 된다");
        assertEquals("아주 긴 본문", list.get(0).summary());
    }

    @Test
    void 상세는_본문을_싣는다() {
        ReportHistory entity = new ReportHistory("u1", ReportHistory.TYPE_DAILY, "본문 전체");
        entity.setId(1L);
        when(repository.findByIdAndUserId(1L, "u1")).thenReturn(java.util.Optional.of(entity));

        assertEquals("본문 전체", service.get("u1", 1L).orElseThrow().content());
    }

    @Test
    void 남의_이력은_조회되지_않는다() {
        when(repository.findByIdAndUserId(1L, "u2")).thenReturn(java.util.Optional.empty());
        assertTrue(service.get("u2", 1L).isEmpty());
    }
}
