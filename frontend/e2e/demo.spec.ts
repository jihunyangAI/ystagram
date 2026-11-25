// 기획서 §16.2 데모 시나리오 1~7 (단계별 합격 기준) 자동화.
// 개발 서버의 가짜 데이터를 쓰고, 만든 것(게시물·스토리·팔로우·가중치·가상 시계)은 끝나면 되돌린다.
import { expect, test } from '@playwright/test'
import type { APIRequestContext } from '@playwright/test'
import { PASSWORD, addMinutes, apiAs, call, createStory, loginAs, newUserPage, pickUsers, resetClock, solidPng } from './helpers'

let A: string, B: string, C: string
const cleanups: (() => Promise<unknown>)[] = []

test.beforeAll(async () => {
  ;({ a: A, b: B, c: C } = await pickUsers())
  console.log(`  사용자 A=${A} B=${B} C(비공개)=${C}`)
})

test.afterAll(async () => {
  for (const f of cleanups.reverse()) await f().catch((e) => console.warn('  정리 실패:', e.message))
  await resetClock()
})

test.describe.configure({ mode: 'serial' })

let sharedShortcode = ''

test('1. A로 로그인하면 홈 피드에 팔로우 게시물과 스토리가 보인다', async ({ page }) => {
  // 시드 스토리는 24시간이 지나면 사라지므로(개발 DB를 며칠 두면 트레이가 빈다), A가 팔로우하는 사람의 스토리를 하나 올려 둔다
  const asA = await apiAs(A)
  const following = await call<{ items: { user: { username: string } }[] } | { user: { username: string } }[]>(
    asA, 'GET', `/api/users/${A}/following`,
  )
  const list = Array.isArray(following) ? following : following.items
  await asA.dispose()
  const friend = await apiAs(list[0].user.username)
  const storyId = await createStory(friend)
  cleanups.push(async () => {
    await call(friend, 'DELETE', `/api/stories/${storyId}`)
    await friend.dispose()
  })

  await page.goto('/accounts/login')
  await page.getByPlaceholder('사용자 이름 또는 이메일').fill(A)
  await page.getByPlaceholder('비밀번호').fill(PASSWORD)
  await page.getByRole('button', { name: '로그인', exact: true }).click()

  await expect(page).toHaveURL(/\/$/)
  const tray = page.getByLabel('스토리', { exact: true })
  await expect(tray).toBeVisible()
  await expect(tray.getByRole('button', { name: /님의 스토리/ }).first()).toBeVisible()
  await expect(page.locator('article[aria-label$="님의 게시물"]').first()).toBeVisible()
  expect(await page.locator('article[aria-label$="님의 게시물"]').count()).toBeGreaterThanOrEqual(3)
})

test('2. 사진 3장 캐러셀을 필터를 적용해 올리고, 해시태그 6개는 막힌다', async ({ page }) => {
  await loginAs(page, A, '/create')
  const files = [
    solidPng('red.png', [220, 60, 60]),
    solidPng('green.png', [60, 180, 90]),
    solidPng('blue.png', [60, 90, 220]),
  ]
  await page.locator('input[type="file"]').setInputFiles(files)
  await expect(page.getByLabel('3번째 사진', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '다음', exact: true }).click() // 자르기 → 편집

  await page.getByRole('button', { name: 'Clarendon' }).click()
  await page.getByRole('button', { name: '다음', exact: true }).click() // 편집 → 문구

  const caption = page.getByRole('textbox', { name: '문구' })
  await caption.fill('E2E 테스트 #하나 #둘 #셋 #넷 #다섯 #여섯')
  await expect(page.getByText('해시태그는 5개까지 쓸 수 있습니다', { exact: false })).toBeVisible()
  const share = page.getByRole('button', { name: '공유하기' })
  await expect(share).toBeDisabled()

  await caption.fill('E2E 테스트 #하나 #둘 #셋 #넷 #다섯')
  await expect(share).toBeEnabled()
  await share.click()

  await expect(page).toHaveURL(/\/p\/[\w-]+$/, { timeout: 30_000 })
  sharedShortcode = page.url().split('/p/')[1]
  const asA = await apiAs(A)
  cleanups.push(() => call(asA, 'DELETE', `/api/posts/${sharedShortcode}`).finally(() => asA.dispose()))

  const post = await call<{ media: { url: string }[] }>(asA, 'GET', `/api/posts/${sharedShortcode}`)
  expect(post.media).toHaveLength(3)
  // 필터는 올리기 전에 사진에 입혀진다: 원래 빨강(220,60,60)보다 대비·채도가 올라가 있어야 한다
  const px = await page.evaluate(async (url) => {
    const img = new Image()
    img.src = url
    await img.decode()
    const c = document.createElement('canvas')
    c.width = img.naturalWidth
    c.height = img.naturalHeight
    const g = c.getContext('2d')!
    g.drawImage(img, 0, 0)
    return Array.from(g.getImageData(c.width >> 1, c.height >> 1, 1, 1).data.slice(0, 3))
  }, post.media[0].url)
  expect(px[0] - px[1], `필터 적용 색 ${px}`).toBeGreaterThan(160 + 20)
  // 캐러셀: 세 장 모두 상세 화면에 들어 있다 (모바일은 화살표 대신 넘기기)
  for (const m of post.media) await expect(page.locator(`img[src="${m.url}"]`).first()).toBeAttached()
})

test('3. B가 좋아요·댓글 → A에게 알림 묶음이 보인다', async ({ browser }) => {
  test.skip(!sharedShortcode, '2번 게시물이 필요')
  const pageB = await newUserPage(browser, B, `/p/${sharedShortcode}`)
  await pageB.getByRole('button', { name: '좋아요', exact: true }).first().click()
  await expect(pageB.getByRole('button', { name: '좋아요 취소' }).first()).toBeVisible()
  const comment = `멋져요 ${Date.now() % 10000}`
  await pageB.getByPlaceholder(/댓글 달기/).fill(comment)
  await pageB.getByRole('button', { name: '게시', exact: true }).click()
  await expect(pageB.getByText(comment)).toBeVisible()
  await pageB.context().close()

  // 한 사람 더 좋아요 → "B님, X님" 또는 "외 N명"으로 묶인다
  const other = await apiAs(C)
  await call(other, 'PUT', `/api/posts/${sharedShortcode}/like`).catch(() => undefined) // 비공개 C도 공개 게시물엔 가능
  await other.dispose()

  const pageA = await newUserPage(browser, A, '/notifications')
  const likeRow = pageA.locator('p', { hasText: '게시물을 좋아합니다' }).filter({ hasText: B }).first()
  await expect(likeRow).toBeVisible()
  await expect(likeRow).toContainText(/님, .+님|외 \d+명/)
  await expect(pageA.locator('p', { hasText: comment }).first()).toContainText(B)
  await pageA.context().close()
})

test('4. 비공개 계정 C에 팔로우 요청 → 승인 전에는 게시물이 안 보인다', async ({ browser }) => {
  const asA = await apiAs(A)
  cleanups.push(() => call(asA, 'DELETE', `/api/users/${C}/follow`).finally(() => asA.dispose()))

  const pageA = await newUserPage(browser, A, `/${C}`)
  await expect(pageA.getByText('비공개 계정입니다')).toBeVisible()
  await pageA.getByRole('button', { name: /^(맞)?팔로우$/ }).first().click()
  await expect(pageA.getByRole('button', { name: '요청됨' }).first()).toBeVisible()
  await pageA.reload()
  await expect(pageA.getByText('비공개 계정입니다')).toBeVisible()
  const before = await call<{ canViewContent: boolean }>(asA, 'GET', `/api/users/${C}`)
  expect(before.canViewContent).toBe(false)

  // C가 활동 → 팔로우 요청에서 승인
  const pageC = await newUserPage(browser, C, '/notifications/requests')
  const row = pageC.locator('li').filter({ has: pageC.getByText(A, { exact: true }) })
  await row.getByRole('button', { name: '확인' }).click()
  await expect(row.getByRole('button', { name: '확인' })).toHaveCount(0)
  await pageC.context().close()

  await pageA.reload()
  await expect(pageA.getByText('비공개 계정입니다')).toHaveCount(0)
  await expect(pageA.locator('a[href^="/p/"]').first()).toBeVisible()
  await pageA.context().close()
})

test('5. A·B 두 브라우저 DM: 실시간 수신, 입력 중, 읽음, 15분 뒤 수정 불가', async ({ browser }) => {
  const asA = await apiAs(A)
  const conv = await call<{ id: number }>(asA, 'POST', '/api/direct/conversations', { usernames: [B] })
  // 가짜 계정의 자동 응답 봇이 먼저 답하면 B의 메시지가 마지막이 아니어서 "읽음"이 안 붙는다 → 이 시나리오 동안 끈다
  const flags = await call<Record<string, unknown>>(asA, 'GET', '/api/lab/flags')
  await call(asA, 'PUT', '/api/lab/flags', { ...flags, autoReply: false })
  cleanups.push(() => call(asA, 'PUT', '/api/lab/flags', flags).finally(() => asA.dispose()))

  const pageA = await newUserPage(browser, A, `/direct/t/${conv.id}`)
  const pageB = await newUserPage(browser, B, `/direct/t/${conv.id}`)
  const inputA = pageA.getByLabel('메시지 입력')
  const inputB = pageB.getByLabel('메시지 입력')
  await expect(inputA).toBeVisible()
  // 서로 팔로우하지 않으면 B에게는 메시지 요청으로 온다 → 수락
  const accept = pageB.getByRole('button', { name: '수락', exact: true })
  await expect(inputB.or(accept)).toBeVisible()
  if (await accept.isVisible()) await accept.click()
  await expect(inputB).toBeVisible()
  // 실시간 연결이 붙을 시간을 조금 준다
  await pageA.waitForTimeout(1500)

  // 입력 중
  await inputB.pressSequentially('안녕 A', { delay: 80 })
  await expect(pageA.getByText('입력 중')).toBeVisible()

  // 실시간 수신
  const text = `E2E 안녕 ${Date.now() % 100000}`
  await inputB.fill(text)
  await inputB.press('Enter')
  await expect(pageA.getByText(text).last()).toBeVisible()

  // A가 보고 있으므로 B 쪽에 읽음
  await expect(pageB.getByText('읽음', { exact: true })).toBeVisible()

  // B의 메시지는 지금은 수정 가능 → 16분 뒤에는 수정 버튼이 없고 서버도 거부
  const mine = pageB.locator('div.group', { hasText: text }).last()
  await mine.hover()
  await expect(mine.getByRole('button', { name: '수정 (15분 안)' })).toBeVisible()

  await addMinutes(16)
  await pageB.reload()
  const mineLater = pageB.locator('div.group', { hasText: text }).last()
  await mineLater.hover()
  await expect(mineLater.getByRole('button', { name: '전송 취소' })).toBeVisible()
  await expect(mineLater.getByRole('button', { name: '수정 (15분 안)' })).toHaveCount(0)

  const asB = await apiAs(B)
  const msgs = await call<{ items: { id: number; body: string }[] }>(asB, 'GET', `/api/direct/conversations/${conv.id}/messages`)
  const id = msgs.items.find((m) => m.body === text)!.id
  await expect(call(asB, 'PATCH', `/api/direct/messages/${id}`, { body: '고침' })).rejects.toThrow(/4\d\d/)
  await asB.dispose()

  await resetClock()
  await pageA.context().close()
  await pageB.context().close()
})

test('6. 스토리를 올리고 +25시간 → 트레이에서 사라지고 아카이브에 남는다', async ({ page }) => {
  const asA = await apiAs(A)
  type Story = { id: number; mediaUrl: string }
  const archive = async () => (await call<{ items: Story[] }>(asA, 'GET', '/api/stories/archive')).items
  const archived = async () => (await archive()).map((s) => s.id)
  const before = new Set(await archived())

  await loginAs(page, A, '/create/story')
  await page.locator('input[type="file"]:not([multiple])').setInputFiles(solidPng('story.png', [250, 170, 40], 270, 480))
  await page.getByRole('button', { name: '공유', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByRole('button', { name: '내 스토리 보기' })).toBeVisible()

  const newId = (await archived()).find((id) => !before.has(id))
  expect(newId, '새 스토리').toBeTruthy()
  cleanups.push(async () => {
    const ctx = await apiAs(A)
    await call(ctx, 'DELETE', `/api/stories/${newId}`).finally(() => ctx.dispose())
  })

  await addMinutes(25 * 60)
  await page.reload()
  await expect(page.getByLabel('스토리', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '내 스토리 보기' })).toHaveCount(0)

  await page.goto('/archive/stories')
  const story = (await archive()).find((s) => s.id === newId)!
  await expect(page.locator(`img[src="${story.mediaUrl}"]`).first()).toBeVisible()
  await asA.dispose()
  await resetClock()
})

test('7. Lab에서 인기도 가중치를 올리면 탐색 결과가 바뀌고 점수 내역이 보인다', async ({ page }) => {
  const lab: APIRequestContext = await apiAs(A)
  cleanups.push(() => call(lab, 'DELETE', '/api/lab/algorithm/EXPLORE').finally(() => lab.dispose()))

  await loginAs(page, A, '/lab/algorithm')
  await expect(page.getByRole('radio', { name: '탐색' })).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByText(/시점 상위 \d+개/)).toBeVisible({ timeout: 20_000 })

  await page.getByLabel('인기도 가중치').fill('6')
  await expect(page.getByText(/B 6\.0/)).toBeVisible()
  const columnB = page.locator('section', { hasText: 'B · 실험' })
  // 미리보기(0.4초 뒤 다시 계산)가 끝나면 B 열에 순위 변화(새로 진입·↑·↓)가 생긴다
  await expect
    .poll(
      async () =>
        (await columnB.getByText('새로 진입').count()) + (await columnB.locator('li svg.lucide-arrow-up, li svg.lucide-arrow-down').count()),
      { message: '순위가 바뀐 게시물 수', timeout: 20_000 },
    )
    .toBeGreaterThan(0)

  // 점수 내역
  await columnB.locator('li button').first().click()
  const breakdown = columnB.locator('table').first()
  await expect(breakdown).toContainText('인기도')
  await expect(breakdown).toContainText('× 6')

  // 적용하면 A(지금 적용 중)가 6이 된다
  await page.getByRole('button', { name: 'B를 적용' }).click()
  await expect(page.getByText(/A 6 →/)).toBeVisible()
  const saved = await call<{ key: string; components: { key: string; weight: number }[] }[]>(lab, 'GET', '/api/lab/algorithm')
  expect(saved.find((s) => s.key === 'EXPLORE')!.components.find((c) => c.key === 'POPULARITY')!.weight).toBe(6)
})
