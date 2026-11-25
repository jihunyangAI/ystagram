// 전체 기능 점검: 쇼핑·구독(가짜 결제), 계정(가입·비공개·다크 모드·로그아웃·로그인·2단계 인증), 설정·인사이트, 10대 보호, Lab 도구
import { expect, test } from '@playwright/test'
import { F, open, sheet, useFeatureSetup } from './feature-setup'
import { apiAs, call, createPost, sql } from './helpers'

useFeatureSetup()

// ------------------------------------------------------------------ 쇼핑·구독

test('쇼핑: 제품 태그 → 제품 페이지 → 위시리스트 → 가짜 결제 → 주문 내역', async ({ page }) => {
  const [[productId, sc]] = sql(
    `select pp.product_id, p.shortcode from post_products pp join posts p on p.id = pp.post_id join users u on u.id = p.author_id join products pr on pr.id = pp.product_id where p.deleted_at is null and pr.deleted_at is null and not u.is_private and not p.subscribers_only and u.username <> '${F.A}' limit 1`,
  )
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/products/${productId}/wishlist`))
  await open(page, F.A, `/p/${sc}`)
  await page.getByLabel('태그된 제품').locator(`a[href="/shop/p/${productId}"]`).first().click()
  await expect(page).toHaveURL(new RegExp(`/shop/p/${productId}`))
  await page.getByRole('button', { name: '위시리스트에 담기' }).click()
  await expect(page.getByText('위시리스트에 담았습니다')).toBeVisible()
  await page.getByRole('button', { name: /구매하기/ }).click()
  await sheet(page).getByRole('button', { name: '하나 더' }).click()
  await sheet(page).getByRole('button', { name: /결제하기 \(가짜\)/ }).click()
  await expect(page.getByText('결제가 완료되었어요 (가짜)')).toBeVisible()
  await page.goto('/accounts/shop')
  await expect(page.locator(`a[href="/shop/p/${productId}"]`).first(), '위시리스트·주문 내역').toBeVisible()
})

test('쇼핑: 비즈니스 계정이 제품 등록 → 내 게시물에 제품 태그 → 제품 삭제', async ({ page }) => {
  const [[biz]] = sql(`select username from users where account_type = 'BUSINESS' and is_fake and not is_private and not totp_enabled and suspended_until is null limit 1`)
  const asBiz = await apiAs(biz)
  await open(page, biz, '/shop/manage')
  await page.locator('input[type="file"]').first().setInputFiles((await import('./helpers')).solidPng('product.png', [240, 200, 40]))
  await page.getByPlaceholder('제품 이름').fill('E2E 머그컵')
  await page.getByPlaceholder('가격 (원)').fill('12000')
  await page.getByRole('button', { name: '등록', exact: true }).click()
  await expect(page.getByText('E2E 머그컵').first()).toBeVisible()
  const [[pid]] = sql(`select id from products where name = 'E2E 머그컵' and deleted_at is null order by id desc limit 1`)
  F.cleanups.push(() => call(asBiz, 'DELETE', `/api/products/${pid}`).catch(() => undefined).finally(() => asBiz.dispose()))

  // 내 게시물 ⋯ → 제품 태그
  const [[sc]] = sql(`select p.shortcode from posts p join users u on u.id = p.author_id where u.username = '${biz}' and p.deleted_at is null and p.type <> 'REEL' order by p.id desc limit 1`)
  const before = sql(`select product_id from post_products pp join posts p on p.id = pp.post_id where p.shortcode = '${sc}'`).map((r) => Number(r[0]))
  F.cleanups.push(() => call(asBiz, 'PUT', `/api/posts/${sc}/products`, { productIds: before }).catch(() => undefined))
  await page.goto(`/p/${sc}`)
  await page.getByRole('button', { name: /옵션/ }).first().click()
  await sheet(page).getByRole('button', { name: /제품 태그/ }).click()
  await sheet(page).getByText('E2E 머그컵').first().click()
  await sheet(page).getByRole('button', { name: /완료|저장|태그/ }).last().click()
  await expect(page.getByLabel('태그된 제품').getByText(/E2E 머그컵|12,000/).first()).toBeVisible()

  await page.goto('/shop/manage')
  await page.locator('li', { hasText: 'E2E 머그컵' }).getByRole('button', { name: '제품 지우기' }).click()
  await page.getByRole('dialog').getByRole('button', { name: /삭제|지우기/ }).last().click()
  await expect(page.getByText('E2E 머그컵')).toHaveCount(0)
})

test('구독: 크리에이터 구독(가짜 결제) → 구독자 전용 열림 → 구독 취소', async ({ page }) => {
  const [[creator]] = sql(`
    select u.username from users u
    where u.subscription_price is not null and not u.is_private and u.username <> '${F.A}'
      and exists (select 1 from posts p where p.author_id = u.id and p.subscribers_only and p.deleted_at is null)
      and not exists (select 1 from subscriptions s join users me on me.id = s.subscriber_id where s.creator_id = u.id and me.username = '${F.A}' and s.status = 'ACTIVE')
    limit 1`)
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/users/${creator}/subscription`).catch(() => undefined))
  await open(page, F.A, `/${creator}`)
  await page.getByRole('tab', { name: /구독자 전용/ }).or(page.getByRole('link', { name: /구독자 전용/ })).first().click()
  await expect(page.locator('img.blur-lg').first(), '잠긴 게시물').toBeVisible()

  await page.getByRole('button', { name: '구독', exact: true }).first().click()
  await sheet(page).getByRole('button', { name: /구독 \(가짜\)/ }).click()
  await expect(sheet(page).getByText(/구독했어요/)).toBeVisible()
  await sheet(page).getByRole('button', { name: '확인' }).click()
  await expect(page.locator('img.blur-lg')).toHaveCount(0)
  await expect(page.getByRole('button', { name: /구독 중/ }).first()).toBeVisible()

  await page.getByRole('button', { name: /구독 중/ }).first().click()
  await sheet(page).getByRole('button', { name: '구독 취소' }).click()
  await expect(page.getByText('구독을 취소했습니다')).toBeVisible()
})

// ------------------------------------------------------------------ 계정

test('계정: 가입 → 비공개 전환 → 다크 모드 → 로그아웃 → 로그인 → 2단계 인증 켜기 → 2단계 로그인 → 끄기', async ({ page }) => {
  test.setTimeout(150_000)
  const name = `e2e_${Date.now().toString(36)}`
  await page.goto('/accounts/signup')
  await page.getByPlaceholder('이메일 주소').fill(`${name}@example.com`)
  await page.getByPlaceholder('비밀번호 (6자 이상)').fill('e2e-pass-1')
  await page.getByPlaceholder('성명').fill('이투이')
  await page.getByPlaceholder('사용자 이름').fill(name)
  await page.getByLabel(/생년월일/).fill('1995-05-05')
  // 가입 인증 코드: "이메일로 보낸" 코드는 Lab(/api/lab/codes)에서 읽는다
  await page.getByRole('button', { name: '인증 코드 받기' }).click()
  await expect(page.getByLabel('인증 코드')).toBeVisible()
  const codes = (await (await page.request.get('/api/lab/codes')).json()) as { target: string; code: string }[]
  await page.getByLabel('인증 코드').fill(codes.find((c) => c.target === `${name}@example.com`)!.code)
  await page.getByRole('button', { name: '가입', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByText(/환영합니다|추천/).first()).toBeVisible()

  // 비공개 계정
  await page.goto('/accounts/edit#privacy')
  await page.locator('label').filter({ hasText: '비공개 계정' }).click()
  await sheet(page).getByRole('button', { name: '비공개로 전환' }).click()
  await expect(page.getByText('비공개 계정으로 바꿨습니다')).toBeVisible()

  // 다크 모드
  await page.goto('/accounts/settings')
  await page.getByRole('radiogroup', { name: '화면 테마' }).getByText('다크').click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.getByRole('radiogroup', { name: '화면 테마' }).getByText(/시스템|자동/).click()

  // 로그아웃 → 비밀번호로 로그인
  await page.getByText('로그아웃').last().click()
  const yes = page.getByRole('dialog').getByRole('button', { name: '로그아웃' })
  if (await yes.isVisible().catch(() => false)) await yes.click()
  await expect(page).toHaveURL(/accounts\/login/)
  await page.getByPlaceholder('사용자 이름 또는 이메일').fill(name)
  await page.getByPlaceholder('비밀번호').fill('e2e-pass-1')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  // 로그인 전에 보던 화면(next)으로 돌아간다
  await expect(page).toHaveURL(/\/accounts\/settings$/)

  // 2단계 인증 켜기 (Lab에서 지금 코드)
  const lab = await apiAs(F.A)
  const code = async () => (await call<{ code: string }>(lab, 'GET', `/api/lab/2fa/${name}/code`)).code
  await page.goto('/accounts/two-factor')
  await page.getByRole('button', { name: '인증 앱으로 설정하기' }).click()
  await expect(page.getByText(/QR 코드를 찍으세요/)).toBeVisible() // 비밀값이 만들어진 뒤
  await page.getByRole('textbox', { name: '인증 앱 코드' }).fill(await code())
  await page.getByRole('button', { name: '확인하고 켜기' }).click()
  await expect(page.getByText('백업 코드').first()).toBeVisible()

  // 로그아웃 → 비밀번호 → 2단계 화면 → Lab 코드 채우기
  await page.goto('/accounts/settings')
  await page.getByText('로그아웃').last().click()
  if (await yes.isVisible().catch(() => false)) await yes.click()
  await page.getByPlaceholder('사용자 이름 또는 이메일').fill(name)
  await page.getByPlaceholder('비밀번호').fill('e2e-pass-1')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByLabel('보안 코드')).toBeVisible()
  await page.getByRole('button', { name: /Lab: 지금 코드 채우기/ }).click()
  await expect(page.getByLabel('보안 코드')).toHaveValue(/^\d{6}$/)
  await page.getByRole('button', { name: '확인', exact: true }).click()
  await expect(page).toHaveURL(/\/accounts\/settings$/)

  // 끄기
  await page.goto('/accounts/two-factor')
  await page.getByRole('textbox', { name: '인증 앱 코드' }).fill(await code())
  await page.getByRole('button', { name: '끄기' }).click()
  await expect(page.getByRole('button', { name: '인증 앱으로 설정하기' })).toBeVisible()
  await lab.dispose()
})

test('설정: 회원님의 알고리즘(관심 주제) → 인사이트 → 신고 내역 → 활동 화면', async ({ page }) => {
  await open(page, F.A, '/accounts/algorithm')
  // "관심 주제 추가"의 첫 주제를 넣었다가 뺀다
  const add = page.locator('section', { hasText: '관심 주제 추가' }).getByRole('button').first()
  const label = (await add.innerText()).trim()
  await add.click()
  const active = page.locator('section', { hasText: '지금 관심 주제' })
  const row = active.locator('li', { hasText: label.replace(/^\S+\s/, '') })
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: /빼기/ }).click()
  await expect(page.locator('section', { hasText: '관심 주제 추가' }).getByRole('button', { name: label })).toBeVisible()

  await page.goto('/insights')
  await expect(page.getByText(/도달|노출|참여/).first()).toBeVisible()
  await page.goto('/accounts/reports')
  await expect(page.getByRole('heading').first()).toBeVisible()
})

test('10대 계정: 팔로우하지 않는 성인이 메시지를 보내면 막힌다', async () => {
  const [[teen]] = sql(`
    select t.username from users t
    where t.is_fake and t.birth_date > now() - interval '18 years'
      and not exists (select 1 from follows f join users a on a.id = f.follower_id where f.following_id = t.id and a.username = '${F.A}')
      and not exists (select 1 from follows f join users a on a.id = f.following_id where f.follower_id = t.id and a.username = '${F.A}')
    limit 1`)
  const res = await F.asA.fetch('/api/direct/conversations', {
    method: 'POST',
    data: { usernames: [teen] },
    headers: { 'X-XSRF-TOKEN': decodeURIComponent((await F.asA.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')!.value) },
  })
  if (res.ok()) {
    const conv = (await res.json()) as { id: number }
    const send = await F.asA.fetch(`/api/direct/conversations/${conv.id}/messages`, {
      method: 'POST',
      data: { body: 'E2E 안녕' },
      headers: { 'X-XSRF-TOKEN': decodeURIComponent((await F.asA.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')!.value) },
    })
    expect(send.status(), '10대에게 메시지').toBe(403)
    expect(await send.text()).toContain('TEEN_PROTECTED')
  } else {
    expect(res.status()).toBe(403)
  }
})

// ------------------------------------------------------------------ Lab 도구

test('Lab: 시뮬레이터 한 번 → 이벤트 로그·CSV → 신고 처리 → 광고 끄고 켜기 → 기능 플래그(해시태그 한도)', async ({ page }) => {
  await open(page, F.A, '/lab/simulator')
  const total = page.getByText(/지금까지 [\d,]+번/)
  await expect(total).toBeVisible()
  const count = async () => Number((await total.innerText()).replace(/\D/g, ''))
  const before = await count()
  await page.getByRole('button', { name: /지금 5번/ }).click()
  await expect.poll(count).toBeGreaterThan(before)

  await page.goto('/lab/events')
  await expect(page.locator('table, ul, ol').first()).toBeVisible()
  const download = page.waitForEvent('download')
  await page.getByRole('link', { name: /CSV/ }).or(page.getByRole('button', { name: /CSV/ })).first().click()
  expect((await download).suggestedFilename()).toMatch(/\.csv$/)

  // 신고 처리: B가 A의 새 게시물을 신고 → Lab 신고 처리에서 "문제없음"
  const sc = await createPost(F.asA, 'E2E 신고 대상 글')
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/posts/${sc}`))
  await call(F.asB, 'POST', '/api/reports', { targetType: 'POST', target: sc, reason: 'SPAM' })
  await page.goto('/lab/moderation')
  const card = page.locator('li, article, div.rounded-xl').filter({ hasText: 'E2E 신고 대상 글' }).last()
  await expect(card).toBeVisible()
  await card.getByRole('button', { name: '문제없음' }).click()
  await expect(page.getByText('처리했습니다: 문제없음')).toBeVisible()

  // 광고 끄고 켜기
  await page.goto('/lab/ads')
  const adToggle = page.getByRole('button', { name: /^(켜짐|꺼짐)$/ }).first()
  const was = await adToggle.getAttribute('aria-pressed')
  await adToggle.click()
  await expect(adToggle).toHaveAttribute('aria-pressed', was === 'true' ? 'false' : 'true')
  await adToggle.click()
  await expect(adToggle).toHaveAttribute('aria-pressed', was!)

  // 기능 플래그: 해시태그 한도 5 → 10이면 만들기 화면의 한도가 바뀐다
  const flags = await call<Record<string, unknown>>(F.asA, 'GET', '/api/lab/flags')
  F.cleanups.push(() => call(F.asA, 'PUT', '/api/lab/flags', flags))
  await call(F.asA, 'PUT', '/api/lab/flags', { ...flags, hashtagLimit: 10 })
  await page.goto('/lab/flags')
  await expect(page.getByText(/해시태그/).first()).toBeVisible()
  // 일반 사용자 화면이 읽는 플래그에도 바로 반영
  const asC = await apiAs(F.C)
  expect((await call<{ hashtagLimit: number }>(asC, 'GET', '/api/flags')).hashtagLimit).toBe(10)
  await asC.dispose()
})
