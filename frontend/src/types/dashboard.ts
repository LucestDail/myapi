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

/** 대시보드 모드 — 상단 탭. 섹션이 어느 모드에 보일지 결정한다. */
export type DashboardMode = 'social' | 'finance' | 'life' | 'productivity'

export const DASHBOARD_MODES: ReadonlyArray<{ id: DashboardMode; label: string }> = [
  { id: 'social', label: '소셜' },
  { id: 'finance', label: '금융' },
  { id: 'life', label: '생활' },
  { id: 'productivity', label: '생산성' }
]

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected'
