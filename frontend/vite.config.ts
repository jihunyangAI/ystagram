import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig, type Plugin } from 'vite'

// 속도 점검용으로 백엔드·포트를 바꿔 띄울 수 있다 (기본은 개발 서버 8080, 5173)
const api = process.env.API_TARGET ?? 'http://127.0.0.1:8080'
const port = Number(process.env.VITE_PORT ?? 5173)

/**
 * 실제 휴대폰 확인용 (PHONE_KEY를 줄 때만): HTTPS 임시 터널(*.trycloudflare.com) 뒤에서 띄운다.
 * 이 노트북은 공인 주소를 쓰므로 터널 주소를 아는 누구나 들어올 수 있다 →
 * 처음 한 번 "?key=접속키"로 들어와야 쿠키가 생기고, 쿠키가 없으면 화면·API 모두 403.
 */
const phoneKey = process.env.PHONE_KEY

function phoneGuard(key: string): Plugin {
  const cookie = `phone_key=${key}`
  return {
    name: 'phone-guard',
    // 훅에서 바로 붙인 미들웨어는 Vite 내부 미들웨어(프록시 포함)보다 먼저 돈다
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = new URL(req.url ?? '/', 'http://local')
        if (url.searchParams.get('key') === key) {
          url.searchParams.delete('key')
          res.statusCode = 302
          res.setHeader('Set-Cookie', `${cookie}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=43200`)
          res.setHeader('Location', url.pathname + url.search)
          res.end()
          return
        }
        if ((req.headers.cookie ?? '').split(/;\s*/).includes(cookie)) return next()
        res.statusCode = 403
        res.setHeader('Content-Type', 'text/plain; charset=utf-8')
        res.end('접속 키가 필요합니다')
      })
    },
  }
}

// 개발 중에는 /api, /media 요청을 Spring Boot(8080)로 넘긴다.
// 브라우저 입장에서는 같은 주소(5173)라서 세션 쿠키·CSRF가 그대로 동작한다.
export default defineConfig({
  plugins: [react(), tailwindcss(), ...(phoneKey ? [phoneGuard(phoneKey)] : [])],
  server: {
    port,
    strictPort: true,
    // 휴대폰 확인: 터널 주소로 오는 요청 허용, HMR은 https(443)로
    ...(phoneKey ? { allowedHosts: ['.trycloudflare.com'], hmr: { clientPort: 443, protocol: 'wss' } } : {}),
    proxy: {
      // 백엔드는 127.0.0.1에서만 받는다 (localhost가 ::1로 풀리면 연결이 안 되므로 주소를 직접 쓴다)
      '/api': api,
      '/media': api,
      // 실시간 이벤트 (DM·알림). 세션 쿠키가 함께 넘어간다.
      // 백엔드는 localhost 출처만 받으므로, 터널 뒤에서는 이 화면 서버의 출처로 바꿔 넘긴다 (접속 키로 이미 걸러진 상태)
      '/ws': {
        target: api.replace(/^http/, 'ws'),
        ws: true,
        ...(phoneKey ? { headers: { origin: `http://127.0.0.1:${port}` } } : {}),
      },
    },
  },
})
