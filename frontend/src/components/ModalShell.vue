<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'

/**
 * 모달 껍데기. 오버레이·헤더·닫기·ESC·바깥 클릭을 한 곳에서 처리한다.
 *
 * 🔴 스크롤 규칙: 본문(.modal-body)이 스크롤을 받는다. 자식이 flex 레이아웃이면
 *    자식에도 min-height:0 을 줘야 한다 — 안 그러면 내용만큼 늘어나 스크롤이 안 생긴다.
 *    (종전 AI 리포트 모달이 인라인 overflow:hidden 때문에 데스크톱에서 스크롤이
 *     안 되던 문제가 정확히 이것이었다 — 2026-09-26)
 */
withDefaults(defineProps<{ title: string; width?: string }>(), {
  width: '720px'
})

const emit = defineEmits<{ close: [] }>()

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => document.addEventListener('keydown', onKey))
onBeforeUnmount(() => document.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal" :style="{ maxWidth: width }" role="dialog" aria-modal="true">
      <header class="modal-header">
        <span class="modal-title">{{ title }}</span>
        <button class="modal-close" aria-label="닫기" @click="emit('close')">×</button>
      </header>
      <div class="modal-body">
        <slot />
      </div>
      <footer v-if="$slots.footer" class="modal-footer">
        <slot name="footer" />
      </footer>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgb(0 0 0 / 65%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  z-index: 50;
}

.modal {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  width: 100%;
  max-height: min(92vh, 900px);
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  flex: 0 0 auto;
}

.modal-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.modal-close {
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  padding: 0 4px;
}

.modal-close:hover { color: var(--text-primary); }

.modal-body {
  padding: 16px;
  overflow-y: auto;
  min-height: 0;
  flex: 1;
}

.modal-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border-color);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex: 0 0 auto;
}

@media (max-width: 720px) {
  .modal-overlay { padding: 0; }
  .modal { max-width: 100% !important; height: 100%; max-height: 100%; border-radius: 0; }
}
</style>
