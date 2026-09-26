<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import ModalShell from '@/components/ModalShell.vue'
import { api, tryGet, userId } from '@/api/client'
import { useDashboardStore } from '@/stores/dashboard'
import { useUiStore } from '@/stores/ui'
import type { TickerConfig } from '@/types/dashboard'

/**
 * 원본: static/js/features/settings.js (472줄) + index.html #settings-modal
 *
 * 여기서 서버로 나가는 건 미디어(youtubeUrl)·주식(tickers) 뿐이다 — 원본도 그랬다.
 * 나머지 탭(하이라이트 간격, 뉴스 자동슬라이드, 시스템 임계값)은 서버에 저장하지 않고
 * localStorage 에만 있던 `uiState` 조각이라 여기서도 그대로 localStorage 에 둔다.
 * (섹션 접힘/모드는 stores/ui.ts 가 별도 키로 관리하므로 건드리지 않는다.)
 */

const emit = defineEmits<{ close: [] }>()

const dashboard = useDashboardStore()
const ui = useUiStore()

type TabId = 'media' | 'stocks' | 'news' | 'system' | 'alerts' | 'profile'

const tabs: ReadonlyArray<{ id: TabId; label: string }> = [
  { id: 'media', label: '미디어' },
  { id: 'stocks', label: '주식' },
  { id: 'news', label: '뉴스' },
  { id: 'system', label: '시스템' },
  { id: 'alerts', label: '알림' },
  { id: 'profile', label: '개인정보' }
]
const activeTab = ref<TabId>('media')

// ── 미디어 / 주식 (서버 저장 대상) ───────────────────────────────────────
const youtubeUrl = ref(dashboard.config?.youtubeUrl ?? '')
const tickers = ref<TickerConfig[]>((dashboard.config?.tickers ?? []).map(t => ({ ...t })))

function addTicker() {
  tickers.value.push({ symbol: '', name: '' })
}
function removeTicker(index: number) {
  tickers.value.splice(index, 1)
}

// ── 로컬 전용 설정 (원본 uiState 이식) ───────────────────────────────────
const LOCAL_PREFS_KEY = 'dashboardUiState'

interface LocalPrefs {
  stocks: { highlightInterval: number; autoHighlight: boolean }
  news: { autoSlide: boolean; slideInterval: number }
  socialNews: { autoSlide: boolean; slideInterval: number }
  traffic: { autoSlide: boolean; slideInterval: number }
  system: { cpuWarning: number; cpuDanger: number; memWarning: number; memDanger: number }
}

function defaultPrefs(): LocalPrefs {
  return {
    stocks: { highlightInterval: 10, autoHighlight: true },
    news: { autoSlide: false, slideInterval: 5 },
    socialNews: { autoSlide: false, slideInterval: 5 },
    traffic: { autoSlide: false, slideInterval: 5 },
    system: { cpuWarning: 70, cpuDanger: 90, memWarning: 70, memDanger: 90 }
  }
}

function loadPrefs(): LocalPrefs {
  const d = defaultPrefs()
  try {
    const raw = localStorage.getItem(LOCAL_PREFS_KEY)
    if (!raw) return d
    const parsed = JSON.parse(raw)
    return {
      stocks: { ...d.stocks, ...parsed.stocks },
      news: { ...d.news, ...parsed.news },
      socialNews: { ...d.socialNews, ...parsed.socialNews },
      traffic: { ...d.traffic, ...parsed.traffic },
      system: { ...d.system, ...parsed.system }
    }
  } catch {
    return d
  }
}

const prefs = reactive(loadPrefs())

function savePrefs() {
  try {
    // 이 키의 다른 필드는 아직 아무도 안 쓰지만, 혹시 몰라 기존 값 위에 우리 하위
    // 트리만 덮어쓴다(원본 saveUiState 는 통째로 덮었다 — 여긴 섹션별 모듈이 아직
    // 없어 안전하게 갈 수 있다).
    const raw = localStorage.getItem(LOCAL_PREFS_KEY)
    const existing = raw ? JSON.parse(raw) : {}
    localStorage.setItem(LOCAL_PREFS_KEY, JSON.stringify({ ...existing, ...prefs }))
  } catch {
    // 저장 실패는 무해하다 — 다음에 기본값으로 다시 시도된다.
  }
}

// ── 알림 규칙 ────────────────────────────────────────────────────────────
interface AlertRule {
  id: number
  type: string
  condition: string
  operator: string
  threshold: number
  enabled: boolean
  description?: string
}

const alertRules = ref<AlertRule[]>([])
const showRuleForm = ref(false)
const editingRuleId = ref<number | null>(null)
const ruleForm = reactive({
  type: 'STOCK_PRICE',
  condition: '',
  operator: 'GREATER_THAN',
  threshold: ''
})

async function loadAlertRules() {
  alertRules.value = await tryGet<AlertRule[]>('api/alerts/rules', [])
}

function formatRuleDesc(rule: AlertRule) {
  return rule.description || `${rule.condition} ${rule.operator} ${rule.threshold}`
}

async function toggleAlertRule(rule: AlertRule) {
  try {
    await api.put(`api/alerts/rules/${rule.id}`, { ...rule, enabled: !rule.enabled })
    await loadAlertRules()
  } catch {
    ui.toast('알림 규칙 변경 실패', 'danger')
  }
}

async function deleteAlertRule(id: number) {
  try {
    await api.delete(`api/alerts/rules/${id}`)
    await loadAlertRules()
  } catch {
    ui.toast('알림 규칙 삭제 실패', 'danger')
  }
}

function showAddRuleForm() {
  editingRuleId.value = null
  ruleForm.type = 'STOCK_PRICE'
  ruleForm.condition = ''
  ruleForm.operator = 'GREATER_THAN'
  ruleForm.threshold = ''
  showRuleForm.value = true
}

function cancelRuleForm() {
  showRuleForm.value = false
}

async function saveAlertRule() {
  const threshold = Number(ruleForm.threshold)
  if (!ruleForm.type || !ruleForm.condition || !ruleForm.operator || Number.isNaN(threshold)) {
    ui.toast('모든 필드를 입력하세요', 'warning')
    return
  }

  try {
    const body = {
      type: ruleForm.type,
      condition: ruleForm.condition,
      operator: ruleForm.operator,
      threshold,
      enabled: true
    }
    if (editingRuleId.value) {
      await api.put(`api/alerts/rules/${editingRuleId.value}`, body)
    } else {
      await api.post('api/alerts/rules', body)
    }
    showRuleForm.value = false
    await loadAlertRules()
    ui.toast('알림 규칙이 저장되었습니다', 'info')
  } catch {
    ui.toast('알림 규칙 저장 실패', 'danger')
  }
}

// ── 프로필 ───────────────────────────────────────────────────────────────
const profile = reactive({
  userId,
  location: '-',
  browser: '-',
  resolution: '-',
  language: '-',
  timezone: '-'
})

function detectBrowser(): string {
  const ua = navigator.userAgent
  if (ua.includes('Edge')) return 'Edge'
  if (ua.includes('Chrome')) return 'Chrome'
  if (ua.includes('Firefox')) return 'Firefox'
  if (ua.includes('Safari')) return 'Safari'
  return '알 수 없음'
}

async function loadProfileInfo() {
  profile.browser = detectBrowser()
  profile.resolution = `${window.screen.width} x ${window.screen.height}`
  profile.language = navigator.language || '알 수 없음'
  profile.timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || '알 수 없음'
  const data = await tryGet<{ location?: string } | null>('api/location/weather', null)
  profile.location = data?.location || '알 수 없음'
}

async function copyUserId() {
  try {
    await navigator.clipboard.writeText(profile.userId)
    ui.toast('복사되었습니다', 'info')
  } catch {
    ui.toast('복사 실패', 'danger')
  }
}

onMounted(() => {
  loadAlertRules()
  loadProfileInfo()
})

// ── 저장 ─────────────────────────────────────────────────────────────────
const saving = ref(false)

async function handleSave() {
  saving.value = true
  try {
    savePrefs()

    // 🔴 2026-09-26 유튜브 플레이어 버그 수정 유지: 종전엔 "방금 보낸 값"과
    // "서버가 돌려준 값"을 비교해서 조건이 절대 참이 안 됐다. saveConfig 가
    // dashboard.config 를 곧장 saved 로 갈아끼우므로, 비교 대상인 "이전 값"은
    // 그 호출 전에 미리 잡아 둬야 한다.
    const prevYoutubeUrl = dashboard.config?.youtubeUrl ?? ''

    // 🔴 티커 추가 후 새로고침해야 반영되던 버그 수정 유지: saveConfig 안에서
    // refreshNow() 로 즉시 화면을 당겨오고 SSE 를 새 설정으로 재연결한다.
    // 여기서 별도로 fetch 하거나 SSE 재연결을 기다리지 않는다.
    const saved = await dashboard.saveConfig({
      youtubeUrl: youtubeUrl.value,
      tickers: tickers.value
    })

    if (saved.youtubeUrl !== prevYoutubeUrl) {
      ui.toast('설정이 저장되었습니다 (유튜브 영상이 변경되었습니다)', 'info')
    } else {
      ui.toast('설정이 저장되었습니다', 'info')
    }

    emit('close')
  } catch {
    ui.toast('설정 저장 실패', 'danger')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ModalShell title="Dashboard Settings" @close="emit('close')">
    <nav class="tabs">
      <button
        v-for="t in tabs"
        :key="t.id"
        class="tab"
        :class="{ active: activeTab === t.id }"
        @click="activeTab = t.id"
      >
        {{ t.label }}
      </button>
    </nav>

    <!-- 미디어 -->
    <div v-show="activeTab === 'media'" class="tab-panel">
      <div class="form-group">
        <label class="form-label">YouTube URL</label>
        <input
          v-model="youtubeUrl"
          type="text"
          class="form-input"
          placeholder="https://www.youtube.com/watch?v=..."
        >
      </div>
    </div>

    <!-- 주식 -->
    <div v-show="activeTab === 'stocks'" class="tab-panel">
      <div class="form-group">
        <label class="form-label">Stock Tickers</label>
        <div class="ticker-list">
          <div v-for="(ticker, index) in tickers" :key="index" class="ticker-item">
            <input v-model="ticker.symbol" type="text" class="form-input" placeholder="심볼">
            <input v-model="ticker.name" type="text" class="form-input" placeholder="이름">
            <button class="ticker-remove" @click="removeTicker(index)">✕</button>
          </div>
        </div>
        <button class="add-ticker-btn" @click="addTicker">+ Add Ticker</button>
      </div>
      <div class="form-group">
        <label class="form-label">뉴스 하이라이트</label>
        <div class="form-row">
          <label class="form-checkbox">
            <input v-model="prefs.stocks.autoHighlight" type="checkbox">
            <span>자동 순환</span>
          </label>
          <div>
            <label class="form-label" style="margin-bottom: 4px;">순환 간격 (초)</label>
            <input v-model.number="prefs.stocks.highlightInterval" type="number" class="form-input" min="5" max="60">
          </div>
        </div>
      </div>
    </div>

    <!-- 뉴스 -->
    <div v-show="activeTab === 'news'" class="tab-panel">
      <div class="form-group">
        <label class="form-label">일반 뉴스</label>
        <label class="form-checkbox">
          <input v-model="prefs.news.autoSlide" type="checkbox">
          <span>자동 슬라이드 기본값</span>
        </label>
        <div class="form-group" style="margin-top: 8px;">
          <label class="form-label">슬라이드 간격 (초)</label>
          <input v-model.number="prefs.news.slideInterval" type="number" class="form-input" min="3" max="30">
        </div>
      </div>
      <div class="form-group">
        <label class="form-label">사회 뉴스</label>
        <label class="form-checkbox">
          <input v-model="prefs.socialNews.autoSlide" type="checkbox">
          <span>자동 슬라이드 기본값</span>
        </label>
        <div class="form-group" style="margin-top: 8px;">
          <label class="form-label">슬라이드 간격 (초)</label>
          <input v-model.number="prefs.socialNews.slideInterval" type="number" class="form-input" min="3" max="30">
        </div>
      </div>
      <div class="form-group">
        <label class="form-label">교통돌발상황</label>
        <label class="form-checkbox">
          <input v-model="prefs.traffic.autoSlide" type="checkbox">
          <span>자동 슬라이드 기본값</span>
        </label>
        <div class="form-group" style="margin-top: 8px;">
          <label class="form-label">슬라이드 간격 (초)</label>
          <input v-model.number="prefs.traffic.slideInterval" type="number" class="form-input" min="3" max="30">
        </div>
      </div>
    </div>

    <!-- 시스템 -->
    <div v-show="activeTab === 'system'" class="tab-panel">
      <div class="form-row">
        <div class="form-group">
          <label class="form-label">CPU 경고 (%)</label>
          <input v-model.number="prefs.system.cpuWarning" type="number" class="form-input" min="0" max="100">
        </div>
        <div class="form-group">
          <label class="form-label">CPU 위험 (%)</label>
          <input v-model.number="prefs.system.cpuDanger" type="number" class="form-input" min="0" max="100">
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label class="form-label">메모리 경고 (%)</label>
          <input v-model.number="prefs.system.memWarning" type="number" class="form-input" min="0" max="100">
        </div>
        <div class="form-group">
          <label class="form-label">메모리 위험 (%)</label>
          <input v-model.number="prefs.system.memDanger" type="number" class="form-input" min="0" max="100">
        </div>
      </div>
    </div>

    <!-- 알림 -->
    <div v-show="activeTab === 'alerts'" class="tab-panel">
      <div class="form-group">
        <label class="form-label">알림 규칙</label>
        <div class="alert-rules-list">
          <div v-if="alertRules.length === 0" class="no-data">알림 규칙이 없습니다</div>
          <div v-for="rule in alertRules" :key="rule.id" class="alert-rule-item">
            <div class="alert-rule-status" :class="{ disabled: !rule.enabled }" />
            <div class="alert-rule-info">
              <span class="alert-rule-type">{{ rule.type }}</span>
              <span class="alert-rule-desc">{{ formatRuleDesc(rule) }}</span>
            </div>
            <button
              class="alert-rule-toggle"
              :class="{ enabled: rule.enabled }"
              @click="toggleAlertRule(rule)"
            >{{ rule.enabled ? '✓' : '○' }}</button>
            <button class="alert-rule-delete" @click="deleteAlertRule(rule.id)">✕</button>
          </div>
        </div>
        <button class="add-rule-btn" @click="showAddRuleForm">+ 새 규칙 추가</button>

        <div v-if="showRuleForm" class="alert-rule-form">
          <div class="form-group">
            <label class="form-label">유형</label>
            <select v-model="ruleForm.type" class="form-input">
              <option value="STOCK_PRICE">주식 - 가격</option>
              <option value="STOCK_CHANGE">주식 - 변화율</option>
              <option value="CPU">시스템 - CPU</option>
              <option value="MEMORY">시스템 - 메모리</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">조건</label>
            <input v-model="ruleForm.condition" type="text" class="form-input" placeholder="NVDA, CPU 등">
          </div>
          <div class="form-group">
            <label class="form-label">연산자</label>
            <select v-model="ruleForm.operator" class="form-input">
              <option value="GREATER_THAN">이상 (&gt;=)</option>
              <option value="LESS_THAN">이하 (&lt;=)</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">값</label>
            <input v-model="ruleForm.threshold" type="number" class="form-input" placeholder="150">
          </div>
          <div class="alert-rule-form-actions">
            <button class="btn btn-secondary" @click="cancelRuleForm">취소</button>
            <button class="btn btn-primary" @click="saveAlertRule">저장</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 개인정보 -->
    <div v-show="activeTab === 'profile'" class="tab-panel">
      <div class="form-group">
        <label class="form-label">사용자 식별 정보</label>
        <div class="profile-info">
          <div class="profile-item">
            <span class="profile-label">사용자 ID (UUID):</span>
            <span class="profile-value">{{ profile.userId }}</span>
            <button class="btn btn-secondary btn-small" @click="copyUserId">복사</button>
          </div>
          <div class="profile-item">
            <span class="profile-label">위치 정보:</span>
            <span class="profile-value">{{ profile.location }}</span>
          </div>
          <div class="profile-item">
            <span class="profile-label">브라우저:</span>
            <span class="profile-value">{{ profile.browser }}</span>
          </div>
          <div class="profile-item">
            <span class="profile-label">화면 해상도:</span>
            <span class="profile-value">{{ profile.resolution }}</span>
          </div>
          <div class="profile-item">
            <span class="profile-label">언어:</span>
            <span class="profile-value">{{ profile.language }}</span>
          </div>
          <div class="profile-item">
            <span class="profile-label">시간대:</span>
            <span class="profile-value">{{ profile.timezone }}</span>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <button class="btn btn-secondary" @click="emit('close')">취소</button>
      <button class="btn btn-primary" :disabled="saving" @click="handleSave">저장</button>
    </template>
  </ModalShell>
</template>

<style scoped>
.tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--border-color);
  margin: -16px -16px 16px;
  padding: 0 12px;
  overflow-x: auto;
}

.tab {
  padding: 10px 14px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  color: var(--text-secondary);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.tab:hover { color: var(--text-primary); }

.tab.active {
  color: var(--accent-cyan);
  border-bottom-color: var(--accent-cyan);
}

.form-group { margin-bottom: 20px; }

.form-label {
  display: block;
  color: var(--text-secondary);
  font-size: 11px;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.form-input {
  width: 100%;
  padding: 10px 12px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-primary);
  font-family: inherit;
  font-size: 12px;
}

.form-input:focus {
  outline: none;
  border-color: var(--accent-cyan);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.form-checkbox input {
  width: 16px;
  height: 16px;
  accent-color: var(--accent-cyan);
}

.form-checkbox span {
  color: var(--text-secondary);
  font-size: 12px;
}

.ticker-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
}

.ticker-item {
  display: grid;
  grid-template-columns: 100px 1fr 32px;
  gap: 8px;
  align-items: center;
}

.ticker-remove {
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--accent-red);
  width: 28px;
  height: 28px;
  border-radius: 4px;
  cursor: pointer;
}

.ticker-remove:hover {
  background: var(--accent-red);
  color: #fff;
  border-color: var(--accent-red);
}

.add-ticker-btn {
  background: transparent;
  border: 1px dashed var(--border-color);
  color: var(--text-secondary);
  padding: 10px;
  border-radius: 4px;
  cursor: pointer;
  font-family: inherit;
  font-size: 12px;
  margin-top: 8px;
  width: 100%;
}

.add-ticker-btn:hover {
  border-color: var(--accent-green);
  color: var(--accent-green);
}

.no-data {
  color: var(--text-muted);
  font-size: 11px;
  font-style: italic;
  padding: 8px 0;
}

.alert-rules-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 260px;
  overflow-y: auto;
}

.alert-rule-item {
  display: grid;
  grid-template-columns: 24px 1fr auto auto;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  border: 1px solid var(--border-color);
}

.alert-rule-status {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--accent-green);
}

.alert-rule-status.disabled { background: var(--text-muted); }

.alert-rule-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.alert-rule-type {
  font-size: 10px;
  color: var(--text-muted);
  text-transform: uppercase;
}

.alert-rule-desc {
  font-size: 11px;
  color: var(--text-primary);
}

.alert-rule-toggle,
.alert-rule-delete {
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-muted);
  width: 28px;
  height: 28px;
  border-radius: 4px;
  cursor: pointer;
}

.alert-rule-toggle:hover,
.alert-rule-delete:hover { border-color: var(--accent-cyan); color: var(--accent-cyan); }

.alert-rule-toggle.enabled { color: var(--accent-green); border-color: var(--accent-green); }

.alert-rule-delete:hover {
  background: var(--accent-red);
  color: #fff;
  border-color: var(--accent-red);
}

.add-rule-btn {
  background: transparent;
  border: 1px dashed var(--border-color);
  color: var(--text-secondary);
  padding: 12px;
  border-radius: 4px;
  cursor: pointer;
  font-family: inherit;
  font-size: 12px;
  margin-top: 8px;
  width: 100%;
}

.add-rule-btn:hover {
  border-color: var(--accent-green);
  color: var(--accent-green);
}

.alert-rule-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 16px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  margin-top: 12px;
  border: 1px solid var(--border-color);
}

.alert-rule-form .form-group { margin-bottom: 0; }

.alert-rule-form-actions {
  grid-column: span 2;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.profile-info {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.profile-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.profile-label { color: var(--text-secondary); }
.profile-value { color: var(--text-primary); word-break: break-all; }

.btn {
  padding: 10px 20px;
  border-radius: 4px;
  cursor: pointer;
  font-family: inherit;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
}

.btn:disabled { opacity: 0.6; cursor: not-allowed; }

.btn-small { padding: 2px 8px; font-size: 10px; margin-left: auto; }

.btn-secondary:hover { border-color: var(--text-primary); color: var(--text-primary); }

.btn-primary {
  background: var(--accent-cyan);
  border-color: var(--accent-cyan);
  color: var(--bg-primary);
}

.btn-primary:hover { background: transparent; color: var(--accent-cyan); }
</style>
