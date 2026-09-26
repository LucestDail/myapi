<script setup lang="ts">
import { ref } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'
import { useUiStore } from '@/stores/ui'

/**
 * 북마크 섹션 — localStorage 기반(서버 연동 없음).
 * 원본: static/js/features/productivity.js 의 renderBookmarks/openBookmarkModal/saveBookmark
 *
 * 🔴 개선: 원본은 북마크 하나 추가하는 데 별도 모달을 열었다(이름 입력 → URL 입력 →
 * 미리보기 → 저장, 4단계). 항목 하나 추가에 모달까지 띄우는 건 과하다 — 여기서는
 * 섹션 안 인라인 입력 두 칸 + 추가 버튼으로 줄였다.
 */
defineProps<{ sectionId: string; title: string }>()

const STORAGE_KEY = 'bookmarksList'

interface Bookmark {
  name: string
  url: string
}

function readBookmarks(): Bookmark[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Bookmark[]) : []
  } catch {
    return []
  }
}

function persist(list: Bookmark[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
  } catch {
    // 저장소가 막혀 있으면 이번 세션에서만 유지된다.
  }
}

const ui = useUiStore()
const bookmarks = ref<Bookmark[]>(readBookmarks())
const newName = ref('')
const newUrl = ref('')

function faviconFor(url: string): string {
  try {
    const host = new URL(url).hostname
    return `https://www.google.com/s2/favicons?domain=${host}&sz=32`
  } catch {
    return ''
  }
}

function onFaviconError(event: Event) {
  const img = event.target as HTMLImageElement
  img.style.visibility = 'hidden'
}

function addBookmark() {
  const name = newName.value.trim()
  const url = newUrl.value.trim()

  if (!name) {
    ui.toast('이름을 입력하세요', 'warning')
    return
  }
  if (!url) {
    ui.toast('URL을 입력하세요', 'warning')
    return
  }
  try {
    new URL(url)
  } catch {
    ui.toast('올바른 URL을 입력하세요(https:// 포함)', 'warning')
    return
  }

  const next = [...bookmarks.value, { name, url }]
  bookmarks.value = next
  persist(next)
  newName.value = ''
  newUrl.value = ''
  ui.toast('북마크가 추가되었습니다', 'success')
}

function removeBookmark(index: number) {
  const next = bookmarks.value.filter((_, i) => i !== index)
  bookmarks.value = next
  persist(next)
}
</script>

<template>
  <DashboardSection :id="sectionId" :title="title">
    <div class="bookmarks-grid">
      <a
        v-for="(bookmark, index) in bookmarks"
        :key="bookmark.url + index"
        class="bookmark-item"
        :href="bookmark.url"
        target="_blank"
        rel="noopener noreferrer"
      >
        <img class="favicon" :src="faviconFor(bookmark.url)" alt="" @error="onFaviconError" />
        <span class="name">{{ bookmark.name }}</span>
        <button
          class="delete-btn"
          aria-label="삭제"
          @click.prevent.stop="removeBookmark(index)"
        >×</button>
      </a>
    </div>

    <p v-if="bookmarks.length === 0" class="empty">북마크가 없습니다</p>

    <form class="add-form" @submit.prevent="addBookmark">
      <input v-model="newName" type="text" class="name-input" placeholder="이름" />
      <input v-model="newUrl" type="url" class="url-input" placeholder="https://example.com" />
      <button type="submit" class="add-btn">추가</button>
    </form>
  </DashboardSection>
</template>

<style scoped>
.bookmarks-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
}

.empty {
  color: var(--text-muted);
  font-size: 12px;
  text-align: center;
  padding: 12px 0;
}

.bookmark-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  text-decoration: none;
}

.bookmark-item:hover {
  background: var(--bg-secondary);
  border-left: 2px solid var(--accent-cyan);
}

.favicon {
  width: 16px;
  height: 16px;
  border-radius: 2px;
  flex: 0 0 auto;
}

.name {
  color: var(--text-primary);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.delete-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 12px;
  padding: 0 2px;
  opacity: 0;
  flex: 0 0 auto;
}

.bookmark-item:hover .delete-btn { opacity: 1; }
.delete-btn:hover { color: var(--accent-red); }

.add-form {
  display: flex;
  gap: 6px;
  margin-top: 10px;
}

.name-input,
.url-input {
  padding: 7px 10px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-primary);
  font-family: inherit;
  font-size: 12px;
  min-width: 0;
}

.name-input { flex: 0 0 30%; }
.url-input { flex: 1; }

.name-input:focus,
.url-input:focus {
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
  flex: 0 0 auto;
}

.add-btn:hover { background: var(--accent-blue); }
</style>
