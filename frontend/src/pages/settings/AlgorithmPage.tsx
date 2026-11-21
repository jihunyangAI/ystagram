import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Minus, Plus, RotateCcw } from 'lucide-react'
import { useNavigate } from 'react-router'
import clsx from 'clsx'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'

type Source = 'PROFILE' | 'ACTIVITY' | 'ADDED'

interface TopicItem {
  topic: string
  label: string
  emoji: string
  sources: Source[]
  removed: boolean
  activityCount: number
}

interface AlgorithmView {
  topics: TopicItem[]
  others: TopicItem[]
}

const SOURCE_TEXT: Record<Source, string> = {
  PROFILE: '프로필 관심사',
  ACTIVITY: '최근 활동',
  ADDED: '직접 추가',
}

/**
 * 회원님의 알고리즘 (분석 문서 §21): 추천(홈 추천·탐색·릴스)의 "관심 주제 일치"에 쓰이는 주제.
 * 프로필 관심사 + 최근 30일 활동(좋아요·저장·댓글·릴스 시청)에서 많이 본 주제 + 직접 추가 − 직접 뺀 것.
 */
export function AlgorithmPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data, isLoading } = useQuery({
    queryKey: ['my-algorithm'],
    queryFn: () => api.get<AlgorithmView>('/api/me/algorithm'),
  })
  const change = useMutation({
    mutationFn: ({ topic, mode }: { topic: string; mode: 'ADD' | 'REMOVE' | 'RESET' }) =>
      api.put<AlgorithmView>(`/api/me/algorithm/${topic}`, { mode }),
    onSuccess: (view) => {
      qc.setQueryData(['my-algorithm'], view)
      // 추천 목록은 새 관심 주제로 다시
      void qc.invalidateQueries({ queryKey: ['explore'] })
      void qc.invalidateQueries({ queryKey: ['feed'] })
    },
    onError: (e) => toast(e.message),
  })

  const active = data?.topics.filter((t) => !t.removed) ?? []
  const removed = data?.topics.filter((t) => t.removed) ?? []

  return (
    <div className="mx-auto max-w-[600px] pb-10">
      <header className="flex items-center gap-2 border-b border-border px-2 py-3">
        <button onClick={() => navigate(-1)} aria-label="뒤로" className="rounded p-1 hover:bg-surface-2">
          <ChevronLeft size={24} />
        </button>
        <h1 className="font-semibold">회원님의 알고리즘</h1>
      </header>
      <p className="px-4 pt-4 text-sm text-muted">
        아래 주제의 게시물이 홈 추천·탐색·릴스에서 더 많이 보입니다. 주제를 빼면 추천 점수의 "관심 주제 일치"에서
        제외되고, 더하면 활동 기록과 상관없이 관심 주제로 쓰입니다.
      </p>

      {isLoading && <p className="p-6 text-center text-sm text-muted">불러오는 중…</p>}

      <section className="px-4 pt-5">
        <h2 className="pb-2 text-sm font-semibold">지금 관심 주제 ({active.length})</h2>
        {data && active.length === 0 && <p className="text-sm text-muted">관심 주제가 없습니다. 아래에서 추가해 보세요.</p>}
        <ul className="divide-y divide-border">
          {active.map((t) => (
            <li key={t.topic} className="flex items-center gap-3 py-2.5">
              <span className="text-2xl" aria-hidden>
                {t.emoji}
              </span>
              <div className="min-w-0 flex-1">
                <div className="text-sm font-semibold">{t.label}</div>
                <div className="text-xs text-muted">
                  {t.sources.map((s) => (s === 'ACTIVITY' ? `최근 30일 활동 ${t.activityCount}건` : SOURCE_TEXT[s])).join(' · ')}
                </div>
              </div>
              {t.sources.includes('ADDED') && t.sources.length === 1 ? (
                <TopicButton label="빼기" icon={<Minus size={14} />} onClick={() => change.mutate({ topic: t.topic, mode: 'RESET' })} />
              ) : (
                <TopicButton label="추천에서 빼기" icon={<Minus size={14} />} onClick={() => change.mutate({ topic: t.topic, mode: 'REMOVE' })} />
              )}
            </li>
          ))}
        </ul>
      </section>

      {removed.length > 0 && (
        <section className="px-4 pt-6">
          <h2 className="pb-2 text-sm font-semibold">뺀 주제</h2>
          <ul className="divide-y divide-border">
            {removed.map((t) => (
              <li key={t.topic} className="flex items-center gap-3 py-2.5 opacity-70">
                <span className="text-2xl" aria-hidden>
                  {t.emoji}
                </span>
                <div className="min-w-0 flex-1 text-sm line-through">{t.label}</div>
                <TopicButton label="되돌리기" icon={<RotateCcw size={14} />} onClick={() => change.mutate({ topic: t.topic, mode: 'RESET' })} />
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="px-4 pt-6">
        <h2 className="pb-2 text-sm font-semibold">관심 주제 추가</h2>
        <div className="flex flex-wrap gap-2">
          {data?.others.map((t) => (
            <button
              key={t.topic}
              onClick={() => change.mutate({ topic: t.topic, mode: 'ADD' })}
              disabled={change.isPending}
              className="flex items-center gap-1 rounded-full border border-border px-3 py-1.5 text-sm hover:bg-surface-2"
            >
              <Plus size={14} /> {t.emoji} {t.label}
            </button>
          ))}
        </div>
      </section>
    </div>
  )
}

function TopicButton({ label, icon, onClick }: { label: string; icon: React.ReactNode; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className={clsx('flex shrink-0 items-center gap-1 rounded-lg bg-surface-2 px-3 py-1.5 text-xs font-semibold hover:bg-border')}
    >
      {icon} {label}
    </button>
  )
}
