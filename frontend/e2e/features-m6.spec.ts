// M6: §4에 있었지만 빠졌던 기능 — 게시물 ⋯ 메뉴(보관·고정·좋아요 수 숨기기·댓글 끄기), 보관함, 최근 삭제됨
import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { F, open, sheet, useFeatureSetup } from './feature-setup'
import { call, createPost } from './helpers'

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
