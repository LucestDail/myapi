<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'
import { tryGet } from '@/api/client'

/**
 * 교통 돌발상황 섹션.
 * 원본: static/js/features/social.js 의 renderTraffic/loadTraffic.
 */
const props = defineProps<{ sectionId: string; title: string }>()

interface TrafficItem {
  startDate?: string
  roadName?: string
  message?: string
  [key: string]: unknown
}

const PER_PAGE = 10
const SLIDE_INTERVAL_MS = 5000
const POLL_INTERVAL_MS = 5 * 60 * 1000
const AUTO_SLIDE_KEY = 'myapi.traffic.autoSlide'

const rawItems = ref<TrafficItem[]>([])
const fetchedAt = ref<string | null>(null)
const loading = ref(true)

const pageIndex = ref(0)
const slideIndex = ref(0)
const autoSlide = ref(loadAutoSlidePref())

let slideTimer: number | undefined
let pollTimer: number | undefined

function loadAutoSlidePref(): boolean {
  try {
    return localStorage.getItem(AUTO_SLIDE_KEY) === '1'
  } catch {
    return false
  }
}

function saveAutoSlidePref(value: boolean) {
  try {
    localStorage.setItem(AUTO_SLIDE_KEY, value ? '1' : '0')
  } catch {
    // 저장 실패는 무해하다
  }
}

// 최신 순 정렬 (startDate: "20260118181908" 형식이라 문자열 비교로 충분하다)
const sortedItems = computed(() =>
  [...rawItems.value].sort((a, b) => (b.startDate || '').localeCompare(a.startDate || ''))
)

const totalPages = computed(() => Math.max(1, Math.ceil(sortedItems.value.length / PER_PAGE)))
const currentPage = computed(() => pageIndex.value + 1)
const hasPrev = computed(() => currentPage.value > 1)
const hasNext = computed(() => currentPage.value < totalPages.value)

const displayedItems = computed(() => {
  const list = sortedItems.value
  if (list.length === 0) return []
  if (autoSlide.value) {
    if (list.length <= PER_PAGE) return list.slice(0, PER_PAGE)
    const start = slideIndex.value % list.length
    const result: TrafficItem[] = []
    for (let i = 0; i < Math.min(PER_PAGE, list.length); i++) {
      result.push(list[(start + i) % list.length])
    }
    return result
  }
  const start = pageIndex.value * PER_PAGE
  return list.slice(start, start + PER_PAGE)
})

function formatDate(startDate?: string): string {
  const s = startDate || ''
  if (s.length === 14) {
    return `${s.slice(0, 4)}-${s.slice(4, 6)}-${s.slice(6, 8)} ${s.slice(8, 10)}:${s.slice(10, 12)}:${s.slice(12, 14)}`
  }
  return s
}

function changePage(delta: number) {
  const next = pageIndex.value + delta
  if (next >= 0 && next < totalPages.value) pageIndex.value = next
}

function stopSlide() {
  if (slideTimer !== undefined) {
    clearInterval(slideTimer)
    slideTimer = undefined
  }
}

function startSlide() {
  stopSlide()
  slideTimer = window.setInterval(() => {
    slideIndex.value += 1
  }, SLIDE_INTERVAL_MS)
}

function toggleAutoSlide() {
  autoSlide.value = !autoSlide.value
  saveAutoSlidePref(autoSlide.value)
  if (autoSlide.value) {
    startSlide()
  } else {
    stopSlide()
    pageIndex.value = 0
  }
}

async function load() {
  const res = await tryGet<{ body?: { items?: TrafficItem[] } }>('api/social/traffic', {})
  rawItems.value = res.body?.items ?? []
  fetchedAt.value = new Date().toISOString()
  loading.value = false
}

onMounted(() => {
  load()
  // 원본: 5분마다 갱신
  pollTimer = window.setInterval(load, POLL_INTERVAL_MS)
  if (autoSlide.value) startSlide()
})

onBeforeUnmount(() => {
  stopSlide()
  if (pollTimer !== undefined) clearInterval(pollTimer)
})
</script>

<template>
  <DashboardSection :id="props.sectionId" :title="props.title" :updated-at="fetchedAt">
    <template #controls>
      <button
        class="auto-slide-btn"
        :class="{ active: autoSlide }"
        :aria-pressed="autoSlide"
        @click="toggleAutoSlide"
      >
        ▶ 자동 슬라이드
      </button>
    </template>

    <div v-if="loading" class="loading">데이터 로딩 중</div>
    <div v-else-if="sortedItems.length === 0" class="no-data">교통돌발상황 정보가 없습니다</div>
    <div v-else class="traffic">
      <div class="news-toolbar">
        <span v-if="autoSlide" class="auto-badge">▶ 자동</span>
        <span v-else-if="sortedItems.length > PER_PAGE" class="pagination">
          <button class="page-btn" :disabled="!hasPrev" @click="changePage(-1)">◀</button>
          <span class="page-info">{{ currentPage }}/{{ totalPages }}</span>
          <button class="page-btn" :disabled="!hasNext" @click="changePage(1)">▶</button>
        </span>
      </div>

      <div class="traffic-container">
        <table class="traffic-table">
          <thead>
            <tr>
              <th>일시</th>
              <th>도로명</th>
              <th>내용</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in displayedItems" :key="`${item.startDate}-${idx}`">
              <td>{{ formatDate(item.startDate) }}</td>
              <td>{{ item.roadName }}</td>
              <td>{{ item.message }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </DashboardSection>
</template>

<style scoped>
.auto-slide-btn {
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  border-radius: 4px;
  padding: 3px 8px;
  font-size: 10px;
  cursor: pointer;
}

.auto-slide-btn:hover {
  border-color: var(--accent-cyan);
  color: var(--accent-cyan);
}

.auto-slide-btn.active {
  border-color: var(--accent-green);
  color: var(--accent-green);
}

.news-toolbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-bottom: 6px;
}

.auto-badge {
  font-size: 9px;
  color: var(--accent-green);
}

.pagination {
  display: flex;
  align-items: center;
  gap: 4px;
}

.page-btn {
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  width: 20px;
  height: 18px;
  border-radius: 3px;
  cursor: pointer;
  font-size: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.page-btn:hover:not(:disabled) {
  border-color: var(--accent-cyan);
  color: var(--accent-cyan);
}

.page-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.page-info {
  font-size: 9px;
  color: var(--text-muted);
  min-width: 28px;
  text-align: center;
}

.traffic-container {
  font-size: 10px;
  overflow-x: auto;
}

.traffic-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 9px;
}

.traffic-table thead {
  background: var(--bg-tertiary);
  position: sticky;
  top: 0;
}

.traffic-table th {
  padding: 6px 4px;
  text-align: left;
  color: var(--accent-cyan);
  font-weight: 600;
  border-bottom: 1px solid var(--border-color);
  white-space: nowrap;
}

.traffic-table td {
  padding: 4px;
  color: var(--text-secondary);
  border-bottom: 1px dotted var(--border-color);
  white-space: nowrap;
}

.traffic-table tr:hover {
  background: var(--bg-tertiary);
}
</style>
