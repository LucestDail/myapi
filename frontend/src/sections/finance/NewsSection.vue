<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'
import { useDashboardStore } from '@/stores/dashboard'
import type { NewsItem } from '@/types/dashboard'

/**
 * 원본: static/js/features/news.js + static/index.html #news-section
 *
 * 야후 파이낸스 + 연합뉴스. SSE 로 이미 들어온 dashboard.news 를 그대로 쓴다.
 *
 * 원본은 두 피드(야후/연합)의 수동 페이지네이션 인덱스를 변수 하나로 공유했다
 * (`newsPageIndex`) — 한쪽에서 페이지를 넘기면 다른 쪽도 같이 넘어가 버려서,
 * 목록 길이가 다르면 빈 페이지가 보일 수 있었다. 여기서는 각 피드에 별도
 * 인덱스를 둬서 그 결함을 없앴다. 자동 슬라이드 틱은 원본처럼 공유한다
 * (둘이 같이 도는 게 "자동" 모드의 의도다).
 */
const props = defineProps<{ sectionId: string; title: string }>()

const dashboard = useDashboardStore()

const NEWS_PER_PAGE = 5
const SLIDE_INTERVAL_MS = 5_000
const PREFS_KEY = 'myapiNewsPrefs'

function loadAutoSlidePref(): boolean {
  try {
    const raw = localStorage.getItem(PREFS_KEY)
    if (raw) return Boolean(JSON.parse(raw).autoSlide)
  } catch {
    // 막혀 있으면 기본값(off)으로 — 동작에는 지장 없다.
  }
  return false
}

function persistAutoSlide(value: boolean) {
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify({ autoSlide: value }))
  } catch {
    // 저장 실패는 무해하다.
  }
}

const autoSlide = ref(loadAutoSlidePref())
const slideIndex = ref(0)
const yahooPageIndex = ref(0)
const yonhapPageIndex = ref(0)

function linkOf(item: NewsItem): string {
  return (item.link as string | undefined) ?? item.url ?? '#'
}

function pubDateOf(item: NewsItem): string {
  const raw = (item.pubDate as string | undefined) ?? item.publishedAt
  return raw ? String(raw) : ''
}

function byDateDesc(a: NewsItem, b: NewsItem): number {
  const aTime = pubDateOf(a) ? new Date(pubDateOf(a)).getTime() : 0
  const bTime = pubDateOf(b) ? new Date(pubDateOf(b)).getTime() : 0
  return (Number.isNaN(bTime) ? 0 : bTime) - (Number.isNaN(aTime) ? 0 : aTime)
}

const sortedYahoo = computed(() => [...dashboard.news.yahooNews].sort(byDateDesc))
const sortedYonhap = computed(() => [...dashboard.news.yonhapNews].sort(byDateDesc))

function getDisplayed(list: NewsItem[], pageIndex: number): NewsItem[] {
  if (autoSlide.value) {
    if (list.length <= NEWS_PER_PAGE) return list.slice(0, NEWS_PER_PAGE)
    const start = slideIndex.value % list.length
    const result: NewsItem[] = []
    for (let i = 0; i < Math.min(NEWS_PER_PAGE, list.length); i++) {
      result.push(list[(start + i) % list.length])
    }
    return result
  }
  const start = pageIndex * NEWS_PER_PAGE
  return list.slice(start, start + NEWS_PER_PAGE)
}

function paginationOf(list: NewsItem[], pageIndex: number) {
  const totalPages = Math.max(1, Math.ceil(list.length / NEWS_PER_PAGE))
  const currentPage = pageIndex + 1
  return { totalPages, currentPage, hasPrev: currentPage > 1, hasNext: currentPage < totalPages }
}

const displayedYahoo = computed(() => getDisplayed(sortedYahoo.value, yahooPageIndex.value))
const displayedYonhap = computed(() => getDisplayed(sortedYonhap.value, yonhapPageIndex.value))
const yahooPagination = computed(() => paginationOf(sortedYahoo.value, yahooPageIndex.value))
const yonhapPagination = computed(() => paginationOf(sortedYonhap.value, yonhapPageIndex.value))

function changeYahooPage(delta: number) {
  const totalPages = Math.ceil(sortedYahoo.value.length / NEWS_PER_PAGE)
  const next = yahooPageIndex.value + delta
  if (next >= 0 && next < totalPages) yahooPageIndex.value = next
}

function changeYonhapPage(delta: number) {
  const totalPages = Math.ceil(sortedYonhap.value.length / NEWS_PER_PAGE)
  const next = yonhapPageIndex.value + delta
  if (next >= 0 && next < totalPages) yonhapPageIndex.value = next
}

let slideTimer: number | undefined

function stopAutoSlide() {
  if (slideTimer !== undefined) {
    clearInterval(slideTimer)
    slideTimer = undefined
  }
}

function startAutoSlide() {
  stopAutoSlide()
  slideTimer = window.setInterval(() => {
    slideIndex.value += 1
  }, SLIDE_INTERVAL_MS)
}

function onAutoSlideChange() {
  persistAutoSlide(autoSlide.value)
  if (autoSlide.value) {
    startAutoSlide()
  } else {
    stopAutoSlide()
    yahooPageIndex.value = 0
    yonhapPageIndex.value = 0
  }
}

function openNews(item: NewsItem) {
  window.open(linkOf(item), '_blank', 'noopener,noreferrer')
}

onMounted(() => {
  if (autoSlide.value) startAutoSlide()
})

onBeforeUnmount(() => {
  stopAutoSlide()
})
</script>

<template>
  <DashboardSection :id="props.sectionId" :title="props.title" :updated-at="dashboard.news.fetchedAt">
    <template #controls>
      <label class="form-checkbox">
        <input v-model="autoSlide" type="checkbox" @change="onAutoSlideChange" />
        <span>자동 슬라이드</span>
      </label>
    </template>

    <div v-if="sortedYahoo.length === 0 && sortedYonhap.length === 0" class="no-data">뉴스 없음</div>
    <div v-else class="news-container">
      <div class="news-section-title">
        <span>YAHOO FINANCE</span>
        <span v-if="autoSlide" class="news-auto-slide">▶ 자동</span>
        <span v-else-if="sortedYahoo.length > NEWS_PER_PAGE" class="news-pagination">
          <button class="page-btn" :disabled="!yahooPagination.hasPrev" @click="changeYahooPage(-1)">◀</button>
          <span class="page-info">{{ yahooPagination.currentPage }}/{{ yahooPagination.totalPages }}</span>
          <button class="page-btn" :disabled="!yahooPagination.hasNext" @click="changeYahooPage(1)">▶</button>
        </span>
      </div>
      <div
        v-for="(item, idx) in displayedYahoo"
        :key="'yahoo-' + idx + linkOf(item)"
        class="news-item"
        :class="{ 'news-highlight': autoSlide && idx === 0 }"
        @click="openNews(item)"
      >
        <div class="news-title">{{ item.title }}</div>
        <div class="news-meta"><span>{{ pubDateOf(item) }}</span></div>
      </div>

      <template v-if="sortedYonhap.length > 0">
        <div class="news-section-title">
          <span>연합뉴스</span>
          <span v-if="autoSlide" class="news-auto-slide">▶ 자동</span>
          <span v-else-if="sortedYonhap.length > NEWS_PER_PAGE" class="news-pagination">
            <button class="page-btn" :disabled="!yonhapPagination.hasPrev" @click="changeYonhapPage(-1)">◀</button>
            <span class="page-info">{{ yonhapPagination.currentPage }}/{{ yonhapPagination.totalPages }}</span>
            <button class="page-btn" :disabled="!yonhapPagination.hasNext" @click="changeYonhapPage(1)">▶</button>
          </span>
        </div>
        <div
          v-for="(item, idx) in displayedYonhap"
          :key="'yonhap-' + idx + linkOf(item)"
          class="news-item"
          :class="{ 'news-highlight': autoSlide && idx === 0 }"
          @click="openNews(item)"
        >
          <div class="news-title">{{ item.title }}</div>
          <div class="news-meta"><span>{{ pubDateOf(item) }}</span></div>
        </div>
      </template>
    </div>
  </DashboardSection>
</template>

<style scoped>
.form-checkbox {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 11px;
  color: var(--text-secondary);
}

.form-checkbox input {
  width: 14px;
  height: 14px;
  accent-color: var(--accent-cyan);
}

.no-data {
  color: var(--text-muted);
  font-size: 11px;
  font-style: italic;
  padding: 8px 0;
}

.news-container {
  font-size: 11px;
}

.news-item {
  padding: 6px 0;
  border-bottom: 1px dotted var(--border-color);
  cursor: pointer;
  transition: all 0.2s;
}

.news-item:last-child {
  border-bottom: none;
}

.news-item:hover {
  background: var(--bg-tertiary);
  margin: 0 -8px;
  padding: 6px 8px;
}

.news-title {
  color: var(--text-primary);
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.news-meta {
  color: var(--text-muted);
  font-size: 9px;
  margin-top: 2px;
  display: flex;
  gap: 8px;
}

.news-section-title {
  color: var(--accent-magenta);
  font-size: 10px;
  font-weight: 600;
  margin: 10px 0 6px 0;
  padding-top: 8px;
  border-top: 1px dotted var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.news-section-title:first-child {
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}

.news-auto-slide {
  font-size: 9px;
  color: var(--accent-green);
}

.news-pagination {
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
  transition: all 0.2s;
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

.news-highlight {
  background: var(--bg-tertiary);
  margin: 0 -8px;
  padding: 6px 8px;
  border-left: 2px solid var(--accent-cyan);
}
</style>
