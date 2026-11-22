import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Crown } from 'lucide-react'
import { Avatar } from '../../components/Avatar'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'
import { useMe } from '../../lib/auth'
import { fullDate } from '../../lib/format'
import { useSubscription, won } from '../../lib/shop'
import type { SubscriptionInfo, UserSummary } from '../../lib/types'

/** 설정 → 구독 (/accounts/subscriptions): 내가 구독 중인 크리에이터, (프로페셔널 계정) 내 구독 가격·구독자 */
export function SubscriptionsPage() {
  const navigate = useNavigate()
  const { data: me } = useMe()
  const mine = useQuery({ queryKey: ['my-subscriptions'], queryFn: () => api.get<SubscriptionInfo[]>('/api/me/subscriptions') })
  const professional = !!me && me.accountType !== 'PERSONAL'

  return (
    <div className="mx-auto max-w-[600px] pb-10">
      <header className="sticky top-0 z-20 flex items-center gap-3 border-b border-border bg-bg px-3 py-3">
        <button aria-label="뒤로" onClick={() => navigate(-1)}>
          <ChevronLeft size={26} />
        </button>
        <h1 className="font-semibold">구독</h1>
      </header>

      {professional && me && <CreatorSection username={me.user.username} />}

      <section className="py-2">
        <h2 className="px-4 pt-3 pb-1 text-sm text-muted">구독 중인 크리에이터</h2>
        {mine.data?.length === 0 && <p className="px-4 py-6 text-sm text-muted">구독 중인 크리에이터가 없습니다. 프로필의 "구독" 버튼으로 구독할 수 있어요.</p>}
        <ul>
          {mine.data?.map((s) => (
            <li key={s.creator.id}>
              <Link to={`/${s.creator.username}`} className="flex items-center gap-3 px-4 py-3 hover:bg-surface-2">
                <Avatar user={s.creator} size={44} />
                <span className="min-w-0 flex-1 text-sm">
                  <span className="block font-semibold">{s.creator.username}</span>
                  <span className="text-muted">
                    월 {won(s.price ?? 0)}
                    {s.renewsAt && ` · ${fullDate(s.renewsAt)} 갱신`}
                  </span>
                </span>
                <Crown size={18} className="text-violet-500" />
              </Link>
            </li>
          ))}
        </ul>
      </section>
      <p className="px-4 text-xs text-muted">연구용 가짜 구독입니다. 실제로 결제·갱신되지 않습니다.</p>
    </div>
  )
}

function CreatorSection({ username }: { username: string }) {
  const qc = useQueryClient()
  const { data: info } = useSubscription(username)
  const subscribers = useQuery({ queryKey: ['my-subscribers'], queryFn: () => api.get<UserSummary[]>('/api/me/subscribers') })
  const [price, setPrice] = useState('')
  const save = useMutation({
    mutationFn: (p: number | null) => api.put<{ price: number | null }>('/api/me/subscription-price', { price: p }),
    onSuccess: (r) => {
      void qc.invalidateQueries({ queryKey: ['subscription', username] })
      setPrice('')
      toast(r.price == null ? '구독을 받지 않습니다' : `구독 가격을 월 ${won(r.price)}으로 정했습니다`)
    },
    onError: (e) => toast((e as Error).message),
  })
  return (
    <section className="space-y-3 border-b border-border p-4">
      <h2 className="text-sm text-muted">내 구독 (크리에이터)</h2>
      <p className="text-sm">
        {info?.price != null ? (
          <>
            월 <b>{won(info.price)}</b> · 구독자 <b>{info.subscriberCount ?? 0}명</b> · 구독자 전용 게시물 {info.exclusiveCount}개
          </>
        ) : (
          '아직 구독을 받지 않습니다. 가격을 정하면 프로필에 "구독" 버튼이 생기고, 게시물·릴스를 구독자 전용으로 올릴 수 있어요.'
        )}
      </p>
      <div className="flex gap-2">
        <input
          className="input flex-1"
          inputMode="numeric"
          placeholder="월 가격 (990~99,000원)"
          value={price}
          onChange={(e) => setPrice(e.target.value.replace(/[^0-9]/g, ''))}
        />
        <button className="btn btn-primary" disabled={!price || save.isPending} onClick={() => save.mutate(Number(price))}>
          저장
        </button>
      </div>
      {info?.price != null && (
        <button className="text-sm text-danger" onClick={() => save.mutate(null)}>
          구독 받지 않기
        </button>
      )}
      {!!subscribers.data?.length && (
        <div>
          <p className="mb-2 text-sm font-semibold">최근 구독자</p>
          <div className="flex flex-wrap gap-2">
            {subscribers.data.slice(0, 30).map((u) => (
              <Link key={u.id} to={`/${u.username}`} title={u.username}>
                <Avatar user={u} size={36} />
              </Link>
            ))}
          </div>
        </div>
      )}
    </section>
  )
}
