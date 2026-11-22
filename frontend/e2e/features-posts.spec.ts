// 전체 기능 점검 2단계: 기능마다 화면에서 실제로 써 본다 (콘솔 오류·예외·5xx도 함께 확인).
// 만든 것은 끝나면 지운다. 가짜 계정은 조건으로 고른다 (A·B 성인 공개 계정).
import { expect, test } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'
import { apiAs, call, createPost, expectClean, loginAs, pickUsers, solidPng, sql, watch } from './helpers'
import type { Watch } from './helpers'

let A: string, B: string, C: string
let asA: APIRequestContext, asB: APIRequestContext
const cleanups: (() => Promise<unknown>)[] = []
let w: Watch

test.describe.configure({ mode: 'serial' })

test.beforeAll(async () => {
  ;({ a: A, b: B, c: C } = await pickUsers())
  asA = await apiAs(A)
  asB = await apiAs(B)
  // A가 B를 팔로우 (스토리·친한 친구 등)
  await call(asA, 'PUT', `/api/users/${B}/follow`).catch(() => undefined)
  console.log(`  A=${A} B=${B} C=${C}`)
})

test.afterAll(async () => {
  for (const f of cleanups.reverse()) await f().catch((e) => console.warn('  정리 실패:', (e as Error).message))
  await asA.dispose()
  await asB.dispose()
})

test.beforeEach(async ({ page }) => {
  w = watch(page)
})

test.afterEach(async () => {
  expectClean(w)
})

async function open(page: Page, user: string, path: string) {
  await loginAs(page, user, path)
  await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => undefined)
}

const sheet = (page: Page) => page.getByRole('dialog')

// ------------------------------------------------------------------ 게시물·상호작용

test('게시물: 댓글 → 답글 → 댓글 좋아요 → 고정 → 수정 → 삭제', async ({ page }) => {
  const sc = await createPost(asA, 'E2E 댓글 시험')
  cleanups.push(() => call(asA, 'DELETE', `/api/posts/${sc}`))
  await call(asB, 'POST', `/api/posts/${sc}/comments`, { body: 'B의 댓글입니다' })

  await open(page, A, `/p/${sc}`)
  const bComment = page.locator('li, div').filter({ hasText: /^.*B의 댓글입니다/ }).last()
  await expect(page.getByText('B의 댓글입니다')).toBeVisible()

  // 답글
  await page.getByRole('button', { name: '답글 달기' }).first().click()
  const input = page.getByRole('textbox', { name: '답글 달기' })
  await expect(input).toBeVisible()
  await input.fill('A의 답글')
  await page.getByRole('button', { name: '게시', exact: true }).click()
  await expect(page.getByText('A의 답글')).toBeVisible()

  // 댓글 좋아요
  await page.getByRole('button', { name: '댓글 좋아요' }).first().click()
  await expect(page.getByRole('button', { name: '댓글 좋아요 취소' }).first()).toBeVisible()

  // 고정 (게시물 주인)
  await page.getByRole('button', { name: '고정', exact: true }).first().click()
  await expect(page.getByText(/고정됨/).first()).toBeVisible()
  void bComment

  // 내 댓글 수정·삭제
  await page.getByRole('textbox', { name: '댓글 달기' }).fill('A의 댓글 처음')
  await page.getByRole('button', { name: '게시', exact: true }).click()
  await expect(page.getByText('A의 댓글 처음')).toBeVisible()
  const mine = page.locator('div', { has: page.getByText('A의 댓글 처음') }).filter({ has: page.getByRole('button', { name: '수정' }) }).last()
  await mine.getByRole('button', { name: '수정', exact: true }).click()
  const edit = page.getByRole('textbox', { name: '댓글 수정' })
  await edit.fill('A의 댓글 고침')
  await edit.press('Enter') // Enter로 저장
  await expect(edit).toHaveCount(0)
  await expect(page.getByText('A의 댓글 고침')).toBeVisible()
  await expect(page.getByText('수정됨').first()).toBeVisible()
  const mine2 = page.locator('div', { has: page.getByText('A의 댓글 고침') }).filter({ has: page.getByRole('button', { name: /삭제/ }) }).last()
  await mine2.getByRole('button', { name: /삭제/ }).click()
  const confirm = sheet(page).getByRole('button', { name: '삭제' })
  if (await confirm.isVisible().catch(() => false)) await confirm.click()
  await expect(page.getByText('A의 댓글 고침')).toHaveCount(0)
})

test('게시물: 더블탭 좋아요, 좋아요 목록, 저장 → 컬렉션, 리포스트, DM 공유', async ({ page }) => {
  const sc = await createPost(asB, 'E2E B의 게시물')
  cleanups.push(() => call(asB, 'DELETE', `/api/posts/${sc}`))
  await open(page, A, `/p/${sc}`)

  // 더블탭(더블클릭) 좋아요
  await page.locator('article img[src*="/media/"]').first().dblclick()
  await expect(page.getByRole('button', { name: '좋아요 취소' }).first()).toBeVisible()
  // 좋아요 목록
  await page.getByText(/좋아요 \d+개|님이 좋아합니다|좋아합니다/).first().click()
  await expect(sheet(page)).toBeVisible()
  await expect(sheet(page).getByText(A).first()).toBeVisible()
  await page.keyboard.press('Escape')

  // 저장 → 새 컬렉션
  await page.getByRole('button', { name: '저장', exact: true }).first().click()
  await expect(page.getByRole('button', { name: '저장 취소' }).first()).toBeVisible()
  const colName = `E2E컬렉션${Date.now() % 1000}`
  const newCol = page.getByRole('textbox', { name: '새 컬렉션 이름' })
  if (!(await newCol.isVisible().catch(() => false))) await page.getByText(/컬렉션에 저장|새 컬렉션/).first().click()
  await newCol.fill(colName)
  await newCol.press('Enter')
  await expect(page.getByText(colName).first()).toBeVisible()
  const cols = await call<{ collections: { id: number; name: string }[] } | { id: number; name: string }[]>(asA, 'GET', '/api/me/saved')
  const list = Array.isArray(cols) ? cols : cols.collections
  const col = list.find((c) => c.name === colName)
  expect(col, '컬렉션이 만들어짐').toBeTruthy()
  cleanups.push(() => call(asA, 'DELETE', `/api/me/collections/${col!.id}`))
  await page.keyboard.press('Escape')

  // 리포스트
  await page.getByRole('button', { name: '리포스트', exact: true }).first().click()
  const repostConfirm = sheet(page).getByRole('button', { name: /리포스트/ }).last()
  if (await repostConfirm.isVisible().catch(() => false)) await repostConfirm.click()
  await expect(page.getByRole('button', { name: '리포스트 취소' }).first()).toBeVisible()
  cleanups.push(() => call(asA, 'DELETE', `/api/posts/${sc}/repost`))

  // DM으로 공유
  await page.getByRole('button', { name: '공유', exact: true }).first().click()
  await sheet(page).getByRole('textbox', { name: '받는 사람 검색' }).fill(B)
  await sheet(page).getByText(B, { exact: true }).first().click()
  await sheet(page).getByRole('button', { name: '보내기', exact: true }).click()
  await expect(page.getByText(/보냈습니다/)).toBeVisible()
})

test('게시물: 수정(캡션) → 좋아요 수 숨기기·댓글 끄기 → 신고 → 삭제', async ({ page }) => {
  const sc = await createPost(asA, 'E2E 수정 전')
  await open(page, A, `/p/${sc}`)
  await page.getByRole('button', { name: /옵션/ }).first().click()
  await sheet(page).getByRole('button', { name: '수정', exact: true }).click()
  const box = sheet(page).getByRole('textbox', { name: '문구' })
  await box.fill('E2E 수정 후 #고침')
  await sheet(page).getByRole('button', { name: /완료|저장/ }).click()
  await expect(page.getByText('E2E 수정 후')).toBeVisible()

  await call(asA, 'PATCH', `/api/posts/${sc}`, { hideLikeCount: true, commentsDisabled: true })
  await page.reload()
  await expect(page.getByText('댓글 기능이 해제되었습니다.')).toBeVisible()

  // B가 신고
  const pageB = await (await page.context().browser()!.newContext()).newPage()
  const wb = watch(pageB, 'B')
  await loginAs(pageB, B, `/p/${sc}`)
  await pageB.getByRole('button', { name: /옵션/ }).first().click()
  await sheet(pageB).getByRole('button', { name: '신고', exact: true }).click()
  await sheet(pageB).getByRole('button').filter({ hasText: /스팸/ }).first().click()
  const submit = sheet(pageB).getByRole('button', { name: /신고하기|제출|보내기/ })
  if (await submit.isVisible().catch(() => false)) await submit.click()
  await expect(pageB.getByText(/신고/).last()).toBeVisible()
  await pageB.goto('/accounts/reports')
  await expect(pageB.getByText(/스팸/).first()).toBeVisible()
  expectClean(wb)
  await pageB.context().close()

  // 삭제
  await page.getByRole('button', { name: /옵션/ }).first().click()
  await sheet(page).getByRole('button', { name: '삭제', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '삭제', exact: true }).click()
  await expect(page).not.toHaveURL(new RegExp(sc))
  const after = await asA.fetch(`/api/posts/${sc}`)
  expect(after.status()).toBe(404)
})

test('게시물: 예약 게시 → 예약된 콘텐츠 → 지금 게시', async ({ page }) => {
  const at = new Date(Date.now() + 3 * 3600_000).toISOString()
  const { readFileSync } = await import('node:fs')
  const state = await asA.storageState()
  const xsrf = decodeURIComponent(state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? '')
  const res = await asA.post('/api/posts/scheduled', {
    headers: { 'X-XSRF-TOKEN': xsrf },
    multipart: {
      files: { name: 'p.png', mimeType: 'image/png', buffer: readFileSync(solidPng('sched.png', [200, 100, 50])) },
      data: { name: 'd.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({ caption: 'E2E 예약 게시물', altTexts: [''], scheduledAt: at })) },
    },
  })
  expect(res.ok(), await res.text()).toBe(true)
  // 실패해도 나중에 저절로 게시되지 않도록 남은 예약은 지운다
  cleanups.push(async () => {
    for (const [id] of sql(`select s.id from scheduled_posts s join users u on u.id = s.author_id where u.username = '${A}' and s.caption_start like 'E2E 예약%' and s.published_at is null`))
      await call(asA, 'DELETE', `/api/me/scheduled/${id}`)
  })

  await open(page, A, '/accounts/scheduled')
  await expect(page.getByText('E2E 예약 게시물').first()).toBeVisible()
  await page.getByRole('button', { name: '예약 옵션' }).filter({ hasText: 'E2E 예약 게시물' }).first().click()
  await sheet(page).getByRole('button', { name: '지금 게시' }).click()
  await expect
    .poll(() => sql(`select count(*) from posts p join users u on u.id = p.author_id where u.username = '${A}' and p.caption = 'E2E 예약 게시물' and p.deleted_at is null`)[0][0])
    .toBe('1')
  // 정리: 방금 게시된 글들 삭제
  for (const [s] of sql(`select p.shortcode from posts p join users u on u.id = p.author_id where u.username = '${A}' and p.caption = 'E2E 예약 게시물' and p.deleted_at is null`))
    await call(asA, 'DELETE', `/api/posts/${s}`)
})
