// M6: §4에 있었지만 빠졌던 기능 — 게시물 ⋯ 메뉴(보관·고정·좋아요 수 숨기기·댓글 끄기), 보관함, 최근 삭제됨
import { expect, request as pwRequest, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import path from 'node:path'
import { F, open, other, sheet, useFeatureSetup } from './feature-setup'
import { BASE, apiAs, call, createPost, solidPng, sql } from './helpers'

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
  await page.locator('input[type="file"]:not([multiple])').setInputFiles(solidPng('draw.png', [40, 40, 40], 540, 960))
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

// ------------------------------------------------------------------ ④-a 계정 센터

test('계정 센터: 비밀번호 재설정(코드) → 로그인 활동에서 다른 기기 로그아웃 → 계정 추가·전환 → 비활성화 → 다시 로그인하면 복구', async ({ page, browser }) => {
  test.setTimeout(150_000)
  // 공용 가짜 계정의 비밀번호를 바꾸지 않도록 새 계정을 만든다 (끝나면 삭제 요청)
  const name = `e2e_ac${Date.now().toString(36)}`
  const email = `${name}@example.com`
  const api = await pwRequest.newContext({ baseURL: BASE })
  await api.get('/api/auth/csrf')
  await call(api, 'POST', '/api/auth/signup/code', { email })
  const labCode = async (target: string) =>
    ((await call<{ target: string; username: string | null; code: string }[]>(api, 'GET', '/api/lab/codes')).find(
      (c) => c.target === target || c.username === target,
    )!).code
  await call(api, 'POST', '/api/auth/signup', {
    email, fullName: '계정 센터', username: name, password: 'first-pass', birthDate: '1990-03-03', code: await labCode(email),
  })
  await api.dispose()

  // 비밀번호 재설정
  await page.goto('/accounts/login')
  await page.getByRole('link', { name: '비밀번호를 잊으셨나요?' }).click()
  await page.getByLabel('사용자 이름 또는 이메일').fill(name)
  await page.getByRole('button', { name: '코드 보내기' }).click()
  await expect(page.getByLabel('인증 코드')).toBeVisible()
  const lab = await pwRequest.newContext({ baseURL: BASE })
  const codes = (await (await lab.get('/api/lab/codes')).json()) as { username: string | null; code: string }[]
  await lab.dispose()
  await page.getByLabel('인증 코드').fill(codes.find((c) => c.username === name)!.code)
  await page.getByLabel('새 비밀번호').fill('second-pass')
  await page.getByRole('button', { name: '비밀번호 바꾸기' }).click()
  await expect(page).toHaveURL(/\/accounts\/login/)
  await page.getByPlaceholder('사용자 이름 또는 이메일').fill(name)
  await page.getByPlaceholder('비밀번호').fill('second-pass')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)

  // 다른 기기(새 브라우저)에서도 로그인 → 로그인 활동에서 끊기
  const phone = await browser.newContext()
  const phoneApi = phone.request
  await phoneApi.get(`${BASE}/api/auth/csrf`)
  const xsrf = (await phone.cookies()).find((c) => c.name === 'XSRF-TOKEN')!.value
  await phoneApi.post(`${BASE}/api/auth/login`, {
    data: { login: name, password: 'second-pass' },
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(xsrf), 'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0) Mobile Safari/604.1' },
  })
  expect((await phoneApi.get(`${BASE}/api/auth/me`)).status()).toBe(200)
  await page.goto('/accounts/settings')
  await page.getByRole('link', { name: /로그인 활동/ }).click()
  await expect(page.getByText('지금 이 기기')).toBeVisible()
  await page.locator('li').filter({ hasText: 'iOS' }).getByRole('button', { name: '로그아웃' }).click()
  await expect(page.getByText('그 기기에서 로그아웃시켰습니다')).toBeVisible()
  expect((await phoneApi.get(`${BASE}/api/auth/me`)).status(), '끊긴 기기는 로그아웃 상태').toBe(204)
  await phone.close()

  // 계정 추가 → 전환 → 다시 원래 계정으로
  await page.goto('/accounts/settings')
  await page.getByRole('button', { name: '계정 전환·추가' }).click()
  await sheet(page).getByRole('button', { name: /계정 추가/ }).click()
  await page.getByLabel('추가할 계정', { exact: true }).fill(F.B)
  await page.getByLabel('추가할 계정 비밀번호').fill('yang1234')
  await sheet(page).getByRole('button', { name: '로그인' }).click()
  await expect(page.getByText(`${F.B}(으)로 전환했습니다`)).toBeVisible()
  await page.goto('/accounts/settings')
  await page.getByRole('button', { name: '계정 전환·추가' }).click()
  await sheet(page).getByRole('button', { name: new RegExp(name) }).click()
  await expect(page.getByText(`${name}(으)로 전환했습니다`)).toBeVisible()

  // 비활성화 → 다른 사람에게 안 보임 → 다시 로그인하면 복구
  await page.goto('/accounts/remove')
  await page.getByLabel('비밀번호 확인').fill('second-pass')
  await page.getByRole('button', { name: '계속' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '비활성화' }).click()
  await expect(page).toHaveURL(/\/accounts\/login/)
  expect((await F.asA.fetch(`/api/users/${name}`)).status()).toBe(404)
  await page.getByPlaceholder('사용자 이름 또는 이메일').fill(name)
  await page.getByPlaceholder('비밀번호').fill('second-pass')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
  expect((await F.asA.fetch(`/api/users/${name}`)).status()).toBe(200)

  // 정리: 삭제 요청 (30일 뒤 영구 삭제, 그 전에도 다른 사람에게 안 보임)
  await page.goto('/accounts/remove')
  await page.getByText('계정 삭제', { exact: true }).click()
  await page.getByLabel('비밀번호 확인').fill('second-pass')
  await page.getByRole('button', { name: '계속' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '삭제 요청' }).click()
  await expect(page).toHaveURL(/\/accounts\/login/)
  expect((await F.asA.fetch(`/api/users/${name}`)).status()).toBe(404)
})

// ------------------------------------------------------------------ ④-b 안전·알림 설정

test('안전 설정: 숨겨진 단어 → 다른 사람에게 안 보이고 주인은 숨겨진 댓글로 → 댓글 허용 범위 끄기', async ({ page, browser }) => {
  const reset = () => call(F.asA, 'PATCH', '/api/me/settings', { hideOffensive: false, hiddenWords: [], commentAudience: 'EVERYONE' })
  F.cleanups.push(reset)
  const sc = await createPost(F.asA, 'E2E 숨김 단어 게시물')
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/posts/${sc}`).catch(() => undefined))

  await open(page, F.A, '/accounts/settings')
  await page.getByRole('link', { name: /숨겨진 단어/ }).click()
  await page.getByLabel('숨길 단어').fill('이투이금지어')
  await page.getByRole('button', { name: '저장' }).click()
  await expect(page.getByText('숨길 단어를 저장했습니다')).toBeVisible()

  const hidden = await call<{ id: number }>(F.asB, 'POST', `/api/posts/${sc}/comments`, { body: '이건 이투이금지어 들어간 댓글' })
  await call(F.asB, 'POST', `/api/posts/${sc}/comments`, { body: 'E2E 평범한 댓글' })
  const c = await other(browser, F.C, `/p/${sc}`)
  await expect(c.page.getByText('E2E 평범한 댓글')).toBeVisible()
  await expect(c.page.getByText('이건 이투이금지어 들어간 댓글')).toHaveCount(0)
  await c.close()

  await page.goto(`/p/${sc}`)
  await expect(page.getByText('이건 이투이금지어 들어간 댓글')).toHaveCount(0)
  await page.getByRole('button', { name: '숨겨진 댓글 보기 (1)' }).click()
  await expect(page.getByRole('dialog', { name: '숨겨진 댓글' }).getByText('이건 이투이금지어 들어간 댓글')).toBeVisible()
  expect(hidden.id).toBeGreaterThan(0)
  await page.keyboard.press('Escape')

  // 댓글 허용 범위: 아무도 안 됨 → B에게는 입력칸 대신 안내
  await page.goto('/accounts/comments')
  await page.getByRole('radio', { name: '아무도 안 됨' }).click()
  await expect(page.getByRole('radio', { name: '아무도 안 됨' })).toBeChecked()
  await expect(page.getByText('댓글 허용 범위를 바꿨습니다')).toBeVisible()
  const b = await other(browser, F.B, `/p/${sc}`)
  await expect(b.page.getByText('게시물 주인이 댓글을 달 수 있는 사람을 제한했습니다.').filter({ visible: true }).first()).toBeVisible()
  await expect(b.page.getByRole('textbox', { name: '댓글 달기' })).toHaveCount(0)
  await b.close()
})

test('알림·민감 콘텐츠·사용 시간: 일시 중지 → 종류 끄기 → 덜 보기 → 하루 사용 시간 알림 → 수면 모드', async ({ page }) => {
  F.cleanups.push(() =>
    call(F.asA, 'PATCH', '/api/me/settings', {
      notifOff: [], resumeNotifications: true, sensitiveLevel: 'STANDARD', clearDailyLimit: true, sleepOn: false,
    }),
  )
  await open(page, F.A, '/accounts/notifications')
  await page.getByRole('button', { name: '1시간' }).click()
  await expect(page.getByText(/까지 일시 중지됨/)).toBeVisible()
  await page.getByRole('button', { name: '다시 받기' }).click()
  await expect(page.getByRole('button', { name: '1시간' })).toBeVisible()
  await page.locator('label').filter({ hasText: '좋아요 (게시물·댓글·스토리)' }).click()
  await expect.poll(async () => (await call<{ notifOff: string[] }>(F.asA, 'GET', '/api/me/settings')).notifOff).toEqual(['LIKES'])

  await page.goto('/accounts/sensitive')
  await page.getByRole('radio', { name: /덜 보기/ }).click()
  await expect(page.getByRole('radio', { name: /덜 보기/ })).toBeChecked()
  await expect.poll(async () => (await call<{ sensitiveLevel: string }>(F.asA, 'GET', '/api/me/settings')).sensitiveLevel).toBe('LESS')

  // 하루 사용 시간 15분 → 오늘 이미 15분 쓴 것으로 두면 10초 안에 알림
  await page.goto('/accounts/time')
  await page.getByRole('button', { name: '15분' }).click()
  await expect(page.getByRole('button', { name: '15분' })).toHaveAttribute('aria-pressed', 'true')
  await page.evaluate(() => localStorage.setItem('usage', JSON.stringify({ day: new Date().toLocaleDateString('sv-SE'), ms: 15 * 60_000, shown: false })))
  await page.reload()
  await expect(page.getByRole('dialog', { name: '사용 시간 알림' })).toBeVisible({ timeout: 15_000 })
  await page.getByRole('dialog').getByRole('button', { name: '닫기' }).last().click()

  await page.locator('label').filter({ hasText: '수면 모드' }).click()
  await expect.poll(async () => (await call<{ sleepOn: boolean }>(F.asA, 'GET', '/api/me/settings')).sleepOn).toBe(true)
})

test('임시 저장: 게시물 만들기 → 임시 저장 → 목록 → 게시', async ({ page }) => {
  await open(page, F.A, '/create')
  await page.locator('input[type="file"]').setInputFiles(solidPng('draft.png', [60, 120, 200], 300, 300))
  await page.getByRole('button', { name: '다음', exact: true }).click()
  await page.getByRole('button', { name: '다음', exact: true }).click()
  await page.getByRole('textbox', { name: '문구' }).fill('E2E 임시 저장 글')
  await page.getByRole('button', { name: '임시 저장' }).click()
  await expect(page).toHaveURL(/\/accounts\/drafts/)
  const row = page.locator('li').filter({ hasText: 'E2E 임시 저장 글' })
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '게시' }).click()
  await expect(page).toHaveURL(/\/p\/[\w-]+$/)
  const sc = page.url().split('/p/')[1]
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/posts/${sc}`).catch(() => undefined))
  await expect(page.getByText('E2E 임시 저장 글').filter({ visible: true }).first()).toBeVisible()
})

// ------------------------------------------------------------------ ⑤ 게시물 위 노트·그리드 재배치·탭 스와이프

test('게시물 위 노트: 맞팔로우가 노트를 남기면 사진 위에 보이고 게시물 주인도 본다', async ({ page, browser }) => {
  // A·B를 서로 팔로우하게 (원래 안 하던 쪽만 끝나고 되돌림)
  for (const [me, them, ctx] of [[F.A, F.B, F.asA], [F.B, F.A, F.asB]] as const) {
    const p = await call<{ relationship: string }>(ctx, 'GET', `/api/users/${them}`)
    if (p.relationship !== 'FOLLOWING') {
      await call(ctx, 'PUT', `/api/users/${them}/follow`)
      F.cleanups.push(() => call(ctx, 'DELETE', `/api/users/${them}/follow`).catch(() => undefined))
    }
    expect(me).toBeTruthy()
  }
  const sc = await createPost(F.asB, 'E2E 노트 받을 게시물')
  F.cleanups.push(() => call(F.asB, 'DELETE', `/api/posts/${sc}`).catch(() => undefined))

  await open(page, F.A, `/p/${sc}`)
  await menu(page, '노트 남기기')
  await page.getByLabel('노트 내용').fill('E2E 여기 꼭 가 볼게요')
  await page.getByRole('dialog').getByRole('button', { name: '노트 남기기' }).click()
  await expect(page.getByText(/노트를 남겼습니다/)).toBeVisible()
  await expect(page.getByLabel('게시물 노트').filter({ visible: true }).first()).toContainText('E2E 여기 꼭 가 볼게요')

  const b = await other(browser, F.B, `/p/${sc}`)
  await expect(b.page.getByLabel('게시물 노트').filter({ visible: true }).first()).toContainText(F.A)
  await b.close()
})

test('그리드 재배치: 내 프로필에서 두 번째 칸을 앞으로', async ({ page }) => {
  await open(page, F.A, `/${F.A}`)
  const tiles = page.locator('a[href^="/p/"], a[href^="/reels/"]')
  await expect(tiles.nth(1)).toBeVisible()
  const firstBefore = await tiles.nth(0).getAttribute('href')
  const second = (await tiles.nth(1).getAttribute('href'))!
  const sc = second.split('/').pop()!
  F.cleanups.push(() => call(F.asA, 'POST', `/api/posts/${sc}/grid-move`, { direction: 'BACK' }).catch(() => undefined))
  await page.getByRole('button', { name: '그리드 순서 바꾸기' }).click()
  await page.getByRole('button', { name: '앞으로' }).nth(1).click()
  await expect(tiles.nth(0)).toHaveAttribute('href', second)
  await expect(tiles.nth(1)).toHaveAttribute('href', firstBefore!)
  await page.getByRole('button', { name: '순서 바꾸기 끝' }).click()
  await expect(page.getByRole('button', { name: '앞으로' })).toHaveCount(0)
})

test.describe('모바일', () => {
  test.use({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true })

  test('탭 간 좌우 스와이프: 홈 → 릴스 → 홈, 사진 넘기기 위에서는 탭이 안 바뀐다', async ({ page }) => {
    const swipe = async (fromX: number, toX: number, y = 420, selector?: string) =>
      page.evaluate(
        ({ fromX, toX, y, selector }) => {
          const target = (selector ? document.querySelector(selector) : document.elementFromPoint(fromX, y)) ?? document.body
          const touch = (x: number) => new Touch({ identifier: 1, target, clientX: x, clientY: y })
          target.dispatchEvent(new TouchEvent('touchstart', { touches: [touch(fromX)], changedTouches: [touch(fromX)], bubbles: true }))
          target.dispatchEvent(new TouchEvent('touchend', { touches: [], changedTouches: [touch(toX)], bubbles: true }))
        },
        { fromX, toX, y, selector },
      )
    await open(page, F.A, '/')
    await expect(page.locator('article').first()).toBeVisible()
    await swipe(320, 80, 300, 'main')
    await expect(page).toHaveURL(/\/reels$/)
    await page.waitForTimeout(600)
    await swipe(80, 320, 300, 'main')
    await expect(page).toHaveURL(/\/$/)
    // 사진 넘기기(캐러셀) 위에서 시작한 손짓은 무시
    await page.waitForTimeout(600)
    await swipe(320, 80, 300, '[data-no-swipe]')
    await page.waitForTimeout(400)
    await expect(page).toHaveURL(/\/$/)
  })
})

// ------------------------------------------------------------------ ⑥ 릴스 자르기·자막, 스토리 효과, 홍보하기, 라이브 게스트, 브라우저 알림

const CLIP = path.join(import.meta.dirname, '..', '..', 'seed-media', 'reels', 'reel-000.mp4')

test('릴스 자르기·자막: 구간과 자막을 정해 올리면 그 구간만 재생되고 자막이 보인다', async ({ page }) => {
  test.setTimeout(150_000)
  await open(page, F.A, '/create/reel')
  await page.locator('input[type="file"]').first().setInputFiles(CLIP)
  await page.getByRole('textbox', { name: '문구' }).fill('E2E 자막 릴스')
  await page.getByLabel('자르기 시작').fill('1000')
  await page.getByRole('button', { name: /자막 추가/ }).click()
  await page.getByLabel('1번째 자막', { exact: true }).fill('E2E 자막이에요')
  await page.getByRole('button', { name: '공유', exact: true }).click()
  await expect(page).toHaveURL(/\/reels\/[\w-]+$/, { timeout: 90_000 })
  const sc = page.url().split('/reels/')[1]
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/posts/${sc}`))
  const post = await call<{ media: { trimStartMs: number | null; overlays: { text: string }[] }[] }>(F.asA, 'GET', `/api/posts/${sc}`)
  expect(post.media[0].trimStartMs).toBe(1000)
  expect(post.media[0].overlays[0].text).toBe('E2E 자막이에요')
  await expect(page.getByLabel('자막').getByText('E2E 자막이에요').first()).toBeVisible({ timeout: 15_000 })
  const t = await page.locator(`video[aria-label$="님의 릴스"]`).first().evaluate((v: HTMLVideoElement) => v.currentTime)
  expect(t, '자른 시작점 이후부터 재생').toBeGreaterThanOrEqual(0.9)
})

test('스토리 효과: 레이아웃(사진 2장) → AR 스티커 → 부메랑 → 공유하면 보는 화면에서 움직인다', async ({ page }) => {
  await open(page, F.A, '/create/story')
  await page.getByLabel('레이아웃 사진들').setInputFiles([
    solidPng('lay1.png', [220, 60, 60], 400, 400),
    solidPng('lay2.png', [60, 90, 220], 400, 400),
  ])
  await expect(page.getByLabel(/스토리 미리보기/)).toBeVisible()
  await page.getByRole('radio', { name: 'AR 🐶' }).click()
  await page.getByRole('button', { name: /부메랑/ }).click()
  await page.getByRole('button', { name: '공유', exact: true }).click()
  await expect(page.getByText(/스토리를 공유했습니다/)).toBeVisible()
  const mine = await call<{ id: number; effect: string | null }[]>(F.asA, 'GET', `/api/stories/users/${F.A}`)
  const story = mine[mine.length - 1]
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/stories/${story.id}`).catch(() => undefined))
  expect(story.effect).toBe('BOOMERANG')
  await page.goto(`/stories/${F.A}`)
  await expect(page.locator('img.boomerang').first()).toBeVisible()
})

test('홍보하기: 비즈니스 계정 게시물을 홍보 → 성과 → 중단, 개인 계정은 안내', async ({ page }) => {
  const [[biz, sc]] = sql(`
    select u.username, p.shortcode from posts p join users u on u.id = p.author_id
    where u.account_type = 'BUSINESS' and not u.is_private and p.type <> 'REEL' and p.deleted_at is null and p.archived_at is null
      and not p.trial and p.id not in (select post_id from ads)
    order by p.id desc limit 1`)
  F.cleanups.push(async () => {
    const ctx = await apiAs(biz)
    await call(ctx, 'DELETE', `/api/posts/${sc}/promotion`).catch(() => undefined)
    await ctx.dispose()
  })
  await open(page, biz, `/p/${sc}`)
  await menu(page, '홍보하기')
  const dlg = page.getByRole('dialog', { name: '홍보하기' })
  await dlg.getByLabel('하루 예산').selectOption('30000')
  await dlg.getByLabel('홍보 기간').selectOption('7')
  await dlg.getByRole('button', { name: '홍보 시작' }).click()
  await expect(page.getByText(/홍보를 시작했습니다/)).toBeVisible()
  await expect(dlg.getByLabel('홍보 성과')).toContainText('210,000원')
  await dlg.getByRole('button', { name: '홍보 중단' }).click()
  await expect(page.getByText('홍보를 중단했습니다')).toBeVisible()
  await page.keyboard.press('Escape')

  // 개인 계정
  const own = await createPost(F.asA, 'E2E 개인 계정 홍보 시도')
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/posts/${own}`).catch(() => undefined))
  await open(page, F.A, `/p/${own}`)
  await menu(page, '홍보하기')
  await expect(page.getByRole('dialog', { name: '홍보하기' }).getByText(/크리에이터·비즈니스 계정만/)).toBeVisible()
})

test.describe('라이브 게스트·브라우저 알림', () => {
  test.use({ permissions: ['camera', 'microphone', 'notifications'] })

  test('라이브 게스트: 시청자를 초대 → 수락하면 진행자·게스트 모두에게 게스트 자리', async ({ page, browser }) => {
    test.setTimeout(120_000)
    await open(page, F.A, '/live/new')
    await page.getByPlaceholder('제목 추가 (선택)').fill('E2E 게스트 방송')
    await page.getByRole('button', { name: /라이브 방송 시작/ }).click()
    await expect(page).toHaveURL(/\/live\/\d+/)
    const liveId = page.url().split('/live/')[1]
    F.cleanups.push(() => call(F.asA, 'POST', `/api/lives/${liveId}/end`).catch(() => undefined))

    const b = await other(browser, F.B, `/live/${liveId}`)
    await expect(page.getByText(new RegExp(`${F.B}.*참여했습니다`)).first()).toBeVisible()
    await page.getByRole('button', { name: '게스트 초대' }).click()
    await page.getByRole('dialog', { name: '게스트 초대' }).getByRole('button', { name: F.B }).click()
    await expect(page.getByText('초대 수락 기다리는 중')).toBeVisible()
    await b.page.getByRole('button', { name: '참여하기' }).click()
    await expect(b.page.getByText('게스트 (영상은 흉내)')).toBeVisible()
    await expect(page.getByText('게스트 (영상은 흉내)')).toBeVisible()
    await b.close()
  })

  test('브라우저 알림: 설정에서 켜면 이 기기에 저장된다', async ({ page }) => {
    await open(page, F.A, '/accounts/notifications')
    await page.locator('label').filter({ hasText: '브라우저 알림 (이 기기)' }).click()
    await expect.poll(() => page.evaluate(() => localStorage.getItem('browserNotify'))).toBe('1')
    await page.locator('label').filter({ hasText: '브라우저 알림 (이 기기)' }).click()
    await expect.poll(() => page.evaluate(() => localStorage.getItem('browserNotify'))).toBe('0')
  })
})
