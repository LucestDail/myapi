<script setup lang="ts">
/**
 * 서버 시스템 상태 — CPU/메모리/힙, 디스크, 업타임, 네트워크, 사용량 히스토리.
 *
 * CPU·메모리·힙·스레드·GC·업타임은 `useDashboardStore().system` 으로 SSE 로 들어온다
 * (지시사항: 폴링 금지, 스토어만 본다). `SystemData` 타입은 인덱스 시그니처라
 * 필드마다 `numOr()` 로 안전하게 숫자를 뽑아 쓴다.
 *
 * 디스크·네트워크 지연시간·히스토리 차트는 SSE 에 없는 정보라 `tryGet` 으로
 * 따로 가져온다 — 원본은 SSE 틱마다(수 초 간격) `api/system/status` 를 다시
 * 불렀는데, 디스크 사용량은 그렇게 자주 바뀌지 않는다. 여기서는 30초 간격으로
 * 줄였다(개선: 불필요한 네트워크 호출 감소).
 *
 * 경고/위험 임계값(기본 70/90)은 원본 설정 모달에서 바꿀 수 있었지만, 그 모달은
 * 이 작업 범위 밖이라 여기서는 상수로 고정한다.
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'
import { tryGet } from '@/api/client'
import { useDashboardStore } from '@/stores/dashboard'

const props = defineProps<{ sectionId: string; title: string }>()
const dashboard = useDashboardStore()

const THRESHOLDS = { cpuWarning: 70, cpuDanger: 90, memWarning: 70, memDanger: 90 } as const

function numOr(value: unknown, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function field(key: string): unknown {
  return dashboard.system ? (dashboard.system as Record<string, unknown>)[key] : undefined
}

function levelClass(value: number, warning: number, danger: number): string {
  if (value > danger) return 'danger'
  if (value > warning) return 'warning'
  return ''
}

const cpuUsage = computed(() => numOr(field('cpuUsage'), -1))
const memPercent = computed(() => numOr(field('memoryUsagePercent')))
const heapPercent = computed(() => numOr(field('heapUsagePercent')))
const memUsed = computed(() => numOr(field('memoryUsed')))
const memTotal = computed(() => numOr(field('memoryTotal')))
const heapUsed = computed(() => numOr(field('heapUsed')))
const heapMax = computed(() => numOr(field('heapMax')))
const threadCount = computed(() => numOr(field('threadCount')))
const gcCount = computed(() => numOr(field('gcCount')))
const gcTime = computed(() => numOr(field('gcTime')))
const uptimeMillis = computed(() => numOr(field('uptimeMillis')))

const cpuClass = computed(() => levelClass(cpuUsage.value, THRESHOLDS.cpuWarning, THRESHOLDS.cpuDanger))
const memClass = computed(() => levelClass(memPercent.value, THRESHOLDS.memWarning, THRESHOLDS.memDanger))
const heapClass = computed(() => levelClass(heapPercent.value, THRESHOLDS.memWarning, THRESHOLDS.memDanger))

function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(k)), sizes.length - 1)
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
}

function formatUptime(millis: number): string {
  const seconds = Math.floor(millis / 1000)
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (days > 0) return `${days}d ${hours}h`
  if (hours > 0) return `${hours}h ${minutes}m`
  return `${minutes}m`
}

// ── 디스크 / 네트워크 (별도 폴링) ──────────────────────────────
interface DiskInfo { path: string; totalSpace: number; freeSpace: number; usedSpace: number; usagePercent: number }
interface SystemStatus { disks: Record<string, DiskInfo> }

const diskPercent = ref<number | null>(null)
const diskUsed = ref(0)
const diskTotal = ref(0)
const diskFree = ref(0)
const online = ref(navigator.onLine)
const latencyMs = ref<number | null>(null)

async function loadStatus() {
  const start = performance.now()
  const status = await tryGet<SystemStatus | null>('api/system/status', null)
  latencyMs.value = online.value ? Math.round(performance.now() - start) : null

  if (!status?.disks) return
  const entries = Object.entries(status.disks)
  if (entries.length === 0) return
  const root = entries.find(([path]) => path === '/' || path === 'C:\\') ?? entries[0]
  const disk = root[1]
  diskPercent.value = Math.round(disk.usagePercent)
  diskUsed.value = disk.usedSpace
  diskTotal.value = disk.totalSpace
  diskFree.value = disk.freeSpace
}

const diskBarColor = computed(() => {
  const p = diskPercent.value ?? 0
  if (p > 90) return 'var(--accent-red)'
  if (p > 70) return 'var(--accent-yellow)'
  return 'var(--accent-cyan)'
})

function handleOnline() { online.value = true }
function handleOffline() { online.value = false; latencyMs.value = null }

// ── 사용량 히스토리 차트 ──────────────────────────────────────
type HistoryPeriod = '1h' | '24h' | '7d'
interface HistoryPoint {
  timestamp: string
  cpuUsage: number | null
  memoryUsagePercent: number | null
  heapUsagePercent: number | null
}

const period = ref<HistoryPeriod>('1h')
const history = ref<HistoryPoint[]>([])
const canvasEl = ref<HTMLCanvasElement | null>(null)
const chartContainer = ref<HTMLDivElement | null>(null)

interface Geometry { padding: { top: number; right: number; bottom: number; left: number }; chartWidth: number; chartHeight: number; xStep: number }
let geometry: Geometry | null = null

async function loadHistory() {
  history.value = await tryGet<HistoryPoint[]>(`api/system/history?period=${period.value}`, [])
  drawChart()
}

function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#666'
}

function drawChart() {
  const canvas = canvasEl.value
  const container = chartContainer.value
  if (!canvas || !container) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const rect = container.getBoundingClientRect()
  const dpr = window.devicePixelRatio || 1
  canvas.width = rect.width * dpr
  canvas.height = rect.height * dpr
  ctx.setTransform(1, 0, 0, 1, 0, 0)
  ctx.scale(dpr, dpr)

  const width = rect.width
  const height = rect.height
  const padding = { top: 10, right: 10, bottom: 20, left: 35 }
  const chartWidth = width - padding.left - padding.right
  const chartHeight = height - padding.top - padding.bottom

  ctx.clearRect(0, 0, width, height)

  const muted = cssVar('--text-muted')
  const border = cssVar('--border-color')

  if (history.value.length === 0) {
    ctx.fillStyle = muted
    ctx.font = '10px JetBrains Mono'
    ctx.textAlign = 'center'
    ctx.fillText('데이터 없음', width / 2, height / 2)
    geometry = null
    return
  }

  ctx.strokeStyle = border
  ctx.lineWidth = 0.5
  for (let i = 0; i <= 4; i++) {
    const y = padding.top + (chartHeight / 4) * i
    ctx.beginPath()
    ctx.moveTo(padding.left, y)
    ctx.lineTo(width - padding.right, y)
    ctx.stroke()

    ctx.fillStyle = muted
    ctx.font = '9px JetBrains Mono'
    ctx.textAlign = 'right'
    ctx.fillText(`${100 - i * 25}%`, padding.left - 5, y + 3)
  }

  const points = history.value.length
  const xStep = points > 1 ? chartWidth / (points - 1) : chartWidth
  geometry = { padding, chartWidth, chartHeight, xStep }

  const drawLine = (key: keyof HistoryPoint, color: string) => {
    ctx.strokeStyle = color
    ctx.lineWidth = 1.5
    ctx.beginPath()
    history.value.forEach((item, i) => {
      const x = padding.left + i * xStep
      const value = numOr(item[key])
      const y = padding.top + chartHeight - (value / 100) * chartHeight
      if (i === 0) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
    })
    ctx.stroke()
  }

  drawLine('cpuUsage', cssVar('--accent-cyan'))
  drawLine('memoryUsagePercent', cssVar('--accent-yellow'))
  drawLine('heapUsagePercent', cssVar('--accent-green'))

  ctx.fillStyle = muted
  ctx.font = '8px JetBrains Mono'
  ctx.textAlign = 'center'
  const labelCount = Math.min(5, points)
  const labelStep = Math.max(1, Math.floor(points / labelCount))
  for (let i = 0; i < labelCount; i++) {
    const idx = i * labelStep
    if (idx >= points) continue
    const time = new Date(history.value[idx].timestamp)
    const x = padding.left + idx * xStep
    const label = period.value === '7d'
      ? `${time.getMonth() + 1}/${time.getDate()}`
      : `${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}`
    ctx.fillText(label, x, height - 5)
  }
}

interface Tooltip { visible: boolean; x: number; y: number; cpu: string; mem: string; heap: string; time: string }
const tooltip = reactive<Tooltip>({ visible: false, x: 0, y: 0, cpu: '', mem: '', heap: '', time: '' })

function onChartMouseMove(e: MouseEvent) {
  const canvas = canvasEl.value
  if (!canvas || !geometry) { tooltip.visible = false; return }
  const rect = canvas.getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  const { padding, chartWidth, chartHeight, xStep } = geometry

  if (x < padding.left || x > padding.left + chartWidth || y < padding.top || y > padding.top + chartHeight) {
    tooltip.visible = false
    return
  }

  const index = Math.round((x - padding.left) / xStep)
  const item = history.value[index]
  if (!item) { tooltip.visible = false; return }

  tooltip.cpu = item.cpuUsage != null ? item.cpuUsage.toFixed(1) : 'N/A'
  tooltip.mem = item.memoryUsagePercent != null ? item.memoryUsagePercent.toFixed(1) : 'N/A'
  tooltip.heap = item.heapUsagePercent != null ? item.heapUsagePercent.toFixed(1) : 'N/A'
  tooltip.time = new Date(item.timestamp).toLocaleTimeString('ko-KR')
  tooltip.x = x + 10
  tooltip.y = y - 10
  tooltip.visible = true
}

function onChartMouseLeave() {
  tooltip.visible = false
}

function selectPeriod(p: HistoryPeriod) {
  period.value = p
  loadHistory()
}

let statusTimer: number | undefined
let historyTimer: number | undefined
let resizeObserver: ResizeObserver | undefined

onMounted(() => {
  loadStatus()
  loadHistory()

  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)

  statusTimer = window.setInterval(loadStatus, 30_000)
  historyTimer = window.setInterval(loadHistory, 60_000)

  if (chartContainer.value) {
    resizeObserver = new ResizeObserver(() => drawChart())
    resizeObserver.observe(chartContainer.value)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
  if (statusTimer !== undefined) clearInterval(statusTimer)
  if (historyTimer !== undefined) clearInterval(historyTimer)
  resizeObserver?.disconnect()
})
</script>

<template>
  <DashboardSection :id="props.sectionId" :title="props.title" :updated-at="dashboard.lastUpdated">
    <div class="system-info-grid">
      <div class="system-info-card">
        <div class="system-info-label">디스크</div>
        <div v-if="diskPercent !== null" class="system-info-value">
          <div class="disk-percent">{{ diskPercent }}%</div>
          <div class="disk-detail">
            <div>사용: {{ formatBytes(diskUsed) }}</div>
            <div>전체: {{ formatBytes(diskTotal) }}</div>
            <div>여유: {{ formatBytes(diskFree) }}</div>
          </div>
        </div>
        <div v-else class="system-info-value">-</div>
        <div class="disk-usage-bar">
          <div class="disk-usage-fill" :style="{ width: `${diskPercent ?? 0}%`, background: diskBarColor }" />
        </div>
      </div>

      <div class="system-info-card">
        <div class="system-info-label">업타임</div>
        <div class="system-info-value">{{ formatUptime(uptimeMillis) }}</div>
      </div>

      <div class="system-info-card">
        <div class="system-info-label">네트워크</div>
        <div class="network-status">
          <span class="network-dot" :class="online ? 'online' : 'offline'" />
          <span class="system-info-value">
            {{ online ? 'Online' : 'Offline' }}
            <span v-if="online && latencyMs !== null" class="network-latency">{{ latencyMs }}ms</span>
          </span>
        </div>
      </div>
    </div>

    <div class="system-grid">
      <div class="system-bar">
        <span class="bar-label">CPU</span>
        <div class="bar-container">
          <div class="bar-fill" :class="cpuClass" :style="{ width: `${Math.min(Math.max(cpuUsage, 0), 100)}%` }" />
        </div>
        <span class="bar-value" :class="cpuClass">{{ cpuUsage >= 0 ? cpuUsage.toFixed(1) : 'N/A' }}%</span>
      </div>
      <div class="system-bar">
        <span class="bar-label">MEM</span>
        <div class="bar-container">
          <div class="bar-fill" :class="memClass" :style="{ width: `${memPercent}%` }" />
        </div>
        <span class="bar-value" :class="memClass">{{ formatBytes(memUsed) }}/{{ formatBytes(memTotal) }}</span>
      </div>
      <div class="system-bar">
        <span class="bar-label">HEAP</span>
        <div class="bar-container">
          <div class="bar-fill" :class="heapClass" :style="{ width: `${heapPercent}%` }" />
        </div>
        <span class="bar-value" :class="heapClass">{{ formatBytes(heapUsed) }}/{{ formatBytes(heapMax) }}</span>
      </div>
      <div class="system-item">
        <span class="system-label">THR</span>
        <span class="system-value">{{ threadCount }}</span>
      </div>
      <div class="system-item">
        <span class="system-label">GC</span>
        <span class="system-value">{{ gcCount }}/{{ gcTime }}ms</span>
      </div>
      <div class="system-item">
        <span class="system-label">UP</span>
        <span class="system-value">{{ formatUptime(uptimeMillis) }}</span>
      </div>
    </div>

    <div class="system-history">
      <div class="system-history-header">
        <span class="system-history-title">사용량 히스토리</span>
        <div class="system-history-controls">
          <button
            v-for="p in (['1h', '24h', '7d'] as HistoryPeriod[])"
            :key="p"
            class="history-period-btn"
            :class="{ active: period === p }"
            type="button"
            @click="selectPeriod(p)"
          >{{ p }}</button>
        </div>
      </div>
      <div ref="chartContainer" class="system-chart-container">
        <canvas
          ref="canvasEl"
          class="system-chart-canvas"
          @mousemove="onChartMouseMove"
          @mouseleave="onChartMouseLeave"
        />
        <div v-if="tooltip.visible" class="chart-tooltip visible" :style="{ left: `${tooltip.x}px`, top: `${tooltip.y}px` }">
          <div class="tooltip-cpu">CPU: {{ tooltip.cpu }}%</div>
          <div class="tooltip-mem">MEM: {{ tooltip.mem }}%</div>
          <div class="tooltip-heap">HEAP: {{ tooltip.heap }}%</div>
          <div class="tooltip-time">{{ tooltip.time }}</div>
        </div>
      </div>
      <div class="system-chart-legend">
        <span class="legend-item"><span class="legend-color cpu" />CPU</span>
        <span class="legend-item"><span class="legend-color mem" />MEM</span>
        <span class="legend-item"><span class="legend-color heap" />HEAP</span>
      </div>
    </div>
  </DashboardSection>
</template>

<style scoped>
.system-info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.system-info-card {
  padding: 10px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.system-info-label {
  color: var(--text-muted);
  font-size: 9px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.system-info-value {
  color: var(--accent-cyan);
  font-size: 11px;
  font-weight: 600;
  line-height: 1.3;
}

.disk-percent {
  font-size: 12px;
  margin-bottom: 4px;
}

.disk-detail {
  font-size: 9px;
  color: var(--text-secondary);
  line-height: 1.4;
  font-weight: 400;
}

.disk-usage-bar {
  height: 6px;
  background: var(--bg-secondary);
  border-radius: 3px;
  overflow: hidden;
  margin-top: 4px;
}

.disk-usage-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s;
}

.network-status {
  display: flex;
  align-items: center;
  gap: 6px;
}

.network-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.network-dot.online {
  background: var(--accent-green);
  box-shadow: 0 0 6px var(--accent-green);
}

.network-dot.offline {
  background: var(--accent-red);
}

.network-latency {
  color: var(--text-muted);
  font-size: 9px;
  font-weight: 400;
  margin-left: 4px;
}

.system-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  font-size: 11px;
}

.system-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.system-label {
  color: var(--text-secondary);
}

.system-value {
  color: var(--text-primary);
  font-weight: 500;
}

.system-bar {
  grid-column: span 2;
  display: flex;
  align-items: center;
  gap: 8px;
}

.bar-label {
  color: var(--text-secondary);
  width: 40px;
}

.bar-container {
  flex: 1;
  height: 8px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: var(--accent-cyan);
  border-radius: 4px;
  transition: width 0.3s;
}

.bar-fill.warning { background: var(--accent-yellow); }
.bar-fill.danger { background: var(--accent-red); }

.bar-value {
  color: var(--text-primary);
  width: 90px;
  text-align: right;
  font-size: 10px;
}

.bar-value.warning { color: var(--accent-yellow); }
.bar-value.danger { color: var(--accent-red); }

.system-history {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
}

.system-history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.system-history-title {
  font-size: 10px;
  color: var(--text-secondary);
}

.system-history-controls {
  display: flex;
  gap: 4px;
}

.history-period-btn {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  color: var(--text-muted);
  padding: 2px 8px;
  border-radius: 3px;
  cursor: pointer;
  font-family: inherit;
  font-size: 9px;
}

.history-period-btn:hover {
  border-color: var(--accent-cyan);
  color: var(--accent-cyan);
}

.history-period-btn.active {
  border-color: var(--accent-cyan);
  background: rgb(0 212 255 / 10%);
  color: var(--accent-cyan);
}

.system-chart-container {
  position: relative;
  height: 120px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 8px;
}

.system-chart-canvas {
  width: 100%;
  height: 100%;
  display: block;
}

.system-chart-legend {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 6px;
  font-size: 9px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary);
}

.legend-color {
  width: 12px;
  height: 3px;
  border-radius: 1px;
  display: inline-block;
}

.legend-color.cpu { background: var(--accent-cyan); }
.legend-color.mem { background: var(--accent-yellow); }
.legend-color.heap { background: var(--accent-green); }

.chart-tooltip {
  position: absolute;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  padding: 6px 10px;
  font-size: 10px;
  pointer-events: none;
  z-index: 5;
  display: none;
}

.chart-tooltip.visible { display: block; }

.tooltip-cpu { color: var(--accent-cyan); }
.tooltip-mem { color: var(--accent-yellow); }
.tooltip-heap { color: var(--accent-green); }
.tooltip-time { color: var(--text-muted); font-size: 8px; }
</style>
