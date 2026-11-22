import { useState } from 'react'
import { Link } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FileText, MessageSquare, UserRound } from 'lucide-react'
import clsx from 'clsx'
import { Avatar } from '../../components/Avatar'
import { LabHeader } from '../../components/lab/LabNav'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'
import { timeAgo } from '../../lib/format'
import type { UserSummary } from '../../lib/types'

interface QueueItem {
  targetType: 'POST' | 'COMMENT' | 'USER'
  targetId: number
  shortcode: string | null
  thumbUrl: string | null
  preview: string | null
  owner: UserSummary
  ownerStrikes: number
  ownerSuspendedUntil: string | null
  reportCount: number
  reasons: { reason: string; label: string; count: number }[]
  firstAt: string
  lastAt: string
  status: string
  action: string | null
}

interface Stats {
  open: number
  resolvedToday: number
  suspendedUsers: number
}

const ACTIONS: { key: string; label: string; danger?: boolean }[] = [
  { key: 'REMOVE', label: '삭제 + 경고', danger: true },
  { key: 'DISMISS', label: '문제없음' },
  { key: 'SUSPEND_7D', label: '7일 정지', danger: true },
  { key: 'SUSPEND_30D', label: '30일 정지', danger: true },
  { key: 'BAN', label: '영구 정지', danger: true },
]

const ACTION_LABEL: Record<string, string> = {
  REMOVE: '삭제 + 경고',
  DISMISS: '문제없음',
  SUSPEND_7D: '7일 정지',
  SUSPEND_30D: '30일 정지',
  BAN: '영구 정지',
}

const TYPE_ICON = { POST: FileText, COMMENT: MessageSquare, USER: UserRound }
const TYPE_LABEL = { POST: '게시물', COMMENT: '댓글', USER: '계정' }

/** Lab 신고·모더레이션 큐 (L10): 같은 대상의 신고를 묶어 보여 주고 한 번에 처리한다 */
export function LabModerationPage() {
  const qc = useQueryClient()
  const [tab, setTab] = useState<'OPEN' | 'RESOLVED'>('OPEN')
  const { data: stats } = useQuery({ queryKey: ['lab-mod-stats'], queryFn: () => api.get<Stats>('/api/lab/moderation/stats') })
  const { data, isLoading } = useQuery({
    queryKey: ['lab-mod', tab],
    queryFn: () => api.get<QueueItem[]>(`/api/lab/moderation?status=${tab}`),
  })
  const act = useMutation({
    mutationFn: ({ item, action }: { item: QueueItem; action: string }) =>
      api.post(`/api/lab/moderation/${item.targetType}/${item.targetId}`, { action }),
    onSuccess: (_, { action }) => {
      void qc.invalidateQueries({ queryKey: ['lab-mod'] })
      void qc.invalidateQueries({ queryKey: ['lab-mod-stats'] })
      toast(action === 'UNSUSPEND' ? '정지를 풀었습니다' : `처리했습니다: ${ACTION_LABEL[action]}`)
    },
    onError: (e) => toast((e as Error).message),
  })

  return (
    <div className="mx-auto w-full max-w-[960px] space-y-6 px-4 py-8">
      <LabHeader
        title="신고·모더레이션"
        description="사용자가 신고한 게시물·댓글·계정을 대상별로 묶어 처리합니다. 삭제하면 주인에게 경고가 1회 쌓이고, 경고 3회마다 7일 정지됩니다. 정지된 계정은 로그인할 수 없고 콘텐츠가 다른 사람에게 보이지 않습니다."
      />
      {stats && (
        <div className="grid grid-cols-3 gap-3">
          <Stat label="처리 대기" value={stats.open} />
          <Stat label="24시간 안 처리" value={stats.resolvedToday} />
          <Stat label="정지 중인 계정" value={stats.suspendedUsers} />
        </div>
      )}
      <div role="tablist" className="flex gap-2">
        {(['OPEN', 'RESOLVED'] as const).map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={clsx('rounded-full border px-3 py-1 text-sm', tab === t ? 'border-text bg-text font-semibold text-bg' : 'border-border')}
          >
            {t === 'OPEN' ? '처리 대기' : '처리됨'}
          </button>
        ))}
      </div>

      {isLoading && <p className="py-8 text-center text-sm text-muted">불러오는 중…</p>}
      {data?.length === 0 && <p className="py-8 text-center text-sm text-muted">{tab === 'OPEN' ? '처리할 신고가 없습니다 🎉' : '아직 처리한 신고가 없습니다'}</p>}
      <ul className="space-y-3">
        {data?.map((item) => {
          const Icon = TYPE_ICON[item.targetType]
          const suspended = item.ownerSuspendedUntil != null && new Date(item.ownerSuspendedUntil) > new Date()
          return (
            <li key={`${item.targetType}-${item.targetId}`} className="rounded-xl border border-border p-4">
              <div className="flex gap-3">
                {item.thumbUrl ? (
                  <Link to={`/p/${item.shortcode}`} className="shrink-0">
                    <img src={item.thumbUrl} alt="" className="h-20 w-20 rounded object-cover" />
                  </Link>
                ) : (
                  <span className="flex h-20 w-20 shrink-0 items-center justify-center rounded bg-surface-2">
                    <Icon size={28} className="text-muted" />
                  </span>
                )}
                <div className="min-w-0 flex-1 text-sm">
                  <p className="flex flex-wrap items-center gap-x-2">
                    <span className="font-semibold">
                      {TYPE_LABEL[item.targetType]} · 신고 {item.reportCount}건
                    </span>
                    <span className="text-xs text-muted">
                      {timeAgo(item.firstAt)} ~ {timeAgo(item.lastAt)}
                    </span>
                  </p>
                  {item.preview && <p className="mt-1 line-clamp-2 text-muted">“{item.preview}”</p>}
                  <p className="mt-1 flex flex-wrap gap-1">
                    {item.reasons.map((r) => (
                      <span key={r.reason} className="rounded-full bg-surface-2 px-2 py-0.5 text-xs">
                        {r.label} {r.count}
                      </span>
                    ))}
                  </p>
                  <p className="mt-2 flex items-center gap-2 text-xs">
                    <Avatar user={item.owner} size={20} />
                    <Link to={`/${item.owner.username}`} className="font-semibold">
                      {item.owner.username}
                    </Link>
                    <span className="text-muted">경고 {item.ownerStrikes}회</span>
                    {suspended && <span className="font-semibold text-danger">정지 중</span>}
                  </p>
                </div>
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {tab === 'OPEN' ? (
                  ACTIONS.map((a) => (
                    <button
                      key={a.key}
                      disabled={act.isPending}
                      onClick={() => act.mutate({ item, action: a.key })}
                      className={clsx('rounded-lg border px-3 py-1 text-sm', a.danger ? 'border-danger/40 text-danger' : 'border-border')}
                    >
                      {a.label}
                    </button>
                  ))
                ) : (
                  <span className="text-sm text-muted">처리: {item.action ? ACTION_LABEL[item.action] : '-'}</span>
                )}
                {suspended && (
                  <button className="rounded-lg border border-border px-3 py-1 text-sm" onClick={() => act.mutate({ item, action: 'UNSUSPEND' })}>
                    정지 풀기
                  </button>
                )}
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl border border-border p-3">
      <p className="text-xs text-muted">{label}</p>
      <p className="mt-1 text-lg font-semibold tabular-nums">{value.toLocaleString('ko-KR')}</p>
    </div>
  )
}
