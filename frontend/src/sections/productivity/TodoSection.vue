<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'
import { api } from '@/api/client'
import { useUiStore } from '@/stores/ui'

/**
 * 할 일 섹션 — 서버 연동.
 * 원본: static/js/features/todo.js, static/css/features/todo.css
 *
 * 🔴 원본 버그를 여기서 고쳤다: 원본은 항상 `priority: 'MEDIUM'`(문자열)을 보냈는데
 *    서버 `Todo.priority` 는 Integer(0 보통/1 높음/2 긴급)다. 문자열을 보내면 서버가
 *    거부하거나(테스트 환경에 따라) 조용히 0으로 떨어진다 — 실제로는 한 번도 우선순위가
 *    반영되지 않았다. 여기서는 처음부터 숫자를 보낸다.
 */
defineProps<{ sectionId: string; title: string }>()

interface TodoDto {
  id: number
  content: string
  completed: boolean
  priority: number
  dueDate: string | null
  createdAt: string
  updatedAt: string
}

type Filter = 'all' | 'pending' | 'completed'

const FILTERS: { id: Filter; label: string }[] = [
  { id: 'all', label: '전체' },
  { id: 'pending', label: '진행중' },
  { id: 'completed', label: '완료' }
]

const ui = useUiStore()
const todos = ref<TodoDto[]>([])
const filter = ref<Filter>('all')
const newContent = ref('')
const newPriority = ref(0)
const loading = ref(false)

const stats = computed(() => {
  const total = todos.value.length
  const completed = todos.value.filter(t => t.completed).length
  return { total, completed, pending: total - completed }
})

/** 목록 중 가장 최근에 갱신된 시각. DashboardSection 헤더에 보여준다. */
const lastUpdated = computed<string | null>(() => {
  if (todos.value.length === 0) return null
  return todos.value.reduce((latest, t) => (t.updatedAt > latest ? t.updatedAt : latest), todos.value[0].updatedAt)
})

async function load() {
  loading.value = true
  try {
    const query = filter.value === 'all' ? '' : `?filter=${filter.value}`
    todos.value = await api.get<TodoDto[]>(`api/todos${query}`)
  } catch {
    ui.toast('할 일을 불러오지 못했습니다', 'danger')
  } finally {
    loading.value = false
  }
}

function setFilter(next: Filter) {
  if (filter.value === next) return
  filter.value = next
  load()
}

async function addTodo() {
  const content = newContent.value.trim()
  if (!content) return
  try {
    await api.post('api/todos', { content, priority: newPriority.value })
    newContent.value = ''
    newPriority.value = 0
    await load()
  } catch {
    ui.toast('할 일 추가 실패', 'danger')
  }
}

async function toggle(todo: TodoDto) {
  // 낙관적 업데이트 — 응답을 기다리지 않고 즉시 화면에 반영, 실패하면 되돌린다.
  const previous = todo.completed
  todo.completed = !previous
  try {
    const updated = await api.patch<TodoDto>(`api/todos/${todo.id}/toggle`)
    Object.assign(todo, updated)
    if (filter.value !== 'all') await load()
  } catch {
    todo.completed = previous
    ui.toast('상태 변경 실패', 'danger')
  }
}

async function remove(todo: TodoDto) {
  try {
    await api.delete(`api/todos/${todo.id}`)
    todos.value = todos.value.filter(t => t.id !== todo.id)
  } catch {
    ui.toast('삭제 실패', 'danger')
  }
}

async function clearCompleted() {
  try {
    await api.delete('api/todos/completed')
    await load()
    ui.toast('완료된 항목을 삭제했습니다', 'success')
  } catch {
    ui.toast('완료 항목 삭제 실패', 'danger')
  }
}

function priorityClass(p: number) {
  return p >= 2 ? 'urgent' : p === 1 ? 'high' : 'normal'
}

onMounted(load)
</script>

<template>
  <DashboardSection :id="sectionId" :title="title" :updated-at="lastUpdated">
    <template #controls>
      <button
        v-for="f in FILTERS"
        :key="f.id"
        class="chip"
        :class="{ active: filter === f.id }"
        @click="setFilter(f.id)"
      >
        {{ f.label }}
      </button>
    </template>

    <div class="todo-list">
      <p v-if="!loading && todos.length === 0" class="empty">할 일이 없습니다</p>
      <div v-for="todo in todos" :key="todo.id" class="todo-item" :class="{ completed: todo.completed }">
        <input type="checkbox" :checked="todo.completed" @change="toggle(todo)" />
        <span class="priority-dot" :class="priorityClass(todo.priority)"></span>
        <span class="content">{{ todo.content }}</span>
        <button class="delete-btn" aria-label="삭제" @click="remove(todo)">×</button>
      </div>
    </div>

    <form class="add-form" @submit.prevent="addTodo">
      <select v-model.number="newPriority" class="priority-select" aria-label="우선순위">
        <option :value="0">보통</option>
        <option :value="1">높음</option>
        <option :value="2">긴급</option>
      </select>
      <input v-model="newContent" type="text" class="add-input" placeholder="새 할 일 추가..." />
      <button type="submit" class="add-btn">추가</button>
    </form>

    <div v-if="todos.length > 0" class="stats">
      <span>전체 {{ stats.total }} · 진행중 {{ stats.pending }} · 완료 {{ stats.completed }}</span>
      <button v-if="stats.completed > 0" class="clear-btn" @click="clearCompleted">완료 항목 삭제</button>
    </div>
  </DashboardSection>
</template>

<style scoped>
.chip {
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
}

.chip:hover {
  color: var(--text-primary);
  border-color: var(--border-accent);
}

.chip.active {
  border-color: var(--accent-cyan);
  color: var(--accent-cyan);
  background: rgba(0, 212, 255, 0.1);
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}

.empty {
  color: var(--text-muted);
  font-size: 12px;
  text-align: center;
  padding: 12px 0;
}

.todo-item {
  display: grid;
  grid-template-columns: 16px 8px 1fr 20px;
  align-items: center;
  padding: 6px 8px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  gap: 8px;
}

.todo-item:hover {
  background: var(--bg-secondary);
  outline: 1px solid var(--border-color);
}

.todo-item input[type='checkbox'] {
  accent-color: var(--accent-cyan);
  cursor: pointer;
}

.priority-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
}

.priority-dot.high { background: var(--accent-yellow); }
.priority-dot.urgent { background: var(--accent-red); }

.content {
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-item.completed .content {
  color: var(--text-muted);
  text-decoration: line-through;
}

.delete-btn {
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 2px;
  opacity: 0;
}

.todo-item:hover .delete-btn { opacity: 1; }
.delete-btn:hover { color: var(--accent-red); }

.add-form {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.priority-select,
.add-input {
  padding: 7px 10px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-primary);
  font-family: inherit;
  font-size: 12px;
}

.priority-select { flex: 0 0 auto; }
.add-input { flex: 1; min-width: 0; }

.priority-select:focus,
.add-input:focus {
  outline: none;
  border-color: var(--accent-cyan);
}

.add-btn {
  background: var(--accent-cyan);
  border: none;
  color: var(--bg-primary);
  padding: 7px 14px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
}

.add-btn:hover { background: var(--accent-blue); }

.stats {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dotted var(--border-color);
}

.clear-btn {
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-muted);
  padding: 3px 8px;
  border-radius: 3px;
  cursor: pointer;
  font-size: 10px;
}

.clear-btn:hover {
  border-color: var(--accent-red);
  color: var(--accent-red);
}
</style>
