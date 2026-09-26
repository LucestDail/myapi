import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 🔴 base 는 반드시 '/myapi/' 다.
//    앱이 게이트웨이 뒤 서브패스(/myapi/)에 서빙된다. base 를 빼면 index.html 이
//    /assets/... 를 참조하는데 실제 파일은 /myapi/assets/... 에 있어 전부 404 가 된다.
//    (waynai 가 정확히 이것 때문에 9일간 빈 화면이었다 — 2026-09-26)
//
// outDir 은 dist 로 둔다. src/main/resources/static 으로 바로 쓰면 빌드 순간
// 기존 앱이 지워져서, 새 앱이 완성될 때까지 서비스가 비는 창이 생긴다.
// 배치는 완성 후 명시적으로 옮긴다.
export default defineConfig({
  base: '/myapi/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    chunkSizeWarningLimit: 900
  },
  server: {
    port: 5190,
    proxy: {
      // 개발 중에는 실제 백엔드로 흘린다. 경로를 /myapi 로 맞춰 운영과 같은 모양을 유지한다.
      '/myapi/api': {
        target: process.env.VITE_DEV_BACKEND || 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
