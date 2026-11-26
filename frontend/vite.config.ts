import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// 속도 점검용으로 백엔드·포트를 바꿔 띄울 수 있다 (기본은 개발 서버 8080, 5173)
const api = process.env.API_TARGET ?? 'http://127.0.0.1:8080'

// 개발 중에는 /api, /media 요청을 Spring Boot(8080)로 넘긴다.
// 브라우저 입장에서는 같은 주소(5173)라서 세션 쿠키·CSRF가 그대로 동작한다.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: Number(process.env.VITE_PORT ?? 5173),
    strictPort: true,
    proxy: {
      // 백엔드는 127.0.0.1에서만 받는다 (localhost가 ::1로 풀리면 연결이 안 되므로 주소를 직접 쓴다)
      '/api': api,
      '/media': api,
      // 실시간 이벤트 (DM·알림). 세션 쿠키가 함께 넘어간다
      '/ws': { target: api.replace(/^http/, 'ws'), ws: true },
    },
  },
})
