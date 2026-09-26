<script setup lang="ts">
/**
 * D-Day 목록.
 *
 * 원본은 항목 하나 추가하는 데도 별도 모달(dday-modal)을 열었다. 여기서는
 * 섹션 안에 입력 두 칸 + 추가 버튼을 인라인으로 둔다 — 모달을 띄우고 닫는
 * 왕복이 없어서 더 빠르다.
 *
 * 데이터 출처: localStorage('ddayList') — 서버에 저장할 이유가 없는 개인 목록이라
 * 원본과 같은 키를 그대로 쓴다(마이그레이션 중에도 기존 사용자 데이터가 이어진다).
 */
import { computed, ref } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'
import { useUiStore } from '@/stores/ui'

const props = defineProps<{ sectionId: string; title: string }>()
const ui = useUiStore()

interface DdayItem {
  title: string
  date: string // yyyy-MM-dd
}

const STORAGE_KEY = 'ddayList'

function loadList(): DdayItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as DdayItem[]) : []
  } catch {
    return []
  }
}

function persist(list: DdayItem[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
  } catch {
    // 저장 실패는 무해하다 — 화면 상태는 이미 반영되어 있다
  }
}

const list = ref<DdayItem[]>(loadList())

const sorted = computed(() =>
  [...list.value].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
)

function dDayLabel(dateStr: string): { text: string; cls: string } {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const target = new Date(dateStr)
  target.setHours(0, 0, 0, 0)
  const diffDays = Math.ceil((target.getTime() - today.getTime()) / 86400000)

  if (diffDays === 0) return { text: 'D-DAY', cls: 'today' }
  if (diffDays > 0) return { text: `D-${diffDays}`, cls: 'future' }
  return { text: `D+${Math.abs(diffDays)}`, cls: 'past' }
}

const newTitle = ref('')
const newDate = ref('')

function addItem() {
  const title = newTitle.value.trim()
  const date = newDate.value

  if (!title) {
    ui.toast('제목을 입력하세요', 'warning')
    return
  }
  if (!date) {
    ui.toast('날짜를 선택하세요', 'warning')
    return
  }

  list.value = [...list.value, { title, date }]
  persist(list.value)
  newTitle.value = ''
  newDate.value = ''
  ui.toast('D-Day가 추가되었습니다', 'success')
}

function removeItem(index: number) {
  const target = sorted.value[index]
  list.value = list.value.filter(item => item !== target)
  persist(list.value)
}
</script>

<template>
  <DashboardSection :id="props.sectionId" :title="props.title">
    <div v-if="sorted.length === 0" class="no-data">D-Day를 추가해보세요</div>
    <div v-else class="dday-list">
      <div v-for="(item, index) in sorted" :key="`${item.title}-${item.date}`" class="dday-item">
        <div>
          <div class="dday-title">{{ item.title }}</div>
          <div class="dday-date">{{ item.date }}</div>
        </div>
        <div class="dday-item-right">
          <span class="dday-count" :class="dDayLabel(item.date).cls">{{ dDayLabel(item.date).text }}</span>
          <button class="dday-remove" type="button" aria-label="삭제" @click="removeItem(index)">✕</button>
        </div>
      </div>
    </div>

    <form class="dday-add-form" @submit.prevent="addItem">
      <input
        v-model="newTitle"
        class="dday-input dday-input-title"
        type="text"
        placeholder="제목 (예: 생일)"
        maxlength="40"
      >
      <input v-model="newDate" class="dday-input dday-input-date" type="date">
      <button class="dday-add-btn" type="submit">+ 추가</button>
    </form>
  </DashboardSection>
</template>

<style scoped>
.dday-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.dday-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  background: var(--bg-tertiary);
  border-radius: 4px;
}

.dday-item-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dday-title {
  color: var(--text-primary);
  font-size: 12px;
}

.dday-date {
  color: var(--text-muted);
  font-size: 10px;
}

.dday-count {
  font-weight: 600;
  font-size: 12px;
}

.dday-count.past { color: var(--text-muted); }
.dday-count.today { color: var(--accent-green); }
.dday-count.future { color: var(--accent-cyan); }

.dday-remove {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 11px;
  padding: 2px;
  line-height: 1;
}

.dday-remove:hover {
  color: var(--accent-red);
}

.dday-add-form {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.dday-input {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-primary);
  font-family: inherit;
  font-size: 11px;
  padding: 5px 8px;
}

.dday-input-title {
  flex: 1;
  min-width: 100px;
}

.dday-input-date {
  flex: 0 0 auto;
}

.dday-add-btn {
  background: transparent;
  border: 1px dashed var(--border-color);
  border-radius: 4px;
  color: var(--text-muted);
  font-size: 11px;
  padding: 5px 10px;
  cursor: pointer;
  white-space: nowrap;
}

.dday-add-btn:hover {
  border-color: var(--accent-cyan);
  color: var(--accent-cyan);
}
</style>
