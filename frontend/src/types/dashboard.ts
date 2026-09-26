/**
 * 서버 DTO 와 1:1 대응하는 타입.
 * 원본: `src/main/java/com/example/myapi/dto/dashboard/DashboardData.java`
 *
 * ⚠️ 서버 record 를 고치면 여기도 같이 고친다. 한쪽만 바꾸면 컴파일은 통과하고
 *    런타임에 undefined 가 흘러다닌다.
 */

export interface StockQuote {
  symbol: string
  name: string | null
  currentPrice: number | null
  change: number | null
  percentChange: number | null
  highPrice: number | null
  lowPrice: number | null
  openPrice: number | null
  previousClose: number | null
}

export interface StocksData {
  quotes: StockQuote[]
  fetchedAt: string | null
}

export interface WeatherData {
  city: string
  cityKo: string
  temperatureCelsius: number
  [key: string]: unknown
}

export interface NewsItem {
  title: string
  link?: string
  url?: string
  publishedAt?: string
  source?: string
  content?: string
  [key: string]: unknown
}

export interface NewsData {
  yahooNews: NewsItem[]
  yonhapNews: NewsItem[]
  fetchedAt: string | null
}

export interface SystemData {
  cpuUsage?: number
  memoryUsage?: number
  [key: string]: unknown
}

/** SSE `dashboard` 이벤트 / `GET api/dashboard/data` 응답. */
export interface DashboardData {
  type: 'stocks' | 'weather' | 'news' | 'system' | 'full'
  timestamp: string
  stocks: StocksData | null
  weather: WeatherData[] | null
  news: NewsData | null
  system: SystemData | null
}

export interface TickerConfig {
  symbol: string
  name: string
}

export interface DashboardConfig {
  youtubeUrl: string
  tickers: TickerConfig[]
}

/**
 * 대시보드 모드 — 우측 패널 상단 탭. 섹션이 어느 모드에 보일지 결정한다.
 *
 * 🔴 2026-09-26 정정: 처음 이식할 때 'system' 을 별도 탭이 아니라 고아 섹션으로
 * 오판해 'life' 에 합쳤다. 원본 index.html 을 다시 확인하니 실제로는 **탭이 5개**였다
 * (사회/금융/생활/생산성/시스템). 라벨·아이콘도 원본 그대로 맞춘다.
 */
export type DashboardMode = 'social' | 'finance' | 'life' | 'productivity' | 'system'

export const DASHBOARD_MODES: ReadonlyArray<{ id: DashboardMode; label: string; icon: string }> = [
  { id: 'social', label: '사회', icon: '📰' },
  { id: 'finance', label: '금융', icon: '$' },
  { id: 'life', label: '생활', icon: '☀' },
  { id: 'productivity', label: '생산성', icon: '✓' },
  { id: 'system', label: '시스템', icon: '▣' }
]

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected'
