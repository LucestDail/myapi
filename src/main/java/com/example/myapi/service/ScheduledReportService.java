package com.example.myapi.service;

import com.example.myapi.entity.ReportHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 일간 자동 리포트 · 주간 트렌드 분석.
 *
 * <h3>🔴 왜 "대상 사용자" 를 설정으로 받는가</h3>
 * <p>이 저장소에는 사용자 프로필이 <b>익명 요청마다</b> 만들어져 736만 행까지 쌓였던 이력이 있다.
 * "모든 사용자에게 매일 리포트를 만들어 준다" 로 짜면 그 표를 그대로 훑으면서 사용자 수만큼
 * Gemini 를 부르고 이력을 쓴다 — 같은 사고를 비용까지 붙여 반복하는 것이다. 그래서 대상은
 * {@code myapi.report.daily.users} 에 <b>명시한 사람만</b>이고, 기본값은 빈 목록(=아무 일도
 * 하지 않음)이다. 비어 있다는 사실도 로그로 남긴다 — "안 도는 것" 과 "돌았는데 대상이 없는 것"은
 * 다르고, 조용하면 켠 줄 알고 기다리게 된다.</p>
 */
@Service
public class ScheduledReportService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledReportService.class);

    /** 주간 트렌드를 만들려면 비교할 일간 리포트가 최소 이만큼은 있어야 한다. */
    static final int MIN_DAILY_FOR_WEEKLY = 2;

    /** 주간 분석이 되짚는 기간(일). */
    static final int WEEKLY_LOOKBACK_DAYS = 7;

    /** 트렌드 프롬프트에 실을 리포트 한 건당 최대 길이. 7건을 통째로 넣으면 프롬프트가 폭주한다. */
    static final int TREND_EXCERPT_CHARS = 1500;

    private final AiReportService aiReportService;
    private final ReportHistoryService historyService;

    /** 쉼표로 구분한 대상 사용자 id. 비어 있으면 아무것도 하지 않는다. */
    @Value("${myapi.report.daily.users:}")
    private String dailyUsers = "";

    @Value("${myapi.report.daily.enabled:true}")
    private boolean enabled = true;

    public ScheduledReportService(AiReportService aiReportService,
                                  ReportHistoryService historyService) {
        this.aiReportService = aiReportService;
        this.historyService = historyService;
    }

    /** 매일 저녁 8시(Asia/Seoul 기준 서버 시간). */
    @Scheduled(cron = "${myapi.report.daily.cron:0 0 20 * * *}")
    public void generateDailyReports() {
        List<String> users = targetUsers();
        if (!enabled) {
            log.info("[report] 일간 자동 리포트가 꺼져 있음(myapi.report.daily.enabled=false)");
            return;
        }
        if (users.isEmpty()) {
            log.info("[report] 일간 자동 리포트 대상 사용자가 없어 생략함 "
                    + "(myapi.report.daily.users 에 id 를 쉼표로 지정)");
            return;
        }
        int saved = 0;
        for (String userId : users) {
            if (generateDailyFor(userId) != null) {
                saved++;
            }
        }
        log.info("[report] 일간 자동 리포트 — 대상 {}명 중 {}건 저장", users.size(), saved);
    }

    /** 한 사용자의 일간 리포트. 실패해도 다음 사용자로 넘어간다. */
    ReportHistory generateDailyFor(String userId) {
        try {
            String content = aiReportService.generate(
                    userId, AiReportService.Topics.all(), Map.of(), null);
            if (content == null || content.isBlank()) {
                log.warn("[report] 사용자 {} 의 일간 리포트가 비어 저장하지 않음", userId);
                return null;
            }
            return historyService.save(userId, ReportHistory.TYPE_DAILY, content);
        } catch (Exception e) {
            log.warn("[report] 사용자 {} 의 일간 리포트 생성 실패: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    /** 매주 일요일 저녁 8시 30분 — 그날 일간 리포트가 만들어진 뒤에 돈다. */
    @Scheduled(cron = "${myapi.report.weekly.cron:0 30 20 * * SUN}")
    public void generateWeeklyReports() {
        List<String> users = targetUsers();
        if (!enabled) {
            log.info("[report] 주간 트렌드 분석이 꺼져 있음(myapi.report.daily.enabled=false)");
            return;
        }
        if (users.isEmpty()) {
            log.info("[report] 주간 트렌드 분석 대상 사용자가 없어 생략함");
            return;
        }
        int saved = 0;
        for (String userId : users) {
            if (generateWeeklyFor(userId) != null) {
                saved++;
            }
        }
        log.info("[report] 주간 트렌드 분석 — 대상 {}명 중 {}건 저장", users.size(), saved);
    }

    /**
     * 한 사용자의 주간 트렌드. 이전 리포트가 {@value #MIN_DAILY_FOR_WEEKLY}건 미만이면
     * <b>만들지 않는다</b> — 비교 대상이 없는데 "트렌드" 라고 쓰면 모델이 지어낸다.
     */
    ReportHistory generateWeeklyFor(String userId) {
        try {
            List<ReportHistory> daily = historyService.recentDaily(userId, WEEKLY_LOOKBACK_DAYS);
            if (daily == null || daily.size() < MIN_DAILY_FOR_WEEKLY) {
                log.info("[report] 사용자 {} 의 최근 {}일 일간 리포트가 {}건뿐이라 주간 분석을 건너뜀(최소 {}건)",
                        userId, WEEKLY_LOOKBACK_DAYS, daily == null ? 0 : daily.size(), MIN_DAILY_FOR_WEEKLY);
                return null;
            }
            String prompt = buildTrendPrompt(daily);
            String content = aiReportService.generateTrend(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[report] 사용자 {} 의 주간 트렌드가 비어 저장하지 않음", userId);
                return null;
            }
            return historyService.save(userId, ReportHistory.TYPE_WEEKLY, content);
        } catch (Exception e) {
            log.warn("[report] 사용자 {} 의 주간 트렌드 생성 실패: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 트렌드 프롬프트. 지난 리포트들을 <b>날짜와 함께 오래된 순</b>으로 넣어야 모델이 방향
     * ("무엇이 늘고 무엇이 줄었는가")을 읽을 수 있다. 섞어 넣으면 요약만 다시 나온다.
     */
    static String buildTrendPrompt(List<ReportHistory> dailyNewestFirst) {
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")
                .withZone(AlertConditions.ALERT_ZONE);

        List<ReportHistory> oldestFirst = new ArrayList<>(dailyNewestFirst);
        java.util.Collections.reverse(oldestFirst);

        StringBuilder sb = new StringBuilder();
        sb.append("아래는 같은 사용자의 최근 일간 리포트예요(오래된 것부터). ")
                .append("이 리포트들을 서로 비교해서 **이번 주의 흐름**을 분석해주세요.\n\n");
        sb.append("## 분석에 반드시 담을 것\n");
        sb.append("1. 지난 리포트와 비교해 **달라진 것**(늘어난 것 / 줄어든 것 / 새로 나타난 것)\n");
        sb.append("2. 계속 반복되는 주제와, 그 주제가 어느 방향으로 움직이고 있는지\n");
        sb.append("3. 숫자가 있는 항목(주가·환율·대기질 등)은 **첫날 대비 마지막 날** 변화를 짚어주세요\n");
        sb.append("4. 리포트에 없는 사실은 추측해서 적지 말고, 자료가 모자라면 모자라다고 말해주세요\n\n");

        for (ReportHistory report : oldestFirst) {
            sb.append("### ").append(dateFormat.format(report.getCreatedAt())).append(" 리포트\n");
            sb.append(excerpt(report.getContent())).append("\n\n");
        }
        return sb.toString();
    }

    static String excerpt(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= TREND_EXCERPT_CHARS
                ? content
                : content.substring(0, TREND_EXCERPT_CHARS) + "\n...(이하 생략)";
    }

    /** 설정에 적힌 대상 사용자. 중복·공백을 걷어낸다. */
    List<String> targetUsers() {
        if (dailyUsers == null || dailyUsers.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> users = new LinkedHashSet<>();
        for (String token : dailyUsers.split(",")) {
            String id = token.trim();
            if (!id.isEmpty()) {
                users.add(id);
            }
        }
        return new ArrayList<>(users);
    }

    void setDailyUsers(String dailyUsers) {
        this.dailyUsers = dailyUsers;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
