<script setup lang="ts">
/**
 * 도시별 날씨.
 *
 * 데이터 출처: `useDashboardStore().weather` — SSE 로 이미 들어온다. 여기서는
 * 폴링도, fetch 도 하지 않는다(지시사항: 날씨는 스토어만 본다).
 *
 * 즐겨찾기만 이 컴포넌트의 로컬 관심사라 localStorage 에 직접 둔다 — 서버에
 * 저장할 이유가 없는 순수 UI 취향이라서다.
 */
import { computed, ref } from 'vue'
import { useDashboardStore } from '@/stores/dashboard'
import DashboardSection from '@/components/DashboardSection.vue'
import type { WeatherData } from '@/types/dashboard'

const props = defineProps<{ sectionId: string; title: string }>()

const dashboard = useDashboardStore()

const FAVORITES_KEY = 'myapiWeatherFavorites'

function loadFavorites(): string[] {
  try {
    const raw = localStorage.getItem(FAVORITES_KEY)
    return raw ? (JSON.parse(raw) as string[]) : []
  } catch {
    return []
  }
}

const favorites = ref<string[]>(loadFavorites())

function persistFavorites() {
  try {
    localStorage.setItem(FAVORITES_KEY, JSON.stringify(favorites.value))
  } catch {
    // 사생활 보호 모드 등 — 저장은 실패해도 화면 동작엔 지장 없다
  }
}

function isFavorite(city: string): boolean {
  return favorites.value.includes(city)
}

function toggleFavorite(city: string) {
  favorites.value = isFavorite(city)
    ? favorites.value.filter(c => c !== city)
    : [...favorites.value, city]
  persistFavorites()
}

const weatherIcons: Record<string, string> = {
  Clear: '☀', Clouds: '☁', Rain: '🌧', Drizzle: '🌦',
  Thunderstorm: '⛈', Snow: '❄', Mist: '🌫', Fog: '🌫', Haze: '🌫'
}

function iconFor(w: WeatherData): string {
  const condition = typeof w.weather === 'string' ? w.weather : ''
  return weatherIcons[condition] ?? '☁'
}

function humidityOf(w: WeatherData): number | null {
  return typeof w.humidity === 'number' ? w.humidity : null
}

function isAlert(w: WeatherData): boolean {
  return w.temperatureCelsius < -10 || w.temperatureCelsius > 35
}

/** 즐겨찾기를 위로 — 나머지는 서버가 준 순서를 유지한다. */
const sorted = computed(() => {
  return [...dashboard.weather].sort((a, b) => {
    const aFav = isFavorite(a.city) ? 1 : 0
    const bFav = isFavorite(b.city) ? 1 : 0
    return bFav - aFav
  })
})
</script>

<template>
  <DashboardSection :id="props.sectionId" :title="props.title" :updated-at="dashboard.lastUpdated">
    <div v-if="sorted.length === 0" class="no-data">데이터 없음</div>
    <div v-else class="weather-list">
      <div
        v-for="w in sorted"
        :key="w.city"
        class="weather-item"
        :class="{ 'weather-alert': isAlert(w) }"
      >
        <button
          class="weather-favorite"
          :class="{ active: isFavorite(w.city) }"
          type="button"
          :aria-pressed="isFavorite(w.city)"
          :aria-label="`${w.cityKo || w.city} 즐겨찾기`"
          @click="toggleFavorite(w.city)"
        >{{ isFavorite(w.city) ? '★' : '☆' }}</button>
        <span class="weather-city-name">{{ w.cityKo || w.city }}</span>
        <span class="weather-icon">{{ iconFor(w) }}</span>
        <span class="weather-temp">{{ w.temperatureCelsius.toFixed(1) }}°</span>
        <span v-if="humidityOf(w) !== null" class="weather-humidity">{{ humidityOf(w) }}%</span>
      </div>
    </div>
  </DashboardSection>
</template>

<style scoped>
.weather-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.weather-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  font-size: 12px;
}

.weather-item.weather-alert {
  border: 1px solid var(--accent-red);
}

.weather-favorite {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--text-muted);
  font-size: 13px;
  padding: 0;
  line-height: 1;
}

.weather-favorite.active {
  color: var(--accent-yellow);
}

.weather-city-name {
  flex: 1;
  color: var(--text-primary);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.weather-icon {
  font-size: 14px;
}

.weather-temp {
  color: var(--accent-cyan);
  font-weight: 600;
  width: 48px;
  text-align: right;
}

.weather-humidity {
  color: var(--text-muted);
  font-size: 11px;
  width: 36px;
  text-align: right;
}
</style>
