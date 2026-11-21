import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDownRight, ArrowUpRight, ChevronLeft, Clapperboard, FlaskConical } from 'lucide-react'
import { toast } from '../../components/Toast'
import clsx from 'clsx'
import { api } from '../../lib/api'
import { formatCount } from '../../lib/format'
import type { GridItem, PostView } from '../../lib/types'

interface Totals {
  reach: number
  impressions: number
  interactions: number
  likes: number
  comments: number
  saves: number
  shares: number
  profileVisits: number
  newFollowers: number
  lostFollowers: number
  followers: number
}
interface Day {
  date: string
  reach: number
  impressions: number
  interactions: number
  newFollowers: number
}
interface Share {
  key: string
  label: string
  count: number
  ratio: number
}
interface AccountInsights {
  days: number
  totals: Totals
  previous: Totals
  daily: Day[]
  topPosts: { post: GridItem; reach: number; impressions: number; interactions: number }[]
  surfaces: Share[]
  audienceTopics: Share[]
}
interface PostInsights {
  shortcode: string
  reach: number
  impressions: number
  likes: number
  comments: number
  saves: number
  shares: number
  profileVisits: number
  avgWatchMs: number | null
  completion: number | null
  durationMs: number | null
  surfaces: Share[]
  daily: Day[]
  /** 시험 릴스였던 게시물만 */
  trial: TrialInfo | null
}

interface TrialInfo {
  active: boolean
  resultsAt: string
  ready: boolean
  sharedAt: string | null
  nonFollowerReach: number
}

type Metric = 'reach' | 'impressions' | 'interactions' | 'newFollowers'
const METRIC_LABEL: Record<Metric, string> = {
  reach: '도달한 계정',
  impressions: '노출',
  interactions: '참여',
  newFollowers: '새 팔로워',
}

/**
 * 인사이트 (분석 문서 §18): 실제로 쌓인 이벤트(노출·프로필 방문)와 좋아요·댓글·저장·팔로우 기록을 집계.
 */
export function InsightsPage() {
  const [days, setDays] = useState<7 | 30 | 90>(7)
  const [metric, setMetric] = useState<Metric>('reach')
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['insights', days],
    queryFn: () => api.get<AccountInsights>(`/api/me/insights?days=${days}`),
  })

  return (
    <div className="mx-auto max-w-[720px] pb-10">
      <PageHeader title="인사이트" />
      <div role="radiogroup" aria-label="기간" className="flex gap-2 px-4 pt-4">
        {([7, 30, 90] as const).map((d) => (
          <button
            key={d}
            role="radio"
            aria-checked={days === d}
            onClick={() => setDays(d)}
            className={clsx(
              'rounded-full border px-3 py-1 text-sm',
              days === d ? 'border-text bg-text font-semibold text-bg' : 'border-border',
            )}
          >
            최근 {d}일
          </button>
        ))}
      </div>

      {isLoading && <p className="p-10 text-center text-sm text-muted">불러오는 중…</p>}
      {isError && <p className="p-10 text-center text-sm text-danger">{error.message}</p>}

      {data && (
        <>
          <section className="grid grid-cols-2 gap-2 px-4 pt-4 sm:grid-cols-4">
            <Tile label="도달한 계정" value={data.totals.reach} prev={data.previous.reach} />
            <Tile label="노출" value={data.totals.impressions} prev={data.previous.impressions} />
            <Tile label="참여" value={data.totals.interactions} prev={data.previous.interactions} />
            <Tile label="프로필 방문" value={data.totals.profileVisits} prev={data.previous.profileVisits} />
          </section>
          <p className="px-4 pt-2 text-xs text-muted">이전 {days}일과 비교</p>

          <section className="px-4 pt-6">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="font-semibold">{METRIC_LABEL[metric]} (하루별)</h2>
              <div role="radiogroup" aria-label="지표" className="flex flex-wrap gap-1">
                {(Object.keys(METRIC_LABEL) as Metric[]).map((m) => (
                  <button
                    key={m}
                    role="radio"
                    aria-checked={metric === m}
                    onClick={() => setMetric(m)}
                    className={clsx(
                      'rounded-md px-2 py-1 text-xs',
                      metric === m ? 'bg-surface-2 font-semibold' : 'text-muted hover:bg-surface-2',
                    )}
                  >
                    {METRIC_LABEL[m]}
                  </button>
                ))}
              </div>
            </div>
            <BarChart days={data.daily} metric={metric} label={METRIC_LABEL[metric]} />
          </section>

          <section className="grid gap-6 px-4 pt-8 sm:grid-cols-2">
            <div>
              <h2 className="pb-2 font-semibold">참여 내역</h2>
              <dl className="space-y-1.5 text-sm">
                <Row label="좋아요" value={data.totals.likes} />
                <Row label="댓글" value={data.totals.comments} />
                <Row label="저장" value={data.totals.saves} />
                <Row label="공유 (리포스트·DM)" value={data.totals.shares} />
              </dl>
            </div>
            <div>
              <h2 className="pb-2 font-semibold">팔로워</h2>
              <dl className="space-y-1.5 text-sm">
                <Row label="전체 팔로워" value={data.totals.followers} />
                <Row label="새 팔로워" value={data.totals.newFollowers} />
                <Row label="팔로우 취소" value={data.totals.lostFollowers} />
                <Row label="순증가" value={data.totals.newFollowers - data.totals.lostFollowers} signed />
              </dl>
            </div>
          </section>

          <section className="grid gap-6 px-4 pt-8 sm:grid-cols-2">
            <ShareBars title="노출된 곳" items={data.surfaces} empty="이 기간에 노출 기록이 없습니다." />
            <ShareBars title="팔로워의 관심 주제" items={data.audienceTopics} empty="팔로워가 없습니다." />
          </section>

          <section className="px-4 pt-8">
            <h2 className="pb-2 font-semibold">도달이 많았던 게시물</h2>
            {data.topPosts.length === 0 ? (
              <p className="text-sm text-muted">이 기간에 노출된 게시물이 없습니다.</p>
            ) : (
              <div className="grid grid-cols-3 gap-1">
                {data.topPosts.map((t) => (
                  <Link key={t.post.shortcode} to={`/p/${t.post.shortcode}/insights`} className="group relative block aspect-square overflow-hidden bg-surface-2">
                    <img src={t.post.thumbUrl} alt="" className="h-full w-full object-cover" loading="lazy" />
                    {t.post.type === 'REEL' && <Clapperboard size={16} className="absolute top-1.5 right-1.5 text-white drop-shadow" />}
                    <span className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent px-2 pt-4 pb-1 text-xs font-semibold text-white">
                      도달 {formatCount(t.reach)}
                    </span>
                  </Link>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}

/** 게시물 인사이트 (작성자만) */
export function PostInsightsPage() {
  const { shortcode = '' } = useParams()
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['post-insights', shortcode],
    queryFn: () => api.get<PostInsights>(`/api/posts/${shortcode}/insights`),
  })
  return (
    <div className="mx-auto max-w-[720px] pb-10">
      <PageHeader title="게시물 인사이트" />
      {isLoading && <p className="p-10 text-center text-sm text-muted">불러오는 중…</p>}
      {isError && <p className="p-10 text-center text-sm text-danger">{error.message}</p>}
      {data?.trial && <TrialCard shortcode={shortcode} trial={data.trial} />}
      {data && !(data.trial?.active && !data.trial.ready) && (
        <>
          <section className="grid grid-cols-2 gap-2 px-4 pt-4 sm:grid-cols-4">
            <Tile label="도달한 계정" value={data.reach} />
            <Tile label="노출" value={data.impressions} />
            <Tile label="좋아요" value={data.likes} />
            <Tile label="댓글" value={data.comments} />
            <Tile label="저장" value={data.saves} />
            <Tile label="공유" value={data.shares} />
            <Tile label="프로필 방문 (7일)" value={data.profileVisits} />
            {data.completion != null && <Tile label="끝까지 본 비율" text={`${Math.round(data.completion * 100)}%`} />}
          </section>
          {data.avgWatchMs != null && data.durationMs != null && (
            <p className="px-4 pt-2 text-sm text-muted">
              평균 시청 시간 {(data.avgWatchMs / 1000).toFixed(1)}초 / 영상 길이 {(data.durationMs / 1000).toFixed(1)}초
            </p>
          )}
          <section className="px-4 pt-6">
            <h2 className="font-semibold">올린 뒤 7일 동안 도달한 계정</h2>
            <BarChart days={data.daily} metric="reach" label="도달한 계정" />
          </section>
          <section className="px-4 pt-8">
            <ShareBars title="노출된 곳" items={data.surfaces} empty="아직 노출 기록이 없습니다." />
          </section>
          <p className="px-4 pt-6">
            <Link to={`/p/${shortcode}`} className="text-sm font-semibold text-primary">
              게시물 보기
            </Link>
          </p>
        </>
      )}
    </div>
  )
}

// ------------------------------------------------------------ 조각

/** 시험 릴스: 24시간 뒤 성과 공개, 팔로워에게 공유 */
function TrialCard({ shortcode, trial }: { shortcode: string; trial: TrialInfo }) {
  const qc = useQueryClient()
  const share = useMutation({
    mutationFn: () => api.post<PostView>(`/api/posts/${shortcode}/trial/share`),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['post-insights', shortcode] })
      void qc.invalidateQueries({ queryKey: ['post', shortcode] })
      void qc.invalidateQueries({ queryKey: ['grid'] })
      toast('팔로워에게도 공유했습니다')
    },
    onError: (e) => toast((e as Error).message),
  })
  const when = new Date(trial.resultsAt).toLocaleString('ko-KR', { month: 'long', day: 'numeric', hour: 'numeric', minute: '2-digit' })
  return (
    <section className="mx-4 mt-4 rounded-xl border border-border p-4">
      <h2 className="flex items-center gap-2 font-semibold">
        <FlaskConical size={18} className="text-primary" /> 시험 릴스
      </h2>
      {!trial.active ? (
        <p className="mt-1 text-sm text-muted">
          {trial.sharedAt && new Date(trial.sharedAt).toLocaleString('ko-KR')}에 팔로워에게 공유했습니다. 이제 팔로워 피드와 프로필에도
          보입니다.
        </p>
      ) : trial.ready ? (
        <>
          <p className="mt-1 text-sm">
            팔로워가 아닌 <b>{trial.nonFollowerReach.toLocaleString('ko-KR')}명</b>에게 도달했습니다. 반응이 좋으면 팔로워에게도 공유해 보세요.
          </p>
        </>
      ) : (
        <p className="mt-1 text-sm text-muted">
          지금은 팔로워가 아닌 사람에게만 보여 주는 중입니다. 결과는 <b className="text-text">{when}</b>부터 볼 수 있어요 (Lab 시간 가속으로
          앞당길 수 있습니다).
        </p>
      )}
      {trial.active && (
        <button className="btn btn-primary mt-3" disabled={share.isPending} onClick={() => share.mutate()}>
          팔로워에게 공유
        </button>
      )}
    </section>
  )
}

function PageHeader({ title }: { title: string }) {
  const navigate = useNavigate()
  return (
    <header className="flex items-center gap-2 border-b border-border px-2 py-3">
      <button onClick={() => navigate(-1)} aria-label="뒤로" className="rounded p-1 hover:bg-surface-2">
        <ChevronLeft size={24} />
      </button>
      <h1 className="font-semibold">{title}</h1>
    </header>
  )
}

function Tile({ label, value, prev, text }: { label: string; value?: number; prev?: number; text?: string }) {
  const change = prev != null && value != null && prev > 0 ? (value - prev) / prev : null
  return (
    <div className="rounded-lg border border-border p-3">
      <div className="text-xs text-muted">{label}</div>
      <div className="pt-1 text-xl font-bold tabular-nums">{text ?? formatCount(value ?? 0)}</div>
      {change != null && (
        <div className={clsx('flex items-center gap-0.5 pt-0.5 text-xs tabular-nums', change >= 0 ? 'text-text' : 'text-muted')}>
          {change >= 0 ? <ArrowUpRight size={12} aria-hidden /> : <ArrowDownRight size={12} aria-hidden />}
          {change >= 0 ? '+' : '−'}
          {Math.abs(Math.round(change * 100))}%
        </div>
      )}
    </div>
  )
}

function Row({ label, value, signed }: { label: string; value: number; signed?: boolean }) {
  return (
    <div className="flex justify-between border-b border-border pb-1.5">
      <dt className="text-muted">{label}</dt>
      <dd className="font-semibold tabular-nums">
        {signed && value > 0 ? '+' : ''}
        {value.toLocaleString('ko-KR')}
      </dd>
    </div>
  )
}

/** 하루별 막대 (한 지표만, 기본 색 하나). 마우스를 올리면 날짜·값, "표로 보기"로 숫자 확인 */
function BarChart({ days, metric, label }: { days: Day[]; metric: Metric; label: string }) {
  const [table, setTable] = useState(false)
  const values = days.map((d) => d[metric])
  const max = Math.max(1, ...values)
  const fmtDate = (s: string) => {
    const [, m, d] = s.split('-')
    return `${Number(m)}/${Number(d)}`
  }
  return (
    <div className="pt-3">
      {table ? (
        <table className="w-full text-sm">
          <thead className="text-xs text-muted">
            <tr>
              <th className="pb-1 text-left font-normal">날짜</th>
              <th className="pb-1 text-right font-normal">{label}</th>
            </tr>
          </thead>
          <tbody>
            {days.map((d) => (
              <tr key={d.date} className="border-t border-border">
                <td className="py-1">{fmtDate(d.date)}</td>
                <td className="py-1 text-right tabular-nums">{d[metric].toLocaleString('ko-KR')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div>
          <div className="relative h-40 border-b border-border">
            <span className="absolute -top-1 right-0 text-[11px] text-muted tabular-nums">{formatCount(max)}</span>
            <div className="absolute inset-0 top-4 flex items-end gap-[2px]">
              {days.map((d, i) => (
                <div key={d.date} className="group relative flex h-full flex-1 items-end justify-center">
                  <div
                    className="w-full max-w-6 rounded-t bg-primary transition-opacity group-hover:opacity-80"
                    style={{ height: `${(values[i] / max) * 100}%`, minHeight: values[i] > 0 ? 2 : 0 }}
                  />
                  <div className="pointer-events-none absolute bottom-full mb-1 hidden rounded-md bg-text px-2 py-1 text-center text-[11px] whitespace-nowrap text-bg group-hover:block">
                    {fmtDate(d.date)}
                    <br />
                    <b className="tabular-nums">{values[i].toLocaleString('ko-KR')}</b>
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="flex justify-between pt-1 text-[11px] text-muted">
            <span>{days[0] && fmtDate(days[0].date)}</span>
            <span>{days.length > 0 && fmtDate(days[days.length - 1].date)}</span>
          </div>
        </div>
      )}
      <button onClick={() => setTable((t) => !t)} className="pt-2 text-xs text-muted hover:underline">
        {table ? '막대로 보기' : '표로 보기'}
      </button>
    </div>
  )
}

/** 비율 막대: 이름·비율은 글자색, 막대만 기본 색 */
function ShareBars({ title, items, empty }: { title: string; items: Share[]; empty: string }) {
  return (
    <div>
      <h2 className="pb-2 font-semibold">{title}</h2>
      {items.length === 0 ? (
        <p className="text-sm text-muted">{empty}</p>
      ) : (
        <ul className="space-y-2">
          {items.map((s) => (
            <li key={s.key} className="text-sm" title={`${s.count.toLocaleString('ko-KR')}건`}>
              <div className="flex justify-between">
                <span>{s.label}</span>
                <span className="tabular-nums text-muted">{Math.round(s.ratio * 100)}%</span>
              </div>
              <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-surface-2">
                <div className="h-full rounded-full bg-primary" style={{ width: `${s.ratio * 100}%` }} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
