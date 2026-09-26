import { defineAsyncComponent, type Component } from 'vue'
import type { DashboardMode } from '@/types/dashboard'

/**
 * 섹션 등록부 — 어떤 섹션이 어느 모드에 보이는지 한 곳에서 정한다.
 *
 * 종전에는 이 정보가 index.html 의 `data-mode` 속성에 흩어져 있었고, 보이기/숨기기를
 * `document.querySelectorAll` 로 직접 클래스를 토글해 처리했다. 섹션을 추가하려면
 * HTML·CSS·JS 세 곳을 건드려야 했다.
 *
 * ⚠️ `system` 섹션은 종전에 `data-mode="system"` 이었는데 그 모드로 가는 탭이 없었다.
 *    즉 어느 탭에서도 안 보이는 고아 상태였다. 'life' 로 편입한다(시스템 상태는
 *    생활 정보와 같이 보는 게 자연스럽다).
 */
export interface SectionDef {
  /** 접힘 상태 저장 키 겸 DOM id. 종전 id 를 그대로 써서 사용자의 접힘 설정을 잇는다. */
  id: string
  title: string
  modes: DashboardMode[]
  component: Component
  /** 넓게 차지해야 읽히는 섹션(뉴스·교통 등)은 그리드에서 2칸을 쓴다. */
  wide?: boolean
}

const lazy = (loader: () => Promise<unknown>) =>
  defineAsyncComponent(loader as () => Promise<{ default: Component }>)

export const SECTIONS: SectionDef[] = [
  // ── 소셜 ──────────────────────────────────────────────
  { id: 'social-news', title: '소셜 뉴스', modes: ['social'], wide: true,
    component: lazy(() => import('./social/SocialNewsSection.vue')) },
  { id: 'traffic', title: '교통 돌발상황', modes: ['social'], wide: true,
    component: lazy(() => import('./social/TrafficSection.vue')) },
  { id: 'emergency', title: '긴급재난문자', modes: ['social'], wide: true,
    component: lazy(() => import('./social/EmergencySection.vue')) },

  // ── 금융 ──────────────────────────────────────────────
  { id: 'stocks', title: '주가', modes: ['finance'], wide: true,
    component: lazy(() => import('./finance/StocksSection.vue')) },
  { id: 'news', title: '금융 뉴스', modes: ['finance'], wide: true,
    component: lazy(() => import('./finance/NewsSection.vue')) },

  // ── 생활 ──────────────────────────────────────────────
  { id: 'weather', title: '날씨', modes: ['life'], wide: true,
    component: lazy(() => import('./life/WeatherSection.vue')) },
  { id: 'lifeinfo', title: '생활 정보', modes: ['life'], wide: true,
    component: lazy(() => import('./life/LifeInfoSection.vue')) },
  { id: 'worldclock', title: '세계 시계', modes: ['life'],
    component: lazy(() => import('./life/WorldClockSection.vue')) },
  { id: 'dday', title: 'D-Day', modes: ['life'],
    component: lazy(() => import('./life/DdaySection.vue')) },
  { id: 'quote', title: '오늘의 문장', modes: ['life'],
    component: lazy(() => import('./life/QuoteSection.vue')) },
  { id: 'system', title: '시스템', modes: ['life'], wide: true,
    component: lazy(() => import('./life/SystemSection.vue')) },

  // ── 생산성 ────────────────────────────────────────────
  { id: 'todo', title: '할 일', modes: ['productivity'], wide: true,
    component: lazy(() => import('./productivity/TodoSection.vue')) },
  { id: 'timer', title: '타이머', modes: ['productivity'],
    component: lazy(() => import('./productivity/TimerSection.vue')) },
  { id: 'focus', title: '집중', modes: ['productivity'],
    component: lazy(() => import('./productivity/FocusSection.vue')) },
  { id: 'memo', title: '메모', modes: ['productivity'],
    component: lazy(() => import('./productivity/MemoSection.vue')) },
  { id: 'bookmarks', title: '북마크', modes: ['productivity'],
    component: lazy(() => import('./productivity/BookmarksSection.vue')) }
]

export function sectionsFor(mode: DashboardMode): SectionDef[] {
  return SECTIONS.filter(s => s.modes.includes(mode))
}
