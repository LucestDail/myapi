<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'
import { api } from '@/api/client'
import { useDashboardStore } from '@/stores/dashboard'
import type { StockQuote } from '@/types/dashboard'

/**
 * 원본: static/js/features/stocks.js + static/index.html #stocks-section
 *
 * 주가는 SSE 로 dashboard 스토어에 이미 들어온다. 여기서는 필터/정렬/즐겨찾기 같은
 * 화면 상태만 다루고, 하이라이트가 도는 종목에 대해서만 관련 뉴스를 따로 불러온다
 * (원본의 `loadStockNews` — 전체 종목 뉴스를 한 번에 당겨오면 API 호출이 너무 많다).
 */
const props = defineProps<{ sectionId: string; title: string }>()

const dashboard = useDashboardStore()

type Filter = 'all' | 'up' | 'down'
type SortBy = 'default' | 'percentChange'
type SortOrder = 'asc' | 'desc'

const PREFS_KEY = 'myapiStocksPrefs'
const HIGHLIGHT_INTERVAL_MS = 10_000

interface Prefs {
  filter: Filter
  sortBy: SortBy
  sortOrder: SortOrder
  favorites: string[]
  autoHighlight: boolean
}

function loadPrefs(): Prefs {
  try {
    const raw = localStorage.getItem(PREFS_KEY)
    if (raw) return { filter: 'all', sortBy: 'default', sortOrder: 'desc', favorites: [], autoHighlight: true, ...JSON.parse(raw) }
  } catch {
    // localStorage 가 막혀 있으면 기본값으로 — 저장만 안 될 뿐 동작은 해야 한다.
  }
  return { filter: 'all', sortBy: 'default', sortOrder: 'desc', favorites: [], autoHighlight: true }
}

const initial = loadPrefs()
const filter = ref<Filter>(initial.filter)
const sortBy = ref<SortBy>(initial.sortBy)
const sortOrder = ref<SortOrder>(initial.sortOrder)
const favorites = ref<string[]>(initial.favorites)
const autoHighlight = ref<boolean>(initial.autoHighlight)

function persist() {
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify({
      filter: filter.value,
      sortBy: sortBy.value,
      sortOrder: sortOrder.value,
      favorites: favorites.value,
      autoHighlight: autoHighlight.value
    }))
  } catch {
    // 저장 실패는 무해하다.
  }
}

const displayedQuotes = computed(() => {
  let quotes = [...dashboard.stocks.quotes]

  if (filter.value === 'up') quotes = quotes.filter(s => (s.change ?? 0) > 0)
  else if (filter.value === 'down') quotes = quotes.filter(s => (s.change ?? 0) < 0)

  if (sortBy.value !== 'default') {
    quotes.sort((a, b) => {
      const aVal = a.percentChange ?? 0
      const bVal = b.percentChange ?? 0
      return sortOrder.value === 'asc' ? aVal - bVal : bVal - aVal
    })
  }

  // 즐겨찾기를 맨 위로 — Array.sort 는 안정 정렬이라 위 정렬 결과의 상대순서는 유지된다.
  quotes.sort((a, b) => {
    const aFav = favorites.value.includes(a.symbol) ? 1 : 0
    const bFav = favorites.value.includes(b.symbol) ? 1 : 0
    return bFav - aFav
  })

  return quotes
})

function setFilter(next: Filter) {
  filter.value = next
  persist()
}

const sortLabel = computed(() => {
  if (sortBy.value === 'default') return '정렬: 변화율'
  return `정렬: 변화율 ${sortOrder.value === 'desc' ? '↓' : '↑'}`
})

function toggleSort() {
  if (sortBy.value === 'default') {
    sortBy.value = 'percentChange'
  } else if (sortBy.value === 'percentChange' && sortOrder.value === 'desc') {
    sortOrder.value = 'asc'
  } else {
    sortBy.value = 'default'
    sortOrder.value = 'desc'
  }
  persist()
}

function toggleFavorite(symbol: string) {
  const idx = favorites.value.indexOf(symbol)
  if (idx === -1) favorites.value = [...favorites.value, symbol]
  else favorites.value = favorites.value.filter(s => s !== symbol)
  persist()
}

function formatPrice(price: number | null): string {
  return price != null ? `$${price.toFixed(2)}` : 'N/A'
}

function formatChange(stock: StockQuote): string {
  const change = stock.change != null ? stock.change.toFixed(2) : '0.00'
  const percent = stock.percentChange != null ? stock.percentChange.toFixed(2) : '0.00'
  const sign = (stock.change ?? 0) >= 0 ? '+' : ''
  return `${sign}${change} (${sign}${percent}%)`
}

function changeClass(stock: StockQuote): string {
  return (stock.change ?? 0) >= 0 ? 'positive' : 'negative'
}

// ── 가격 변동 시 flip 애니메이션 (원본 stocks.css .flipping) ──────────────
const prevPrices = new Map<string, string>()
const flippingSymbols = ref<Set<string>>(new Set())
const flipTimers = new Set<number>()

watch(
  () => dashboard.stocks.quotes,
  quotes => {
    quotes.forEach(stock => {
      const priceStr = formatPrice(stock.currentPrice)
      const prev = prevPrices.get(stock.symbol)
      if (prev !== undefined && prev !== priceStr) {
        const next = new Set(flippingSymbols.value)
        next.add(stock.symbol)
        flippingSymbols.value = next
        const timer = window.setTimeout(() => {
          const after = new Set(flippingSymbols.value)
          after.delete(stock.symbol)
          flippingSymbols.value = after
          flipTimers.delete(timer)
        }, 300)
        flipTimers.add(timer)
      }
      prevPrices.set(stock.symbol, priceStr)
    })
  },
  { deep: true }
)

// ── 하이라이트 순환 + 종목 뉴스 ───────────────────────────────────────────
const highlightIndex = ref(0)
const highlightedSymbol = computed<string | null>(() => {
  const quotes = dashboard.stocks.quotes
  if (quotes.length === 0) return null
  return quotes[highlightIndex.value % quotes.length]?.symbol ?? null
})

interface RssItem {
  title: string
  link: string
  description?: string | null
  pubDate?: string | null
  source?: string | null
}

interface RssFeedResponse {
  feedUrl: string
  feedTitle: string
  source: string
  itemCount: number
  items: RssItem[]
  fetchedAt: string | null
  fromCache: boolean
}

const stockNewsSymbol = ref<string | null>(null)
const stockNewsItems = ref<RssItem[]>([])
const stockNewsTime = ref<string | null>(null)
const stockNewsLoading = ref(false)
const stockNewsError = ref(false)

function formatNewsDate(dateStr: string | null | undefined): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return dateStr

  const diffMs = Date.now() - date.getTime()
  if (diffMs < 0) return date.toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })

  const diffMins = Math.floor(diffMs / 60_000)
  const diffHours = Math.floor(diffMs / 3_600_000)
  const diffDays = Math.floor(diffMs / 86_400_000)

  if (diffMins < 1) return '방금 전'
  if (diffHours < 1) return `${diffMins}분 전`
  if (diffDays < 1) return `${diffHours}시간 전`
  if (diffDays < 7) return `${diffDays}일 전`
  return date.toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function loadStockNews(symbol: string | null) {
  if (!symbol) return
  stockNewsSymbol.value = symbol
  stockNewsLoading.value = true
  stockNewsError.value = false

  try {
    const data = await api.get<RssFeedResponse>(`api/rss/yahoo/stock?symbol=${encodeURIComponent(symbol)}`)
    if (stockNewsSymbol.value !== symbol) return // 그 사이 다른 종목이 선택됐다
    stockNewsItems.value = (data.items ?? []).slice(0, 3)
    stockNewsTime.value = data.fetchedAt
  } catch (error) {
    console.warn(`[stocks] ${symbol} 뉴스 로딩 실패`, error)
    if (stockNewsSymbol.value !== symbol) return
    stockNewsItems.value = []
    stockNewsError.value = true
  } finally {
    if (stockNewsSymbol.value === symbol) stockNewsLoading.value = false
  }
}

let highlightTimer: number | undefined
let initialLoadTimer: number | undefined
let initialLoaded = false

function stopHighlightTimer() {
  if (highlightTimer !== undefined) {
    clearInterval(highlightTimer)
    highlightTimer = undefined
  }
}

function startHighlightTimer() {
  stopHighlightTimer()
  if (!autoHighlight.value) return
  highlightTimer = window.setInterval(() => {
    const quotes = dashboard.stocks.quotes
    if (quotes.length === 0) return
    highlightIndex.value = (highlightIndex.value + 1) % quotes.length
    loadStockNews(highlightedSymbol.value)
  }, HIGHLIGHT_INTERVAL_MS)
}

function selectStock(symbol: string) {
  const idx = dashboard.stocks.quotes.findIndex(s => s.symbol === symbol)
  if (idx === -1) return
  highlightIndex.value = idx
  loadStockNews(symbol)
  if (autoHighlight.value) startHighlightTimer()
}

// 데이터가 처음 도착하면(빈 배열 → 채워짐) 1초 뒤 초기 종목 뉴스를 불러온다 — 원본과 동일.
watch(
  () => dashboard.stocks.quotes.length,
  len => {
    if (len > 0 && !initialLoaded) {
      initialLoaded = true
      initialLoadTimer = window.setTimeout(() => {
        loadStockNews(highlightedSymbol.value)
      }, 1000)
    }
  },
  { immediate: true }
)

onMounted(() => {
  startHighlightTimer()
})

onBeforeUnmount(() => {
  stopHighlightTimer()
  if (initialLoadTimer !== undefined) clearTimeout(initialLoadTimer)
  flipTimers.forEach(t => clearTimeout(t))
  flipTimers.clear()
})
</script>

<template>
  <DashboardSection :id="props.sectionId" :title="props.title" :updated-at="dashboard.stocks.fetchedAt">
    <template #controls>
      <button class="ctrl-btn" :class="{ active: filter === 'all' }" @click="setFilter('all')">전체</button>
      <button class="ctrl-btn" :class="{ active: filter === 'up' }" @click="setFilter('up')">상승</button>
      <button class="ctrl-btn" :class="{ active: filter === 'down' }" @click="setFilter('down')">하락</button>
      <button class="ctrl-btn" :class="{ active: sortBy !== 'default' }" @click="toggleSort">{{ sortLabel }}</button>
    </template>

    <div v-if="displayedQuotes.length === 0" class="no-data">데이터 없음</div>
    <div v-else class="stock-list">
      <div
        v-for="stock in displayedQuotes"
        :key="stock.symbol"
        class="stock-item"
        :class="{ highlighted: stock.symbol === highlightedSymbol }"
        @click="selectStock(stock.symbol)"
      >
        <span
          class="stock-favorite"
          :class="{ active: favorites.includes(stock.symbol) }"
          @click.stop="toggleFavorite(stock.symbol)"
        >{{ favorites.includes(stock.symbol) ? '★' : '☆' }}</span>
        <span class="stock-symbol">{{ stock.symbol }}</span>
        <span class="stock-name">{{ stock.name || '' }}</span>
        <span class="stock-price" :class="{ flipping: flippingSymbols.has(stock.symbol) }">{{ formatPrice(stock.currentPrice) }}</span>
        <span class="stock-change" :class="changeClass(stock)">{{ formatChange(stock) }}</span>
      </div>
    </div>

    <div class="stock-news-container">
      <div class="stock-news-header">
        <span><span class="stock-news-ticker">{{ stockNewsSymbol || '-' }}</span> 관련 뉴스</span>
        <span>{{ stockNewsTime ? new Date(stockNewsTime).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) : '' }}</span>
      </div>
      <div class="stock-news-list">
        <div v-if="!stockNewsSymbol" class="stock-news-empty">티커를 선택하면 관련 뉴스가 표시됩니다</div>
        <div v-else-if="stockNewsLoading" class="stock-news-empty">뉴스 로딩 중...</div>
        <div v-else-if="stockNewsError" class="stock-news-empty">뉴스를 불러올 수 없습니다</div>
        <div v-else-if="stockNewsItems.length === 0" class="stock-news-empty">관련 뉴스가 없습니다</div>
        <template v-else>
          <a
            v-for="(item, idx) in stockNewsItems"
            :key="idx"
            class="stock-news-item"
            :href="item.link"
            target="_blank"
            rel="noopener noreferrer"
          >
            <div class="stock-news-title">{{ item.title }}</div>
            <div class="stock-news-meta">
              <span>{{ item.source || 'Yahoo Finance' }}</span>
              <span>{{ formatNewsDate(item.pubDate) }}</span>
            </div>
          </a>
        </template>
      </div>
    </div>
  </DashboardSection>
</template>

<style scoped>
.ctrl-btn {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  padding: 3px 8px;
  border-radius: 3px;
  cursor: pointer;
  font-family: inherit;
  font-size: 9px;
  transition: all 0.2s;
}

.ctrl-btn:hover {
  border-color: var(--accent-cyan);
  color: var(--accent-cyan);
}

.ctrl-btn.active {
  border-color: var(--accent-blue);
  color: var(--accent-blue);
  background: color-mix(in srgb, var(--accent-blue) 12%, transparent);
}

.no-data {
  color: var(--text-muted);
  font-size: 11px;
  font-style: italic;
  padding: 8px 0;
}

.stock-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11px;
}

.stock-item {
  display: grid;
  grid-template-columns: 16px 55px 70px 70px 95px;
  align-items: center;
  padding: 4px 0;
  border-bottom: 1px dotted var(--border-color);
  gap: 4px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.stock-item:last-child {
  border-bottom: none;
}

.stock-favorite {
  cursor: pointer;
  color: var(--text-muted);
  font-size: 10px;
  text-align: center;
}

.stock-favorite.active {
  color: var(--accent-yellow);
}

.stock-symbol {
  color: var(--accent-cyan);
  font-weight: 600;
  text-align: left;
}

.stock-name {
  color: var(--text-secondary);
  font-size: 10px;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stock-price {
  color: var(--text-primary);
  font-weight: 500;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.stock-change {
  font-size: 10px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.stock-change.positive {
  color: var(--accent-green);
}

.stock-change.negative {
  color: var(--accent-red);
}

.stock-item.highlighted {
  background: color-mix(in srgb, var(--accent-cyan) 10%, transparent);
  border-left: 2px solid var(--accent-cyan);
  margin-left: -2px;
  padding-left: 2px;
  transform: translateX(2px);
}

.stock-price.flipping {
  animation: digitFlip 0.3s ease-out;
}

@keyframes digitFlip {
  0% { transform: translateY(0) rotateX(0); opacity: 1; }
  50% { transform: translateY(-30%) rotateX(-90deg); opacity: 0.5; }
  100% { transform: translateY(0) rotateX(0); opacity: 1; }
}

.stock-news-container {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border-color);
  min-height: 120px;
  display: flex;
  flex-direction: column;
}

.stock-news-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  color: var(--text-secondary);
  font-size: 10px;
}

.stock-news-ticker {
  color: var(--accent-cyan);
  font-weight: 600;
}

.stock-news-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stock-news-item {
  display: block;
  padding: 6px 8px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  text-decoration: none;
  transition: all 0.2s;
  cursor: pointer;
}

.stock-news-item:hover {
  background: var(--bg-secondary);
  border-left: 2px solid var(--accent-cyan);
}

.stock-news-title {
  color: var(--text-primary);
  font-size: 10px;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.stock-news-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 9px;
  color: var(--text-muted);
}

.stock-news-empty {
  text-align: center;
  color: var(--text-muted);
  font-size: 10px;
  padding: 12px;
}
</style>
