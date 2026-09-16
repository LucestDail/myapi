package com.example.myapi.service;

import com.example.myapi.entity.ReportHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 일간 자동 리포트 · 주간 트렌드 분석.
 *
 * <p>가장 중요한 테스트는 "대상이 없으면 아무 일도 안 한다" 쪽이다. 모든 사용자를 훑도록
 * 짜면 익명 프로필 수만큼 Gemini 를 부르게 된다 — 736만 행 사고에 비용까지 붙인 모양.</p>
 */
class ScheduledReportServiceTest {

    private AiReportService aiReportService;
    private ReportHistoryService historyService;
    private ScheduledReportService service;

    @BeforeEach
    void setUp() {
        aiReportService = mock(AiReportService.class);
        historyService = mock(ReportHistoryService.class);
        service = new ScheduledReportService(aiReportService, historyService);
    }

    private ReportHistory daily(String content, int daysAgo) {
        ReportHistory r = new ReportHistory("u1", ReportHistory.TYPE_DAILY, content);
        r.setCreatedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        return r;
    }

    // ==================== 대상 사용자 ====================

    @Test
    void 대상_사용자가_없으면_Gemini_를_부르지_않는다() {
        service.setDailyUsers("");

        service.generateDailyReports();
        service.generateWeeklyReports();

        verifyNoInteractions(aiReportService);
        verifyNoInteractions(historyService);
    }

    @Test
    void 꺼_두면_대상이_있어도_돌지_않는다() {
        service.setDailyUsers("u1");
        service.setEnabled(false);

        service.generateDailyReports();
        service.generateWeeklyReports();

        verifyNoInteractions(aiReportService);
    }

    @Test
    void 대상_목록의_공백과_중복을_걷어낸다() {
        service.setDailyUsers(" u1 , u2 ,, u1 ");
        assertEquals(List.of("u1", "u2"), service.targetUsers());
    }

    // ==================== 일간 ====================

    @Test
    void 대상마다_리포트를_만들어_이력에_남긴다() {
        service.setDailyUsers("u1,u2");
        when(aiReportService.generate(anyString(), any(), any(), any())).thenReturn("리포트 본문");

        service.generateDailyReports();

        verify(aiReportService).generate(eq("u1"), any(), any(), any());
        verify(aiReportService).generate(eq("u2"), any(), any(), any());
        verify(historyService).save("u1", ReportHistory.TYPE_DAILY, "리포트 본문");
        verify(historyService).save("u2", ReportHistory.TYPE_DAILY, "리포트 본문");
    }

    @Test
    void 한_사용자가_실패해도_나머지는_계속한다() {
        service.setDailyUsers("u1,u2");
        when(aiReportService.generate(eq("u1"), any(), any(), any()))
                .thenThrow(new RuntimeException("gemini 503"));
        when(aiReportService.generate(eq("u2"), any(), any(), any())).thenReturn("리포트 본문");

        service.generateDailyReports();

        verify(historyService, never()).save(eq("u1"), anyString(), anyString());
        verify(historyService).save("u2", ReportHistory.TYPE_DAILY, "리포트 본문");
    }

    @Test
    void 본문이_비면_이력에_남기지_않는다() {
        service.setDailyUsers("u1");
        when(aiReportService.generate(anyString(), any(), any(), any())).thenReturn("   ");

        service.generateDailyReports();

        verify(historyService, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void 일간_리포트는_모든_토픽을_켠다() {
        service.setDailyUsers("u1");
        when(aiReportService.generate(anyString(), any(), any(), any())).thenReturn("본문");

        service.generateDailyReports();

        ArgumentCaptor<AiReportService.Topics> topics =
                ArgumentCaptor.forClass(AiReportService.Topics.class);
        verify(aiReportService).generate(eq("u1"), topics.capture(), any(), any());
        assertEquals(AiReportService.Topics.all(), topics.getValue());
    }

    // ==================== 주간 트렌드 ====================

    @Test
    void 비교할_리포트가_한_건뿐이면_트렌드를_만들지_않는다() {
        // 비교 대상이 없는데 "트렌드" 라고 쓰면 모델이 지어낸다.
        service.setDailyUsers("u1");
        when(historyService.recentDaily(eq("u1"), anyInt())).thenReturn(List.of(daily("어제", 1)));

        service.generateWeeklyReports();

        verify(aiReportService, never()).generateTrend(anyString());
        verify(historyService, never()).save(anyString(), eq(ReportHistory.TYPE_WEEKLY), anyString());
    }

    @Test
    void 이력이_아예_없어도_조용히_넘어간다() {
        service.setDailyUsers("u1");
        when(historyService.recentDaily(eq("u1"), anyInt())).thenReturn(List.of());

        service.generateWeeklyReports();

        verify(aiReportService, never()).generateTrend(anyString());
    }

    @Test
    void 이력이_둘_이상이면_트렌드를_만들어_저장한다() {
        service.setDailyUsers("u1");
        when(historyService.recentDaily(eq("u1"), anyInt()))
                .thenReturn(List.of(daily("최근", 1), daily("예전", 5)));
        when(aiReportService.generateTrend(anyString())).thenReturn("주간 트렌드");

        service.generateWeeklyReports();

        verify(historyService).save("u1", ReportHistory.TYPE_WEEKLY, "주간 트렌드");
    }

    @Test
    void 트렌드_프롬프트는_오래된_것부터_넣는다() {
        // 순서가 섞이면 모델이 방향("늘었다/줄었다")을 못 읽고 요약만 다시 낸다.
        List<ReportHistory> newestFirst = new ArrayList<>(
                List.of(daily("최근입니다", 1), daily("중간입니다", 3), daily("예전입니다", 6)));

        String prompt = ScheduledReportService.buildTrendPrompt(newestFirst);

        int oldest = prompt.indexOf("예전입니다");
        int middle = prompt.indexOf("중간입니다");
        int newest = prompt.indexOf("최근입니다");
        assertTrue(oldest >= 0 && middle >= 0 && newest >= 0, "세 리포트가 모두 실려야 한다");
        assertTrue(oldest < middle && middle < newest,
                "오래된 것 → 최근 순이어야 한다 (oldest=" + oldest + ", newest=" + newest + ")");
    }

    @Test
    void 트렌드_프롬프트는_비교를_명시적으로_요구한다() {
        String prompt = ScheduledReportService.buildTrendPrompt(
                List.of(daily("최근", 1), daily("예전", 5)));
        assertTrue(prompt.contains("비교"), "비교 지시가 빠지면 요약이 한 번 더 나올 뿐이다");
        assertTrue(prompt.contains("추측"), "없는 사실을 지어내지 말라는 지시가 있어야 한다");
    }

    @Test
    void 트렌드_프롬프트에_들어가는_본문은_길이를_제한한다() {
        String huge = "가".repeat(ScheduledReportService.TREND_EXCERPT_CHARS * 3);
        String excerpt = ScheduledReportService.excerpt(huge);
        assertTrue(excerpt.length() < huge.length(), "7건을 통째로 넣으면 프롬프트가 폭주한다");
        assertTrue(excerpt.contains("이하 생략"));
    }

    @Test
    void 트렌드_생성이_실패해도_예외가_새지_않는다() {
        service.setDailyUsers("u1");
        when(historyService.recentDaily(eq("u1"), anyInt()))
                .thenReturn(List.of(daily("최근", 1), daily("예전", 5)));
        when(aiReportService.generateTrend(anyString())).thenThrow(new RuntimeException("gemini 503"));

        service.generateWeeklyReports(); // 던지면 테스트 실패

        verify(historyService, never()).save(anyString(), eq(ReportHistory.TYPE_WEEKLY), anyString());
    }

    @Test
    void 주간_분석은_최근_7일만_본다() {
        service.setDailyUsers("u1");
        when(historyService.recentDaily(anyString(), anyInt())).thenReturn(List.of());

        service.generateWeeklyReports();

        verify(historyService).recentDaily("u1", ScheduledReportService.WEEKLY_LOOKBACK_DAYS);
        assertEquals(7, ScheduledReportService.WEEKLY_LOOKBACK_DAYS);
    }

    @Test
    void 일간과_주간은_같은_대상_목록을_쓴다() {
        // 목록이 갈리면 "일간은 오는데 주간은 안 오는" 상태가 된다.
        service.setDailyUsers("u1,u2");
        when(historyService.recentDaily(anyString(), anyInt())).thenReturn(List.of());

        service.generateWeeklyReports();

        verify(historyService, times(1)).recentDaily("u1", ScheduledReportService.WEEKLY_LOOKBACK_DAYS);
        verify(historyService, times(1)).recentDaily("u2", ScheduledReportService.WEEKLY_LOOKBACK_DAYS);
    }

    @Test
    void 토픽_기본값은_전부_켜짐이다() {
        AiReportService.Topics all = AiReportService.Topics.all();
        assertTrue(all.news() && all.weather() && all.stocks() && all.system() && all.lifeInfo());
        assertEquals(9, AiReportService.Topics.NAMES.size());
    }

    @Test
    void 토픽은_화면이_보낸_map_을_그대로_반영한다() {
        AiReportService.Topics topics = AiReportService.Topics.from(Map.of("news", false, "system", false));
        assertTrue(topics.weather(), "지정 안 한 토픽은 기본 켜짐");
        assertEquals(false, topics.news());
        assertEquals(false, topics.system());
    }

    @Test
    void 저장_실패는_null_로_돌아온다() {
        service.setDailyUsers("u1");
        when(aiReportService.generate(anyString(), any(), any(), any())).thenReturn("본문");
        when(historyService.save(anyString(), anyString(), anyString())).thenReturn(null);

        assertNull(service.generateDailyFor("u1"));
    }
}
