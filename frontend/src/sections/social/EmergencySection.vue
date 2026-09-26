<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'
import { tryGet } from '@/api/client'

/**
 * 긴급재난문자 섹션.
 * 원본: static/js/features/social.js 의 renderEmergency/loadEmergency.
 *
 * 🔧 원본은 `EMERGENCY_PER_PAGE` 상수를 10 으로 export 해놓고 실제 렌더 함수에서는
 *    `emergencyPerPage = 20` 을 하드코딩해 무시했다(죽은 상수). 여기서는 요구사항대로
 *    10개씩 페이지네이션한다.
 */
const props = defineProps<{ sectionId: string; title: string }>()

interface EmergencyItem {
  createDate?: string
  registerDate?: string
  locationName?: string
  msg?: string
  category?: string
  emergencyStep?: string
  detail?: string
  [key: string]: unknown
}

const PER_PAGE = 10
const SLIDE_INTERVAL_MS = 5000
const POLL_INTERVAL_MS = 5 * 60 * 1000
const AUTO_SLIDE_KEY = 'myapi.emergency.autoSlide'

const rawItems = ref<EmergencyItem[]>([])
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

function sortKey(item: EmergencyItem): string {
  return item.createDate || item.registerDate || ''
}

// 최신 순 정렬 (createDate/registerDate: "20260118181908" 형식)
const sortedItems = computed(() =>
  [...rawItems.value].sort((a, b) => sortKey(b).localeCompare(sortKey(a)))
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
    const result: EmergencyItem[] = []
    for (let i = 0; i < Math.min(PER_PAGE, list.length); i++) {
      result.push(list[(start + i) % list.length])
    }
    return result
  }
  const start = pageIndex.value * PER_PAGE
  return list.slice(start, start + PER_PAGE)
})

function formatDate(item: EmergencyItem): string {
  const s = sortKey(item)
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
  const res = await tryGet<{ items?: EmergencyItem[] }>('api/social/emergency', {})
  rawItems.value = res.items ?? []
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
    <div v-else-if="sortedItems.length === 0" class="no-data">현재 긴급재난문자가 없습니다</div>
    <div v-else class="emergency">
      <div class="news-toolbar">
        <span v-if="autoSlide" class="auto-badge">▶ 자동</span>
        <span v-else-if="sortedItems.length > PER_PAGE" class="pagination">
          <button class="page-btn" :disabled="!hasPrev" @click="changePage(-1)">◀</button>
          <span class="page-info">{{ currentPage }}/{{ totalPages }}</span>
          <button class="page-btn" :disabled="!hasNext" @click="changePage(1)">▶</button>
        </span>
      </div>

      <div class="emergency-container">
        <table class="emergency-table">
          <thead>
            <tr>
              <th>일시</th>
              <th>지역</th>
              <th>내용</th>
              <th>분류</th>
              <th>상세</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in displayedItems" :key="`${sortKey(item)}-${idx}`">
              <td>{{ formatDate(item) }}</td>
              <td>{{ item.locationName }}</td>
              <td>{{ item.msg }}</td>
              <td>{{ item.category || item.emergencyStep }}</td>
              <td>{{ item.detail }}</td>
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

.emergency-container {
  font-size: 10px;
  overflow-x: auto;
}

.emergency-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 9px;
}

.emergency-table thead {
  background: var(--bg-tertiary);
  position: sticky;
  top: 0;
}

.emergency-table th {
  padding: 6px 4px;
  text-align: left;
  color: var(--accent-magenta);
  font-weight: 600;
  border-bottom: 1px solid var(--border-color);
  white-space: nowrap;
}

.emergency-table td {
  padding: 4px;
  color: var(--text-secondary);
  border-bottom: 1px dotted var(--border-color);
  white-space: nowrap;
}

.emergency-table tr:hover {
  background: var(--bg-tertiary);
}
</style>
