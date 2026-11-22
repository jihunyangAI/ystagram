import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { Avatar } from '../../components/Avatar'
import { LabHeader } from '../../components/lab/LabNav'
import { api } from '../../lib/api'
import { formatCount, timeAgo } from '../../lib/format'
import { won } from '../../lib/shop'
import type { UserSummary } from '../../lib/types'

type Kind = 'ORDER' | 'SUBSCRIPTION' | 'GIFT' | 'BADGE'

interface Overview {
  totals: { kind: Kind; count: number; amount: number }[]
  top: { user: UserSummary; orders: number; subscribers: number; stars: number; amount: number }[]
  recent: { kind: Kind; from: UserSummary; to: UserSummary; label: string; amount: number; at: string }[]
  now: { lives: number; fakeLives: number; liveViewers: number; ringingCalls: number; activeCalls: number }
}

const LABEL: Record<Kind, string> = {
  ORDER: '쇼핑 주문',
  SUBSCRIPTION: '활성 구독 (월)',
  GIFT: '릴스 선물',
  BADGE: '라이브 배지',
}

/** Lab → 가짜 수익: 주문·구독·선물·배지 합계, 많이 번 계정, 최근 결제, 지금 켜진 라이브·통화 */
export function LabCommercePage() {
  const { data } = useQuery({
    queryKey: ['lab-commerce'],
    queryFn: () => api.get<Overview>('/api/lab/commerce'),
    refetchInterval: 10_000,
  })

  return (
    <div className="mx-auto w-full max-w-[960px] space-y-6 px-4 py-8">
      <LabHeader
        title="가짜 수익·라이브"
        description="쇼핑 주문, 구독, 릴스 선물, 라이브 배지는 모두 가짜 결제입니다. 실제 돈은 오가지 않고, 연구용 숫자로만 쌓입니다."
      />

      {data && (
        <>
          <section className="grid grid-cols-2 gap-3 md:grid-cols-4">
            {data.totals.map((t) => (
              <div key={t.kind} className="rounded-xl border border-border p-4">
                <p className="text-xs text-muted">{LABEL[t.kind]}</p>
                <p className="mt-1 text-xl font-semibold tabular-nums">{won(t.amount)}</p>
                <p className="text-xs text-muted">{formatCount(t.count)}건</p>
              </div>
            ))}
          </section>

          <section className="rounded-xl border border-border p-4 text-sm">
            <h2 className="mb-2 font-semibold">지금</h2>
            <p>
              라이브 <b>{data.now.lives}</b>개 (가짜 영상 라이브 {data.now.fakeLives}개) · 누적 시청자 {data.now.liveViewers}명 · 통화 중{' '}
              <b>{data.now.activeCalls}</b>건 · 벨 울리는 중 {data.now.ringingCalls}건
            </p>
            <p className="mt-1 text-xs text-muted">
              가짜 라이브는 1분마다 3개가 켜져 있도록 맞춰지고, 사람이 보고 있으면 가짜 팔로워가 댓글·하트를 보냅니다. 기능 플래그 "가짜 라이브"로 끌 수 있어요.
            </p>
          </section>

          <section>
            <h2 className="mb-2 font-semibold">많이 번 계정</h2>
            <div className="overflow-x-auto rounded-xl border border-border">
              <table className="w-full text-sm">
                <thead className="bg-surface-2 text-left text-xs text-muted">
                  <tr>
                    <th className="px-3 py-2">계정</th>
                    <th className="px-3 py-2 text-right">주문</th>
                    <th className="px-3 py-2 text-right">구독자</th>
                    <th className="px-3 py-2 text-right">받은 별·배지</th>
                    <th className="px-3 py-2 text-right">합계</th>
                  </tr>
                </thead>
                <tbody>
                  {data.top.map((e) => (
                    <tr key={e.user.id} className="border-t border-border">
                      <td className="px-3 py-2">
                        <Link to={`/${e.user.username}`} className="flex items-center gap-2">
                          <Avatar user={e.user} size={24} /> {e.user.username}
                        </Link>
                      </td>
                      <td className="px-3 py-2 text-right tabular-nums">{e.orders}</td>
                      <td className="px-3 py-2 text-right tabular-nums">{e.subscribers}</td>
                      <td className="px-3 py-2 text-right tabular-nums">{e.stars}</td>
                      <td className="px-3 py-2 text-right font-semibold tabular-nums">{won(e.amount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section>
            <h2 className="mb-2 font-semibold">최근 결제</h2>
            <ul className="divide-y divide-border rounded-xl border border-border">
              {data.recent.map((p, i) => (
                <li key={i} className="flex items-center gap-3 px-3 py-2 text-sm">
                  <span className="w-20 shrink-0 text-xs text-muted">{LABEL[p.kind].replace(' (월)', '')}</span>
                  <span className="min-w-0 flex-1 truncate">
                    <b>{p.from.username}</b> → <b>{p.to.username}</b> · {p.label}
                  </span>
                  <span className="shrink-0 tabular-nums">{won(p.amount)}</span>
                  <span className="w-14 shrink-0 text-right text-xs text-muted">{timeAgo(p.at)}</span>
                </li>
              ))}
            </ul>
          </section>
        </>
      )}
    </div>
  )
}
