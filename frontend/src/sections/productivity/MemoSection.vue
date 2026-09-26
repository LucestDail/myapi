<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'

/**
 * 간단 메모 섹션 — localStorage 기반(서버 연동 없음).
 * 원본: static/js/features/productivity.js 의 loadMemo/handleMemoInput
 */
defineProps<{ sectionId: string; title: string }>()

const STORAGE_KEY = 'memoContent'

function readMemo(): string {
  try {
    return localStorage.getItem(STORAGE_KEY) || ''
  } catch {
    return ''
  }
}

const content = ref(readMemo())
const status = ref<'idle' | 'pending' | 'saved'>('idle')
let saveTimer: number | undefined

function onInput() {
  status.value = 'pending'
  if (saveTimer !== undefined) window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(() => {
    try {
      localStorage.setItem(STORAGE_KEY, content.value)
      status.value = 'saved'
    } catch {
      // 저장소가 막혀 있으면 이번 세션에서만 유지된다.
      status.value = 'idle'
    }
  }, 500)
}

onBeforeUnmount(() => {
  if (saveTimer !== undefined) window.clearTimeout(saveTimer)
})
</script>

<template>
  <DashboardSection :id="sectionId" :title="title">
    <div class="memo">
      <textarea
        v-model="content"
        class="memo-textarea"
        placeholder="메모를 입력하세요..."
        @input="onInput"
      ></textarea>
      <div class="memo-footer">
        <span class="memo-status" :class="{ saved: status === 'saved' }">
          {{ status === 'pending' ? '저장 중...' : status === 'saved' ? '저장됨' : '' }}
        </span>
      </div>
    </div>
  </DashboardSection>
</template>

<style scoped>
.memo {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.memo-textarea {
  width: 100%;
  min-height: 80px;
  padding: 10px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-primary);
  font-family: inherit;
  font-size: 12px;
  resize: vertical;
}

.memo-textarea:focus {
  outline: none;
  border-color: var(--accent-cyan);
}

.memo-footer {
  display: flex;
  justify-content: flex-end;
}

.memo-status {
  color: var(--text-muted);
  font-size: 10px;
}

.memo-status.saved {
  color: var(--accent-green);
}
</style>
