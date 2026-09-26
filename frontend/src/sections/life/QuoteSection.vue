<script setup lang="ts">
/**
 * 오늘의 문장.
 *
 * 원본은 외부 https://api.quotable.io 에서 명언을 받아오고, 실패하면 4개짜리
 * 로컬 폴백 목록을 썼다. 이 프로젝트의 네트워크 규칙(모든 호출은 `api.*`를 지나야
 * 하고, 그 클라이언트는 우리 백엔드 상대경로만 겨냥한다 — `X-User-Id` 헤더를
 * 붙이는 게 목적이라 외부 도메인엔 애초에 맞지 않는다)과, 외부 API 장애/CORS에
 * 화면이 흔들리지 않아야 한다는 점을 같이 고려해 **로컬 명언 목록만 쓰도록
 * 바꿨다** — 폴백이 아니라 유일한 소스다. 백엔드에 명언 프록시가 생기면 그때
 * `tryGet` 으로 교체한다.
 */
import { ref } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'

const props = defineProps<{ sectionId: string; title: string }>()

interface Quote {
  content: string
  author: string
}

const quotes: Quote[] = [
  { content: 'The only way to do great work is to love what you do.', author: 'Steve Jobs' },
  { content: "Believe you can and you're halfway there.", author: 'Theodore Roosevelt' },
  { content: 'Innovation distinguishes between a leader and a follower.', author: 'Steve Jobs' },
  { content: 'The future belongs to those who believe in the beauty of their dreams.', author: 'Eleanor Roosevelt' },
  { content: "Life is what happens when you're busy making other plans.", author: 'John Lennon' },
  { content: 'The way to get started is to quit talking and begin doing.', author: 'Walt Disney' },
  { content: "Don't let yesterday take up too much of today.", author: 'Will Rogers' },
  { content: 'It does not matter how slowly you go as long as you do not stop.', author: 'Confucius' },
  { content: 'Success is not final, failure is not fatal: it is the courage to continue that counts.', author: 'Winston Churchill' },
  { content: 'The only limit to our realization of tomorrow is our doubts of today.', author: 'Franklin D. Roosevelt' },
  { content: 'Everything you can imagine is real.', author: 'Pablo Picasso' },
  { content: 'It always seems impossible until it is done.', author: 'Nelson Mandela' }
]

function randomQuote(excludeIndex: number | null): number {
  if (quotes.length <= 1) return 0
  let next = Math.floor(Math.random() * quotes.length)
  while (next === excludeIndex) {
    next = Math.floor(Math.random() * quotes.length)
  }
  return next
}

const currentIndex = ref(randomQuote(null))
const current = ref<Quote>(quotes[currentIndex.value])

function refresh() {
  currentIndex.value = randomQuote(currentIndex.value)
  current.value = quotes[currentIndex.value]
}
</script>

<template>
  <DashboardSection :id="props.sectionId" :title="props.title">
    <template #controls>
      <button class="quote-refresh" type="button" @click="refresh">↻ 새로운 명언</button>
    </template>

    <div class="quote-container">
      <div class="quote-text">&ldquo;{{ current.content }}&rdquo;</div>
      <div class="quote-author">— {{ current.author }}</div>
    </div>
  </DashboardSection>
</template>

<style scoped>
.quote-container {
  padding: 12px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  border-left: 3px solid var(--accent-yellow);
}

.quote-text {
  color: var(--text-primary);
  font-size: 12px;
  line-height: 1.5;
  font-style: italic;
  margin-bottom: 8px;
}

.quote-author {
  color: var(--accent-yellow);
  font-size: 11px;
  text-align: right;
}

.quote-refresh {
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-secondary);
  font-size: 11px;
  cursor: pointer;
  padding: 4px 10px;
}

.quote-refresh:hover {
  color: var(--accent-cyan);
  border-color: var(--accent-cyan);
}
</style>
