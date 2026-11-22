// M6: §4에 있었지만 빠졌던 기능 — 게시물 ⋯ 메뉴(보관·고정·좋아요 수 숨기기·댓글 끄기), 보관함, 최근 삭제됨
import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { F, open, other, sheet, useFeatureSetup } from './feature-setup'
import { call, createPost, solidPng } from './helpers'

useFeatureSetup()

const menu = async (page: Page, item: string | RegExp) => {
  await page.getByRole('button', { name: /옵션/ }).filter({ visible: true }).first().click()
  await sheet(page).getByRole('button', { name: item }).click()
}

test('게시물 메뉴: 프로필에 고정 → 보관 → 보관함 → 프로필에 표시 → 좋아요 수 숨기기·댓글 끄기', async ({ page }) => {
  const sc = await createPost(F.asA, 'E2E 보관 고정 게시물')
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/posts/${sc}`).catch(() => undefined))
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/posts/${sc}/pin`).catch(() => undefined))

  await open(page, F.A, `/p/${sc}`)
  await menu(page, '프로필에 고정')
  await expect(page.getByText('프로필에 고정했습니다')).toBeVisible()
  await page.goto(`/${F.A}`)
  const tile = page.locator(`a[href="/p/${sc}"]`).first()
  await expect(tile.getByLabel('고정됨')).toBeVisible()

  await page.goto(`/p/${sc}`)
  await menu(page, '보관')
  await expect(page.getByText(/게시물을 보관했습니다/)).toBeVisible()
  await page.goto(`/${F.A}`)
  await expect(page.locator('a[href^="/p/"]').first()).toBeVisible()
  await expect(page.locator(`a[href="/p/${sc}"]`)).toHaveCount(0)
  const res = await F.asB.fetch(`/api/posts/${sc}`)
  expect(res.status(), '다른 사람에게는 안 보임').toBe(404)

  await page.goto('/archive/posts')
  await page.locator(`a[href="/p/${sc}"]`).first().click()
  await menu(page, '프로필에 표시')
  await expect(page.getByText('프로필에 다시 표시합니다')).toBeVisible()
  expect((await F.asB.fetch(`/api/posts/${sc}`)).status()).toBe(200)

  await menu(page, '좋아요 수 숨기기')
  await expect(page.getByText('다른 사람에게 좋아요 수를 숨깁니다')).toBeVisible()
  await menu(page, '댓글 기능 해제')
  await expect(page.getByText('댓글 기능을 해제했습니다')).toBeVisible()
  await expect(page.getByText('댓글 기능이 해제되었습니다.')).toBeVisible()
  const post = await call<{ hideLikeCount: boolean; commentsDisabled: boolean }>(F.asA, 'GET', `/api/posts/${sc}`)
  expect(post).toMatchObject({ hideLikeCount: true, commentsDisabled: true })
})

test('최근 삭제됨: 지운 게시물을 복원하면 프로필로 돌아온다', async ({ page }) => {
  const sc = await createPost(F.asA, 'E2E 복원 게시물')
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/posts/${sc}`).catch(() => undefined))
  await open(page, F.A, `/p/${sc}`)
  await menu(page, '삭제')
  await page.getByRole('dialog').getByRole('button', { name: '삭제', exact: true }).click()
  await expect(page.getByText('게시물을 삭제했습니다')).toBeVisible()

  await page.goto('/accounts/settings')
  await page.getByRole('link', { name: /최근 삭제됨/ }).click()
  await expect(page).toHaveURL(/\/accounts\/recently-deleted/)
  await page.getByRole('button', { name: '삭제한 게시물' }).first().click()
  await sheet(page).getByRole('button', { name: '복원' }).click()
  await expect(page.getByText('게시물을 복원했습니다')).toBeVisible()
  await page.goto(`/${F.A}`)
  await expect(page.locator(`a[href="/p/${sc}"]`).first()).toBeVisible()
})

// ------------------------------------------------------------------ ② DM

test('DM: 번역 보기 → 메시지 고정 → 음성 메시지 → 1회 보기 사진(받는 사람이 한 번만) → 사라지는 메시지 → 채팅방 고정', async ({ page, browser }) => {
  test.setTimeout(120_000)
  const conv = await call<{ id: number }>(F.asA, 'POST', '/api/direct/conversations', { usernames: [F.B] })
  F.cleanups.push(() => call(F.asA, 'PUT', `/api/direct/conversations/${conv.id}/vanish`, { on: false }).catch(() => undefined))
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/direct/conversations/${conv.id}/pin`).catch(() => undefined))
  await open(page, F.A, `/direct/t/${conv.id}`)
  const input = page.getByLabel('메시지 입력')
  const bubble = (text: string) => page.locator('[id^="msg-"]').filter({ has: page.getByText(text, { exact: true }) }).last()

  // 번역·고정
  await input.fill('안녕하세요 고마워요')
  await input.press('Enter')
  await bubble('안녕하세요 고마워요').hover()
  await bubble('안녕하세요 고마워요').getByRole('button', { name: '메시지 더 보기' }).click()
  await sheet(page).getByRole('button', { name: '번역 보기' }).click()
  await expect(page.getByText('hello thank you')).toBeVisible()
  await bubble('안녕하세요 고마워요').getByRole('button', { name: '메시지 더 보기' }).click()
  await sheet(page).getByRole('button', { name: '고정', exact: true }).click()
  await expect(page.getByRole('button', { name: '고정된 메시지' })).toContainText('안녕하세요 고마워요')

  // 음성 메시지 (Chrome 가짜 마이크)
  await page.getByRole('button', { name: '음성 메시지 녹음' }).click()
  await expect(page.getByText(/녹음 중/)).toBeVisible()
  await page.waitForTimeout(1500)
  await page.getByRole('button', { name: '녹음 보내기' }).click()
  await expect(page.getByRole('button', { name: '음성 메시지 재생' }).last()).toBeVisible()

  // 1회 보기 사진
  await page.getByLabel('1회 보기 사진 파일').setInputFiles(solidPng('once.png', [200, 60, 60]))
  await expect(page.getByText('1회 보기 사진 · 보냄').last()).toBeVisible()
  const b = await other(browser, F.B, `/direct/t/${conv.id}`)
  await b.page.getByRole('button', { name: '사진 보기' }).last().click()
  await expect(b.page.getByRole('dialog', { name: '1회 보기 사진' }).locator('img')).toBeVisible()
  await b.page.keyboard.press('Escape')
  await expect(b.page.getByRole('button', { name: '사진 보기' })).toHaveCount(0)
  await expect(page.getByText('열어봄').last()).toBeVisible()

  // 사라지는 메시지: B가 켜면 A 화면에도 표시 → 보낸 메시지는 끄면 사라진다
  await b.page.getByRole('button', { name: '대화 정보' }).click()
  await sheet(b.page).getByRole('button', { name: '사라지는 메시지 켜기' }).click()
  await expect(page.getByText(/사라지는 메시지 모드/)).toBeVisible()
  await input.fill('E2E 사라질 메시지')
  await input.press('Enter')
  await expect(bubble('E2E 사라질 메시지')).toBeVisible()
  await page.getByRole('button', { name: '대화 정보' }).click()
  await sheet(page).getByRole('button', { name: '사라지는 메시지 끄기' }).click()
  await expect(page.getByText('E2E 사라질 메시지', { exact: true })).toHaveCount(0)
  await expect(b.page.getByText('E2E 사라질 메시지', { exact: true })).toHaveCount(0)
  await b.close()

  // 채팅방 고정
  await page.getByRole('button', { name: '대화 정보' }).click()
  await sheet(page).getByRole('button', { name: '채팅방 고정' }).click()
  await expect(page.getByText('채팅방을 고정했습니다')).toBeVisible()
  const folder = (await call<{ folder: string }>(F.asA, 'GET', `/api/direct/conversations/${conv.id}`)).folder
  const inbox = await call<{ id: number; pinned: boolean }[]>(F.asA, 'GET', `/api/direct/inbox?folder=${folder}`)
  expect(inbox[0]).toMatchObject({ id: conv.id, pinned: true })
})

// ------------------------------------------------------------------ ③ 검색·음악·스토리

test('검색·음악: 오디오 검색 → 오디오 페이지(미리 듣기) → 이 오디오 사용 → 키워드 검색 → 게시물에 붙은 음악', async ({ page }) => {
  const word = `kw${Date.now().toString(36)}`
  const [track] = await call<{ id: number; title: string }[]>(F.asA, 'GET', '/api/audio?q=한강')
  const sc = await createPost(F.asA, `E2E ${word} 키워드 게시물`, { audioId: track.id })
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/posts/${sc}`).catch(() => undefined))

  await open(page, F.A, '/explore/search')
  await page.getByLabel('검색어').fill('한강 산책')
  await page.getByRole('tab', { name: '오디오' }).click()
  await page.getByText('한강 산책', { exact: true }).first().click()
  await expect(page).toHaveURL(new RegExp(`/audio/${track.id}$`))
  await expect(page.getByRole('heading', { name: '한강 산책' })).toBeVisible()
  await page.getByRole('button', { name: '한강 산책 미리 듣기' }).click()
  await expect(page.getByRole('button', { name: '한강 산책 멈춤' })).toBeVisible()
  await page.getByRole('link', { name: /이 오디오 사용/ }).click()
  await page.waitForURL((u) => u.pathname === '/create/reel' && u.searchParams.get('audio') === String(track.id))

  await page.goto('/explore/search')
  await page.getByLabel('검색어').fill(word)
  await page.getByRole('button', { name: new RegExp(`${word}.*키워드로 게시물 보기`) }).click()
  await page.locator(`a[href="/p/${sc}"]`).first().click()
  await expect(page.getByRole('link', { name: /오디오: 새벽세시 · 한강 산책/ }).filter({ visible: true }).first()).toBeVisible()
})

test('스토리: 그리기 → 음악 추가 → 공유 → 보는 사람 화면에 음악 표시', async ({ page }) => {
  await open(page, F.A, '/create/story')
  await page.locator('input[type="file"]').setInputFiles(solidPng('draw.png', [40, 40, 40], 540, 960))
  await page.getByRole('button', { name: '그리기', exact: true }).click()
  const canvas = page.getByLabel('스토리에 그리기')
  const box = (await canvas.boundingBox())!
  await page.mouse.move(box.x + 40, box.y + 60)
  await page.mouse.down()
  await page.mouse.move(box.x + 160, box.y + 200, { steps: 8 })
  await page.mouse.up()
  await expect(page.getByRole('button', { name: /되돌리기/ })).toBeEnabled()
  // 그린 자리가 펜 색(노랑)으로 칠해졌는지
  const yellow = await canvas.evaluate((c: HTMLCanvasElement, at) => {
    const r = c.getBoundingClientRect()
    const d = c.getContext('2d')!.getImageData(((at.x) / r.width) * c.width, ((at.y) / r.height) * c.height, 1, 1).data
    return d[0] > 200 && d[1] > 180 && d[2] < 80
  }, { x: 100, y: 130 })
  expect(yellow, '그린 선이 사진에 들어감').toBe(true)
  await page.getByRole('button', { name: '완료' }).click()

  await page.getByRole('button', { name: '음악 추가' }).click()
  const picker = page.getByRole('dialog', { name: '음악' })
  await picker.getByLabel('음악 검색').fill('Neon')
  await picker.getByRole('button', { name: /Neon Seoul/ }).first().click()
  await expect(page.getByText('KITE · Neon Seoul')).toBeVisible()
  await page.getByRole('button', { name: '공유', exact: true }).click()
  await expect(page.getByText(/스토리를 공유했습니다/)).toBeVisible()
  const mine = await call<{ id: number; audio: { title: string } | null }[]>(F.asA, 'GET', `/api/stories/users/${F.A}`)
  const story = mine[mine.length - 1]
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/stories/${story.id}`).catch(() => undefined))
  expect(story.audio?.title).toBe('Neon Seoul')
})
