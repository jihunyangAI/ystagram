// 전체 기능 점검: 릴스(올리기·시험 릴스·좋아요·댓글·소리·리믹스·선물), 라이브(가짜 라이브 시청·댓글·하트·배지, 내 방송)
import path from 'node:path'
import { expect, test } from '@playwright/test'
import { F, open, other, sheet, useFeatureSetup } from './feature-setup'
import { call, sql } from './helpers'

useFeatureSetup()

const CLIP = path.join(import.meta.dirname, '..', '..', 'seed-media', 'reels', 'reel-000.mp4')

test('릴스: 영상 올리기(시험 릴스) → 릴스 화면 → 인사이트', async ({ page }) => {
  test.setTimeout(150_000)
  await open(page, F.A, '/create/reel')
  await page.locator('input[type="file"]').first().setInputFiles(CLIP)
  await page.getByRole('textbox', { name: '문구' }).fill('E2E 릴스 #테스트')
  await page.locator('label').filter({ hasText: '시험 릴스' }).click()
  await page.getByRole('button', { name: '공유', exact: true }).click()
  await expect(page).toHaveURL(/\/reels\/[\w-]+$/, { timeout: 90_000 })
  const sc = page.url().split('/reels/')[1]
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/posts/${sc}`))
  await expect(page.locator('video').first()).toBeVisible()
  const post = await call<{ type: string }>(F.asA, 'GET', `/api/posts/${sc}`)
  expect(post.type).toBe('REEL')
  expect(sql(`select trial from posts where shortcode = '${sc}'`)[0][0], '시험 릴스').toBe('t')
  // 시험 릴스 인사이트: 결과는 하루 뒤, 지금은 "팔로워에게 공유"로 일반 릴스로 바꿀 수 있다
  await page.goto(`/p/${sc}/insights`)
  await expect(page.getByText('시험 릴스').first()).toBeVisible()
  await page.getByRole('button', { name: '팔로워에게 공유' }).click()
  await expect.poll(() => sql(`select trial_shared_at is not null from posts where shortcode = '${sc}'`)[0][0]).toBe('t')
})

test('릴스: 좋아요 → 댓글 → 소리 켜기 → 리믹스 만들기 → 원본에 "리믹스 N개"', async ({ page }) => {
  test.setTimeout(150_000)
  const [[sc, author]] = sql(
    `select p.shortcode, u.username from posts p join users u on u.id = p.author_id where p.type = 'REEL' and p.deleted_at is null and p.allow_remix and not p.trial and not p.subscribers_only and not u.is_private and u.username not in ('${F.A}') and p.remix_of_id is null order by p.like_count desc limit 1`,
  )
  await open(page, F.A, `/reels/${sc}`)
  const reel = page.getByLabel(`${author}님의 릴스`).first()
  await expect(reel).toBeVisible()
  const like = page.getByRole('button', { name: /^좋아요( 취소)?$/ }).first()
  const was = await like.getAttribute('aria-pressed')
  await like.click()
  await expect(like).toHaveAttribute('aria-pressed', was === 'true' ? 'false' : 'true')
  await like.click() // 되돌리기

  // 릴스의 댓글 버튼은 그 자리에서 댓글 창을 연다(페이지 이동 없음), 댓글 수가 바로 늘어난다
  const commentBtn = page.getByRole('button', { name: '댓글', exact: true }).filter({ visible: true }).first()
  const count = async () => Number((await commentBtn.innerText()).replace(/[^\d]/g, '') || 0)
  const before = await count()
  await commentBtn.click()
  const comments = page.getByRole('dialog', { name: '댓글' })
  await expect(comments).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`/reels/${sc}`))
  await comments.getByRole('textbox', { name: '댓글 달기' }).fill('E2E 릴스 댓글')
  await comments.getByRole('button', { name: '게시', exact: true }).click()
  await expect(comments.getByText('E2E 릴스 댓글')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(comments).toBeHidden()
  if (before < 1000) await expect.poll(count).toBe(before + 1)
  await expect(reel).toBeVisible()
  F.cleanups.push(async () => {
    for (const [id] of sql(`select c.id from comments c join users u on u.id = c.author_id where u.username = '${F.A}' and c.body = 'E2E 릴스 댓글'`))
      await call(F.asA, 'DELETE', `/api/comments/${id}`)
  })

  await page.getByRole('button', { name: '소리 켜기' }).first().click()
  await expect(page.getByRole('button', { name: '소리 끄기' }).first()).toBeVisible()

  // 리믹스
  await page.getByRole('button', { name: '옵션 더 보기' }).first().click()
  await sheet(page).getByRole('button', { name: /리믹스/ }).first().click()
  await expect(page).toHaveURL(new RegExp(`/reels/${sc}/remix`))
  await page.locator('input[type="file"]').first().setInputFiles(CLIP)
  await page.getByRole('textbox', { name: '문구' }).fill('E2E 리믹스')
  await page.getByRole('button', { name: '공유', exact: true }).click()
  await expect(page).toHaveURL(/\/reels\/[\w-]+$/, { timeout: 90_000 })
  const remix = page.url().split('/reels/')[1]
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/posts/${remix}`))
  await expect(page.getByLabel('원본 릴스').first()).toBeVisible()
  await expect(page.getByText(/리믹스함/).first()).toBeVisible()
  const remixes = await call<unknown[] | { items: unknown[] }>(F.asA, 'GET', `/api/posts/${sc}/remixes`)
  expect((Array.isArray(remixes) ? remixes : remixes.items).length).toBeGreaterThan(0)
})

test('릴스: 선물(별) 보내기 → 작성자에게 받은 별', async ({ page }) => {
  const [[sc, author]] = sql(
    `select p.shortcode, u.username from posts p join users u on u.id = p.author_id where p.type = 'REEL' and p.deleted_at is null and not p.trial and not p.subscribers_only and not u.is_private and u.account_type = 'CREATOR' and u.username <> '${F.A}' limit 1`,
  )
  const before = Number(sql(`select coalesce(sum(stars), 0) from gifts g join posts p on p.id = g.post_id where p.shortcode = '${sc}'`)[0][0])
  await open(page, F.A, `/reels/${sc}`)
  await expect(page.getByLabel(`${author}님의 릴스`).first()).toBeVisible()
  await page.getByRole('button', { name: '선물 보내기' }).first().click()
  await sheet(page).getByRole('button', { name: /10/ }).first().click()
  await sheet(page).getByRole('button', { name: /결제하고 보내기/ }).click()
  await expect(page.getByText(/보냈|선물/).last()).toBeVisible()
  await expect.poll(() => Number(sql(`select coalesce(sum(stars), 0) from gifts g join posts p on p.id = g.post_id where p.shortcode = '${sc}'`)[0][0])).toBeGreaterThan(before)
})

test('라이브: 가짜 라이브 보기 → 댓글 → 하트 → 배지(가짜 결제)', async ({ page }) => {
  await open(page, F.A, '/')
  const liveBtn = page.getByLabel(/님의 라이브 방송/).first()
  await expect(liveBtn).toBeVisible()
  await liveBtn.click()
  await expect(page).toHaveURL(/\/live\/\d+/)
  await expect(page.locator('video').first()).toBeVisible()
  const box = page.getByPlaceholder('댓글 달기…')
  await box.fill('E2E 라이브 댓글')
  await box.press('Enter')
  await expect(page.getByText('E2E 라이브 댓글')).toBeVisible()
  await page.getByRole('button', { name: '하트' }).click()
  await page.getByRole('button', { name: '배지 보내기' }).click()
  await sheet(page).getByRole('button', { name: /배지 1단계/ }).click()
  await expect(page.getByText('배지를 보냈어요 (가짜 결제)')).toBeVisible()
  await page.getByRole('button', { name: '나가기' }).click()
})

test.describe('내 라이브 방송', () => {
  // 카메라·마이크는 설정 파일의 Chrome 가짜 장치를 쓴다 (권한 창 없이)
  test.use({ permissions: ['camera', 'microphone'] })

  test('라이브: 방송 시작 → 시청자 입장·댓글이 진행자에게 → 종료 요약', async ({ page, browser }) => {
    test.setTimeout(120_000)
    await open(page, F.A, '/live/new')
    await page.getByPlaceholder('제목 추가 (선택)').fill('E2E 방송')
    await page.getByRole('button', { name: /라이브 방송 시작/ }).click()
    await expect(page).toHaveURL(/\/live\/\d+/)
    const liveId = page.url().split('/live/')[1]
    F.cleanups.push(() => call(F.asA, 'POST', `/api/lives/${liveId}/end`).catch(() => undefined))
    await expect(page.getByRole('button', { name: '방송 끝내기' })).toBeVisible()

    const b = await other(browser, F.B, `/live/${liveId}`)
    await expect(b.page.getByText('E2E 방송').first()).toBeVisible()
    const bb = b.page.getByPlaceholder('댓글 달기…')
    await bb.fill('E2E 시청자 댓글')
    await bb.press('Enter')
    await expect(page.getByText('E2E 시청자 댓글')).toBeVisible()
    await expect(page.getByText(new RegExp(`${F.B}.*참여했습니다`)).first()).toBeVisible()
    // 시청자 브라우저로 영상이 실제로 연결된다 (같은 PC)
    await expect
      .poll(() => b.page.evaluate(() => document.querySelector('video')?.readyState ?? 0), { timeout: 30_000 })
      .toBeGreaterThanOrEqual(2)
    await b.close()

    await page.getByRole('button', { name: '방송 끝내기' }).click()
    await page.getByRole('dialog').getByRole('button', { name: '지금 끝내기' }).click()
    await expect(page.getByText('라이브 방송을 종료했습니다')).toBeVisible()
    await expect(page.getByText('시청자', { exact: true })).toBeVisible()
  })
})
