// 전체 기능 점검: 스토리·하이라이트·친한 친구, 프로필·관계(친한 친구·즐겨찾기·숨기기·제한·차단), 검색·탐색
import { expect, test } from '@playwright/test'
import { F, nextUntil, open, other, sheet, useFeatureSetup } from './feature-setup'
import { apiAs, call, createStory, sql } from './helpers'

useFeatureSetup()

// ------------------------------------------------------------------ 스토리

test('스토리: 보기 → 투표 스티커 → 좋아요 → 답장(DM) → 주인이 활동(조회자) 확인', async ({ page, browser }) => {
  const id = await createStory(F.asB, {
    stickers: [{ type: 'POLL', x: 0.5, y: 0.6, question: 'E2E 투표', options: ['예', '아니요'] }],
  })
  F.cleanups.push(() => call(F.asB, 'DELETE', `/api/stories/${id}`))

  await open(page, F.A, `/stories/${F.B}`)
  const viewer = page.getByRole('dialog', { name: `${F.B}님의 스토리` })
  await expect(viewer).toBeVisible()
  await nextUntil(page, () => viewer.getByText('E2E 투표').isVisible())
  await viewer.getByRole('button', { name: '예', exact: true }).click()
  await expect(viewer.getByText(/\d+%/).first()).toBeVisible()
  await viewer.getByRole('button', { name: '좋아요', exact: true }).click()
  await expect(viewer.getByRole('button', { name: '좋아요 취소' })).toBeVisible()
  await viewer.getByRole('textbox', { name: '스토리에 답장' }).fill('E2E 스토리 답장')
  await viewer.getByRole('button', { name: '보내기' }).click()
  await expect(page.getByText(/보냈습니다/)).toBeVisible()

  // B: 내 스토리 → 활동에 A / DM에 답장
  const b = await other(browser, F.B, `/stories/${F.B}`)
  const own = b.page.getByRole('dialog', { name: `${F.B}님의 스토리` })
  await nextUntil(b.page, () => own.getByText('E2E 투표').isVisible())
  await own.getByRole('button', { name: /활동/ }).click()
  await expect(b.page.getByRole('dialog').getByText(F.A).first()).toBeVisible()
  // 받은 메시지함 미리보기는 자동 응답 봇의 답장으로 바뀔 수 있어, A와의 대화방 안에서 확인
  const conv = await call<{ id: number }>(F.asB, 'POST', '/api/direct/conversations', { usernames: [F.A] })
  await b.page.goto(`/direct/t/${conv.id}`)
  await expect(b.page.getByText('E2E 스토리 답장').last()).toBeVisible()
  await b.close()
})

test('스토리: 하이라이트 만들기 → 프로필에 표시 → 열기', async ({ page }) => {
  const id = await createStory(F.asA)
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/stories/${id}`))
  await open(page, F.A, `/${F.A}`)
  await page.getByLabel('하이라이트').getByText('새로 만들기').click()
  const dlg = sheet(page)
  await dlg.locator('button:has(img)').first().click()
  await dlg.getByRole('button', { name: /다음/ }).click()
  const title = `E2E${Date.now() % 1000}`
  await dlg.getByRole('textbox', { name: '하이라이트 이름' }).fill(title)
  await dlg.getByRole('button', { name: '추가' }).click()
  const hl = page.getByLabel('하이라이트').getByText(title)
  await expect(hl).toBeVisible()
  const hid = sql(`select h.id from highlights h join users u on u.id = h.user_id where u.username = '${F.A}' and h.title = '${title}'`)[0][0]
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/stories/highlights/${hid}`))
  await hl.click()
  await expect(page).toHaveURL(new RegExp(`/stories/highlights/${hid}`))
  await expect(page.getByRole('dialog').first()).toBeVisible()
})

test('스토리: 친한 친구 전용 스토리는 친한 친구에게만 보인다', async ({ page }) => {
  await call(F.asB, 'PUT', `/api/users/${F.A}/close-friend`)
  F.cleanups.push(() => call(F.asB, 'DELETE', `/api/users/${F.A}/close-friend`))
  const id = await createStory(F.asB, { audience: 'CLOSE_FRIENDS' })
  F.cleanups.push(() => call(F.asB, 'DELETE', `/api/stories/${id}`))

  type S = { id: number; audience: string }
  const items = async (ctx: typeof F.asA) => {
    const res = await ctx.fetch(`/api/stories/users/${F.B}`)
    if (!res.ok()) return [] as S[]
    const body = (await res.json()) as S[] | { items: S[] } | { stories: S[] }
    return Array.isArray(body) ? body : 'items' in body ? body.items : body.stories
  }
  expect((await items(F.asA)).some((s) => s.id === id), 'A(친한 친구)에게 보임').toBe(true)
  const asC = await apiAs(F.C)
  expect((await items(asC)).some((s) => s.id === id), 'C에게는 안 보임').toBe(false)
  await asC.dispose()

  await open(page, F.A, `/stories/${F.B}`)
  const viewer = page.getByRole('dialog', { name: `${F.B}님의 스토리` })
  await nextUntil(page, () => viewer.getByText('친한 친구', { exact: true }).isVisible())
})

// ------------------------------------------------------------------ 프로필·관계

test('프로필: 편집(소개) → 팔로워 목록 → 탭 전환', async ({ page }) => {
  const before = await call<{ bio: string | null }>(F.asA, 'GET', '/api/me/profile')
  F.cleanups.push(() => call(F.asA, 'PATCH', '/api/me/profile', { bio: before.bio ?? '' }))
  await open(page, F.A, '/accounts/edit')
  await page.getByLabel(/소개/).first().fill('E2E 소개 글입니다')
  await page.getByRole('button', { name: /제출|저장|완료/ }).first().click()
  await expect.poll(async () => (await call<{ bio: string }>(F.asA, 'GET', '/api/me/profile')).bio).toBe('E2E 소개 글입니다')
  await page.goto(`/${F.A}`)
  await expect(page.getByText('E2E 소개 글입니다').filter({ visible: true }).first()).toBeVisible()

  await page.getByText(/팔로워/).first().click()
  await expect(sheet(page)).toBeVisible()
  await expect(sheet(page).locator('a[href^="/"]').first()).toBeVisible()
  await page.keyboard.press('Escape')

  for (const tab of ['릴스', '리포스트', '저장됨', '태그됨', '게시물']) {
    await page.getByRole('link', { name: tab }).or(page.getByRole('tab', { name: tab })).first().click()
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => undefined)
  }
})

test('관계: 친한 친구·즐겨찾기·숨기기·제한·차단 → 목록 화면 → 해제', async ({ page }) => {
  const { A, B, asA, asB } = F
  F.cleanups.push(async () => {
    for (const op of ['close-friend', 'favorite', 'restrict', 'block']) await call(asA, 'DELETE', `/api/users/${B}/${op}`).catch(() => undefined)
    await call(asA, 'PUT', `/api/users/${B}/mute`, { posts: false, stories: false }).catch(() => undefined)
    await call(asA, 'PUT', `/api/users/${B}/follow`).catch(() => undefined)
  })
  await open(page, A, `/${B}`)
  const menu = async () => {
    await page.getByRole('button', { name: '옵션' }).first().click()
    return sheet(page)
  }
  await (await menu()).getByRole('button', { name: '친한 친구에 추가' }).click()
  await expect(page.getByText('친한 친구에 추가했습니다')).toBeVisible()
  await (await menu()).getByRole('button', { name: '즐겨찾기에 추가' }).click()
  await expect(page.getByText(/즐겨찾기에 추가/).first()).toBeVisible()
  await (await menu()).getByRole('button', { name: '숨기기' }).click()
  await sheet(page).locator('label').filter({ hasText: '홈 피드에' }).click()
  await sheet(page).getByRole('button').last().click()
  await expect(page.getByText('숨겼습니다')).toBeVisible()
  await (await menu()).getByRole('button', { name: '제한', exact: true }).click()
  await expect(page.getByText(`${B}님을 제한했습니다`)).toBeVisible()

  for (const path of ['/accounts/close-friends', '/accounts/favorites', '/accounts/muted', '/accounts/restricted']) {
    await page.goto(path)
    await expect(page.getByText(B, { exact: true }).first(), `${path} 목록에 B`).toBeVisible()
  }

  await page.goto(`/${B}`)
  await (await menu()).getByRole('button', { name: '차단', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '차단', exact: true }).click()
  await expect(page.getByText(`${B}님을 차단했습니다`)).toBeVisible()
  await page.reload()
  await expect(page.getByText('차단한 계정입니다')).toBeVisible()
  await page.goto('/accounts/blocked')
  await expect(page.getByText(B, { exact: true }).first()).toBeVisible()
  const res = await asB.fetch(`/api/users/${A}`)
  const profile = res.ok() ? ((await res.json()) as { canViewContent: boolean }) : null
  expect(profile === null || profile.canViewContent === false, 'B에게 A가 안 보임').toBe(true)
  await page.goto(`/${B}`)
  await (await menu()).getByRole('button', { name: '차단 해제' }).click()
  await expect(page.getByText('차단을 해제했습니다')).toBeVisible()
})

// ------------------------------------------------------------------ 검색·탐색

test('검색·탐색: 해시태그 검색 → 태그 페이지 → 최근 검색 → 사람 검색 → 장소 → 탐색 그리드', async ({ page }) => {
  const [[tag]] = sql(
    `select h.name from hashtags h join post_hashtags ph on ph.hashtag_id = h.id where h.name ~ '^[a-z]+$' group by h.name order by count(*) desc limit 1`,
  )
  await open(page, F.A, '/explore/search')
  await page.getByRole('textbox').first().fill(tag)
  await page.getByText(`#${tag}`, { exact: true }).first().click()
  await expect(page).toHaveURL(/\/explore\/tags\//)
  await expect(page.locator('a[href^="/p/"], a[href^="/reels/"]').first()).toBeVisible()

  await page.goto('/explore/search')
  await expect(page.getByText(`#${tag}`).first(), '최근 검색').toBeVisible()

  await page.getByRole('textbox').first().fill(F.B.slice(0, 5))
  await expect(page.getByText(F.B, { exact: true }).first()).toBeVisible()

  const [[loc]] = sql(`select location_id from posts where location_id is not null and deleted_at is null group by location_id order by count(*) desc limit 1`)
  await page.goto(`/explore/locations/${loc}`)
  await expect(page.locator('a[href^="/p/"], a[href^="/reels/"]').first()).toBeVisible()

  await page.goto('/explore')
  const first = page.locator('a[href^="/p/"], a[href^="/reels/"]').first()
  const href = (await first.getAttribute('href'))!
  await first.click()
  await expect(page).toHaveURL(new RegExp(href.split('?')[0]))
})
