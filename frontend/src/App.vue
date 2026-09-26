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
  connecting: '연결 중',
  connected: '연결됨',
  disconnected: '끊김'
}[dashboard.status]))
</script>

<template>
  <div class="dashboard">
    <header class="dashboard-header">
      <div class="header-left">
        <h1 class="dashboard-title">MyAPI</h1>
        <span class="dashboard-time">{{ timeLabel }}</span>
      </div>

      <nav class="mode-tabs" aria-label="대시보드 모드">
        <button
          v-for="m in DASHBOARD_MODES"
          :key="m.id"
          class="mode-tab"
          :class="{ active: ui.mode === m.id }"
          :aria-pressed="ui.mode === m.id"
          @click="ui.setMode(m.id)"
        >
          {{ m.label }}
        </button>
      </nav>

      <div class="header-right">
        <span class="conn" :class="dashboard.status" :title="statusLabel">
          <i class="dot" aria-hidden="true"></i>{{ statusLabel }}
        </span>
        <button class="btn" @click="ui.openModal = 'ai-report'">AI 리포트</button>
        <button class="btn" @click="ui.openModal = 'settings'">설정</button>
      </div>
    </header>

    <MediaPlayer />

    <main class="dashboard-grid">
      <component
        :is="s.component"
        v-for="s in visibleSections"
        :key="s.id"
        :section-id="s.id"
        :title="s.title"
        :class="{ wide: s.wide }"
      />
    </main>

    <SettingsModal v-if="ui.openModal === 'settings'" @close="ui.openModal = null" />
    <AiReportModal v-if="ui.openModal === 'ai-report'" @close="ui.openModal = null" />
    <ToastHost />
  </div>
</template>

<style scoped>
.dashboard {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.dashboard-header {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-secondary);
  position: sticky;
  top: env(safe-area-inset-top, 0px);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}

.dashboard-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--accent-cyan);
  letter-spacing: 0.02em;
}

.dashboard-time {
  font-size: 12px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.mode-tabs {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.mode-tab {
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  border-radius: 6px;
  padding: 5px 12px;
  font-size: 13px;
  cursor: pointer;
}

.mode-tab:hover { color: var(--text-primary); border-color: var(--border-accent); }

.mode-tab.active {
  background: var(--bg-tertiary);
  color: var(--accent-cyan);
  border-color: var(--accent-cyan);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.btn {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  border-radius: 6px;
  padding: 5px 12px;
  font-size: 13px;
  cursor: pointer;
}

.btn:hover { border-color: var(--border-accent); }

.conn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.conn .dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--text-muted);
  display: inline-block;
}

.conn.connected .dot { background: var(--accent-green); }
.conn.connecting .dot { background: var(--accent-yellow); }
.conn.disconnected .dot { background: var(--accent-red); }

.dashboard-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
  padding: 12px 16px 24px;
  align-content: start;
}

/* 내용이 넓어야 읽히는 섹션은 두 칸을 쓴다. 한 칸만 남으면 자연히 한 칸이 된다. */
.dashboard-grid > :deep(.wide) {
  grid-column: span 2;
}

@media (max-width: 720px) {
  .dashboard-grid { grid-template-columns: 1fr; padding-inline: 16px; }
  .dashboard-grid > :deep(.wide) { grid-column: span 1; }
  .header-right { width: 100%; margin-left: 0; }
}
</style>
