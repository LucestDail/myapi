<script setup lang="ts">
/**
 * 여러 도시의 현재 시각. 서버 데이터가 필요 없다 — 브라우저 시계 + Intl 시간대 변환만으로
 * 충분하다. 1초마다 다시 그린다.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import DashboardSection from '@/components/DashboardSection.vue'

const props = defineProps<{ sectionId: string; title: string }>()

interface ClockCity {
  name: string
  timezone: string
  flag: string
}

const cities: ClockCity[] = [
  { name: '서울', timezone: 'Asia/Seoul', flag: '🇰🇷' },
  { name: '뉴욕', timezone: 'America/New_York', flag: '🇺🇸' },
  { name: '런던', timezone: 'Europe/London', flag: '🇬🇧' },
  { name: '도쿄', timezone: 'Asia/Tokyo', flag: '🇯🇵' },
  { name: '시드니', timezone: 'Australia/Sydney', flag: '🇦🇺' },
  { name: '파리', timezone: 'Europe/Paris', flag: '🇫🇷' }
]

const now = ref(new Date())
let timer: number | undefined

onMounted(() => {
  timer = window.setInterval(() => { now.value = new Date() }, 1000)
})

onBeforeUnmount(() => {
  if (timer !== undefined) clearInterval(timer)
})

const rows = computed(() =>
  cities.map(city => ({
    ...city,
    time: now.value.toLocaleTimeString('ko-KR', {
      timeZone: city.timezone, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    }),
    date: now.value.toLocaleDateString('ko-KR', {
      timeZone: city.timezone, month: 'short', day: 'numeric', weekday: 'short'
    })
  }))
)
</script>

<template>
  <DashboardSection :id="props.sectionId" :title="props.title">
    <div class="worldclock-grid">
      <div v-for="city in rows" :key="city.timezone" class="worldclock-item">
        <div class="worldclock-city">{{ city.flag }} {{ city.name }}</div>
        <div class="worldclock-time">{{ city.time }}</div>
        <div class="worldclock-date">{{ city.date }}</div>
      </div>
    </div>
  </DashboardSection>
</template>

<style scoped>
.worldclock-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}

.worldclock-item {
  padding: 8px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  text-align: center;
}

.worldclock-city {
  color: var(--text-secondary);
  font-size: 10px;
  margin-bottom: 4px;
}

.worldclock-time {
  color: var(--accent-cyan);
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.worldclock-date {
  color: var(--text-muted);
  font-size: 9px;
  margin-top: 2px;
}
</style>
