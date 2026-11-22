// 전체 기능 점검 1단계: 모든 화면(라우트)을 실제 데이터로 열어 콘솔 오류·예외·서버 오류가 없는지 (모바일·데스크톱)
import { expect, test } from '@playwright/test'
import { apiAs, call, expectClean, loginAs, sql, watch } from './helpers'

interface Targets {
  user: string
  other: string
  post: string
  reel: string
  tag: string
  location: string
  highlight: string
  audio: string
  conversation: string
  channel: string
  blend: string
  live: string
  product: string
  collection: string
}

let T: Targets

test.beforeAll(async () => {
  // 채널·Blend·컬렉션·대화가 모두 있는 성인 공개 계정 (화면마다 내용이 차 있도록)
  const adult = (a: string) => `not ${a}.is_private and (${a}.birth_date is null or ${a}.birth_date < now() - interval '18 years') and not ${a}.totp_enabled`
  const [u] = sql(`
    select u.username, cm.channel_id, c.id, conv.id, p.username
    from users u
    join channel_members cm on cm.user_id = u.id
    join collections c on c.user_id = u.id
    join conversation_members m1 on m1.user_id = u.id
    join conversations conv on conv.id = m1.conversation_id and not conv.is_group
    join conversation_members m2 on m2.conversation_id = conv.id and m2.user_id <> u.id
    join users p on p.id = m2.user_id and ${adult('p')}
    where ${adult('u')}
    limit 1`)
  const one = (q: string) => sql(q)[0]?.[0] ?? ''
  T = {
    user: u[0],
    channel: u[1],
    collection: u[2],
    conversation: u[3],
    blend: '',
    other: one(`select username from users where account_type = 'CREATOR' and not is_private and username <> '${u[0]}' order by followers_count desc limit 1`),
    post: one(`select p.shortcode from posts p join users a on a.id = p.author_id where p.type <> 'REEL' and p.deleted_at is null and not a.is_private and not p.subscribers_only limit 1`),
    reel: one(`select p.shortcode from posts p join users a on a.id = p.author_id where p.type = 'REEL' and p.deleted_at is null and not a.is_private and not p.subscribers_only and not p.trial limit 1`),
    tag: one(`select name from hashtags order by id limit 1`),
    location: one(`select location_id from posts where location_id is not null limit 1`),
    highlight: one(`select h.id from highlights h join users a on a.id = h.user_id where not a.is_private limit 1`),
    audio: one(`select audio_id from posts where audio_id is not null and type = 'REEL' group by audio_id order by count(*) desc limit 1`),
    live: one(`select id from lives where status = 'LIVE' limit 1`),
    product: one(`select id from products where deleted_at is null limit 1`),
  }
  // Blend는 시드에 없으므로 이 대화에서 만든다 (있으면 그대로)
  const me = await apiAs(T.user)
  const existing = await call<{ id: number } | null>(me, 'GET', `/api/direct/conversations/${T.conversation}/blend`).catch(() => null)
  const blend = existing?.id ? existing : await call<{ id: number }>(me, 'POST', `/api/direct/conversations/${T.conversation}/blend`)
  T.blend = String(blend.id)
  const partner = await apiAs(u[4])
  await call(partner, 'POST', `/api/blends/${blend.id}/accept`).catch(() => undefined)
  await me.dispose()
  await partner.dispose()
  console.log('  대상:', JSON.stringify(T))
})

const routes = (t: Targets) => [
  '/',
  '/?mode=following',
  '/reels',
  `/reels/${t.reel}`,
  '/create/reel',
  `/reels/${t.reel}/remix`,
  '/direct/inbox',
  `/direct/t/${t.conversation}`,
  `/direct/channel/${t.channel}`,
  `/direct/blend/${t.blend}`,
  '/direct/map',
  '/explore',
  '/explore/search',
  `/explore/tags/${t.tag}`,
  `/explore/locations/${t.location}`,
  '/notifications',
  '/notifications/requests',
  `/stories/${t.other}`,
  `/stories/highlights/${t.highlight}`,
  '/archive/stories',
  '/archive/posts',
  `/audio/${t.audio}`,
  '/accounts/recently-deleted',
  '/create',
  '/create/story',
  `/p/${t.post}`,
  `/p/${t.post}/insights`,
  '/accounts/edit',
  '/accounts/settings',
  '/accounts/blocked',
  '/accounts/restricted',
  '/accounts/muted',
  '/accounts/close-friends',
  '/accounts/favorites',
  '/accounts/algorithm',
  '/accounts/scheduled',
  '/accounts/two-factor',
  '/accounts/reports',
  '/accounts/shop',
  '/accounts/subscriptions',
  '/insights',
  '/lab',
  '/lab/algorithm',
  '/lab/events',
  '/lab/simulator',
  '/lab/flags',
  '/lab/moderation',
  '/lab/ads',
  '/lab/commerce',
  '/live/new',
  `/live/${t.live}`,
  `/shop/p/${t.product}`,
  '/shop/manage',
  `/${t.user}`,
  `/${t.other}`,
  `/${t.user}/saved/${t.collection}`,
  '/no-such-user-xyz',
]

for (const [name, viewport] of [
  ['모바일', { width: 390, height: 844 }],
  ['데스크톱', { width: 1280, height: 860 }],
] as const) {
  test.describe(name, () => {
    test.use({ viewport, isMobile: name === '모바일', hasTouch: name === '모바일' })

    test('모든 화면이 오류 없이 열린다', async ({ page }) => {
      test.setTimeout(300_000)
      const w = watch(page, name)
      await loginAs(page, T.user, '/')
      const blank: string[] = []
      for (const path of routes(T)) {
        await page.goto(path)
        await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => undefined)
        await page.waitForTimeout(400)
        // 화면이 통째로 비어 있으면(그리기 중 예외) 잡는다
        const text = (await page.locator('main, body').first().innerText()).trim()
        if (text.length < 5) blank.push(path)
      }
      expect(blank, '빈 화면').toEqual([])
      expectClean(w)
    })
  })
}
