import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, Clapperboard, RotateCcw, Save } from 'lucide-react'
import clsx from 'clsx'
import { LabHeader } from '../../components/lab/LabNav'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'
import { useMe } from '../../lib/auth'
import { SOURCE_LABEL, fmtScore, setScoreOverlay, useScoreOverlay } from '../../lib/ranking'
import type { RankSurface, Ranking } from '../../lib/types'

interface ComponentInfo {
  key: string
  label: string
  description: string
  sign: number
  weight: number
  defaultWeight: number
}
interface SurfaceInfo {
  key: RankSurface
  label: string
  components: ComponentInfo[]
}
interface PreviewItem {
  shortcode: string
  thumbUrl: string
  type: string
  author: string
  topic: string | null
  captionStart: string
  ranking: Ranking
}
interface PreviewResponse {
  username: string
  a: PreviewItem[]
  b: PreviewItem[]
}

type Weights = Record<string, number>

/**
 * 알고리즘 실험실 (Lab L06): 영역별 점수식 가중치를 슬라이더로 바꿔, 같은 사용자에 대해
 * A(지금 적용 중) / B(실험) 결과를 나란히 비교한다. B가 마음에 들면 저장해서 실제 화면에 적용.
 */
export function LabAlgorithmPage() {
  const { data: me } = useMe()
  const qc = useQueryClient()
  const overlay = useScoreOverlay()
  const [surface, setSurface] = useState<RankSurface>('EXPLORE')
  const [username, setUsername] = useState('')
  const [b, setB] = useState<Weights>({})
  const [open, setOpen] = useState<string | null>(null)

  const info = useQuery({ queryKey: ['lab-algorithm'], queryFn: () => api.get<SurfaceInfo[]>('/api/lab/algorithm') })
  const current = info.data?.find((s) => s.key === surface)
  const a: Weights = useMemo(
    () => Object.fromEntries((current?.components ?? []).map((c) => [c.key, c.weight])),
    [current],
  )

  // 영역을 바꾸거나 저장값이 바뀌면 B를 A로 맞춘다
  useEffect(() => setB(a), [a])
  useEffect(() => {
    if (me && !username) setUsername(me.user.username)
  }, [me, username])

  // 슬라이더를 움직이면 0.4초 뒤 미리보기
  const [debounced, setDebounced] = useState({ b, username })
  useEffect(() => {
    const t = window.setTimeout(() => setDebounced({ b, username }), 400)
    return () => window.clearTimeout(t)
  }, [b, username])

  const preview = useQuery({
    queryKey: ['lab-preview', surface, debounced.username, a, debounced.b],
    queryFn: () =>
      api.post<PreviewResponse>('/api/lab/algorithm/preview', {
        surface,
        username: debounced.username,
        a,
        b: debounced.b,
        limit: 30,
      }),
    enabled: !!current && !!debounced.username && Object.keys(debounced.b).length > 0,
    placeholderData: (prev) => prev,
    retry: false,
  })

  const onSaved = (data: SurfaceInfo[]) => {
    qc.setQueryData(['lab-algorithm'], data)
    // 실제 화면의 추천 목록도 새 가중치로
    void qc.invalidateQueries({ queryKey: ['explore'] })
    void qc.invalidateQueries({ queryKey: ['feed'] })
  }
  const save = useMutation({
    mutationFn: () => api.put<SurfaceInfo[]>(`/api/lab/algorithm/${surface}`, b),
    onSuccess: (data) => {
      onSaved(data)
      toast(`${current?.label} 가중치를 저장했습니다. 실제 화면에 바로 적용됩니다.`)
    },
    onError: (e) => toast(e.message),
  })
  const reset = useMutation({
    mutationFn: () => api.delete<SurfaceInfo[]>(`/api/lab/algorithm/${surface}`),
    onSuccess: (data) => {
      onSaved(data)
      toast('기본값으로 되돌렸습니다')
    },
  })

  const changed = current?.components.some((c) => b[c.key] !== c.weight) ?? false
  const aRank = new Map(preview.data?.a.map((x, i) => [x.shortcode, i]) ?? [])
  const overlap = preview.data ? preview.data.b.filter((x) => aRank.has(x.shortcode)).length : 0

  return (
    <div className="mx-auto w-full max-w-[1100px] space-y-6 px-4 py-8">
      <LabHeader
        title="알고리즘 실험실"
        description="점수 = Σ 가중치 × 구성 요소 값. 가중치를 바꾸면 같은 사용자에게 보일 순서가 어떻게 달라지는지 A/B로 비교합니다."
      />

      <div className="flex flex-wrap items-end gap-4">
        <div role="radiogroup" aria-label="영역" className="flex gap-1">
          {(info.data ?? []).map((s) => (
            <button
              key={s.key}
              role="radio"
              aria-checked={surface === s.key}
              onClick={() => setSurface(s.key)}
              className={clsx(
                'rounded-lg border px-3 py-1.5 text-sm',
                surface === s.key ? 'border-primary font-semibold text-primary' : 'border-border',
              )}
            >
              {s.label}
            </button>
          ))}
        </div>
        <label className="text-sm">
          <span className="block text-xs text-muted">누구 시점으로</span>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value.trim().replace(/^@/, ''))}
            className="input mt-1 w-48"
            placeholder="사용자 이름"
          />
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={overlay} onChange={(e) => setScoreOverlay(e.target.checked)} />
          실제 화면(홈·탐색·릴스)에 점수 표시
        </label>
      </div>

      {current && (
        <section className="rounded-xl border border-border p-4">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <h2 className="font-semibold">B 가중치 ({current.label})</h2>
            <div className="flex gap-2">
              <button
                onClick={() => setB(a)}
                disabled={!changed}
                className="rounded-lg border border-border px-3 py-1.5 text-sm disabled:opacity-40"
              >
                A로 되돌리기
              </button>
              <button
                onClick={() => reset.mutate()}
                className="flex items-center gap-1 rounded-lg border border-border px-3 py-1.5 text-sm"
              >
                <RotateCcw size={14} /> 기본값 저장
              </button>
              <button
                onClick={() => save.mutate()}
                disabled={!changed || save.isPending}
                className="btn btn-primary px-3 py-1.5"
              >
                <Save size={14} /> B를 적용
              </button>
            </div>
          </div>
          <div className="grid gap-x-8 gap-y-3 sm:grid-cols-2">
            {current.components.map((c) => (
              <label key={c.key} className="block text-sm" title={c.description}>
                <div className="flex items-baseline justify-between gap-2">
                  <span>
                    {c.sign < 0 && <span className="text-danger">− </span>}
                    {c.label}
                  </span>
                  <span className="text-xs text-muted tabular-nums">
                    A {c.weight} → <b className={clsx(b[c.key] !== c.weight && 'text-primary')}>B {b[c.key]?.toFixed(1)}</b>
                  </span>
                </div>
                <input
                  type="range"
                  min={0}
                  max={10}
                  step={0.1}
                  value={b[c.key] ?? 0}
                  onChange={(e) => setB((w) => ({ ...w, [c.key]: Number(e.target.value) }))}
                  className="w-full accent-[var(--primary)]"
                  aria-label={`${c.label} 가중치`}
                />
                <span className="block text-[11px] leading-tight text-muted">
                  {c.description} · 기본 {c.defaultWeight}
                </span>
              </label>
            ))}
          </div>
        </section>
      )}

      {preview.isError && <p className="text-sm text-danger">{preview.error.message}</p>}
      {preview.data && (
        <>
          <p className="text-sm text-muted">
            @{preview.data.username} 시점 상위 {preview.data.a.length}개 · A와 B에 함께 있는 게시물 {overlap}개
            {preview.isFetching && ' · 계산 중…'}
          </p>
          <div className="grid gap-4 md:grid-cols-2">
            <ResultColumn title="A · 지금 적용 중" items={preview.data.a} open={open} onToggle={setOpen} />
            <ResultColumn title="B · 실험" items={preview.data.b} compare={aRank} open={open} onToggle={setOpen} />
          </div>
        </>
      )}
    </div>
  )
}

function ResultColumn({
  title,
  items,
  compare,
  open,
  onToggle,
}: {
  title: string
  items: PreviewItem[]
  compare?: Map<string, number>
  open: string | null
  onToggle: (key: string | null) => void
}) {
  return (
    <section className="overflow-hidden rounded-xl border border-border">
      <h3 className="border-b border-border bg-surface-2 px-3 py-2 text-sm font-semibold">{title}</h3>
      {items.length === 0 && <p className="p-6 text-center text-sm text-muted">후보가 없습니다.</p>}
      <ol>
        {items.map((it, i) => {
          const before = compare?.get(it.shortcode)
          const move = compare ? (before == null ? 'new' : before - i) : null
          const key = `${title}:${it.shortcode}`
          const expanded = open === key
          return (
            <li key={it.shortcode} className="border-b border-border last:border-0">
              <button
                onClick={() => onToggle(expanded ? null : key)}
                className="flex w-full items-center gap-3 px-3 py-2 text-left hover:bg-surface-2"
                aria-expanded={expanded}
              >
                <span className="w-5 text-right text-xs text-muted tabular-nums">{i + 1}</span>
                <span className="relative h-11 w-11 shrink-0 overflow-hidden rounded bg-surface-2">
                  <img src={it.thumbUrl} alt="" className="h-full w-full object-cover" loading="lazy" />
                  {it.type === 'REEL' && <Clapperboard size={12} className="absolute top-0.5 right-0.5 text-white drop-shadow" />}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold">@{it.author}</span>
                  <span className="block truncate text-xs text-muted">
                    {SOURCE_LABEL[it.ranking.source]}
                    {it.topic && ` · ${it.topic}`} · {it.captionStart}
                  </span>
                </span>
                <span className="text-right">
                  <span className="block font-mono text-sm tabular-nums">{fmtScore(it.ranking.score)}</span>
                  {move === 'new' && <span className="text-[11px] font-semibold text-primary">새로 진입</span>}
                  {typeof move === 'number' && move !== 0 && (
                    <span className={clsx('flex items-center justify-end text-[11px] tabular-nums', move > 0 ? 'text-primary' : 'text-muted')}>
                      {move > 0 ? <ArrowUp size={11} /> : <ArrowDown size={11} />}
                      {Math.abs(move)}
                    </span>
                  )}
                </span>
              </button>
              {expanded && (
                <div className="bg-surface-2/50 px-3 pb-3 text-xs">
                  <table className="w-full">
                    <tbody>
                      {it.ranking.parts.map((p) => (
                        <tr key={p.key}>
                          <td className="py-0.5">{p.label}</td>
                          <td className="py-0.5 text-right tabular-nums text-muted">
                            {p.value.toFixed(2)} × {p.weight}
                          </td>
                          <td className={clsx('w-16 py-0.5 text-right tabular-nums', p.contribution < 0 && 'text-danger')}>
                            {fmtScore(p.contribution)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <a href={`/p/${it.shortcode}`} className="mt-1 inline-block text-primary hover:underline">
                    게시물 열기
                  </a>
                </div>
              )}
            </li>
          )
        })}
      </ol>
    </section>
  )
}
