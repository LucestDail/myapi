<script setup lang="ts">
import { computed } from 'vue'
import { useDashboardStore } from '@/stores/dashboard'

/**
 * 유튜브 임베드.
 *
 * 🔴 src 를 computed 로 둔 것이 핵심이다. 종전 바닐라 구현은 설정 저장 후
 *    `updateYouTubePlayer(url)` 을 손으로 불러야 iframe 이 바뀌었고, 그 호출 조건이
 *    "방금 보낸 값 !== 서버가 돌려준 값" 이라 **절대 참이 되지 않아** 플레이어가
 *    한 번도 갱신되지 않았다. 스토어의 config 가 바뀌면 여기가 알아서 다시 그린다 —
 *    갱신을 호출로 관리하지 않으면 빠뜨릴 수도 없다.
 */
const dashboard = useDashboardStore()

/** watch?v= · youtu.be/ · /embed/ 세 형태를 받는다. */
function extractVideoId(url: string | undefined | null): string | null {
  if (!url) return null
  const m = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([^&\s?/]+)/)
  return m ? m[1] : null
}

const videoId = computed(() => extractVideoId(dashboard.config?.youtubeUrl))

const embedSrc = computed(() =>
  videoId.value ? `https://www.youtube.com/embed/${videoId.value}?autoplay=1&mute=0` : null
)
</script>

<template>
  <section v-if="embedSrc" class="media">
    <div class="media-frame">
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
.media {
  padding: 12px 16px 0;
}

.media-frame {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  max-height: 42vh;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
}

.media-frame iframe {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border: 0;
  display: block;
}
</style>
