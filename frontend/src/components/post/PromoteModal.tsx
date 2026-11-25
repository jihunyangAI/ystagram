import { useState } from 'react'
import { Link } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Megaphone } from 'lucide-react'
import { api, ApiError } from '../../lib/api'
import { useMe } from '../../lib/auth'
import type { PostView } from '../../lib/types'
import { Modal } from '../Modal'
import { toast } from '../Toast'

interface Promotion {
  active: boolean
  headline: string
  cta: string
  dailyBudget: number
  startedAt: string
  endsAt: string | null
  impressions: number
  reach: number
  clicks: number
  spent: number
  budget: number
}

const CTAS = ['더 알아보기', '지금 구매하기', '예약하기', '쇼핑하기', '메시지 보내기', '프로필 방문하기']

/**
 * 홍보하기 (기획서 ⚪ 가짜 광고 캠페인): 실제 결제 없이 내 게시물을 다른 사람 피드의 광고 자리에 내보낸다.
 * 성과(노출·도달·클릭)는 실제 이벤트로 세고, 쓴 돈은 노출 1번에 5원으로 계산한 가짜 금액.
 */
export function PromoteModal({ post, open, onClose }: { post: PostView; open: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const { data: me } = useMe()
  const key = ['promotion', post.shortcode]
  const { data: promo, isLoading } = useQuery({
    queryKey: key,
    queryFn: () =>
      api.get<Promotion>(`/api/posts/${post.shortcode}/promotion`).catch((e) => {
        if (e instanceof ApiError && e.status === 404) return null
        throw e
      }),
    enabled: open,
  })
  const [budget, setBudget] = useState(10_000)
  const [days, setDays] = useState(3)
  const [cta, setCta] = useState(CTAS[0])
  const [busy, setBusy] = useState(false)
  const personal = me?.accountType === 'PERSONAL'

  const run = async (stop: boolean) => {
    setBusy(true)
    try {
      const p = stop
        ? await api.delete<Promotion>(`/api/posts/${post.shortcode}/promotion`)
        : await api.post<Promotion>(`/api/posts/${post.shortcode}/promotion`, { dailyBudget: budget, days, cta })
      qc.setQueryData(key, p)
      toast(stop ? '홍보를 중단했습니다' : '홍보를 시작했습니다. 팔로우하지 않는 사람들의 피드에 광고로 나옵니다')
    } catch (e) {
      toast((e as Error).message)
    } finally {
      setBusy(false)
    }
  }
  const won = (n: number) => `${n.toLocaleString('ko-KR')}원`

  return (
    <Modal open={open} onClose={onClose} title="홍보하기">
      <div className="space-y-4 p-4 text-sm">
        <p className="flex items-start gap-2 rounded-lg bg-surface-2 p-3 text-xs text-muted">
          <Megaphone size={16} className="mt-0.5 shrink-0" />
          연구용 가짜 광고입니다. 실제 결제는 없고, 다른 사람 피드의 광고 자리에 이 게시물이 나옵니다.
        </p>
        {isLoading ? (
          <p className="text-center text-muted">불러오는 중…</p>
        ) : promo?.active ? (
          <>
            <dl className="grid grid-cols-2 gap-2" aria-label="홍보 성과">
              {(
                [
                  ['노출', promo.impressions.toLocaleString('ko-KR')],
                  ['도달', promo.reach.toLocaleString('ko-KR')],
                  ['클릭', promo.clicks.toLocaleString('ko-KR')],
                  ['쓴 금액', `${won(promo.spent)} / ${won(promo.budget)}`],
                ] as const
              ).map(([k, v]) => (
                <div key={k} className="rounded-lg border border-border p-2">
                  <dt className="text-xs text-muted">{k}</dt>
                  <dd className="font-semibold">{v}</dd>
                </div>
              ))}
            </dl>
            <p className="text-xs text-muted">
              버튼 "{promo.cta}" · 하루 {won(promo.dailyBudget)} ·{' '}
              {promo.endsAt ? `${new Date(promo.endsAt).toLocaleDateString('ko-KR')}까지` : ''}
            </p>
            <button className="btn btn-secondary w-full text-danger" disabled={busy} onClick={() => run(true)}>
              홍보 중단
            </button>
          </>
        ) : personal ? (
          <p className="text-center">
            홍보하기는 크리에이터·비즈니스 계정만 쓸 수 있습니다.{' '}
            <Link to="/accounts/edit" className="font-semibold text-primary" onClick={onClose}>
              계정 유형 바꾸기
            </Link>
          </p>
        ) : (
          <>
            <label className="block">
              <span className="mb-1 block font-semibold">하루 예산 (가짜)</span>
              <select aria-label="하루 예산" className="input" value={budget} onChange={(e) => setBudget(Number(e.target.value))}>
                {[5_000, 10_000, 30_000, 100_000].map((b) => (
                  <option key={b} value={b}>
                    {won(b)}
                  </option>
                ))}
              </select>
            </label>
            <label className="block">
              <span className="mb-1 block font-semibold">기간</span>
              <select aria-label="홍보 기간" className="input" value={days} onChange={(e) => setDays(Number(e.target.value))}>
                {[1, 3, 7, 14, 30].map((d) => (
                  <option key={d} value={d}>
                    {d}일
                  </option>
                ))}
              </select>
            </label>
            <label className="block">
              <span className="mb-1 block font-semibold">버튼 문구</span>
              <select aria-label="버튼 문구" className="input" value={cta} onChange={(e) => setCta(e.target.value)}>
                {CTAS.map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </select>
            </label>
            <p className="text-xs text-muted">총 예산 {won(budget * days)} (가짜)</p>
            <button className="btn btn-primary w-full" disabled={busy} onClick={() => run(false)}>
              {promo ? '다시 홍보하기' : '홍보 시작'}
            </button>
          </>
        )}
      </div>
    </Modal>
  )
}
