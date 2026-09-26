<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import ModalShell from '@/components/ModalShell.vue'
import { userId } from '@/api/client'
import { useDashboardStore } from '@/stores/dashboard'
import { useUiStore } from '@/stores/ui'

/**
 * 원본: static/js/features/ai-report.js (324줄, 2026-09-26 개편) + index.html #ai-report-modal
 *
 * 🔴 이 컴포넌트는 `POST api/ai-report/generate/stream` 을 SSE 로 소비한다. EventSource 는
 * GET 전용이라 못 쓰고(topics·snapshot 을 쿼리로 보내기엔 크다), `fetch` + ReadableStream 으로
 * 프레임을 직접 파싱한다 — api/client.ts 로 감쌀 수 없는 유일한 예외라 여기서 fetch 를 직접 쓴다.
 *
 * 생성 파라미터(Temperature·Top P 등) 입력은 2026-09-26 에 화면에서 빠졌다. 서버 고정값
 * (AIReportController.FIXED_SETTINGS) 을 쓰므로 여기서 다시 만들지 않는다.
 */

const emit = defineEmits<{ close: [] }>()

const dashboard = useDashboardStore()
const ui = useUiStore()

interface Topics {
  news: boolean
  weather: boolean
  traffic: boolean
  emergency: boolean
  stocks: boolean
  yahooFinance: boolean
  yonhapNews: boolean
  lifeInfo: boolean
  system: boolean
}

const CHECKBOX_KEY = 'aiReportCheckboxState'
const REPORT_KEY = 'aiReportLastReport'

const checkboxItems: ReadonlyArray<{ key: keyof Topics; label: string }> = [
  { key: 'news', label: '뉴스 (DB)' },
  { key: 'weather', label: '날씨' },
  { key: 'traffic', label: '교통돌발상황' },
  { key: 'emergency', label: '긴급재난문자' },
  { key: 'stocks', label: '주식 정보' },
  { key: 'yahooFinance', label: '야후 파이낸스' },
  { key: 'yonhapNews', label: '연합뉴스' },
  { key: 'lifeInfo', label: '생활 정보' },
  { key: 'system', label: '시스템 정보' }
]

function defaultTopics(): Topics {
  return {
    news: true, weather: true, traffic: true, emergency: true, stocks: true,
    yahooFinance: true, yonhapNews: true, lifeInfo: true, system: true
  }
}

function loadTopics(): Topics {
  try {
    const raw = localStorage.getItem(CHECKBOX_KEY)
    if (raw) return { ...defaultTopics(), ...JSON.parse(raw) }
  } catch {
    // 손상됐으면 기본값
  }
  return defaultTopics()
}

function saveTopics() {
  try {
    localStorage.setItem(CHECKBOX_KEY, JSON.stringify(topics))
  } catch {
    // 저장 실패는 무해하다
  }
}

function loadLastReport(): string | null {
  try {
    return localStorage.getItem(REPORT_KEY)
  } catch {
    return null
  }
}

function saveLastReport(html: string) {
  try {
    localStorage.setItem(REPORT_KEY, html)
  } catch {
    // 저장 실패는 무해하다
  }
}

const topics = reactive(loadTopics())

const running = ref(false)
const showLoading = ref(false)
const showResult = ref(false)
const reportHtml = ref('')
const stageLabel = ref('준비 중')
const stageLog = ref<string[]>([])
const progressPct = ref(0)
const elapsedLabel = ref('')

let elapsedTimer: number | undefined
let hideLoadingTimer: number | undefined
let abortController: AbortController | undefined

onMounted(() => {
  const last = loadLastReport()
  if (last) {
    reportHtml.value = last
    showResult.value = true
  }
})

onBeforeUnmount(() => {
  stopElapsed()
  if (hideLoadingTimer !== undefined) clearTimeout(hideLoadingTimer)
  abortController?.abort()
})

function stopElapsed() {
  if (elapsedTimer !== undefined) {
    clearInterval(elapsedTimer)
    elapsedTimer = undefined
  }
}

function startElapsed() {
  const t0 = Date.now()
  stopElapsed()
  elapsedTimer = window.setInterval(() => {
    elapsedLabel.value = ((Date.now() - t0) / 1000).toFixed(0) + '초'
  }, 250)
}

function setStage(label: string) {
  stageLabel.value = label
  stageLog.value = [...stageLog.value, label]
}

/** 현재 대시보드 스토어 상태로 세션 스냅샷을 만든다. */
function buildSnapshot(): Record<string, unknown> {
  const snapshot: Record<string, unknown> = { fetchedAt: new Date().toISOString() }
  if (dashboard.stocks.quotes.length) snapshot.stocks = dashboard.stocks
  if (dashboard.weather.length) snapshot.weather = dashboard.weather
  if (dashboard.news.yahooNews.length) snapshot.yahooNews = dashboard.news.yahooNews
  if (dashboard.news.yonhapNews.length) snapshot.yonhapNews = dashboard.news.yonhapNews
  return snapshot
}

/** 마크다운 → HTML 간이 변환. 원본과 동일한 규칙만 지원한다. */
function convertMarkdownToHtml(markdown: string): string {
  let html = markdown
  html = html.replace(/^### (.*$)/gim, '<h3>$1</h3>')
  html = html.replace(/^## (.*$)/gim, '<h2>$1</h2>')
  html = html.replace(/^# (.*$)/gim, '<h1>$1</h1>')
  html = html.replace(/\*\*(.*?)\*\*/gim, '<strong>$1</strong>')
  html = html.replace(/^\* (.*$)/gim, '<li>$1</li>')
  html = html.replace(/^- (.*$)/gim, '<li>$1</li>')
  html = html.replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>')
  html = html.replace(/\n/g, '<br>')
  return html
}

async function generate() {
  if (running.value) {
    ui.toast('이미 리포트를 만들고 있어요', 'warning')
    return
  }

  saveTopics()

  const selected = checkboxItems.filter(item => topics[item.key]).length
  if (selected === 0) {
    ui.toast('최소 하나 이상의 데이터를 선택해주세요', 'warning')
    return
  }

  running.value = true
  stageLog.value = []
  showLoading.value = true
  showResult.value = true
  reportHtml.value = ''
  progressPct.value = 0
  setStage('요청 보내는 중')
  startElapsed()

  // 서버 단계 수 = 수집원(선택 개수) + 요청접수 + AI 모델 호출 + 이력 저장
  const totalStages = selected + 3
  let stagesDone = 0
  let buffer = ''
  let finalReport: string | null = null

  abortController = new AbortController()

  try {
    const response = await fetch('api/ai-report/generate/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        'X-User-Id': userId
      },
      body: JSON.stringify({ topics, snapshot: buildSnapshot() }),
      signal: abortController.signal
    })

    if (!response.ok || !response.body) {
      throw new Error('HTTP ' + response.status)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let raw = ''

    // SSE 프레임 파싱. POST 라 EventSource 를 못 쓴다.
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      raw += decoder.decode(value, { stream: true })

      let sep = raw.indexOf('\n\n')
      while (sep >= 0) {
        const frame = raw.slice(0, sep)
        raw = raw.slice(sep + 2)

        let eventName = 'message'
        const dataLines: string[] = []
        frame.split('\n').forEach(line => {
          if (line.startsWith('event:')) eventName = line.slice(6).trim()
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
        })

        if (dataLines.length > 0) {
          let payload: { label?: string; t?: string; report?: string; detail?: string; message?: string } | undefined
          try {
            payload = JSON.parse(dataLines.join('\n'))
          } catch {
            payload = undefined
          }

          if (payload) {
            if (eventName === 'stage') {
              stagesDone++
              setStage(payload.label ?? '')
              progressPct.value = totalStages > 0 ? Math.min(100, Math.round((stagesDone / totalStages) * 100)) : 0
            } else if (eventName === 'delta') {
              buffer += payload.t ?? ''
              reportHtml.value = convertMarkdownToHtml(buffer)
            } else if (eventName === 'done') {
              finalReport = payload.report ?? null
            } else if (eventName === 'error') {
              throw new Error(payload.detail || payload.message || '알 수 없는 오류')
            }
          }
        }

        sep = raw.indexOf('\n\n')
      }
    }

    const reportText = finalReport || buffer
    if (!reportText) throw new Error('빈 응답')

    reportHtml.value = convertMarkdownToHtml(reportText)
    saveLastReport(reportHtml.value)

    progressPct.value = 100
    setStage('완료')
    ui.toast('리포트 생성이 완료되었어요', 'success')
  } catch (error) {
    const message = error instanceof Error ? error.message : '알 수 없는 오류'
    setStage('실패: ' + message)
    // 🔴 여기까지 받은 본문은 지우지 않는다 — 중간에 끊겨도 읽을 수 있는 게 낫다.
    if (!buffer) {
      showResult.value = false
    }
    ui.toast('리포트 생성 중 오류가 발생했어요', 'danger')
  } finally {
    running.value = false
    stopElapsed()
    hideLoadingTimer = window.setTimeout(() => {
      showLoading.value = false
    }, 1500)
  }
}
</script>

<template>
  <ModalShell title="AI 리포트 생성" width="1600px" @close="emit('close')">
    <div class="layout">
      <div class="left">
        <div class="form-group">
          <label class="form-label">리포트에 포함할 데이터 선택</label>
          <div class="checkbox-list">
            <label v-for="item in checkboxItems" :key="item.key" class="form-checkbox">
              <input v-model="topics[item.key]" type="checkbox">
              <span>{{ item.label }}</span>
            </label>
          </div>
        </div>
      </div>

      <div class="right">
        <div v-if="showLoading" class="loading-bar">
          <div class="loading-row">
            <span class="spinner" />
            <span class="progress-text">{{ stageLabel }}</span>
            <span class="elapsed">{{ elapsedLabel }}</span>
          </div>
          <div class="progress-track">
            <div class="progress-fill" :style="{ width: progressPct + '%' }" />
          </div>
          <div class="stage-log">
            <div v-for="(line, i) in stageLog" :key="i">· {{ line }}</div>
          </div>
        </div>

        <div v-if="showResult" class="result">
          <label class="form-label">생성된 리포트</label>
          <div class="content" v-html="reportHtml" />
        </div>
        <div v-else class="empty">좌측에서 리포트를 생성해주세요.</div>
      </div>
    </div>

    <template #footer>
      <button class="btn btn-secondary" @click="emit('close')">닫기</button>
      <button class="btn btn-primary" :disabled="running" @click="generate">
        {{ running ? '생성 중...' : '리포트 생성하기' }}
      </button>
    </template>
  </ModalShell>
</template>

<style scoped>
/*
 * 🔴 2026-09-26 스크롤 재정정: "리포트 생성하기" 버튼이 좌측 패널 안에 있었고
 * .left 에 overflow-y:auto 가 걸려 있어서, 본문이 스트리밍으로 길어지며
 * 스크롤이 내려가면 버튼도 함께 밀려 눌리지 않는 상태가 됐다.
 * → 버튼은 ModalShell 의 footer(항상 고정)로 옮겼다.
 * → .left 는 스크롤하지 않는다. 체크박스 9개는 항상 다 보이는 짧은 목록이라
 *   스크롤이 애초에 필요 없다 — 스크롤은 우측 .result 에서만 일어난다.
 */
.layout {
  display: flex;
  gap: 20px;
  /* 부모(.modal-body)는 flex 컨테이너가 아니라 평범한 블록이라 flex:1 은 무의미하다.
     height:100% 로 부모의 실제(used) 높이를 그대로 받아야 우측 .result 가 그
     안에서 내부 스크롤한다 — 안 그러면 .layout 이 content 만큼 자라서 이 블록
     전체를 modal-body 가 통째로 스크롤하게 되고, 그때 좌측도 같이 끌려 내려간다. */
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.left {
  flex: 0 0 320px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-self: flex-start;
}

.right {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--border-color);
  padding-left: 20px;
  min-height: 0;
  min-width: 0;
}

.form-group { margin: 0; }

.form-label {
  display: block;
  color: var(--text-secondary);
  font-size: 11px;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.checkbox-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
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

.btn-secondary:hover { border-color: var(--text-primary); color: var(--text-primary); }

.btn-primary {
  background: var(--accent-cyan);
  border-color: var(--accent-cyan);
  color: var(--bg-primary);
}

.btn-primary:hover:not(:disabled) { background: transparent; color: var(--accent-cyan); }

.loading-bar {
  flex: 0 0 auto;
  padding: 10px 12px;
  margin-bottom: 12px;
  background: var(--bg-tertiary);
  border-radius: 6px;
}

.loading-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--border-color);
  border-top-color: var(--accent-cyan);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  flex: 0 0 auto;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.progress-text { color: var(--text-muted); font-size: 13px; }

.elapsed {
  margin-left: auto;
  color: var(--text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.progress-track {
  width: 100%;
  height: 3px;
  background: var(--border-color);
  border-radius: 2px;
  margin-top: 8px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--accent-cyan);
  transition: width 0.25s ease;
}

.stage-log {
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.6;
  max-height: 90px;
  overflow-y: auto;
}

/* 🔴 2026-09-26 스크롤 버그 수정 유지: 이 블록에 overflow-y:auto 와 min-height:0 을
   같이 줘야 스크롤이 된다. 부모가 flex 컨테이너라 min-height 기본값(auto) 때문에
   내용만큼 늘어나면 스크롤이 안 생긴다. */
.result {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.content {
  background: var(--bg-tertiary);
  padding: 16px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  min-height: 200px;
}

.empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px;
  color: var(--text-muted);
}

@media (max-width: 768px) {
  .layout { flex-direction: column; }
  .left { flex: 0 0 auto; width: 100%; }
  .right {
    flex: 1 1 auto;
    border-left: none;
    border-top: 1px solid var(--border-color);
    padding-left: 0;
    padding-top: 16px;
    min-height: 300px;
  }
}
</style>
