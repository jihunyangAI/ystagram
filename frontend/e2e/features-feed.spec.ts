// 전체 기능 점검: 홈 피드(모드·광고·무한 스크롤·표시 이유), 옵션을 다 켠 게시물 만들기(위치·사람 태그·대체 텍스트·좋아요 수 숨기기·AI 라벨)
import { expect, test } from '@playwright/test'
import { F, open, sheet, useFeatureSetup } from './feature-setup'
import { call, solidPng } from './helpers'

useFeatureSetup()

test('홈 피드: 광고 카드 → 무한 스크롤 → "이 게시물이 표시되는 이유" → 팔로잉(최신순)·즐겨찾기 모드', async ({ page }) => {
  await open(page, F.A, '/')
  const articles = page.locator('article[aria-label$="님의 게시물"]')
  await expect(articles.first()).toBeVisible()

  // 무한 스크롤: 끝까지 내리면 더 불러온다
  const first = await articles.count()
  for (let i = 0; i < 6 && (await articles.count()) <= first; i++) {
    await page.mouse.wheel(0, 20_000)
    await page.waitForTimeout(800)
  }
  expect(await articles.count(), '스크롤하면 게시물이 더 나온다').toBeGreaterThan(first)
  await expect(page.locator('article').filter({ hasText: '광고' }).first(), '광고 카드').toBeAttached()

  // 표시 이유
  await page.evaluate(() => window.scrollTo(0, 0))
  await articles.first().getByRole('button', { name: /옵션/ }).click()
  await sheet(page).getByRole('button', { name: '이 게시물이 표시되는 이유' }).click()
  await expect(sheet(page).getByText(/점수|팔로우|추천|관심/).first()).toBeVisible()
  await page.keyboard.press('Escape')

  // 팔로잉(최신순): 시간 순서
  await page.getByRole('tablist', { name: '홈 보기' }).getByRole('tab', { name: /팔로잉/ }).click()
  await expect(articles.first()).toBeVisible()
  const times = await page.locator('article time').evaluateAll((els) => els.slice(0, 8).map((e) => new Date(e.getAttribute('datetime')!).getTime()))
  expect(times.length).toBeGreaterThan(1)
  expect([...times].sort((a, b) => b - a), '최신순').toEqual(times)

  // 즐겨찾기: B를 즐겨찾기에 넣으면 B의 글만
  await call(F.asA, 'PUT', `/api/users/${F.B}/favorite`)
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/users/${F.B}/favorite`))
  await page.getByRole('tablist', { name: '홈 보기' }).getByRole('tab', { name: /즐겨찾기/ }).click()
  await page.waitForLoadState('networkidle').catch(() => undefined)
  const authors = await articles.evaluateAll((els) => els.map((e) => e.getAttribute('aria-label')))
  for (const a of authors) expect(a).toBe(`${F.B}님의 게시물`)
})

test('게시물 만들기: 위치 → 사람 태그 → 대체 텍스트 → 좋아요 수 숨기기 → AI 라벨 → 공유', async ({ page }) => {
  await open(page, F.A, '/create')
  await page.locator('input[type="file"]').setInputFiles(solidPng('opt.png', [180, 90, 160], 300, 300))
  await page.getByRole('button', { name: '다음', exact: true }).click()
  await page.getByRole('button', { name: '다음', exact: true }).click()

  await page.getByRole('textbox', { name: '문구' }).fill('E2E 옵션 게시물')
  await page.getByPlaceholder('위치 추가').fill('남산')
  await page.locator('li button', { hasText: '남산' }).first().click()

  // 사진을 눌러 사람 태그
  await page.locator('.cursor-crosshair').first().click({ position: { x: 60, y: 60 } })
  await page.getByPlaceholder('태그할 사람 검색').fill(F.B)
  await page.getByText(F.B, { exact: true }).first().click()
  await expect(page.getByText(/사람 태그 \(1\/20\)/)).toBeVisible()

  await page.getByText('접근성').click()
  await page.getByRole('textbox', { name: '1번째 사진의 대체 텍스트' }).fill('보라색 단색 시험 사진')
  await page.locator('label').filter({ hasText: '이 게시물의 좋아요 수 숨기기' }).click()
  await page.locator('label').filter({ hasText: 'AI 정보 라벨 추가' }).click()
  await page.getByRole('button', { name: '공유하기' }).click()

  await expect(page).toHaveURL(/\/p\/[\w-]+$/, { timeout: 30_000 })
  const sc = page.url().split('/p/')[1]
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/posts/${sc}`))
  const post = await call<{
    location: { name: string } | null
    hideLikeCount: boolean
    tags: { user: { username: string } }[]
    media: { altText: string | null }[]
    aiLabel?: boolean
    aiLabeled?: boolean
  }>(F.asA, 'GET', `/api/posts/${sc}`)
  expect(post.location?.name).toContain('남산')
  expect(post.hideLikeCount).toBe(true)
  expect(post.tags.map((t) => t.user.username)).toContain(F.B)
  expect(post.media[0].altText).toBe('보라색 단색 시험 사진')
  await expect(page.getByText(/AI 정보|AI로 만든/).first()).toBeVisible()
  await expect(page.getByRole('link', { name: /남산/ }).first()).toBeVisible()
  await expect(page.locator('img[alt="보라색 단색 시험 사진"]').first()).toBeAttached()
})
