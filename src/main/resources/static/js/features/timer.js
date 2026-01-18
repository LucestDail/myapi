// ===========================================
// Timer Feature Module
// ===========================================

import { 
    userId, uiState, saveUiState,
    timerData, setTimerData, timerInterval, setTimerInterval,
    timerRemainingSeconds, setTimerRemainingSeconds,
    POMODORO_WORK_DURATION, POMODORO_BREAK_DURATION, TIMER_CIRCLE_CIRCUMFERENCE
} from '../state.js';
import { showToast } from '../ui.js';
import { incrementPomodoro } from './productivity.js';

/**
 * Initialize timer
 */
export async function initTimer() {
    try {
        // Try to get latest timer or create new one
        const mode = uiState.timer.mode;
        const response = await fetch(`/api/timer/${mode}`, {
            headers: { 'X-User-Id': userId }
        });
        if (response.ok) {
            const data = await response.json();
            setTimerData(data);
            syncTimerUI();
        } else if (response.status === 404) {
            // Timer doesn't exist yet, that's OK
            syncTimerUI();
        }
    } catch (error) {
        console.error('Failed to load timer:', error);
        // Continue without timer data
        syncTimerUI();
    }
}

/**
 * Sync timer UI with data
 */
export function syncTimerUI() {
    if (!timerData) return;

    // Update mode buttons
    const mode = uiState.timer.mode;
    document.querySelectorAll('.timer-mode-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.mode === mode);
    });

    // Calculate remaining seconds
    if (timerData.status === 'RUNNING') {
        const elapsed = (Date.now() - timerData.startedAt) / 1000;
        const remaining = Math.max(0, timerData.totalSeconds - elapsed);
        setTimerRemainingSeconds(Math.floor(remaining));
        startTimerInterval();
    } else if (timerData.status === 'PAUSED') {
        setTimerRemainingSeconds(timerData.remainingSeconds || 0);
    } else {
        setTimerRemainingSeconds(mode === 'pomodoro' ? POMODORO_WORK_DURATION : uiState.timer.duration * 60);
    }

    updateTimerDisplay();
    updatePomodoroCount();
}

/**
 * Start timer interval
 */
function startTimerInterval() {
    if (timerInterval) clearInterval(timerInterval);
    
    const interval = setInterval(() => {
        if (timerRemainingSeconds > 0) {
            setTimerRemainingSeconds(timerRemainingSeconds - 1);
            updateTimerDisplay();
        } else {
            timerComplete();
        }
    }, 1000);
    setTimerInterval(interval);
}

/**
 * Stop timer interval
 */
function stopTimerInterval() {
    if (timerInterval) {
        clearInterval(timerInterval);
        setTimerInterval(null);
    }
}

/**
 * Update timer display
 */
export function updateTimerDisplay() {
    const timeEl = document.getElementById('timer-time');
    const progressEl = document.getElementById('timer-progress');
    const statusEl = document.getElementById('timer-status');
    
    if (!timeEl) return;

    // Format time
    const minutes = Math.floor(timerRemainingSeconds / 60);
    const seconds = timerRemainingSeconds % 60;
    timeEl.textContent = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;

    // Update progress circle
    const totalSeconds = uiState.timer.mode === 'pomodoro' 
        ? (timerData?.isBreak ? POMODORO_BREAK_DURATION : POMODORO_WORK_DURATION)
        : uiState.timer.duration * 60;
    const progress = timerRemainingSeconds / totalSeconds;
    const offset = TIMER_CIRCLE_CIRCUMFERENCE * (1 - progress);
    
    if (progressEl) {
        progressEl.style.strokeDasharray = TIMER_CIRCLE_CIRCUMFERENCE;
        progressEl.style.strokeDashoffset = offset;
        progressEl.classList.toggle('break', timerData?.isBreak);
    }

    // Update status
    if (statusEl && timerData) {
        const statusMap = {
            'RUNNING': timerData.isBreak ? '휴식 중' : '집중 중',
            'PAUSED': '일시정지',
            'STOPPED': '대기중'
        };
        statusEl.textContent = statusMap[timerData.status] || '';
        statusEl.className = `timer-status ${(timerData.status || '').toLowerCase()}`;
    }
}

/**
 * Update pomodoro count display
 */
export function updatePomodoroCount() {
    const container = document.getElementById('pomodoro-count');
    if (!container || !timerData) return;

    const count = timerData.pomodoroCount || 0;
    const icons = [];
    for (let i = 0; i < 4; i++) {
        icons.push(`<span class="pomodoro-icon ${i < count % 4 ? 'completed' : ''}">🍅</span>`);
    }
    container.innerHTML = icons.join('');
}

/**
 * Set timer mode
 */
export function setTimerMode(mode) {
    uiState.timer.mode = mode;
    saveUiState();
    
    document.querySelectorAll('.timer-mode-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.mode === mode);
    });

    // Reset timer
    stopTimerInterval();
    const totalSeconds = mode === 'pomodoro' ? POMODORO_WORK_DURATION : uiState.timer.duration * 60;
    setTimerRemainingSeconds(totalSeconds);
    updateTimerDisplay();
}

/**
 * Start timer
 */
export async function startTimer() {
    const mode = uiState.timer.mode;
    const totalSeconds = mode === 'pomodoro' 
        ? POMODORO_WORK_DURATION 
        : uiState.timer.duration * 60;

    try {
        const response = await fetch(`/api/timer/${mode}/start`, {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-User-Id': userId 
            }
        });

        if (response.ok) {
            setTimerData(await response.json());
            startTimerInterval();
            updateTimerDisplay();
        }
    } catch (error) {
        console.error('Failed to start timer:', error);
    }
}

/**
 * Pause timer
 */
export async function pauseTimer() {
    const mode = uiState.timer.mode;
    try {
        const response = await fetch(`/api/timer/${mode}/pause`, {
            method: 'POST',
            headers: { 'X-User-Id': userId }
        });

        if (response.ok) {
            setTimerData(await response.json());
            stopTimerInterval();
            updateTimerDisplay();
        }
    } catch (error) {
        console.error('Failed to pause timer:', error);
    }
}

/**
 * Resume timer (same as start)
 */
export async function resumeTimer() {
    await startTimer();
}

/**
 * Reset timer
 */
export async function resetTimer() {
    const mode = uiState.timer.mode;
    try {
        const response = await fetch(`/api/timer/${mode}/stop`, {
            method: 'POST',
            headers: { 'X-User-Id': userId }
        });

        if (response.ok) {
            setTimerData(await response.json());
        }
    } catch (error) {
        console.error('Failed to reset timer:', error);
    }
    
    // Reset timer UI
    stopTimerInterval();
    const totalSeconds = mode === 'pomodoro' 
        ? POMODORO_WORK_DURATION 
        : uiState.timer.duration * 60;
    setTimerRemainingSeconds(totalSeconds);
    updateTimerDisplay();
}

/**
 * Timer complete handler
 */
export function timerComplete() {
    stopTimerInterval();
    
    if (uiState.timer.mode === 'pomodoro') {
        showToast(timerData?.isBreak ? '휴식 종료! 다시 집중하세요 🍅' : '뽀모도로 완료! 잠시 쉬세요 ☕', 'info');
        
        if (!timerData?.isBreak) {
            incrementPomodoro();
        }
        
        // Auto start break/work
        const nextSeconds = timerData?.isBreak ? POMODORO_WORK_DURATION : POMODORO_BREAK_DURATION;
        setTimerRemainingSeconds(nextSeconds);
        
        if (timerData) {
            timerData.isBreak = !timerData.isBreak;
        }
        
        updateTimerDisplay();
    } else {
        showToast('타이머 종료!', 'info');
        setTimerRemainingSeconds(uiState.timer.duration * 60);
        updateTimerDisplay();
    }
}

/**
 * Update timer duration from input
 */
export function updateTimerDuration() {
    const input = document.getElementById('timer-duration');
    if (!input) return;
    
    const duration = parseInt(input.value) || 25;
    uiState.timer.duration = duration;
    saveUiState();
    
    if (!timerData || timerData.status === 'STOPPED') {
        setTimerRemainingSeconds(duration * 60);
        updateTimerDisplay();
    }
}
