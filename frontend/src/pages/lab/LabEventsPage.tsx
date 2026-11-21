import { useState } from 'react'
import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { Download, RefreshCw } from 'lucide-react'
import { Link } from 'react-router'
import { LabHeader } from '../../components/lab/LabNav'
import { api } from '../../lib/api'
import { fullDate } from '../../lib/format'

interface EventRow {
  id: number
  type: string
  username: string
  shortcode: string | null
  targetUsername: string | null
  surface: string | null
  valueMs: number | null
  createdAt: string
}
interface TypeCount {
  type: string
  last24h: number
  total: number
}

const TYPE_LABEL: Record<string, string> = {
  IMPRESSION: '노출',
  VIEW_TIME: '시청 시간',
  PROFILE_VISIT: '프로필 방문',
  SKIP: '건너뛰기',
  NOT_INTERESTED: '관심 없음',
  LIKE: '좋아요',
  COMMENT: '댓글',
  SAVE: '저장',
  SHARE: '공유',
  FOLLOW: '팔로우',
  UNFOLLOW: '언팔로우',
}

/** 이벤트 로그 뷰어 (Lab L08): 조건으로 걸러 보고 CSV로 내려받는다 */
export function LabEventsPage() {
  const [type, setType] = useState('')
  const [username, setUsername] = useState('')
  const [shortcode, setShortcode] = useState('')
  const [filters, setFilters] = useState({ type: '', username: '', shortcode: '' })

  const qs = (extra: Record<string, string | number | undefined> = {}) => {
    const p = new URLSearchParams()
    Object.entries({ ...filters, ...extra }).forEach(([k, v]) => v !== undefined && v !== '' && p.set(k, String(v)))
    return p.toString()
  }

  const summary = useQuery({ queryKey: ['lab-events-summary'], queryFn: () => api.get<TypeCount[]>('/api/lab/events/summary') })
  const events = useInfiniteQuery({
    queryKey: ['lab-events', filters],
    queryFn: ({ pageParam }) =>
      api.get<{ items: EventRow[]; nextBefore: number | null }>(`/api/lab/events?${qs({ before: pageParam || undefined })}`),
    initialPageParam: 0,
    getNextPageParam: (last) => last.nextBefore ?? undefined,
  })
  const rows = events.data?.pages.flatMap((p) => p.items) ?? []

  return (
    <div className="mx-auto w-full max-w-[1000px] space-y-6 px-4 py-8">
      <LabHeader
        title="이벤트 로그"
        description="노출·시청 시간·프로필 방문·건너뛰기·관심 없음(화면이 보냄)과 좋아요·댓글·저장·공유·팔로우(서버가 남김). 추천 점수와 인사이트가 이 기록을 씁니다."
      />

      <section aria-label="종류별 건수" className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-6">
        {summary.data?.map((c) => (
          <button
            key={c.type}
            onClick={() => {
              setType(c.type)
              setFilters((f) => ({ ...f, type: c.type }))
            }}
            className="rounded-lg border border-border p-2 text-left hover:bg-surface-2"
          >
            <div className="text-xs text-muted">{TYPE_LABEL[c.type] ?? c.type}</div>
            <div className="font-bold tabular-nums">{c.total.toLocaleString('ko-KR')}</div>
            <div className="text-[11px] text-muted tabular-nums">24시간 {c.last24h.toLocaleString('ko-KR')}</div>
          </button>
        ))}
      </section>

      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault()
          setFilters({ type, username: username.replace(/^@/, ''), shortcode })
        }}
      >
        <label className="text-sm">
          <span className="block text-xs text-muted">종류</span>
          <select value={type} onChange={(e) => setType(e.target.value)} className="input mt-1 w-36">
            <option value="">전체</option>
            {Object.entries(TYPE_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="block text-xs text-muted">사용자 (보낸 사람 또는 대상)</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} className="input mt-1 w-44" placeholder="username" />
        </label>
        <label className="text-sm">
          <span className="block text-xs text-muted">게시물 코드</span>
          <input value={shortcode} onChange={(e) => setShortcode(e.target.value)} className="input mt-1 w-36" placeholder="shortcode" />
        </label>
        <button type="submit" className="btn btn-primary">
          조회
        </button>
        <button
          type="button"
          onClick={() => {
            void summary.refetch()
            void events.refetch()
          }}
          className="btn btn-secondary"
          aria-label="새로 고침"
        >
          <RefreshCw size={14} />
        </button>
        <a href={`/api/lab/events.csv?${qs()}`} className="btn btn-secondary ml-auto">
          <Download size={14} /> CSV (최대 5만 건)
        </a>
      </form>

      <div className="overflow-x-auto rounded-xl border border-border">
        <table className="w-full min-w-[640px] text-sm">
          <thead className="bg-surface-2 text-xs text-muted">
            <tr>
              <th className="px-3 py-2 text-left font-normal">시각</th>
              <th className="px-3 py-2 text-left font-normal">종류</th>
              <th className="px-3 py-2 text-left font-normal">사용자</th>
              <th className="px-3 py-2 text-left font-normal">게시물</th>
              <th className="px-3 py-2 text-left font-normal">대상</th>
              <th className="px-3 py-2 text-left font-normal">영역</th>
              <th className="px-3 py-2 text-right font-normal">값</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id} className="border-t border-border">
                <td className="px-3 py-1.5 text-xs whitespace-nowrap text-muted">{fullDate(r.createdAt)}</td>
                <td className="px-3 py-1.5">{TYPE_LABEL[r.type] ?? r.type}</td>
                <td className="px-3 py-1.5">
                  <Link to={`/${r.username}`} className="hover:underline">
                    @{r.username}
                  </Link>
                </td>
                <td className="px-3 py-1.5 font-mono text-xs">
                  {r.shortcode && (
                    <Link to={`/p/${r.shortcode}`} className="hover:underline">
                      {r.shortcode}
                    </Link>
                  )}
                </td>
                <td className="px-3 py-1.5">{r.targetUsername && `@${r.targetUsername}`}</td>
                <td className="px-3 py-1.5 text-xs text-muted">{r.surface}</td>
                <td className="px-3 py-1.5 text-right tabular-nums">{r.valueMs != null && `${(r.valueMs / 1000).toFixed(1)}초`}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {events.isSuccess && rows.length === 0 && <p className="p-6 text-center text-sm text-muted">조건에 맞는 이벤트가 없습니다.</p>}
      </div>
      {events.hasNextPage && (
        <button onClick={() => void events.fetchNextPage()} disabled={events.isFetchingNextPage} className="btn btn-secondary w-full">
          {events.isFetchingNextPage ? '불러오는 중…' : '더 보기'}
        </button>
      )}
    </div>
  )
}
