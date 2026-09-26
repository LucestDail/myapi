<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'
import { api, ApiError } from '@/api/client'
import { useUiStore } from '@/stores/ui'

/**
 * 타이머/포모도로 섹션 — 서버 연동.
 * 원본: static/js/features/timer.js, static/css/features/timer.css
 *
 * 🔴 원본 버그 두 가지를 여기서 고쳤다.
 *  1) 원본은 타이머를 생성(`POST api/timer/{type}`)하는 코드를 앱 어디에서도 부르지
 *     않았다. 그 상태에서 시작 버튼을 누르면 서버가 "Timer not found" 로 500 을
 *     던지는데, 원본은 `response.ok` 만 보고 조용히 무시했다 — 최초 1회는 항상
 *     먹통이었다. 여기서는 시작 전에 조회해서 없으면 만든다(`prepare()`).
 *  2) 원본은 `timerData.isBreak` 를 읽었는데 서버 `TimerDto` 에는 그런 필드가
 *     없다(항상 undefined). 휴식 상태가 시각적으로 반영된 적이 없다. 여기서는
 *     서버가 실제로 제공하는 `/api/timer/pomodoro/complete`·`/next` 전용
 *     엔드포인트로 작업→휴식→작업 전환을 명시적으로 추적한다(`isBreak` 로컬 상태).
 *
 * 집중 섹션(FocusSection)과는 파일을 공유하지 않으므로, 포모도로 1회 완료 시
 * `localStorage['focusStats']` 를 갱신하고 `focus-stats-updated` 커스텀 이벤트를
 * 쏴서 알린다. 두 섹션 모두 이 키/이벤트 이름을 하드코딩으로 공유한다 — 생산성
 * 섹션이 하나 더 생기면 작은 공용 스토어로 옮길 시점이다.
 */
defineProps<{ sectionId: string; title: string }>()

interface TimerDto {
  id: number
  type: string
  durationSeconds: number
  remainingSeconds: number
  startedAt: string | null
  status: 'idle' | 'running' | 'paused' | 'completed' | string
  pomodoroCount: number
  createdAt: string
  updatedAt: string
}

type TimerType = 'timer' | 'pomodoro'

const CIRCUMFERENCE = 2 * Math.PI * 62
const POMODORO_WORK_SECONDS = 25 * 60
const PREFS_KEY = 'myapiTimerPrefs'
const FOCUS_STATS_KEY = 'focusStats'
const FOCUS_STATS_EVENT = 'focus-stats-updated'

function loadPrefs(): { mode: TimerType; durationMinutes: number } {
  try {
    const raw = localStorage.getItem(PREFS_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as { mode?: string; durationMinutes?: number }
      return {
        mode: parsed.mode === 'pomodoro' ? 'pomodoro' : 'timer',
        durationMinutes: Number(parsed.durationMinutes) > 0 ? Number(parsed.durationMinutes) : 25
      }
    }
  } catch {
    // 저장소가 막혀 있으면 기본값으로 진행한다.
  }
  return { mode: 'timer', durationMinutes: 25 }
}

function savePrefs() {
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify({ mode: mode.value, durationMinutes: durationMinutes.value }))
  } catch {
    // 무해하다 — 저장 안 되면 다음 방문 때 기본값일 뿐.
  }
}

function getWeekStart(date: Date): Date {
  const d = new Date(date)
  const day = d.getDay()
  const diff = d.getDate() - day + (day === 0 ? -6 : 1)
  return new Date(d.setDate(diff))
}

/** 뽀모도로 1회 완료를 집중 섹션의 로컬 통계에 반영한다. */
function bumpFocusStats() {
  try {
    const now = new Date()
    const todayStr = now.toISOString().split('T')[0]
    const weekStr = getWeekStart(now).toISOString().split('T')[0]
    const raw = localStorage.getItem(FOCUS_STATS_KEY)
    const stats = raw
      ? (JSON.parse(raw) as { today: number; todayDate: string; week: number; weekStart: string; total: number })
      : { today: 0, todayDate: '', week: 0, weekStart: '', total: 0 }

    if (stats.todayDate !== todayStr) {
      stats.today = 0
      stats.todayDate = todayStr
    }
    if (stats.weekStart !== weekStr) {
      stats.week = 0
      stats.weekStart = weekStr
    }
    stats.today += 1
    stats.week += 1
    stats.total = (stats.total || 0) + 1

    localStorage.setItem(FOCUS_STATS_KEY, JSON.stringify(stats))
    window.dispatchEvent(new CustomEvent(FOCUS_STATS_EVENT))
  } catch {
    // 저장소가 막혀 있으면 통계만 못 남길 뿐, 타이머 동작에는 영향 없다.
  }
}

const ui = useUiStore()
const initialPrefs = loadPrefs()
const mode = ref<TimerType>(initialPrefs.mode)
const durationMinutes = ref(initialPrefs.durationMinutes)
const timer = ref<TimerDto | null>(null)
const remainingSeconds = ref(mode.value === 'pomodoro' ? POMODORO_WORK_SECONDS : durationMinutes.value * 60)
const isBreak = ref(false)
const busy = ref(false)
let tickHandle: number | undefined

function desiredSeconds(): number {
  return mode.value === 'pomodoro' ? POMODORO_WORK_SECONDS : durationMinutes.value * 60
}

function stopTicking() {
  if (tickHandle !== undefined) {
    window.clearInterval(tickHandle)
    tickHandle = undefined
  }
}

function startTicking() {
  stopTicking()
  tickHandle = window.setInterval(() => {
    if (remainingSeconds.value > 0) {
      remainingSeconds.value -= 1
    } else {
      onComplete()
    }
  }, 1000)
}

async function load() {
  try {
    const data = await api.get<TimerDto>(`api/timer/${mode.value}`)
    timer.value = data
    remainingSeconds.value = data.remainingSeconds
    if (data.status === 'running') startTicking()
    else stopTicking()
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      timer.value = null
      remainingSeconds.value = desiredSeconds()
    } else {
      ui.toast('타이머 정보를 불러오지 못했습니다', 'danger')
    }
  }
}

/** 시작 전에 서버에 타이머가 있는지 확인하고, 없거나 시간이 바뀌었으면 새로 만든다. */
async function prepare(): Promise<TimerDto> {
  const wanted = desiredSeconds()
  let current: TimerDto | null = null
  try {
    current = await api.get<TimerDto>(`api/timer/${mode.value}`)
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 404)) throw error
  }
  if (!current || (current.status === 'idle' && current.durationSeconds !== wanted)) {
    current = await api.post<TimerDto>(`api/timer/${mode.value}`, { durationSeconds: wanted })
  }
  return current
}

async function start() {
  if (busy.value) return
  busy.value = true
  try {
    await prepare()
    const started = await api.post<TimerDto>(`api/timer/${mode.value}/start`)
    timer.value = started
    remainingSeconds.value = started.remainingSeconds
    startTicking()
  } catch {
    ui.toast('타이머 시작 실패', 'danger')
  } finally {
    busy.value = false
  }
}

async function pause() {
  if (busy.value) return
  busy.value = true
  try {
    const data = await api.post<TimerDto>(`api/timer/${mode.value}/pause`)
    timer.value = data
    remainingSeconds.value = data.remainingSeconds
    stopTicking()
  } catch {
    ui.toast('일시정지 실패', 'danger')
  } finally {
    busy.value = false
  }
}

async function reset() {
  if (busy.value) return
  busy.value = true
  stopTicking()
  try {
    const data = await api.post<TimerDto>(`api/timer/${mode.value}/stop`)
    timer.value = data
    remainingSeconds.value = data.remainingSeconds
    isBreak.value = false
  } catch {
    ui.toast('리셋 실패', 'danger')
  } finally {
    busy.value = false
  }
}

function toggleStart() {
  if (timer.value?.status === 'running') pause()
  else start()
}

async function onComplete() {
  stopTicking()

  if (mode.value !== 'pomodoro') {
    ui.toast('타이머 종료!', 'info')
    await reset()
    return
  }

  try {
    if (!isBreak.value) {
      const completed = await api.post<TimerDto>('api/timer/pomodoro/complete')
      timer.value = completed
      isBreak.value = true
      bumpFocusStats()
      ui.toast('뽀모도로 완료! 잠시 쉬세요 ☕', 'success')
    } else {
      const next = await api.post<TimerDto>('api/timer/pomodoro/next')
      timer.value = next
      isBreak.value = false
      ui.toast('휴식 종료! 다시 집중하세요 🍅', 'info')
    }
    const started = await api.post<TimerDto>('api/timer/pomodoro/start')
    timer.value = started
    remainingSeconds.value = started.remainingSeconds
    startTicking()
  } catch {
    ui.toast('뽀모도로 전환 실패', 'danger')
  }
}

async function setMode(next: TimerType) {
  if (mode.value === next || busy.value) return
  stopTicking()
  mode.value = next
  isBreak.value = false
  timer.value = null
  remainingSeconds.value = desiredSeconds()
  savePrefs()
  await load()
}

function onDurationChange() {
  if (durationMinutes.value < 1) durationMinutes.value = 1
  if (durationMinutes.value > 120) durationMinutes.value = 120
  savePrefs()
  if (!timer.value || timer.value.status === 'idle') {
    remainingSeconds.value = desiredSeconds()
  }
}

const minutesLabel = computed(() => String(Math.floor(remainingSeconds.value / 60)).padStart(2, '0'))
const secondsLabel = computed(() => String(remainingSeconds.value % 60).padStart(2, '0'))

const totalSecondsForProgress = computed(() => timer.value?.durationSeconds || desiredSeconds())
const progressOffset = computed(() => {
  const total = totalSecondsForProgress.value
  const ratio = total > 0 ? Math.min(1, Math.max(0, remainingSeconds.value / total)) : 0
  return CIRCUMFERENCE * (1 - ratio)
})

const statusLabel = computed(() => {
  const status = timer.value?.status
  if (status === 'running') return isBreak.value ? '휴식 중' : '집중 중'
  if (status === 'paused') return '일시정지'
  return '대기 중'
})

const isRunning = computed(() => timer.value?.status === 'running')

const pomodoroDots = computed(() => {
  const count = timer.value?.pomodoroCount ?? 0
  return Array.from({ length: 4 }, (_, i) => i < count % 4)
})

onMounted(load)
onBeforeUnmount(stopTicking)
</script>

<template>
  <DashboardSection :id="sectionId" :title="title">
    <div class="timer">
      <div class="mode-tabs">
        <button class="mode-btn" :class="{ active: mode === 'timer' }" @click="setMode('timer')">타이머</button>
        <button class="mode-btn" :class="{ active: mode === 'pomodoro' }" @click="setMode('pomodoro')">포모도로</button>
      </div>

      <div v-if="mode === 'timer'" class="duration-input">
        <label for="timer-duration">시간(분):</label>
        <input
          id="timer-duration"
          v-model.number="durationMinutes"
          type="number"
          min="1"
          max="120"
          @change="onDurationChange"
        />
      </div>

      <div class="display">
        <svg class="circle" viewBox="0 0 140 140">
          <circle class="circle-bg" cx="70" cy="70" r="62" />
          <circle
            class="circle-progress"
            :class="{ break: isBreak }"
            cx="70"
            cy="70"
            r="62"
            :stroke-dasharray="CIRCUMFERENCE"
            :stroke-dashoffset="progressOffset"
          />
        </svg>
        <div class="time">{{ minutesLabel }}:{{ secondsLabel }}</div>
      </div>

      <div class="status" :class="{ running: isRunning, break: isBreak && isRunning }">{{ statusLabel }}</div>

      <div class="controls">
        <button class="ctrl-btn" title="리셋" :disabled="busy" @click="reset">⏹</button>
        <button class="ctrl-btn primary" :title="isRunning ? '일시정지' : '시작'" :disabled="busy" @click="toggleStart">
          {{ isRunning ? '⏸' : '▶' }}
        </button>
      </div>

      <div v-if="mode === 'pomodoro'" class="pomodoro-dots">
        <span v-for="(filled, i) in pomodoroDots" :key="i" class="dot" :class="{ filled }">🍅</span>
      </div>
    </div>
  </DashboardSection>
</template>

<style scoped>
.timer {
  text-align: center;
  padding: 4px 0;
}

.mode-tabs {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-bottom: 12px;
}

.mode-btn {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  padding: 6px 16px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 11px;
}

.mode-btn:hover { border-color: var(--accent-cyan); color: var(--accent-cyan); }

.mode-btn.active {
  border-color: var(--accent-cyan);
  background: rgba(0, 212, 255, 0.1);
  color: var(--accent-cyan);
}

.duration-input {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 11px;
  color: var(--text-muted);
}

.duration-input input {
  width: 60px;
  padding: 4px 8px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-primary);
  font-family: inherit;
  font-size: 12px;
  text-align: center;
}

.duration-input input:focus { outline: none; border-color: var(--accent-cyan); }

.display {
  position: relative;
  width: 140px;
  height: 140px;
  margin: 0 auto 16px;
}

.circle { position: absolute; inset: 0; width: 100%; height: 100%; }

.circle-bg { fill: none; stroke: var(--bg-tertiary); stroke-width: 6; }

.circle-progress {
  fill: none;
  stroke: var(--accent-cyan);
  stroke-width: 6;
  stroke-linecap: round;
  transform: rotate(-90deg);
  transform-origin: center;
  transition: stroke-dashoffset 0.5s ease;
}

.circle-progress.break { stroke: var(--accent-green); }

.time {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 26px;
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.status {
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 10px;
}

.status.running { color: var(--accent-green); }
.status.break { color: var(--accent-cyan); }

.controls {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-bottom: 10px;
}

.ctrl-btn {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  width: 36px;
  height: 36px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ctrl-btn:hover { border-color: var(--accent-cyan); color: var(--accent-cyan); }

.ctrl-btn.primary {
  background: var(--accent-cyan);
  border-color: var(--accent-cyan);
  color: var(--bg-primary);
}

.ctrl-btn.primary:hover { background: var(--accent-blue); border-color: var(--accent-blue); }

.ctrl-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.pomodoro-dots {
  display: flex;
  justify-content: center;
  gap: 6px;
  font-size: 16px;
}

.dot { opacity: 0.3; }
.dot.filled { opacity: 1; }
</style>
