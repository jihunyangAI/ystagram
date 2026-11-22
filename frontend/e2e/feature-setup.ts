// 전체 기능 점검(features-*.spec.ts) 공통 준비: 가짜 계정 고르기, 정리 목록, 페이지마다 오류 감시
import { expect, test } from '@playwright/test'
import type { APIRequestContext, Browser, Page } from '@playwright/test'
import { apiAs, call, expectClean, loginAs, pickUsers, watch } from './helpers'
import type { Watch } from './helpers'

export const F = {
  A: '',
  B: '',
  C: '',
  asA: null as unknown as APIRequestContext,
  asB: null as unknown as APIRequestContext,
  cleanups: [] as (() => Promise<unknown>)[],
  w: null as unknown as Watch,
}

export function useFeatureSetup() {
  test.describe.configure({ mode: 'serial' })

  test.beforeAll(async () => {
    const u = await pickUsers()
    F.A = u.a
    F.B = u.b
    F.C = u.c
    F.asA = await apiAs(F.A)
    F.asB = await apiAs(F.B)
    F.cleanups = []
    // A가 B를 팔로우 (스토리·DM 등)
    await call(F.asA, 'PUT', `/api/users/${F.B}/follow`).catch(() => undefined)
    console.log(`  A=${F.A} B=${F.B} C=${F.C}`)
  })

  test.afterAll(async () => {
    for (const f of F.cleanups.reverse()) await f().catch((e) => console.warn('  정리 실패:', (e as Error).message))
    await F.asA.dispose()
    await F.asB.dispose()
  })

  test.beforeEach(async ({ page }) => {
    F.w = watch(page)
  })

  test.afterEach(async () => {
    expectClean(F.w)
  })
}

export async function open(page: Page, user: string, path: string) {
  await loginAs(page, user, path)
  await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => undefined)
}

/** 다른 사람으로 새 브라우저 창 (오류 감시 포함). 끝나면 close()로 닫고 오류를 확인 */
export async function other(browser: Browser, user: string, path: string) {
  const page = await (await browser.newContext()).newPage()
  const w = watch(page, user)
  await open(page, user, path)
  return {
    page,
    close: async () => {
      await page.context().close()
      expectClean(w)
    },
  }
}

export const sheet = (page: Page) => page.getByRole('dialog')

/** 스토리 뷰어에서 조건이 보일 때까지 다음 스토리로 */
export async function nextUntil(page: Page, visible: () => Promise<boolean>, max = 20) {
  for (let i = 0; i < max && !(await visible()); i++) {
    await page.keyboard.press('ArrowRight')
    await page.waitForTimeout(300)
  }
  expect(await visible()).toBe(true)
}
