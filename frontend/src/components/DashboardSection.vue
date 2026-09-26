<script setup lang="ts">
import { computed } from 'vue'
import { useUiStore } from '@/stores/ui'

/**
 * 모든 대시보드 섹션의 껍데기.
 *
 * 🔴 2026-09-26 정정: 처음엔 카드 그리드를 전제로 배경·테두리·radius 를 준
 * "카드"로 만들었다. 원본(`css/sections.css` `.section`)은 카드가 아니라
 * **점선 구분선만 있는 플랫 블록**이 420px 폭 패널 안에 세로로 쌓이는 구조다.
 * 배경·테두리·radius 를 없애고 원본 그대로 맞춘다.
 */
const props = defineProps<{
  id: string
  title: string
  updatedAt?: string | null
}>()

const ui = useUiStore()
const isCollapsed = computed(() => ui.collapsed[props.id] === true)

const updatedLabel = computed(() => {
  if (!props.updatedAt) return ''
  const d = new Date(props.updatedAt)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
})
</script>

<template>
  <section class="section" :class="{ collapsed: isCollapsed }">
    <header class="section-header" @click="ui.toggleSection(id)">
      <div class="section-header-left">
        <span class="section-toggle" :class="{ collapsed: isCollapsed }">▾</span>
        <h2 class="section-title">{{ title }}</h2>
      </div>
      <div class="section-header-right" @click.stop>
        <slot name="controls" />
        <span v-if="updatedLabel" class="section-time">{{ updatedLabel }}</span>
      </div>
    </header>

    <div v-show="!isCollapsed" class="section-body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.section {
  border-bottom: 1px dashed var(--border-color);
  padding: 12px 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  cursor: pointer;
  user-select: none;
}

.section.collapsed .section-header {
  margin-bottom: 0;
}

.section-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.section-toggle {
  color: var(--text-muted);
  font-size: 10px;
  transition: transform 0.2s;
  flex: 0 0 auto;
}

.section-toggle.collapsed {
  transform: rotate(-90deg);
}

.section-title {
  margin: 0;
  color: var(--accent-yellow);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.section-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.section-time {
  color: var(--text-muted);
  font-size: 9px;
  font-variant-numeric: tabular-nums;
}

.section-body {
  min-width: 0;
}
</style>
