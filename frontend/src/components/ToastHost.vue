<script setup lang="ts">
import { useUiStore } from '@/stores/ui'
const ui = useUiStore()
</script>

<template>
  <div class="toast-host" role="status" aria-live="polite">
    <div v-for="t in ui.toasts" :key="t.id" class="toast" :class="t.kind">
      {{ t.message }}
    </div>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  right: 16px;
  bottom: calc(16px + env(safe-area-inset-bottom, 0px));
  display: flex;
  flex-direction: column;
  gap: 8px;
  z-index: 100;
  pointer-events: none;
}

.toast {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-left-width: 3px;
  border-radius: 6px;
  padding: 10px 14px;
  font-size: 13px;
  color: var(--text-primary);
  max-width: min(360px, calc(100vw - 32px));
  box-shadow: 0 4px 14px rgb(0 0 0 / 35%);
}

.toast.info    { border-left-color: var(--accent-cyan); }
.toast.success { border-left-color: var(--accent-green); }
.toast.warning { border-left-color: var(--accent-yellow); }
.toast.danger  { border-left-color: var(--accent-red); }
</style>
