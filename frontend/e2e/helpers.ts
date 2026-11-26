// E2E 공용 도우미. 가짜 계정을 고르고, Lab 계정 전환으로 로그인하고, 브라우저 안에서 API를 부른다.
import { expect, request as pwRequest } from '@playwright/test'
import type { APIRequestContext, Browser, Page } from '@playwright/test'
import { deflateSync } from 'node:zlib'
import { execFileSync } from 'node:child_process'
import { appendFileSync, mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

export const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:5173'
export const PASSWORD = 'yang1234'
/** 속도 점검용: 지정하면 watch()가 API 응답 시간을 이 파일(JSON 줄)에 적는다 */
const TIMINGS = process.env.E2E_TIMINGS

/** 세션 쿠키 + CSRF를 갖춘 API 호출기 (브라우저 없이 준비·정리용) */
export async function apiAs(username: string): Promise<APIRequestContext> {
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  await ctx.get('/api/auth/csrf')
  await call(ctx, 'POST', `/api/lab/impersonate/${username}`)
  return ctx
}

export async function call<T = unknown>(ctx: APIRequestContext, method: string, url: string, data?: unknown): Promise<T> {
  const state = await ctx.storageState()
  const xsrf = state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const res = await ctx.fetch(url, {
    method,
    data,
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(xsrf), Accept: 'application/json' },
  })
  if (!res.ok()) throw new Error(`${method} ${url} → ${res.status()} ${await res.text()}`)
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

interface LabUser {
  user: { username: string; isPrivate: boolean }
  accountType: string
  followersCount: number
  followingCount: number
  fake: boolean
}

interface ProfileRes {
  relationship: string
  followsMe: boolean
  postsCount: number
  canViewContent: boolean
}

/** 시나리오에 쓸 가짜 사용자들. A·B는 공개 개인 계정, C는 A가 팔로우하지 않는 비공개 계정 */
export async function pickUsers(): Promise<{ a: string; b: string; c: string }> {
  const lab = await apiAs(await anyUser())
  const users = await call<LabUser[]>(lab, 'GET', '/api/lab/users?limit=300')
  const personal = users.filter((u) => u.fake && u.accountType === 'PERSONAL' && !u.user.isPrivate)
  // 10대 계정은 DM 등에 보호 규칙이 걸리므로 A·B에서 뺀다
  const adults: string[] = []
  for (const u of personal.filter((x) => x.followingCount >= 10)) {
    const ctx = await apiAs(u.user.username)
    const me = await call<{ teen: boolean }>(ctx, 'GET', '/api/auth/me')
    await ctx.dispose()
    if (!me.teen) adults.push(u.user.username)
    if (adults.length === 2) break
  }
  const [a, b] = adults
  const asA = await apiAs(a)
  let c: string | undefined
  for (const u of users.filter((x) => x.fake && x.user.isPrivate)) {
    const p = await call<ProfileRes>(asA, 'GET', `/api/users/${u.user.username}`)
    if (p.relationship === 'NONE' && p.postsCount > 0) {
      c = u.user.username
      break
    }
  }
  await lab.dispose()
  await asA.dispose()
  if (!c) throw new Error('A가 팔로우하지 않는 비공개 계정이 없습니다')
  return { a, b, c }
}

async function anyUser(): Promise<string> {
  // 계정 목록은 로그인 없이도 열려 있지 않으므로, 목록 첫 사용자를 얻기 위해 Lab 상태의 아무 계정이 필요하다
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.get('/api/lab/users?limit=1')
  const list = res.ok() ? ((await res.json()) as LabUser[]) : []
  await ctx.dispose()
  if (list.length) return list[0].user.username
  throw new Error('개발 서버가 떠 있지 않거나 시드가 없습니다 (/api/lab/users)')
}

/** 화면에서 Lab 계정 전환 (비밀번호 없이) 후 홈으로 */
export async function loginAs(page: Page, username: string, to = '/') {
  await page.goto('/accounts/login')
  await page.evaluate(async (u) => {
    await fetch('/api/auth/csrf')
    const xsrf = decodeURIComponent(document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))?.split('=')[1] ?? '')
    const res = await fetch(`/api/lab/impersonate/${u}`, { method: 'POST', headers: { 'X-XSRF-TOKEN': xsrf } })
    if (!res.ok) throw new Error(`impersonate ${res.status}`)
  }, username)
  await page.goto(to)
}

export async function newUserPage(browser: Browser, username: string, to = '/') {
  const context = await browser.newContext()
  const page = await context.newPage()
  await loginAs(page, username, to)
  return page
}

/** 가상 시계 (Lab 시간 가속) */
export async function addMinutes(minutes: number) {
  const ctx = await apiAs(await anyUser())
  await call(ctx, 'POST', '/api/lab/clock', { addMinutes: minutes })
  await ctx.dispose()
}

export async function resetClock() {
  const ctx = await apiAs(await anyUser())
  await call(ctx, 'POST', '/api/lab/clock', { reset: true })
  await ctx.dispose()
}

/** 단색 PNG를 만들어 파일로 (업로드용). 외부 라이브러리 없이 zlib로 */
export function solidPng(name: string, rgb: [number, number, number], w = 120, h = 120): string {
  const row = Buffer.alloc(1 + w * 3)
  for (let x = 0; x < w; x++) row.set(rgb, 1 + x * 3)
  const raw = Buffer.concat(Array.from({ length: h }, () => row))
  const chunk = (type: string, data: Buffer) => {
    const len = Buffer.alloc(4)
    len.writeUInt32BE(data.length)
    const td = Buffer.concat([Buffer.from(type), data])
    const crc = Buffer.alloc(4)
    crc.writeUInt32BE(crc32(td))
    return Buffer.concat([len, td, crc])
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(w, 0)
  ihdr.writeUInt32BE(h, 4)
  ihdr[8] = 8
  ihdr[9] = 2
  const png = Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ])
  const dir = path.join(import.meta.dirname, '.tmp')
  mkdirSync(dir, { recursive: true })
  const file = path.join(dir, name)
  writeFileSync(file, png)
  return file
}

function crc32(buf: Buffer): number {
  let c = ~0
  for (const b of buf) {
    c ^= b
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1))
  }
  return ~c >>> 0
}

/** 페이지에 가로 스크롤이 생겼는지 (360px 기준) */
export async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(() => {
    const doc = document.documentElement
    return { scroll: doc.scrollWidth, client: doc.clientWidth }
  })
  expect(overflow.scroll, `가로 스크롤 ${overflow.scroll} > ${overflow.client}`).toBeLessThanOrEqual(overflow.client)
}

/** 페이지의 콘솔 오류·잡히지 않은 예외·서버 오류(5xx)를 모은다. 4xx GET은 따로 (경고용) */
export interface Watch {
  errors: string[]
  warnings: string[]
}

const BENIGN = [
  /favicon/,
  /play\(\) (request|failed)/i, // 자동 재생이 막힌 영상
  /The play\(\) request was interrupted/,
  /AbortError/,
  /Download the React DevTools/,
  /net::ERR_ABORTED/, // 페이지를 떠나며 취소된 요청
]

export function watch(page: Page, label = ''): Watch {
  const w: Watch = { errors: [], warnings: [] }
  const tag = (s: string) => `${label ? `[${label}] ` : ''}${page.url().replace(BASE, '')} → ${s}`
  page.on('console', (m) => {
    if (m.type() !== 'error') return
    const text = m.text()
    if (BENIGN.some((r) => r.test(text))) return
    // 4xx·5xx 응답에 대해 브라우저가 찍는 "Failed to load resource"는 아래 response에서 따로 판단
    if (/Failed to load resource: the server responded with a status of 4\d\d/.test(text)) return
    w.errors.push(tag(`console: ${text.slice(0, 300)}`))
  })
  page.on('pageerror', (e) => w.errors.push(tag(`예외: ${e.message.slice(0, 300)}`)))
  page.on('response', (r) => {
    const url = r.url()
    if (!url.includes('/api/') && !url.includes('/media/')) return
    const s = r.status()
    if (TIMINGS && url.includes('/api/')) {
      // 속도 점검: 요청을 보낸 때부터 응답 머리가 올 때까지 (서버 처리 시간에 가깝다)
      const t = r.request().timing()
      const path = url.replace(/^https?:\/\/[^/]+/, '')
      appendFileSync(TIMINGS, JSON.stringify({ m: r.request().method(), path, s, ms: Math.round(t.responseStart) }) + '\n')
    }
    if (s >= 500) w.errors.push(tag(`${r.request().method()} ${url.replace(BASE, '')} → ${s}`))
    else if (s >= 400 && r.request().method() === 'GET' && s !== 401) w.warnings.push(tag(`GET ${url.replace(BASE, '')} → ${s}`))
  })
  return w
}

export function expectClean(w: Watch) {
  if (w.warnings.length) console.log('  경고(4xx GET):\n   ' + [...new Set(w.warnings)].join('\n   '))
  expect(w.errors, '콘솔 오류·예외·5xx').toEqual([])
}

/** 테스트 대상을 고를 때만 쓰는 읽기 전용 SQL (개발 DB 컨테이너) */
export function sql(query: string): string[][] {
  const out = execFileSync('docker', ['exec', 'yangstagram-postgres', 'psql', '-U', 'yangstagram', '-d', process.env.E2E_DB ?? 'yangstagram', '-tA', '-F', '\t', '-c', query], {
    encoding: 'utf8',
  })
  return out
    .trim()
    .split('\n')
    .filter(Boolean)
    .map((l) => l.split('\t'))
}

/** API로 사진 한 장짜리 게시물을 올린다 (준비용). 돌려주는 값은 shortcode */
export async function createPost(ctx: APIRequestContext, caption: string, extra: Record<string, unknown> = {}): Promise<string> {
  const { readFileSync } = await import('node:fs')
  const state = await ctx.storageState()
  const xsrf = decodeURIComponent(state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? '')
  const res = await ctx.post('/api/posts', {
    headers: { 'X-XSRF-TOKEN': xsrf },
    multipart: {
      files: { name: 'p.png', mimeType: 'image/png', buffer: readFileSync(solidPng(`post-${Date.now()}.png`, [90, 140, 200])) },
      data: { name: 'data.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({ caption, altTexts: [''], ...extra })) },
    },
  })
  if (!res.ok()) throw new Error(`게시물 만들기 실패 ${res.status()} ${await res.text()}`)
  return ((await res.json()) as { shortcode: string }).shortcode
}

/** API로 스토리를 올린다 (준비용). 돌려주는 값은 스토리 id */
export async function createStory(
  ctx: APIRequestContext,
  data: { audience?: 'PUBLIC' | 'CLOSE_FRIENDS'; stickers?: unknown[]; altText?: string } = {},
): Promise<number> {
  const { readFileSync } = await import('node:fs')
  const state = await ctx.storageState()
  const xsrf = decodeURIComponent(state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? '')
  const res = await ctx.post('/api/stories', {
    headers: { 'X-XSRF-TOKEN': xsrf },
    multipart: {
      file: { name: 's.png', mimeType: 'image/png', buffer: readFileSync(solidPng(`story-${Date.now()}.png`, [120, 60, 200], 180, 320)) },
      data: { name: 'd.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({ audience: 'PUBLIC', stickers: [], ...data })) },
    },
  })
  if (!res.ok()) throw new Error(`스토리 만들기 실패 ${res.status()} ${await res.text()}`)
  return ((await res.json()) as { id: number }).id
}
