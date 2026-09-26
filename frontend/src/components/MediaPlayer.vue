<script setup lang="ts">
import { computed } from 'vue'
import { useDashboardStore } from '@/stores/dashboard'

/**
 * 유튜브 임베드 — 좌측 컬럼 전체.
 *
 * 🔴 2026-09-26 두 번째 정정. 처음엔 `aspect-ratio:16/9` + `max-height:42vh` 를
 * 임의로 넣어서 화면 일부만 차지하는 고정 높이 박스가 됐다. 원본(`css/layout.css`
 * `.youtube-section`/`.youtube-wrapper`/`.youtube-wrapper iframe`)엔 그런 제약이
 * 전혀 없다 — 그냥 좌측 그리드 컬럼(1fr, 세로 100vh) 을 20px 패딩만 두고
 * **꽉 채운다**(`width:100%; height:100%`). 원본 클래스 구조 그대로 옮긴다.
 *
 * 그리고 `v-if` 로 URL 없을 때 컴포넌트 자체를 안 그리던 것도 고쳤다. 원본은
 * 구조(section/wrapper/iframe)를 항상 유지하고 `src` 만 비운다 — 안 그러면
 * 좌측 그리드 컬럼이 통째로 사라져 그리드 행 높이 계산이 깨질 수 있다.
 */
const dashboard = useDashboardStore()

function extractVideoId(url: string | undefined | null): string | null {
  if (!url) return null
  const m = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([^&\s?/]+)/)
  return m ? m[1] : null
}

const videoId = computed(() => extractVideoId(dashboard.config?.youtubeUrl))

const embedSrc = computed(() =>
  videoId.value ? `https://www.youtube.com/embed/${videoId.value}?autoplay=1&mute=0` : ''
)
</script>

<template>
  <section class="youtube-section">
    <div class="youtube-wrapper">
      <iframe
        :key="embedSrc"
        :src="embedSrc"
        title="YouTube"
        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
        allowfullscreen
      ></iframe>
    </div>
  </section>
</template>

<style scoped>
.youtube-section {
  background: var(--bg-primary);
  display: flex;
  flex-direction: column;
  position: relative;
  min-height: 0;
}

.youtube-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  min-height: 0;
}

.youtube-wrapper iframe {
  width: 100%;
  height: 100%;
  border: none;
  border-radius: 8px;
}

@media (max-width: 768px) {
  .youtube-wrapper { padding: 10px; }
}

@media (max-width: 480px) {
  .youtube-wrapper { padding: 8px; }
}
</style>
