<script setup lang="ts">
import { computed } from 'vue'
import { useUiStore } from '@/stores/ui'

/**
 * 모든 대시보드 섹션의 껍데기.
 *
 * 종전에는 섹션마다 헤더 마크업을 복붙하고 접기 토글을 각자 구현했다.
 * 여기 하나로 모아서, 새 섹션을 만들 때 껍데기를 다시 쓰지 않게 한다.
 */
const props = defineProps<{
  /** 접힘 상태 저장 키. 화면 전체에서 유일해야 한다. */
  id: string
  title: string
  /** 마지막 갱신 시각(ISO). 없으면 표시하지 않는다. */
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
  <section class="section" :class="{ 'is-collapsed': isCollapsed }">
    <header class="section-header">
      <div class="section-header-left">
        <button
          class="section-toggle"
          :aria-expanded="!isCollapsed"
          :aria-controls="`${id}-body`"
          @click="ui.toggleSection(id)"
        >
          {{ isCollapsed ? '▸' : '▾' }}
        </button>
        <h2 class="section-title">{{ title }}</h2>
      </div>
      <div class="section-controls">
        <slot name="controls" />
        <span v-if="updatedLabel" class="section-time">{{ updatedLabel }}</span>
      </div>
    </header>

    <div v-show="!isCollapsed" :id="`${id}-body`" class="section-body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.section {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  min-width: 0; /* grid 자식이 내용 때문에 넘치지 않게 */
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color);
}

.is-collapsed .section-header {
  border-bottom: none;
}

.section-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.section-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.section-toggle {
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 12px;
  padding: 2px 4px;
  line-height: 1;
}

.section-toggle:hover {
  color: var(--text-primary);
}

.section-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.section-time {
  font-size: 11px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.section-body {
  padding: 12px;
  overflow: auto;
  min-height: 0;
}
</style>
