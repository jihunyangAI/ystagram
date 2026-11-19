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
      '/api': 'http://localhost:8080',
      '/media': 'http://localhost:8080',
    },
  },
})
