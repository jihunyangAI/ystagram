import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pause, Play, StepForward } from 'lucide-react'
import clsx from 'clsx'
import { LabHeader } from '../../components/lab/LabNav'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'
import { useMe } from '../../lib/auth'
import { fullDate } from '../../lib/format'

type Action = 'LIKE' | 'COMMENT' | 'FOLLOW' | 'UNFOLLOW' | 'POST' | 'STORY' | 'DM' | 'BROWSE'
interface Config {
  running: boolean
  intervalSec: number
  actionsPerTick: number
  focusUsername: string | null
  mix: Record<Action, number>
}
interface LogEntry {
  at: string
  actor: string
  action: Action
  target: string | null
  detail: string
  ok: boolean
}
interface Status {
  config: Config
  totalActions: number
  recent: LogEntry[]
  counts: Partial<Record<Action, number>>
}

const ACTION_LABEL: Record<Action, string> = {
  LIKE: '좋아요',
  COMMENT: '댓글',
  FOLLOW: '팔로우',
  UNFOLLOW: '언팔로우',
  POST: '게시물',
  STORY: '스토리',
  DM: 'DM',
  BROWSE: '둘러보기',
}

/**
 * 활동 시뮬레이터 (Lab L05): 가짜 사용자들이 주기적으로 행동한다.
 * 실제 서비스 코드를 거쳐서 알림·실시간 DM·이벤트 기록이 그대로 생긴다.
 */
export function LabSimulatorPage() {
  const { data: me } = useMe()
  const qc = useQueryClient()
  const status = useQuery({
    queryKey: ['lab-sim'],
    queryFn: () => api.get<Status>('/api/lab/sim'),
    refetchInterval: (q) => (q.state.data?.config.running ? 2000 : false),
  })
  const [draft, setDraft] = useState<Config | null>(null)
  useEffect(() => {
    if (status.data && !draft) setDraft(status.data.config)
  }, [status.data, draft])

  const configure = useMutation({
    mutationFn: (c: Config) => api.put<Status>('/api/lab/sim', c),
    onSuccess: (s) => {
      qc.setQueryData(['lab-sim'], s)
      setDraft(s.config)
    },
    onError: (e) => toast(e.message),
  })
  const step = useMutation({
    mutationFn: () => api.post<Status>('/api/lab/sim/step?n=5'),
    onSuccess: (s) => qc.setQueryData(['lab-sim'], s),
    onError: (e) => toast(e.message),
  })

  const s = status.data
  const running = s?.config.running ?? false
  const total = draft ? Object.values(draft.mix).reduce((a, b) => a + b, 0) : 0

  return (
    <div className="mx-auto w-full max-w-[1000px] space-y-6 px-4 py-8">
      <LabHeader
        title="활동 시뮬레이터"
        description="가짜 사용자들이 좋아요·댓글·팔로우·게시물·스토리·DM·둘러보기를 합니다. 집중 대상을 정하면 행동의 절반이 그 사람을 향해서 알림이 오는 것을 바로 볼 수 있습니다."
      />

      {draft && (
        <section className="space-y-4 rounded-xl border border-border p-4">
          <div className="flex flex-wrap items-center gap-3">
            <span
              className={clsx(
                'flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-semibold',
                running ? 'bg-primary-soft text-primary' : 'bg-surface-2 text-muted',
              )}
            >
              <span className={clsx('h-2 w-2 rounded-full', running ? 'animate-pulse bg-primary' : 'bg-muted')} />
              {running ? '실행 중' : '멈춤'}
            </span>
            <button
              onClick={() => configure.mutate({ ...draft, running: !running })}
              className={clsx('btn', running ? 'btn-secondary' : 'btn-primary')}
            >
              {running ? <Pause size={16} /> : <Play size={16} />} {running ? '멈추기' : '시작'}
            </button>
            <button onClick={() => step.mutate()} disabled={step.isPending} className="btn btn-secondary">
              <StepForward size={16} /> 지금 5번
            </button>
            <span className="ml-auto text-sm text-muted">지금까지 {s?.totalActions.toLocaleString('ko-KR')}번</span>
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <label className="text-sm">
              <span className="block text-xs text-muted">간격: {draft.intervalSec}초마다</span>
              <input
                type="range"
                min={1}
                max={30}
                value={draft.intervalSec}
                onChange={(e) => setDraft({ ...draft, intervalSec: Number(e.target.value) })}
                className="w-full accent-[var(--primary)]"
              />
            </label>
            <label className="text-sm">
              <span className="block text-xs text-muted">한 번에: {draft.actionsPerTick}개 행동</span>
              <input
                type="range"
                min={1}
                max={30}
                value={draft.actionsPerTick}
                onChange={(e) => setDraft({ ...draft, actionsPerTick: Number(e.target.value) })}
                className="w-full accent-[var(--primary)]"
              />
            </label>
            <label className="text-sm">
              <span className="block text-xs text-muted">집중 대상 (비우면 무작위)</span>
              <div className="mt-1 flex gap-1">
                <input
                  value={draft.focusUsername ?? ''}
                  onChange={(e) => setDraft({ ...draft, focusUsername: e.target.value || null })}
                  className="input"
                  placeholder="username"
                />
                {me && (
                  <button
                    type="button"
                    onClick={() => setDraft({ ...draft, focusUsername: me.user.username })}
                    className="btn btn-secondary shrink-0 px-2 text-xs"
                  >
                    나
                  </button>
                )}
              </div>
            </label>
          </div>

          <fieldset>
            <legend className="pb-2 text-xs text-muted">행동 비중 (합계 {total})</legend>
            <div className="grid grid-cols-2 gap-x-6 gap-y-2 sm:grid-cols-4">
              {(Object.keys(ACTION_LABEL) as Action[]).map((a) => (
                <label key={a} className="text-sm">
                  <span className="flex justify-between">
                    {ACTION_LABEL[a]}
                    <span className="text-xs text-muted tabular-nums">
                      {total > 0 ? Math.round((draft.mix[a] / total) * 100) : 0}%
                    </span>
                  </span>
                  <input
                    type="range"
                    min={0}
                    max={50}
                    value={draft.mix[a]}
                    onChange={(e) => setDraft({ ...draft, mix: { ...draft.mix, [a]: Number(e.target.value) } })}
                    className="w-full accent-[var(--primary)]"
                  />
                </label>
              ))}
            </div>
          </fieldset>
          <button onClick={() => configure.mutate({ ...draft, running })} disabled={configure.isPending} className="btn btn-primary">
            설정 저장
          </button>
        </section>
      )}

      {s && Object.keys(s.counts).length > 0 && (
        <section className="flex flex-wrap gap-2 text-sm">
          {(Object.keys(ACTION_LABEL) as Action[])
            .filter((a) => s.counts[a])
            .map((a) => (
              <span key={a} className="rounded-full bg-surface-2 px-3 py-1">
                {ACTION_LABEL[a]} <b className="tabular-nums">{s.counts[a]}</b>
              </span>
            ))}
        </section>
      )}

      <section className="overflow-hidden rounded-xl border border-border">
        <h2 className="border-b border-border bg-surface-2 px-3 py-2 text-sm font-semibold">최근 행동 (최대 200개)</h2>
        {s?.recent.length === 0 && <p className="p-6 text-center text-sm text-muted">아직 기록이 없습니다. 시작하거나 "지금 5번"을 눌러 보세요.</p>}
        <ul className="max-h-[480px] overflow-y-auto text-sm">
          {s?.recent.map((e, i) => (
            <li key={i} className={clsx('flex gap-3 border-b border-border px-3 py-1.5 last:border-0', !e.ok && 'text-muted')}>
              <span className="shrink-0 text-xs text-muted tabular-nums">{fullDate(e.at)}</span>
              <span className="w-16 shrink-0 font-semibold">{ACTION_LABEL[e.action]}</span>
              <span className="min-w-0 flex-1 truncate">
                @{e.actor}
                {e.target && <> → @{e.target}</>} <span className="text-muted">{e.detail}</span>
              </span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
