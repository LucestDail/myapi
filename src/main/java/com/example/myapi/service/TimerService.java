package com.example.myapi.service;

import com.example.myapi.dto.productivity.TimerDto;
import com.example.myapi.entity.Timer;
import com.example.myapi.repository.TimerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 타이머/포모도로 서비스
 */
@Service
public class TimerService {

    private static final Logger log = LoggerFactory.getLogger(TimerService.class);

    // 포모도로 기본 설정
    private static final int POMODORO_WORK_MINUTES = 25;
    private static final int POMODORO_SHORT_BREAK_MINUTES = 5;
    private static final int POMODORO_LONG_BREAK_MINUTES = 15;
    private static final int POMODORO_LONG_BREAK_INTERVAL = 4;

    private final TimerRepository timerRepository;

    public TimerService(TimerRepository timerRepository) {
        this.timerRepository = timerRepository;
    }

    /**
     * 타이머 조회 (타입별)
     */
    public Optional<TimerDto> getTimer(String userId, String type) {
        return timerRepository.findByUserIdAndType(userId, type)
                .map(this::calculateRemainingTime)
                .map(TimerDto::from);
    }

    /**
     * 최근 타이머 조회
     */
    public Optional<TimerDto> getLatestTimer(String userId) {
        return timerRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId)
                .map(this::calculateRemainingTime)
                .map(TimerDto::from);
    }

    /**
     * 타이머 생성/초기화
     */
    @Transactional
    public TimerDto createTimer(String userId, String type, int durationSeconds) {
        Timer timer = timerRepository.findByUserIdAndType(userId, type)
                .orElse(new Timer(userId, type, durationSeconds));

        timer.setDurationSeconds(durationSeconds);
        timer.setRemainingSeconds(durationSeconds);
        timer.setStatus("idle");
        timer.setStartedAt(null);

        return TimerDto.from(timerRepository.save(timer));
    }

    /**
     * 포모도로 타이머 생성
     */
    @Transactional
    public TimerDto createPomodoro(String userId) {
        Timer timer = timerRepository.findByUserIdAndType(userId, "pomodoro")
                .orElse(new Timer(userId, "pomodoro", POMODORO_WORK_MINUTES * 60));

        timer.setDurationSeconds(POMODORO_WORK_MINUTES * 60);
        timer.setRemainingSeconds(POMODORO_WORK_MINUTES * 60);
        timer.setStatus("idle");
        timer.setStartedAt(null);
        timer.setPomodoroCount(timer.getPomodoroCount() != null ? timer.getPomodoroCount() : 0);

        return TimerDto.from(timerRepository.save(timer));
    }

    /**
     * 타이머 시작
     */
    @Transactional
    public TimerDto startTimer(String userId, String type) {
        Timer timer = timerRepository.findByUserIdAndType(userId, type)
                .orElseThrow(() -> new IllegalArgumentException("Timer not found"));

        if ("running".equals(timer.getStatus())) {
            return TimerDto.from(timer);
        }

        timer.setStatus("running");
        timer.setStartedAt(Instant.now());

        return TimerDto.from(timerRepository.save(timer));
    }

    /**
     * 타이머 일시정지
     */
    @Transactional
    public TimerDto pauseTimer(String userId, String type) {
        Timer timer = timerRepository.findByUserIdAndType(userId, type)
                .orElseThrow(() -> new IllegalArgumentException("Timer not found"));

        if (!"running".equals(timer.getStatus())) {
            return TimerDto.from(timer);
        }

        // 남은 시간 계산
        timer = calculateRemainingTime(timer);
        timer.setStatus("paused");
        timer.setStartedAt(null);

        return TimerDto.from(timerRepository.save(timer));
    }

    /**
     * 타이머 정지/리셋
     */
    @Transactional
    public TimerDto stopTimer(String userId, String type) {
        Timer timer = timerRepository.findByUserIdAndType(userId, type)
                .orElseThrow(() -> new IllegalArgumentException("Timer not found"));

        timer.setStatus("idle");
        timer.setRemainingSeconds(timer.getDurationSeconds());
        timer.setStartedAt(null);

        return TimerDto.from(timerRepository.save(timer));
    }

    /**
     * 포모도로 완료 처리
     */
    @Transactional
    public TimerDto completePomodoro(String userId) {
        Timer timer = timerRepository.findByUserIdAndType(userId, "pomodoro")
                .orElseThrow(() -> new IllegalArgumentException("Pomodoro timer not found"));

        int count = timer.getPomodoroCount() + 1;
        timer.setPomodoroCount(count);
        timer.setStatus("completed");

        // 다음 세션 준비 (휴식 시간 설정)
        int breakMinutes = (count % POMODORO_LONG_BREAK_INTERVAL == 0) 
                ? POMODORO_LONG_BREAK_MINUTES 
                : POMODORO_SHORT_BREAK_MINUTES;

        timer.setDurationSeconds(breakMinutes * 60);
        timer.setRemainingSeconds(breakMinutes * 60);
        timer.setStartedAt(null);

        log.info("Pomodoro {} completed for user {}", count, userId);
        return TimerDto.from(timerRepository.save(timer));
    }

    /**
     * 포모도로 휴식 완료 후 작업 세션으로 복귀
     */
    @Transactional
    public TimerDto startNextPomodoro(String userId) {
        Timer timer = timerRepository.findByUserIdAndType(userId, "pomodoro")
                .orElse(new Timer(userId, "pomodoro", POMODORO_WORK_MINUTES * 60));

        timer.setDurationSeconds(POMODORO_WORK_MINUTES * 60);
        timer.setRemainingSeconds(POMODORO_WORK_MINUTES * 60);
        timer.setStatus("idle");
        timer.setStartedAt(null);

        return TimerDto.from(timerRepository.save(timer));
    }

    /**
     * 실시간 남은 시간 계산
     */
    private Timer calculateRemainingTime(Timer timer) {
        if ("running".equals(timer.getStatus()) && timer.getStartedAt() != null) {
            long elapsedSeconds = Duration.between(timer.getStartedAt(), Instant.now()).toSeconds();
            int remaining = Math.max(0, timer.getRemainingSeconds() - (int) elapsedSeconds);
            timer.setRemainingSeconds(remaining);

            if (remaining == 0) {
                timer.setStatus("completed");
            }
        }
        return timer;
    }
}
