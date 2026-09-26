import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'
import { api, tryGet, userId } from '@/api/client'
import type {
  ConnectionStatus,
  DashboardConfig,
  DashboardData,
  NewsData,
  StocksData,
  SystemData,
  WeatherData
} from '@/types/dashboard'

/**
 * 대시보드 데이터 + SSE 연결.
 *
 * 종전 구조에서 고친 것:
 *  - 데이터가 state.js 의 모듈 전역 변수였고, 갱신하면 각 기능이 자기 render() 를
 *    직접 불러야 했다(호출을 빠뜨리면 화면만 조용히 낡는다). 여기서는 반응형이라
 *    스토어를 바꾸면 쓰는 쪽이 알아서 다시 그린다.
 *  - 설정 저장 후 화면 갱신을 SSE 재연결에만 맡겨서, 첫 페이로드 도착 전에 모달을
 *    닫으면 "저장했는데 안 바뀐다"로 보였다. refreshNow() 로 명시적으로 당겨온다.
 */
export const useDashboardStore = defineStore('dashboard', () => {
  const stocks = ref<StocksData>({ quotes: [], fetchedAt: null })
  const weather = ref<WeatherData[]>([])
  const news = ref<NewsData>({ yahooNews: [], yonhapNews: [], fetchedAt: null })
  const system = ref<SystemData | null>(null)
  const config = ref<DashboardConfig | null>(null)

  const status = ref<ConnectionStatus>('disconnected')
  const lastUpdated = ref<string | null>(null)

  // EventSource 는 반응형으로 감쌀 필요가 없다(내부 필드가 많고 깊은 추적은 낭비다).
  const source = shallowRef<EventSource | null>(null)
  let reconnectAttempts = 0
  const MAX_RECONNECT = 10

  function apply(data: DashboardData) {
    if (data.stocks) stocks.value = data.stocks
    if (data.weather) weather.value = data.weather
    if (data.news) news.value = data.news
    if (data.system) system.value = data.system
    if (data.timestamp) lastUpdated.value = data.timestamp
  }

  /** 지금 즉시 한 번 당겨온다. SSE 첫 페이로드를 기다리지 않는다. */
  async function refreshNow() {
    const data = await tryGet<DashboardData | null>('api/dashboard/data', null)
    if (data) apply(data)
  }

  async function loadConfig() {
    config.value = await tryGet<DashboardConfig>('api/dashboard/config', {
      youtubeUrl: '',
      tickers: []
    })
  }

  async function saveConfig(next: DashboardConfig): Promise<DashboardConfig> {
    const saved = await api.post<DashboardConfig>('api/dashboard/config', {
      youtubeUrl: next.youtubeUrl,
      // 심볼이 빈 행은 사용자가 추가만 하고 안 채운 것이다. 서버로 보내지 않는다.
      tickers: next.tickers.filter(t => t.symbol && t.symbol.trim() !== '')
    })
    config.value = saved
    await refreshNow()  // 저장 즉시 화면 반영 — SSE 재연결 타이밍에 기대지 않는다
    connect()           // 이후 주기 갱신은 SSE 가 받는다
    return saved
  }

  function disconnect() {
    source.value?.close()
    source.value = null
  }

  function connect() {
    disconnect()
    status.value = 'connecting'

    // EventSource 는 헤더를 못 붙인다. 서버가 쿼리 파라미터를 우선으로 읽도록 되어 있다.
    const es = new EventSource(`api/dashboard/stream?userId=${encodeURIComponent(userId)}`)
    source.value = es

    es.onopen = () => {
      reconnectAttempts = 0
      status.value = 'connected'
    }

    es.addEventListener('dashboard', event => {
      try {
        apply(JSON.parse((event as MessageEvent).data) as DashboardData)
      } catch (error) {
        console.warn('[sse] dashboard 파싱 실패', error)
      }
    })

    es.addEventListener('system', event => {
      try {
        const data = JSON.parse((event as MessageEvent).data) as DashboardData
        if (data.system) system.value = data.system
      } catch (error) {
        console.warn('[sse] system 파싱 실패', error)
      }
    })

    es.onerror = () => {
      status.value = 'disconnected'
      es.close()
      if (reconnectAttempts < MAX_RECONNECT) {
        const delay = Math.min(1000 * 2 ** reconnectAttempts, 30000)
        reconnectAttempts++
        setTimeout(connect, delay)
      }
    }
  }

  /** 앱 시작. 설정 → 즉시 1회 조회 → SSE 연결 순서다. */
  async function start() {
    await loadConfig()
    await refreshNow()
    connect()
  }

  return {
    stocks, weather, news, system, config,
    status, lastUpdated,
    start, connect, disconnect, refreshNow, loadConfig, saveConfig
  }
})
