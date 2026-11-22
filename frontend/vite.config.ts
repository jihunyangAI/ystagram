import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// 개발 중에는 /api, /media 요청을 Spring Boot(8080)로 넘긴다.
// 브라우저 입장에서는 같은 주소(5173)라서 세션 쿠키·CSRF가 그대로 동작한다.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      // 백엔드는 127.0.0.1에서만 받는다 (localhost가 ::1로 풀리면 연결이 안 되므로 주소를 직접 쓴다)
      '/api': 'http://127.0.0.1:8080',
      '/media': 'http://127.0.0.1:8080',
      // 실시간 이벤트 (DM·알림). 세션 쿠키가 함께 넘어간다
      '/ws': { target: 'ws://127.0.0.1:8080', ws: true },
    },
  },
})
