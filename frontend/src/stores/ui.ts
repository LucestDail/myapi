import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { DashboardMode } from '@/types/dashboard'

export type ToastKind = 'info' | 'success' | 'warning' | 'danger'

export interface Toast {
  id: number
  message: string
  kind: ToastKind
}

const PREFS_KEY = 'myapiUiPrefs'

interface Prefs {
  mode: DashboardMode
  collapsed: Record<string, boolean>
}

function loadPrefs(): Prefs {
  try {
    const raw = localStorage.getItem(PREFS_KEY)
    if (raw) return { mode: 'social', collapsed: {}, ...JSON.parse(raw) }
  } catch {
    // 막혀 있으면 기본값으로 간다 — 저장 안 되는 것뿐이지 동작은 해야 한다.
  }
  return { mode: 'social', collapsed: {} }
}

export const useUiStore = defineStore('ui', () => {
  const initial = loadPrefs()
  const mode = ref<DashboardMode>(initial.mode)
  const collapsed = ref<Record<string, boolean>>(initial.collapsed)
  const toasts = ref<Toast[]>([])

  /** 지금 열려 있는 모달 id. 한 번에 하나만 띄운다. */
  const openModal = ref<string | null>(null)

  let toastSeq = 0

  function persist() {
    try {
      localStorage.setItem(PREFS_KEY, JSON.stringify({ mode: mode.value, collapsed: collapsed.value }))
    } catch {
      // 저장 실패는 무해하다
    }
  }

  function setMode(next: DashboardMode) {
    mode.value = next
    persist()
  }

  function toggleSection(id: string) {
    collapsed.value = { ...collapsed.value, [id]: !collapsed.value[id] }
    persist()
  }

  function toast(message: string, kind: ToastKind = 'info') {
    const id = ++toastSeq
    toasts.value = [...toasts.value, { id, message, kind }]
    setTimeout(() => {
      toasts.value = toasts.value.filter(t => t.id !== id)
    }, 3200)
  }

  return { mode, collapsed, toasts, openModal, setMode, toggleSection, toast }
})
