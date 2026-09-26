<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'
import { tryGet } from '@/api/client'

/**
 * 소셜 뉴스 섹션.
 * 원본: static/js/features/social.js 의 renderSocialNews/loadSocialNews.
 *
 * 원본은 `onclick="changeSocialNewsPage(-1)"` 같은 인라인 핸들러와 전역 mutable
 * 변수(socialNewsPageIndex 등)로 페이지/슬라이드 상태를 관리했다. 여기서는 컴포넌트
 * 로컬 상태(ref)로 대체한다.
 */
const props = defineProps<{ sectionId: string; title: string }>()

interface SocialNewsItem {
  title?: string
  content?: string
  company?: string
  link?: string
  createDT?: string
  [key: string]: unknown
}

const PER_PAGE = 3
const SLIDE_INTERVAL_MS = 5000
const AUTO_SLIDE_KEY = 'myapi.socialNews.autoSlide'

const items = ref<SocialNewsItem[]>([])
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

const totalPages = computed(() => Math.max(1, Math.ceil(items.value.length / PER_PAGE)))
const currentPage = computed(() => pageIndex.value + 1)
const hasPrev = computed(() => currentPage.value > 1)
const hasNext = computed(() => currentPage.value < totalPages.value)

const displayedNews = computed(() => {
  const list = items.value
  if (list.length === 0) return []
  if (autoSlide.value) {
    if (list.length <= PER_PAGE) return list.slice(0, PER_PAGE)
    const start = slideIndex.value % list.length
    const result: SocialNewsItem[] = []
    for (let i = 0; i < Math.min(PER_PAGE, list.length); i++) {
      result.push(list[(start + i) % list.length])
    }
    return result
  }
  const start = pageIndex.value * PER_PAGE
  return list.slice(start, start + PER_PAGE)
})

function formatNewsDate(dateStr?: string): string {
  if (!dateStr || dateStr.trim() === '') return ''
  try {
    let date: Date
    const m = dateStr.match(/^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})$/)
    if (m) {
      // 서버가 "yyyy-MM-dd HH:mm:ss" 로 KST 시각을 보낸다. KST 로 해석해 UTC 로 변환한다.
      const [, year, month, day, hours, minutes, seconds] = m
      const kstOffsetMs = 9 * 60 * 60 * 1000
      const utcMs = Date.UTC(+year, +month - 1, +day, +hours, +minutes, +seconds)
      date = new Date(utcMs - kstOffsetMs)
    } else {
      date = new Date(dateStr)
    }
    if (Number.isNaN(date.getTime())) return dateStr

    const pad = (n: number) => String(n).padStart(2, '0')
    const absolute = () =>
      `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`

    const diffMs = Date.now() - date.getTime()
    if (diffMs < 0) return absolute()

    const diffMins = Math.floor(diffMs / 60000)
    const diffHours = Math.floor(diffMs / 3600000)
    const diffDays = Math.floor(diffMs / 86400000)
    if (diffMins < 1) return '방금 전'
    if (diffHours < 1) return `${diffMins}분 전`
    if (diffDays < 1) return `${diffHours}시간 전`
    if (diffDays < 7) return `${diffDays}일 전`
    return absolute()
  } catch {
    return dateStr
  }
}

function truncate(content: string | undefined): string {
  const text = content || ''
  return text.length > 200 ? `${text.substring(0, 200)}...` : text
}

function openLink(link?: string) {
  if (link) window.open(link, '_blank')
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
  const res = await tryGet<{ data?: { items?: SocialNewsItem[] } }>('api/social/news', {})
  items.value = res.data?.items ?? []
  fetchedAt.value = new Date().toISOString()
  loading.value = false
}

onMounted(() => {
  load()
  // 원본: 1시간마다 갱신 (뉴스는 자주 안 바뀐다)
  pollTimer = window.setInterval(load, 60 * 60 * 1000)
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
    <div v-else-if="items.length === 0" class="no-data">뉴스 데이터가 없습니다</div>
    <div v-else class="social-news">
      <div class="news-toolbar">
        <span v-if="autoSlide" class="auto-badge">▶ 자동</span>
        <span v-else-if="items.length > PER_PAGE" class="pagination">
          <button class="page-btn" :disabled="!hasPrev" @click="changePage(-1)">◀</button>
          <span class="page-info">{{ currentPage }}/{{ totalPages }}</span>
          <button class="page-btn" :disabled="!hasNext" @click="changePage(1)">▶</button>
        </span>
      </div>

      <div class="social-news-container">
        <div
          v-for="(news, idx) in displayedNews"
          :key="`${news.link || news.title}-${idx}`"
          class="social-news-item"
          :class="{ 'news-highlight': autoSlide && idx === 0, clickable: !!news.link }"
          @click="openLink(news.link)"
        >
          <div class="social-news-header">
            <span class="social-news-time">{{ formatNewsDate(news.createDT) || news.createDT }}</span>
            <span class="social-news-company">{{ news.company }}</span>
          </div>
          <div class="social-news-title">{{ news.title }}</div>
          <div class="social-news-content">{{ truncate(news.content) }}</div>
        </div>
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

.social-news-container {
  font-size: 11px;
}

.social-news-item {
  padding: 8px 0;
  border-bottom: 1px dotted var(--border-color);
  transition: all 0.2s;
}

.social-news-item.clickable {
  cursor: pointer;
}

.social-news-item.clickable:hover {
  background: var(--bg-tertiary);
  margin: 0 -8px;
  padding: 8px 8px;
}

.social-news-item:last-child {
  border-bottom: none;
}

.social-news-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.social-news-time {
  color: var(--text-muted);
  font-size: 9px;
}

.social-news-company {
  color: var(--accent-magenta);
  font-size: 9px;
  font-weight: 600;
}

.social-news-title {
  color: var(--text-primary);
  font-weight: 600;
  line-height: 1.4;
  margin-bottom: 4px;
}

.social-news-content {
  color: var(--text-secondary);
  font-size: 10px;
  line-height: 1.5;
}

.news-highlight {
  background: var(--bg-tertiary);
  margin: 0 -8px;
  padding: 8px 8px;
  border-left: 2px solid var(--accent-cyan);
}
</style>
