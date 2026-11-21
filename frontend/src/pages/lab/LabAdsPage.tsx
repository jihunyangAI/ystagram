import { Link } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { LabHeader } from '../../components/lab/LabNav'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'
import { useFlagValues } from '../../lib/flags'

interface AdStat {
  id: number
  shortcode: string
  thumbUrl: string | null
  advertiser: string
  headline: string
  cta: string
  topic: string
  active: boolean
  impressions: number
  reach: number
  clicks: number
  hides: number
  ctr: number
}

const fmt = (n: number) => n.toLocaleString('ko-KR')

/** Lab 가짜 광고: 광고별 노출·도달·클릭·숨기기·클릭률, 켜고 끄기 */
export function LabAdsPage() {
  const qc = useQueryClient()
  const flags = useFlagValues()
  const { data } = useQuery({ queryKey: ['lab-ads'], queryFn: () => api.get<AdStat[]>('/api/lab/ads') })
  const toggle = useMutation({
    mutationFn: (a: AdStat) => api.put<AdStat[]>(`/api/lab/ads/${a.id}`, { active: !a.active }),
    onSuccess: (list) => {
      qc.setQueryData(['lab-ads'], list)
      void qc.invalidateQueries({ queryKey: ['feed'] })
    },
    onError: (e) => toast((e as Error).message),
  })

  const total = (data ?? []).reduce(
    (t, a) => ({ imp: t.imp + a.impressions, clicks: t.clicks + a.clicks, hides: t.hides + a.hides }),
    { imp: 0, clicks: 0, hides: 0 },
  )

  return (
    <div className="mx-auto w-full max-w-[960px] space-y-6 px-4 py-8">
      <LabHeader
        title="광고"
        description="비즈니스 계정의 게시물을 '광고' 라벨로 홈 피드에 끼웁니다. 관심 주제가 맞는 광고 → 덜 본 광고 순으로 고르고, 10대 계정에는 관심사 타기팅을 하지 않습니다."
      />
      <p className="rounded-lg bg-surface-2 px-3 py-2 text-sm">
        지금 홈 피드 한 쪽에 광고 <b>{flags.adsPerPage === 0 ? '없음 (꺼짐)' : `${flags.adsPerPage}개`}</b> ·{' '}
        <Link to="/lab/flags" className="font-semibold text-primary">
          기능 플래그에서 바꾸기
        </Link>
      </p>

      {data && (
        <div className="grid grid-cols-3 gap-3">
          <Stat label="광고 노출" value={fmt(total.imp)} />
          <Stat label="클릭" value={`${fmt(total.clicks)} (${total.imp ? ((total.clicks / total.imp) * 100).toFixed(2) : '0'}%)`} />
          <Stat label="광고 숨기기" value={fmt(total.hides)} />
        </div>
      )}

      <div className="overflow-x-auto rounded-xl border border-border">
        <table className="w-full min-w-[720px] text-sm">
          <thead className="bg-surface-2 text-left text-xs text-muted">
            <tr>
              <th className="px-3 py-2">광고</th>
              <th className="px-3 py-2">타기팅 주제</th>
              <th className="px-3 py-2 text-right">노출</th>
              <th className="px-3 py-2 text-right">도달</th>
              <th className="px-3 py-2 text-right">클릭</th>
              <th className="px-3 py-2 text-right">클릭률</th>
              <th className="px-3 py-2 text-right">숨기기</th>
              <th className="px-3 py-2 text-center">상태</th>
            </tr>
          </thead>
          <tbody>
            {data?.map((a) => (
              <tr key={a.id} className={clsx('border-t border-border', !a.active && 'opacity-50')}>
                <td className="px-3 py-2">
                  <Link to={`/p/${a.shortcode}`} className="flex items-center gap-2">
                    {a.thumbUrl && <img src={a.thumbUrl} alt="" className="h-10 w-10 shrink-0 rounded object-cover" />}
                    <span className="min-w-0">
                      <span className="block truncate font-semibold">{a.advertiser}</span>
                      <span className="block max-w-[260px] truncate text-xs text-muted">
                        {a.cta} · {a.headline}
                      </span>
                    </span>
                  </Link>
                </td>
                <td className="px-3 py-2">{a.topic || '—'}</td>
                <td className="px-3 py-2 text-right tabular-nums">{fmt(a.impressions)}</td>
                <td className="px-3 py-2 text-right tabular-nums">{fmt(a.reach)}</td>
                <td className="px-3 py-2 text-right tabular-nums">{fmt(a.clicks)}</td>
                <td className="px-3 py-2 text-right tabular-nums">{a.ctr.toFixed(2)}%</td>
                <td className="px-3 py-2 text-right tabular-nums">{fmt(a.hides)}</td>
                <td className="px-3 py-2 text-center">
                  <button
                    className={clsx('rounded-full border px-2.5 py-0.5 text-xs', a.active ? 'border-primary text-primary' : 'border-border')}
                    onClick={() => toggle.mutate(a)}
                    aria-pressed={a.active}
                  >
                    {a.active ? '켜짐' : '꺼짐'}
                  </button>
                </td>
              </tr>
            ))}
            {data && data.length === 0 && (
              <tr>
                <td colSpan={8} className="px-3 py-8 text-center text-muted">
                  광고가 없습니다. 비즈니스 계정이 있는 가짜 데이터로 다시 만들어 보세요.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border p-3">
      <p className="text-xs text-muted">{label}</p>
      <p className="mt-1 text-lg font-semibold tabular-nums">{value}</p>
    </div>
  )
}
