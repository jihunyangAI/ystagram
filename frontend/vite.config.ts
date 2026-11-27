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
        // 왜 막혔는지 화면 서버 출력에 남긴다 (키는 남기지 않음)
        const given = url.searchParams.get('key')
        console.log(
          `[phone-guard] 403 ${req.method} ${url.pathname} key=${given == null ? '없음' : `틀림(${given.length}자)`} ` +
            `cookie=${req.headers.cookie ? req.headers.cookie.split(/;\s*/).map((c) => c.split('=')[0]).join(',') : '없음'} ` +
            `ua=${(req.headers['user-agent'] ?? '').slice(0, 90)}`,
        )
        res.statusCode = 403
        res.setHeader('Content-Type', 'text/html; charset=utf-8')
        res.setHeader('Cache-Control', 'no-store')
        // 키를 직접 넣을 수 있는 작은 화면 (링크가 잘렸거나 다른 브라우저로 열렸을 때)
        res.end(`<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>접속 키</title><body style="font-family:system-ui,sans-serif;max-width:360px;margin:15vh auto;padding:0 16px">
<h2 style="font-size:18px">접속 키가 필요합니다</h2>
<p style="color:#666;font-size:14px">받은 링크 전체(<b>?key=</b>까지)를 그대로 열거나, 키를 아래에 붙여 넣으세요.</p>
<form method="get" action="${url.pathname}"><input name="key" autocomplete="off" autocapitalize="off" spellcheck="false"
 style="width:100%;box-sizing:border-box;padding:10px;font-size:16px;border:1px solid #ccc;border-radius:8px" placeholder="접속 키">
<button style="margin-top:10px;width:100%;padding:10px;font-size:16px;border:0;border-radius:8px;background:#1f8a70;color:#fff">들어가기</button></form>`)
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
