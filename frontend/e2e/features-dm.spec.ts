// 전체 기능 점검: DM(그룹·사진·반응·답장·수정·전송 취소·예약), 노트, 채널, 지도, 통화, 알림
import { expect, test } from '@playwright/test'
import { F, open, other, sheet, useFeatureSetup } from './feature-setup'
import { apiAs, call, solidPng, sql } from './helpers'

useFeatureSetup()

const adultPublic = (a: string) =>
  `${a}.is_fake and not ${a}.is_private and (${a}.birth_date is null or ${a}.birth_date < now() - interval '18 years') and not ${a}.totp_enabled and ${a}.suspended_until is null`

test('DM: 새 그룹 대화 → 글·사진 → 반응 → 답장 → 수정 → 전송 취소', async ({ page }) => {
  const [[third]] = sql(`select u.username from users u where ${adultPublic('u')} and u.username not in ('${F.A}', '${F.B}') and u.account_type = 'PERSONAL' order by u.id limit 1`)
  await open(page, F.A, '/direct/inbox')
  await page.getByRole('button', { name: '새 메시지' }).click()
  const dlg = sheet(page)
  for (const u of [F.B, third]) {
    await dlg.getByRole('textbox', { name: '받는 사람 검색' }).fill(u)
    await dlg.getByText(u, { exact: true }).first().click()
  }
  await dlg.getByPlaceholder('그룹 이름 (선택)').fill('E2E 그룹')
  await dlg.getByRole('button', { name: '채팅' }).click()
  await expect(page).toHaveURL(/\/direct\/t\/\d+/)
  const convId = page.url().split('/direct/t/')[1]
  F.cleanups.push(() => call(F.asA, 'DELETE', `/api/direct/conversations/${convId}`))
  await expect(page.getByText('E2E 그룹').first()).toBeVisible()

  const input = page.getByLabel('메시지 입력')
  await input.fill('E2E 그룹 첫 메시지')
  await input.press('Enter')
  // 말풍선: 글자가 정확히 같은 것만 (답장 인용문 "A: E2E 그룹 첫 메시지"는 제외)
  const msg = (text: string) => page.locator('div.group').filter({ has: page.getByText(text, { exact: true }) }).last()
  const bubble = msg('E2E 그룹 첫 메시지')
  await expect(bubble).toBeVisible()

  // 사진
  await page.locator('input[type="file"]').last().setInputFiles(solidPng('dm.png', [30, 160, 120]))
  await expect(page.locator('img[src*="/media/"]').last()).toBeVisible()

  // 반응
  await bubble.hover()
  await bubble.getByRole('button', { name: '반응', exact: true }).click()
  await page.getByRole('button', { name: /^반응 ❤️/ }).first().click()
  await expect(bubble.getByText('❤️')).toBeVisible()

  // 답장
  await bubble.hover()
  await bubble.getByRole('button', { name: '답장' }).click()
  await input.fill('E2E 답장 메시지')
  await input.press('Enter')
  await expect(msg('E2E 답장 메시지')).toBeVisible()

  // 수정
  await bubble.hover()
  await bubble.getByRole('button', { name: '수정 (15분 안)' }).click()
  await input.fill('E2E 그룹 첫 메시지 (고침)')
  await input.press('Enter')
  await expect(msg('E2E 그룹 첫 메시지 (고침)')).toBeVisible()
  await expect(page.getByText('수정됨').first()).toBeVisible()

  // 전송 취소
  const reply = msg('E2E 답장 메시지')
  await reply.hover()
  await reply.getByRole('button', { name: '전송 취소' }).click()
  const confirm = page.getByRole('dialog').getByRole('button', { name: /전송 취소/ })
  if (await confirm.isVisible().catch(() => false)) await confirm.click()
  await expect(page.getByText('E2E 답장 메시지', { exact: true })).toHaveCount(0)
})

test('DM: 메시지 예약 → 예약 목록 → 지금 보내기', async ({ page }) => {
  const conv = await call<{ id: number }>(F.asA, 'POST', '/api/direct/conversations', { usernames: [F.B] })
  // 실패한 이전 실행이 남긴 예약은 지운다 (나중에 저절로 보내지지 않도록)
  const clearScheduled = async () => {
    for (const s of await call<{ id: number }[]>(F.asA, 'GET', `/api/direct/conversations/${conv.id}/scheduled`))
      await call(F.asA, 'DELETE', `/api/direct/scheduled/${s.id}`)
  }
  await clearScheduled()
  F.cleanups.push(clearScheduled)
  const sent = () => Number(sql(`select count(*) from messages where conversation_id = ${conv.id} and body = 'E2E 예약 메시지'`)[0][0])
  const before = sent()

  await open(page, F.A, `/direct/t/${conv.id}`)
  const input = page.getByLabel('메시지 입력')
  await input.fill('E2E 예약 메시지')
  await page.getByRole('button', { name: '메시지 예약' }).click()
  await sheet(page).getByRole('button', { name: /예약/ }).last().click()
  // 예약 목록(접혀 있음)을 펼쳐서 "지금 보내기"
  await page.getByRole('button', { name: /예약된 메시지 1개/ }).click()
  await expect(page.locator('li', { hasText: 'E2E 예약 메시지' }).getByRole('button', { name: '지금 보내기' })).toBeVisible()
  await page.getByRole('button', { name: '지금 보내기' }).click()
  await expect.poll(sent).toBe(before + 1)
  await expect(page.getByRole('button', { name: /예약된 메시지/ })).toHaveCount(0)
})

test('노트: 쓰기 → 노트 줄에 표시 → 다른 사람이 답장(DM) → 삭제', async ({ page, browser }) => {
  F.cleanups.push(() => call(F.asA, 'DELETE', '/api/notes'))
  // B가 A를 팔로우해야 노트가 보인다 (맞팔)
  await call(F.asB, 'PUT', `/api/users/${F.A}/follow`).catch(() => undefined)
  await open(page, F.A, '/direct/inbox')
  await page.getByLabel('노트').getByText(/내 노트|생각을 남겨/).first().click()
  await sheet(page).getByRole('textbox', { name: '노트 내용' }).fill('E2E 노트 🎧')
  await sheet(page).getByRole('button', { name: '공유', exact: true }).click()
  await expect(page.getByLabel('노트').getByText('E2E 노트 🎧')).toBeVisible()

  const b = await other(browser, F.B, '/direct/inbox')
  const note = b.page.getByLabel('노트').getByText('E2E 노트 🎧')
  await expect(note).toBeVisible()
  await note.click()
  await sheet(b.page).getByRole('textbox', { name: '노트에 답장' }).fill('E2E 노트 답장')
  await sheet(b.page).getByRole('textbox', { name: '노트에 답장' }).press('Enter')
  await expect(b.page.getByText(/보냈습니다/)).toBeVisible()
  await b.close()

  await page.getByLabel('노트').getByText('E2E 노트 🎧').click()
  await sheet(page).getByRole('button', { name: '노트 삭제' }).click()
  await expect(page.getByText('노트를 삭제했습니다')).toBeVisible()
})

test('채널: 크리에이터가 투표 글 → 참여자가 투표·반응', async ({ page }) => {
  // 시드 채널(크리에이터 1인 1채널)에 주인이 투표 글을 올리고, A가 참여해서 투표
  const [[chId, creator]] = sql(`select c.id, u.username from channels c join users u on u.id = c.owner_id where ${adultPublic('u')} order by c.id limit 1`)
  const ch = { id: Number(chId) }
  const asCreator = await apiAs(creator)
  const msg = await call<{ id: number }>(asCreator, 'POST', `/api/channels/${ch.id}/messages`, { body: 'E2E 어느 쪽?', pollOptions: ['왼쪽', '오른쪽'] })
  F.cleanups.push(() => call(asCreator, 'DELETE', `/api/channels/messages/${msg.id}`).finally(() => asCreator.dispose()))
  const joined = sql(`select count(*) from channel_members cm join users u on u.id = cm.user_id where cm.channel_id = ${ch.id} and u.username = '${F.A}'`)[0][0] === '1'
  if (!joined) {
    await call(F.asA, 'PUT', `/api/channels/${ch.id}/members/me`)
    F.cleanups.push(() => call(F.asA, 'DELETE', `/api/channels/${ch.id}/members/me`))
  }

  await open(page, F.A, `/direct/channel/${ch.id}`)
  await expect(page.getByText('E2E 어느 쪽?', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: /왼쪽/ }).click()
  await expect(page.getByText(/\d+%/).first()).toBeVisible()
  await page.getByRole('button', { name: '반응 남기기' }).first().click()
  await page.getByRole('button', { name: /❤️|🔥|👍/ }).first().click()
  // 참여자는 글을 쓸 수 없다
  await expect(page.getByLabel('채널 메시지')).toHaveCount(0)
})

test('지도: 지도를 눌러 위치 공유 → 설정에서 끄기', async ({ page }) => {
  F.cleanups.push(() => call(F.asA, 'DELETE', '/api/map/me'))
  await open(page, F.A, '/direct/map')
  const map = page.getByLabel('친구 지도')
  await expect(map).toBeVisible()
  await page.waitForTimeout(1000)
  const box = (await map.boundingBox())!
  // 가운데에는 장소 핀이 몰려 있어 핀이 없는 가장자리를 누른다
  await page.mouse.click(box.x + box.width * 0.08, box.y + box.height * 0.85)
  await sheet(page).getByPlaceholder(/장소 이름/).fill('E2E 장소')
  await sheet(page).getByRole('button', { name: '공유', exact: true }).click()
  await expect(page.getByText(/공유 중/).first()).toBeVisible()
  await page.getByText(/공유 중/).first().click()
  await sheet(page).getByRole('button', { name: '위치 공유 끄기' }).click()
  await expect(page.getByText('위치 공유 꺼짐').first()).toBeVisible()
})

test('통화: 가짜 사용자에게 음성 통화 → 부재중 → 통화 기록·자동 답장', async ({ page }) => {
  test.setTimeout(120_000)
  const conv = await call<{ id: number }>(F.asA, 'POST', '/api/direct/conversations', { usernames: [F.B] })
  // 이전 실행의 통화 기록이 대화방에 남아 있으므로, 이번 통화(id가 더 큰 것)만 본다
  const lastCall = Number(sql(`select coalesce(max(id), 0) from calls`)[0][0])
  const lastMsg = Number(sql(`select coalesce(max(id), 0) from messages where conversation_id = ${conv.id}`)[0][0])
  await open(page, F.A, `/direct/t/${conv.id}`)
  const missedCards = page.getByText('부재중 통화')
  const cardsBefore = await missedCards.count()
  await page.getByRole('button', { name: '음성 통화' }).click()
  await expect(page.getByRole('button', { name: '통화 끊기' })).toBeVisible({ timeout: 20_000 })
  // 상대(가짜, 접속 안 함)는 12초 뒤 부재중 → 대화방에 통화 기록 카드가 하나 늘어난다
  await expect
    .poll(() => sql(`select status from calls where id > ${lastCall} and conversation_id = ${conv.id} order by id desc limit 1`)[0]?.[0], { timeout: 45_000 })
    .toBe('MISSED')
  await expect.poll(() => missedCards.count(), { timeout: 15_000 }).toBe(cardsBefore + 1)
  // 자동 답장 (AutoReplyBot.CALL 문장 중 하나)이 이번에 새로 온다
  await expect
    .poll(
      () =>
        sql(`select count(*) from messages where conversation_id = ${conv.id} and id > ${lastMsg} and body ~ '전화 못 받았어요|통화가 좀 어려워요|방금 봤어요|회의 중이었어요'`)[0][0],
      { timeout: 30_000 },
    )
    .toBe('1')
})

test('알림: 다른 사람이 팔로우·좋아요 → 활동 화면·알림 수 표시 → 읽음', async ({ page }) => {
  const sc = sql(`select p.shortcode from posts p join users u on u.id = p.author_id where u.username = '${F.A}' and p.deleted_at is null order by p.id desc limit 1`)[0][0]
  await call(F.asB, 'DELETE', `/api/posts/${sc}/like`).catch(() => undefined)
  await call(F.asB, 'PUT', `/api/posts/${sc}/like`)
  await open(page, F.A, '/')
  const unread = await call<{ count: number } | number>(F.asA, 'GET', '/api/notifications/unread')
  const n = typeof unread === 'number' ? unread : unread.count
  expect(n, '읽지 않은 알림').toBeGreaterThan(0)
  await page.goto('/notifications')
  await expect(page.locator('p', { hasText: F.B }).filter({ hasText: '좋아합니다' }).first()).toBeVisible()
  await expect
    .poll(async () => {
      const u = await call<{ count: number } | number>(F.asA, 'GET', '/api/notifications/unread')
      return typeof u === 'number' ? u : u.count
    })
    .toBe(0)
})
