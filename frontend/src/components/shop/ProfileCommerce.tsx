import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Crown, Lock, Plus } from 'lucide-react'
import { Avatar } from '../Avatar'
import { Modal } from '../Modal'
import { toast } from '../Toast'
import { api } from '../../lib/api'
import { fullDate } from '../../lib/format'
import { useSubscription, won } from '../../lib/shop'
import type { ProductView, SubscriptionInfo } from '../../lib/types'
import { ProductCard } from '../../pages/shop/ShopPages'

/** 프로필의 "구독" 버튼 (구독을 받는 크리에이터에게만) */
export function SubscribeButton({ username, className }: { username: string; className?: string }) {
  const { data } = useSubscription(username)
  const [open, setOpen] = useState(false)
  if (!data || data.mine || data.price == null) return null
  return (
    <>
      <button
        className={className ?? 'btn flex-1 bg-gradient-to-r from-violet-500 to-fuchsia-500 text-white md:flex-none'}
        onClick={() => setOpen(true)}
      >
        <Crown size={16} /> {data.subscribed ? '구독 중' : '구독'}
      </button>
      {open && <SubscribeModal info={data} onClose={() => setOpen(false)} />}
    </>
  )
}

/** 구독하기·구독 관리 (가짜 결제) */
export function SubscribeModal({ info, onClose }: { info: SubscriptionInfo; onClose: () => void }) {
  const qc = useQueryClient()
  const name = info.creator.username
  const [done, setDone] = useState(false)
  const update = (s: SubscriptionInfo) => {
    qc.setQueryData(['subscription', name], s)
    void qc.invalidateQueries({ queryKey: ['exclusive', name] })
    void qc.invalidateQueries({ queryKey: [name] })
  }
  const subscribe = useMutation({
    mutationFn: () => api.post<SubscriptionInfo>(`/api/users/${name}/subscription`),
    onSuccess: (s) => {
      update(s)
      setDone(true)
    },
    onError: (e) => toast((e as Error).message),
  })
  const cancel = useMutation({
    mutationFn: () => api.delete<SubscriptionInfo>(`/api/users/${name}/subscription`),
    onSuccess: (s) => {
      update(s)
      toast('구독을 취소했습니다')
      onClose()
    },
  })

  return (
    <Modal open onClose={onClose} title={info.subscribed && !done ? '구독 관리' : '구독'}>
      <div className="space-y-4 p-5 text-center text-sm">
        <Avatar user={info.creator} size={72} className="mx-auto" />
        {done ? (
          <>
            <CheckCircle2 size={40} className="mx-auto text-green-500" />
            <p className="font-semibold">{name}님을 구독했어요 (가짜 결제)</p>
            <p className="text-muted">댓글에 구독자 배지가 붙고, 구독자 전용 게시물을 볼 수 있어요.</p>
            <button className="btn btn-primary w-full" onClick={onClose}>
              확인
            </button>
          </>
        ) : info.subscribed ? (
          <>
            <p className="font-semibold">{name}님을 구독 중</p>
            <p className="text-muted">
              월 {won(info.price ?? 0)} · {info.since && fullDate(info.since)}부터
              {info.renewsAt && ` · ${fullDate(info.renewsAt)} 갱신 예정`}
            </p>
            <button className="btn btn-secondary w-full text-danger" disabled={cancel.isPending} onClick={() => cancel.mutate()}>
              구독 취소
            </button>
          </>
        ) : (
          <>
            <p className="text-base font-semibold">{name}님 구독하기</p>
            <ul className="space-y-1.5 text-left">
              <li>♛ 댓글에 보라색 구독자 배지</li>
              <li>🔒 구독자 전용 게시물 {info.exclusiveCount}개 보기</li>
              <li>💜 좋아하는 크리에이터를 응원</li>
            </ul>
            <p className="rounded-lg bg-surface-2 p-3 text-xs text-muted">
              연구용 가짜 결제입니다. 카드 정보를 받지 않고 실제로 돈이 나가지 않습니다. 언제든 취소할 수 있어요.
            </p>
            <button
              className="btn w-full bg-gradient-to-r from-violet-500 to-fuchsia-500 text-white"
              disabled={subscribe.isPending}
              onClick={() => subscribe.mutate()}
            >
              월 {won(info.price ?? 0)}에 구독 (가짜)
            </button>
          </>
        )}
      </div>
    </Modal>
  )
}

/** 프로필 "샵" 탭 */
export function ShopTab({ username, mine }: { username: string; mine: boolean }) {
  const { data } = useQuery({
    queryKey: ['shop', username],
    queryFn: () => api.get<ProductView[]>(`/api/users/${username}/products`),
  })
  return (
    <div className="p-3 md:px-0">
      {mine && (
        <Link to="/shop/manage" className="mb-3 flex items-center gap-1 text-sm font-semibold text-primary">
          <Plus size={16} /> 제품 관리
        </Link>
      )}
      {data?.length === 0 && <p className="py-12 text-center text-sm text-muted">아직 제품이 없습니다.</p>}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {data?.map((p) => (
          <ProductCard key={p.id} p={p} />
        ))}
      </div>
    </div>
  )
}

interface ExclusiveItem {
  shortcode: string | null
  thumbUrl: string
  type: string
  locked: boolean
  createdAt: string
}

/** 프로필 "구독자 전용" 탭: 구독하지 않았으면 흐리게 + 자물쇠 */
export function ExclusiveTab({ username }: { username: string }) {
  const navigate = useNavigate()
  const { data: info } = useSubscription(username)
  const [open, setOpen] = useState(false)
  const { data } = useQuery({
    queryKey: ['exclusive', username],
    queryFn: () => api.get<ExclusiveItem[]>(`/api/users/${username}/exclusive`),
  })
  return (
    <div>
      {data?.length === 0 && <p className="py-12 text-center text-sm text-muted">구독자 전용 게시물이 없습니다.</p>}
      <div className="grid grid-cols-3 gap-0.5">
        {data?.map((it, i) => (
          <button
            key={i}
            className="relative aspect-square overflow-hidden bg-surface-2"
            onClick={() =>
              it.locked ? setOpen(true) : navigate(it.type === 'REEL' ? `/reels/${it.shortcode}` : `/p/${it.shortcode}`)
            }
          >
            <img src={it.thumbUrl} alt="" className={it.locked ? 'h-full w-full scale-110 object-cover blur-lg' : 'h-full w-full object-cover'} />
            {it.locked && (
              <span className="absolute inset-0 flex items-center justify-center bg-black/20 text-white">
                <Lock size={24} />
              </span>
            )}
            <Crown size={16} className="absolute top-2 right-2 text-white drop-shadow" />
          </button>
        ))}
      </div>
      {open && info && <SubscribeModal info={info} onClose={() => setOpen(false)} />}
    </div>
  )
}
