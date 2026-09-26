<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'

/**
 * 집중 통계 섹션 — localStorage 기반(서버 연동 없음).
 * 원본: static/js/features/productivity.js 의 incrementPomodoro/renderFocusStats
 *
 * 실제 카운트 증가는 TimerSection 이 뽀모도로 작업 세션을 완료할 때 일으킨다.
 * 두 섹션은 서로 import 하지 않고, `localStorage['focusStats']` 와
 * `focus-stats-updated` 커스텀 이벤트로만 연결된다(TimerSection.vue 상단 주석 참고).
 * 같은 탭 안에서는 커스텀 이벤트로, 다른 탭에서는 브라우저 기본 `storage` 이벤트로 갱신된다.
 */
defineProps<{ sectionId: string; title: string }>()

const STATS_KEY = 'focusStats'
const STATS_EVENT = 'focus-stats-updated'

interface FocusStats {
  today: number
  todayDate: string
  week: number
  weekStart: string
  total: number
}

function getWeekStart(date: Date): Date {
  const d = new Date(date)
  const day = d.getDay()
  const diff = d.getDate() - day + (day === 0 ? -6 : 1)
  return new Date(d.setDate(diff))
}

function readStats(): FocusStats {
  try {
    const raw = localStorage.getItem(STATS_KEY)
    if (raw) return JSON.parse(raw) as FocusStats
  } catch {
    // 저장소가 막혀 있으면 0으로 보여준다.
  }
  return { today: 0, todayDate: '', week: 0, weekStart: '', total: 0 }
}

const stats = ref<FocusStats>(readStats())

/** 날짜/주가 바뀌었으면 저장된 값과 무관하게 0으로 보여준다(자정을 넘겨도 새로고침 없이 맞다). */
const today = computed(() => {
  const todayStr = new Date().toISOString().split('T')[0]
  return stats.value.todayDate === todayStr ? stats.value.today : 0
})

const week = computed(() => {
  const weekStr = getWeekStart(new Date()).toISOString().split('T')[0]
  return stats.value.weekStart === weekStr ? stats.value.week : 0
})

const total = computed(() => stats.value.total || 0)

const totalHours = computed(() => Math.round((total.value * 25) / 60))

function refresh() {
  stats.value = readStats()
}

function onStorage(event: StorageEvent) {
  if (event.key === STATS_KEY) refresh()
}

onMounted(() => {
  window.addEventListener(STATS_EVENT, refresh)
  window.addEventListener('storage', onStorage)
})

onBeforeUnmount(() => {
  window.removeEventListener(STATS_EVENT, refresh)
  window.removeEventListener('storage', onStorage)
})
</script>

<template>
  <DashboardSection :id="sectionId" :title="title">
    <div class="focus-grid">
      <div class="stat-card highlight">
        <div class="value">{{ today }}</div>
        <div class="label">오늘 뽀모도로</div>
      </div>
      <div class="stat-card">
        <div class="value">{{ week }}</div>
        <div class="label">이번 주</div>
      </div>
      <div class="stat-card">
        <div class="value">{{ total }}</div>
        <div class="label">전체</div>
      </div>
      <div class="stat-card">
        <div class="value">{{ totalHours }}h</div>
        <div class="label">총 집중 시간</div>
      </div>
    </div>
  </DashboardSection>
</template>

<style scoped>
.focus-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.stat-card {
  padding: 12px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  text-align: center;
}

.stat-card.highlight {
  border: 1px solid var(--accent-green);
  background: rgba(0, 255, 136, 0.05);
}

.value {
  color: var(--accent-cyan);
  font-size: 20px;
  font-weight: 600;
}

.stat-card.highlight .value {
  color: var(--accent-green);
}

.label {
  color: var(--text-muted);
  font-size: 10px;
  margin-top: 4px;
}
</style>
