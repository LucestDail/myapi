<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useDashboardStore } from '@/stores/dashboard'
import { useUiStore } from '@/stores/ui'
import { DASHBOARD_MODES } from '@/types/dashboard'
import { sectionsFor } from '@/sections/registry'
import ToastHost from '@/components/ToastHost.vue'
import MediaPlayer from '@/components/MediaPlayer.vue'
import SettingsModal from '@/modals/SettingsModal.vue'
import AiReportModal from '@/modals/AiReportModal.vue'

/**
 * 🔴 2026-09-26 레이아웃 재작성.
 *
 * 첫 시도가 "상단 헤더 + 전체 폭 카드 그리드"로 원본과 완전히 다른 화면을 만들었다.
 * 원본은 좌우 2단 고정 레이아웃이다(`css/base.css` `.container`):
 *
 *   .container { display: grid; grid-template-columns: 1fr 420px; height: 100vh; }
 *
 * 좌측(1fr) = 유튜브 하나만, 우측(420px 고정) = 헤더+탭+섹션 목록이 전부 들어가는
 * 좁은 패널. 섹션은 카드 그리드가 아니라 그 좁은 패널 안에서 위아래로 쌓이는
 * 목록이다. 1024px 이하에서는 세로 스택(위 45vh 유튜브 / 아래 나머지)으로 바뀐다.
 */
const dashboard = useDashboardStore()
const ui = useUiStore()

const visibleSections = computed(() => sectionsFor(ui.mode))

const now = ref(new Date())
let clock: number | undefined

onMounted(() => {
  dashboard.start()
  clock = window.setInterval(() => { now.value = new Date() }, 1000)
})

onBeforeUnmount(() => {
  if (clock) clearInterval(clock)
  dashboard.disconnect()
})

const timeLabel = computed(() =>
  now.value.toLocaleString('ko-KR', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
)

const statusLabel = computed(() => ({
  connecting: '연결 중...',
  connected: '연결됨',
  disconnected: '끊김'
}[dashboard.status]))
</script>

<template>
  <div class="container">
    <MediaPlayer />

    <section class="dashboard-panel">
      <header class="dashboard-header">
        <div class="header-title-block">
          <div class="dashboard-title">MyAPI Dashboard</div>
          <div class="dashboard-time">{{ timeLabel }}</div>
        </div>
        <div class="header-actions">
          <span class="connection-status">
            <i class="status-dot" :class="dashboard.status"></i>
            <span class="status-text">{{ statusLabel }}</span>
          </span>
          <button class="settings-btn ai-report-btn" @click="ui.openModal = 'ai-report'">AI 리포트</button>
          <button class="settings-btn" @click="ui.openModal = 'settings'">설정</button>
        </div>
      </header>

      <nav class="dashboard-mode-tabs" aria-label="대시보드 모드">
        <button
          v-for="m in DASHBOARD_MODES"
          :key="m.id"
          class="mode-tab"
          :class="{ active: ui.mode === m.id }"
          :aria-pressed="ui.mode === m.id"
          @click="ui.setMode(m.id)"
        >
          <span class="mode-tab-icon">{{ m.icon }}</span>{{ m.label }}
        </button>
      </nav>

      <div class="dashboard-content">
        <component
          :is="s.component"
          v-for="s in visibleSections"
          :key="s.id"
          :section-id="s.id"
          :title="s.title"
        />
      </div>
    </section>

    <SettingsModal v-if="ui.openModal === 'settings'" @close="ui.openModal = null" />
    <AiReportModal v-if="ui.openModal === 'ai-report'" @close="ui.openModal = null" />
    <ToastHost />
  </div>
</template>

<style scoped>
.container {
  display: grid;
  grid-template-columns: 1fr 420px;
  height: 100vh;
  gap: 0;
}

.dashboard-panel {
  background: var(--bg-secondary);
  border-left: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.dashboard-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--bg-tertiary);
  flex: 0 0 auto;
}

.header-title-block {
  min-width: 0;
}

.dashboard-title {
  color: var(--accent-cyan);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dashboard-time {
  color: var(--text-secondary);
  font-size: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 0 0 auto;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text-muted);
  display: inline-block;
}

.status-dot.connected { background: var(--accent-green); }
.status-dot.connecting { background: var(--accent-yellow); }
.status-dot.disconnected { background: var(--accent-red); }

.settings-btn {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  border-radius: 4px;
  padding: 5px 10px;
  font-size: 11px;
  font-family: inherit;
  cursor: pointer;
}

.settings-btn:hover { border-color: var(--border-accent); }

.dashboard-mode-tabs {
  display: flex;
  background: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-color);
  padding: 0 4px;
  gap: 2px;
  flex: 0 0 auto;
}

.mode-tab {
  flex: 1;
  padding: 6px 4px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  color: var(--text-muted);
  font-family: inherit;
  font-size: 9px;
  font-weight: 500;
  cursor: pointer;
  text-align: center;
  letter-spacing: 0.3px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.mode-tab:hover {
  color: var(--text-secondary);
  background: var(--bg-secondary);
}

.mode-tab.active {
  color: var(--accent-cyan);
  border-bottom-color: var(--accent-cyan);
}

.dashboard-content {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

@media (max-width: 1024px) {
  .container {
    grid-template-columns: 1fr;
    grid-template-rows: 45vh 1fr;
  }
  .dashboard-panel {
    border-left: none;
    border-top: 1px solid var(--border-color);
  }
}

@media (max-width: 768px) {
  .container { grid-template-rows: 40vh 1fr; }
  .dashboard-header { padding: 10px 12px; flex-wrap: wrap; gap: 8px; }
  .dashboard-title { font-size: 12px; }
  .ai-report-btn { display: none; }
}

@media (max-width: 480px) {
  .container { grid-template-rows: 35vh 1fr; }
}
</style>
