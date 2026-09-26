import { defineAsyncComponent, type Component } from 'vue'
import type { DashboardMode } from '@/types/dashboard'

/**
 * 섹션 등록부 — 어떤 섹션이 어느 모드에 보이는지 한 곳에서 정한다.
 *
 * 종전에는 이 정보가 index.html 의 `data-mode` 속성에 흩어져 있었고, 보이기/숨기기를
 * `document.querySelectorAll` 로 직접 클래스를 토글해 처리했다. 섹션을 추가하려면
 * HTML·CSS·JS 세 곳을 건드려야 했다.
 *
 * 🔴 `system` 을 "탭이 없는 고아 섹션"으로 잘못 판단해 처음엔 'life' 에 합쳤었다.
 *    원본은 탭이 5개였고 system 은 자기 탭이 있었다 — 되돌린다.
 *
 * 화면은 좌측 유튜브(고정폭 아님, 1fr) + 우측 420px 고정폭 패널 두 칸이고,
 * 패널 안에서 섹션은 카드 그리드가 아니라 **세로로 쌓이는 목록**이다(원본 `.section`
 * 이 점선 구분선만 있는 플랫 블록). 그래서 폭 관련 속성은 없다.
 */
export interface SectionDef {
  /** 접힘 상태 저장 키 겸 DOM id. 종전 id 를 그대로 써서 사용자의 접힘 설정을 잇는다. */
  id: string
  title: string
  modes: DashboardMode[]
  component: Component
}

const lazy = (loader: () => Promise<unknown>) =>
  defineAsyncComponent(loader as () => Promise<{ default: Component }>)

export const SECTIONS: SectionDef[] = [
  // ── 사회 ──────────────────────────────────────────────
  { id: 'social-news', title: '소셜 뉴스', modes: ['social'],
    component: lazy(() => import('./social/SocialNewsSection.vue')) },
  { id: 'traffic', title: '교통 돌발상황', modes: ['social'],
    component: lazy(() => import('./social/TrafficSection.vue')) },
  { id: 'emergency', title: '긴급재난문자', modes: ['social'],
    component: lazy(() => import('./social/EmergencySection.vue')) },

  // ── 금융 ──────────────────────────────────────────────
  { id: 'stocks', title: '주가', modes: ['finance'],
    component: lazy(() => import('./finance/StocksSection.vue')) },
  { id: 'news', title: '금융 뉴스', modes: ['finance'],
    component: lazy(() => import('./finance/NewsSection.vue')) },

  // ── 생활 ──────────────────────────────────────────────
  { id: 'weather', title: '날씨', modes: ['life'],
    component: lazy(() => import('./life/WeatherSection.vue')) },
  { id: 'lifeinfo', title: '생활 정보', modes: ['life'],
    component: lazy(() => import('./life/LifeInfoSection.vue')) },
  { id: 'worldclock', title: '세계 시계', modes: ['life'],
    component: lazy(() => import('./life/WorldClockSection.vue')) },
  { id: 'dday', title: 'D-Day', modes: ['life'],
    component: lazy(() => import('./life/DdaySection.vue')) },
  { id: 'quote', title: '오늘의 문장', modes: ['life'],
    component: lazy(() => import('./life/QuoteSection.vue')) },

  // ── 생산성 ────────────────────────────────────────────
  { id: 'todo', title: '할 일', modes: ['productivity'],
    component: lazy(() => import('./productivity/TodoSection.vue')) },
  { id: 'timer', title: '타이머', modes: ['productivity'],
    component: lazy(() => import('./productivity/TimerSection.vue')) },
  { id: 'focus', title: '집중', modes: ['productivity'],
    component: lazy(() => import('./productivity/FocusSection.vue')) },
  { id: 'memo', title: '메모', modes: ['productivity'],
    component: lazy(() => import('./productivity/MemoSection.vue')) },
  { id: 'bookmarks', title: '북마크', modes: ['productivity'],
    component: lazy(() => import('./productivity/BookmarksSection.vue')) },

  // ── 시스템 (원본에 별도 탭이 있었다 — 처음엔 이걸 놓치고 'life' 에 합쳤었다) ──
  { id: 'system', title: '시스템', modes: ['system'],
    component: lazy(() => import('./life/SystemSection.vue')) }
]

export function sectionsFor(mode: DashboardMode): SectionDef[] {
  return SECTIONS.filter(s => s.modes.includes(mode))
}
