// 기획서 §16.3 비기능 기준: 360px 가로 스크롤 없음, 키보드만으로 주요 흐름, 피드 응답 300ms(로컬, medium)
import { expect, test } from '@playwright/test'
import { apiAs, call, expectNoHorizontalScroll, loginAs, pickUsers } from './helpers'

let A: string, B: string

test.beforeAll(async () => {
  ;({ a: A, b: B } = await pickUsers())
})

test.describe('모바일 폭 360px에서 가로 스크롤 없음', () => {
  test.use({ viewport: { width: 360, height: 760 } })

  const pages = [
    '/',
    '/explore',
    '/explore/search',
    '/reels',
    '/direct/inbox',
    '/notifications',
    '/create',
    '/create/story',
    '/create/reel',
    '/live/new',
    '/accounts/edit',
    '/accounts/settings',
    '/accounts/algorithm',
    '/accounts/shop',
    '/accounts/subscriptions',
    '/archive/stories',
    '/insights',
    '/lab',
    '/lab/algorithm',
    '/lab/simulator',
    '/lab/flags',
    '/lab/events',
    '/lab/moderation',
    '/lab/ads',
    '/lab/commerce',
  ]

  test('주요 화면', async ({ page }) => {
    test.setTimeout(180_000)
    await loginAs(page, A, '/')
    const bad: string[] = []
    const check = async (label: string) => {
      await page.waitForLoadState('networkidle').catch(() => undefined)
      await page.waitForTimeout(300)
      const { scroll, client } = await page.evaluate(() => ({
        scroll: document.documentElement.scrollWidth,
        client: document.documentElement.clientWidth,
      }))
      if (scroll > client) bad.push(`${label} (${scroll}px)`)
    }
    for (const path of pages) {
      await page.goto(path)
      await check(path)
    }
    // 내용이 있는 화면: 프로필, 게시물 상세, 대화방, 해시태그
    await page.goto(`/${A}`)
    await check('/프로필')
    const firstPost = await page.locator('a[href^="/p/"]').first().getAttribute('href')
    if (firstPost) {
      await page.goto(firstPost)
      await check('/p/상세')
    }
    await page.goto(`/${B}`)
    await check('/다른 사람 프로필')
    await page.goto('/direct/inbox')
    const thread = await page.locator('a[href^="/direct/t/"]').first().getAttribute('href')
    if (thread) {
      await page.goto(thread)
      await check('/direct/t/대화방')
    }
    expect(bad, '가로 스크롤이 생긴 화면').toEqual([])
  })

  test('라이브·제품 화면', async ({ page }) => {
    await loginAs(page, A, '/')
    const asA = await apiAs(A)
    const lives = await call<{ id: number }[]>(asA, 'GET', '/api/lives')
    if (lives.length) {
      await page.goto(`/live/${lives[0].id}`)
      await page.waitForTimeout(1000)
      await expectNoHorizontalScroll(page)
    }
    await asA.dispose()
    const product = page.locator('a[href^="/shop/p/"]').first()
    await page.goto('/')
    if (await product.count()) {
      await page.goto((await product.getAttribute('href'))!)
      await expectNoHorizontalScroll(page)
    }
  })
})

test.describe('키보드만으로 주요 흐름', () => {
  test.use({ viewport: { width: 1280, height: 900 }, isMobile: false, hasTouch: false })

  test('Tab으로 게시물에 좋아요 → 공유 창 열고 Esc로 닫기', async ({ page }) => {
    await loginAs(page, A, '/')
    const article = page.locator('article[aria-label$="님의 게시물"]').first()
    await expect(article).toBeVisible()
    const like = article.getByRole('button', { name: /^좋아요( 취소)?$/ })
    const wasLiked = (await like.getAttribute('aria-label')) === '좋아요 취소'

    // 좋아요 버튼까지 Tab으로 이동 (최대 80번)
    let reached = false
    for (let i = 0; i < 80 && !reached; i++) {
      await page.keyboard.press('Tab')
      reached = await like.evaluate((el) => el === document.activeElement)
    }
    expect(reached, 'Tab으로 좋아요 버튼에 도달').toBe(true)
    await expect(like).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(article.getByRole('button', { name: wasLiked ? '좋아요' : '좋아요 취소', exact: true })).toBeVisible()
    await page.keyboard.press('Enter') // 되돌리기
    await expect(article.getByRole('button', { name: wasLiked ? '좋아요 취소' : '좋아요', exact: true })).toBeVisible()

    // 공유(DM으로 보내기) 창: 열리면 검색창에 포커스, Esc로 닫히고 포커스가 돌아온다
    const shareBtn = article.getByRole('button', { name: /공유/ }).first()
    await shareBtn.focus()
    await page.keyboard.press('Enter')
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    const focusedInside = await dialog.evaluate((d) => d.contains(document.activeElement))
    expect(focusedInside, '창이 열리면 포커스가 창 안에').toBe(true)
    for (let i = 0; i < 15; i++) {
      await page.keyboard.press('Tab')
      expect(await dialog.evaluate((d) => d.contains(document.activeElement)), `Tab ${i + 1}번째도 창 안`).toBe(true)
    }
    await page.keyboard.press('Escape')
    await expect(dialog).toHaveCount(0)
    await expect(shareBtn).toBeFocused()
  })

  test('키보드로 로그인', async ({ page }) => {
    await page.goto('/accounts/login')
    await page.keyboard.press('Tab')
    // 첫 Tab 몇 번 안에 아이디 입력창
    const id = page.getByPlaceholder('사용자 이름 또는 이메일')
    for (let i = 0; i < 10 && !(await id.evaluate((el) => el === document.activeElement)); i++) await page.keyboard.press('Tab')
    await expect(id).toBeFocused()
    await page.keyboard.type(A)
    await page.keyboard.press('Tab')
    await page.keyboard.type('yang1234')
    await page.keyboard.press('Enter')
    await expect(page).toHaveURL(/\/$/)
    await expect(page.locator('article').first()).toBeVisible()
  })

  test('포커스가 눈에 보인다 (outline)', async ({ page }) => {
    await loginAs(page, A, '/')
    await expect(page.locator('article').first()).toBeVisible()
    for (let i = 0; i < 5; i++) await page.keyboard.press('Tab')
    const style = await page.evaluate(() => {
      const el = document.activeElement as HTMLElement
      const s = getComputedStyle(el)
      return { tag: el.tagName, outline: s.outlineStyle, width: s.outlineWidth, shadow: s.boxShadow }
    })
    expect(style.outline !== 'none' || style.shadow !== 'none', JSON.stringify(style)).toBe(true)
  })
})

test('medium 시드 기준 피드 첫 화면 API 300ms 이내 (로컬)', async () => {
  const asA = await apiAs(A)
  const measure = async (url: string) => {
    const times: number[] = []
    for (let i = 0; i < 6; i++) {
      const t = performance.now()
      await call(asA, 'GET', url)
      times.push(performance.now() - t)
    }
    times.sort((x, y) => x - y)
    return times[Math.floor(times.length / 2)] // 중앙값 (첫 요청의 캐시 영향 제외)
  }
  const result: Record<string, number> = {}
  for (const url of ['/api/feed?mode=home', '/api/feed?mode=following', '/api/stories/tray', '/api/explore', '/api/reels']) {
    result[url] = Math.round(await measure(url))
  }
  console.log('  응답 시간(중앙값, ms):', result)
  await asA.dispose()
  for (const [url, ms] of Object.entries(result)) expect(ms, url).toBeLessThan(300)
})
