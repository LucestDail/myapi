<script setup lang="ts">
/**
 * 생활 정보 — 환율·미세먼지·일출일몰·공휴일.
 *
 * 원본(`features/lifeinfo.js`)이 `GET api/info/summary` 하나로 네 가지를 한 번에
 * 받아오던 것을 그대로 따른다. 서버 DTO 는 `InfoController.getSummary` 참고.
 * 10분마다 자동 갱신 + 수동 새로고침 버튼을 `#controls` 에 둔다(원본엔 수동
 * 갱신이 없었다 — 자동 갱신을 8분 더 기다리기 싫을 때를 위한 개선).
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'
import { tryGet } from '@/api/client'

const props = defineProps<{ sectionId: string; title: string }>()

interface ExchangeRates {
  base: string
  timestamp: string
  rates: Record<string, number>
}

interface AirQuality {
  location: string
  pm10: number | null
  pm25: number | null
  pm10Grade: string
  pm25Grade: string
  aqi: number | null
  overallGrade: string
  recommendation: string
  measuredAt: string
}

interface SunTimes {
  location: string
  sunrise: string
  sunset: string
  dayLength: string
}

interface HolidayNext {
  date: string
  name: string
}

interface HolidayInfo {
  isToday: boolean
  next: HolidayNext | null
}

interface LifeInfoSummary {
  exchange: ExchangeRates | null
  airQuality: AirQuality | null
  sunTimes: SunTimes | null
  holiday: HolidayInfo | null
}

const REFRESH_MS = 10 * 60 * 1000 // 10분 — 원본과 동일 주기

const info = ref<LifeInfoSummary | null>(null)
const lastFetched = ref<string | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  const data = await tryGet<LifeInfoSummary | null>(
    'api/info/summary?location=Seoul&lat=37.5665&lon=126.9780',
    null
  )
  info.value = data
  lastFetched.value = new Date().toISOString()
  loading.value = false
}

const airQualityClass: Record<string, string> = {
  '좋음': 'good',
  '보통': 'moderate',
  '나쁨': 'bad',
  '매우나쁨': 'very-bad'
}

function aqClass(grade: string): string {
  return airQualityClass[grade] ?? 'moderate'
}

function holidayDday(dateStr: string): number {
  const target = new Date(dateStr)
  const today = new Date()
  return Math.ceil((target.getTime() - today.getTime()) / 86400000)
}

let timer: number | undefined

onMounted(() => {
  load()
  timer = window.setInterval(load, REFRESH_MS)
})

onBeforeUnmount(() => {
  if (timer !== undefined) clearInterval(timer)
})
</script>

<template>
  <DashboardSection :id="props.sectionId" :title="props.title" :updated-at="lastFetched">
    <template #controls>
      <button class="refresh-btn" type="button" :disabled="loading" @click="load">
        {{ loading ? '갱신 중…' : '↻' }}
      </button>
    </template>

    <div v-if="!info" class="no-data">데이터 없음</div>
    <div v-else class="lifeinfo-grid">
      <div v-if="info.exchange?.rates" class="lifeinfo-row">
        <span class="lifeinfo-icon">💱</span>
        <span class="lifeinfo-label">환율</span>
        <div class="lifeinfo-value">
          <span class="lifeinfo-item">
            <span class="currency">USD</span>
            <span class="value">{{ info.exchange.rates.KRW ? info.exchange.rates.KRW.toLocaleString() : '-' }}</span>
            <span class="unit">₩</span>
          </span>
          <span class="lifeinfo-item">
            <span class="currency">JPY</span>
            <span class="value">
              {{ info.exchange.rates.JPY ? (info.exchange.rates.KRW / info.exchange.rates.JPY * 100).toFixed(1) : '-' }}
            </span>
            <span class="unit">₩/100</span>
          </span>
        </div>
      </div>

      <div v-if="info.airQuality" class="lifeinfo-row">
        <span class="lifeinfo-icon">🌫️</span>
        <span class="lifeinfo-label">대기</span>
        <div class="lifeinfo-value">
          <span class="lifeinfo-item">
            <span>PM10</span>
            <span class="value">{{ info.airQuality.pm10 ?? '-' }}</span>
            <span class="air-quality-badge" :class="aqClass(info.airQuality.pm10Grade)">{{ info.airQuality.pm10Grade || '-' }}</span>
          </span>
          <span class="lifeinfo-item">
            <span>PM2.5</span>
            <span class="value">{{ info.airQuality.pm25 ?? '-' }}</span>
            <span class="air-quality-badge" :class="aqClass(info.airQuality.pm25Grade)">{{ info.airQuality.pm25Grade || '-' }}</span>
          </span>
        </div>
      </div>

      <div v-if="info.sunTimes" class="lifeinfo-row">
        <span class="lifeinfo-icon">🌅</span>
        <span class="lifeinfo-label">일출</span>
        <div class="lifeinfo-value">
          <span class="lifeinfo-item"><span>🌅</span><span class="sun-time">{{ info.sunTimes.sunrise || '-' }}</span></span>
          <span class="lifeinfo-item"><span>🌇</span><span class="sun-time">{{ info.sunTimes.sunset || '-' }}</span></span>
        </div>
      </div>

      <div v-if="info.holiday" class="lifeinfo-row">
        <span class="lifeinfo-icon">📅</span>
        <span class="lifeinfo-label">공휴일</span>
        <div class="lifeinfo-value">
          <span v-if="info.holiday.isToday" class="holiday-badge">오늘은 공휴일!</span>
          <template v-else-if="info.holiday.next?.date">
            <span class="holiday-badge">D-{{ holidayDday(info.holiday.next.date) }}</span> {{ info.holiday.next.name }}
          </template>
          <template v-else>-</template>
        </div>
      </div>
    </div>
  </DashboardSection>
</template>

<style scoped>
.lifeinfo-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
}

.lifeinfo-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: var(--bg-tertiary);
  border-radius: 4px;
}

.lifeinfo-icon {
  font-size: 14px;
  width: 20px;
  text-align: center;
}

.lifeinfo-label {
  color: var(--text-secondary);
  width: 40px;
  font-size: 11px;
}

.lifeinfo-value {
  flex: 1;
  color: var(--text-primary);
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.lifeinfo-item {
  display: flex;
  gap: 4px;
  align-items: center;
}

.lifeinfo-item .currency {
  color: var(--accent-cyan);
  font-weight: 500;
}

.lifeinfo-item .value {
  color: var(--text-primary);
}

.lifeinfo-item .unit {
  color: var(--text-muted);
  font-size: 10px;
}

.air-quality-badge {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 500;
}

.air-quality-badge.good {
  background: rgb(0 255 136 / 20%);
  color: var(--accent-green);
}

.air-quality-badge.moderate {
  background: rgb(255 215 0 / 20%);
  color: var(--accent-yellow);
}

.air-quality-badge.bad {
  background: rgb(255 71 87 / 20%);
  color: var(--accent-red);
}

.air-quality-badge.very-bad {
  background: rgb(168 85 247 / 20%);
  color: var(--accent-purple);
}

.holiday-badge {
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 11px;
  background: rgb(255 107 214 / 20%);
  color: var(--accent-magenta);
}

.sun-time {
  color: var(--accent-yellow);
}

.refresh-btn {
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-secondary);
  font-size: 11px;
  cursor: pointer;
  padding: 3px 8px;
}

.refresh-btn:hover:not(:disabled) {
  color: var(--accent-cyan);
  border-color: var(--accent-cyan);
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
