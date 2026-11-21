import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { LabHeader } from '../../components/lab/LabNav'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'
import { useFlags } from '../../lib/flags'
import type { Flags } from '../../lib/flags'

/** 기능 플래그 (Lab L09): 정책을 바꿔 가며 비교한다. 모든 사용자에게 바로 적용된다 */
export function LabFlagsPage() {
  const qc = useQueryClient()
  const { data } = useFlags()
  const [draft, setDraft] = useState<Flags | null>(null)
  useEffect(() => {
    if (data) setDraft(data)
  }, [data])

  const onDone = (f: Flags) => {
    qc.setQueryData(['flags'], f)
    void qc.invalidateQueries({ queryKey: ['feed'] })
    void qc.invalidateQueries({ queryKey: ['post'] })
    toast('기능 플래그를 적용했습니다')
  }
  const save = useMutation({ mutationFn: (f: Flags) => api.put<Flags>('/api/lab/flags', f), onSuccess: onDone, onError: (e) => toast(e.message) })
  const reset = useMutation({ mutationFn: () => api.delete<Flags>('/api/lab/flags'), onSuccess: onDone })

  return (
    <div className="mx-auto w-full max-w-[760px] space-y-6 px-4 py-8">
      <LabHeader title="기능 플래그" description="정책을 바꿔 비교합니다. 저장하면 모든 가짜 계정 화면에 바로 적용됩니다." />
      {draft && (
        <div className="space-y-4">
          <Flag title="게시물당 해시태그 최대 개수" hint="지금 인스타그램은 5개, 예전에는 30개">
            <Choice value={draft.hashtagLimit} options={[[5, '5개'], [10, '10개'], [30, '30개']]} onChange={(v) => setDraft({ ...draft, hashtagLimit: v })} />
          </Flag>
          <Flag title="아래 탭 배치" hint="새 배치: 홈·릴스·DM·검색·프로필 / 예전 배치: 홈·검색·만들기·릴스·프로필">
            <Choice
              value={draft.navLayout}
              options={[
                ['NEW', '새 배치'],
                ['CLASSIC', '예전 배치'],
              ]}
              onChange={(v) => setDraft({ ...draft, navLayout: v })}
            />
          </Flag>
          <Flag title="좋아요 수 숨기기 (전체)" hint="모든 게시물의 좋아요 수를 숨깁니다. 작성자 본인에게는 보입니다.">
            <Choice
              value={draft.hideLikeCounts}
              options={[
                [false, '보이기'],
                [true, '숨기기'],
              ]}
              onChange={(v) => setDraft({ ...draft, hideLikeCounts: v })}
            />
          </Flag>
          <Flag title="홈 피드 10개 중 추천 게시물 수" hint="0이면 팔로우 게시물만 (다 보면 추천)">
            <Choice
              value={draft.feedSuggestedPerPage}
              options={[0, 1, 2, 3, 5].map((n) => [n, `${n}개`] as [number, string])}
              onChange={(v) => setDraft({ ...draft, feedSuggestedPerPage: v })}
            />
          </Flag>
          <div className="flex gap-2">
            <button onClick={() => save.mutate(draft)} disabled={save.isPending} className="btn btn-primary">
              저장
            </button>
            <button onClick={() => reset.mutate()} className="btn btn-secondary">
              기본값으로
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

function Flag({ title, hint, children }: { title: string; hint: string; children: React.ReactNode }) {
  return (
    <section className="rounded-xl border border-border p-4">
      <h2 className="font-semibold">{title}</h2>
      <p className="pb-3 text-xs text-muted">{hint}</p>
      {children}
    </section>
  )
}

function Choice<T extends string | number | boolean>({
  value,
  options,
  onChange,
}: {
  value: T
  options: [T, string][]
  onChange: (v: T) => void
}) {
  return (
    <div role="radiogroup" className="flex flex-wrap gap-2">
      {options.map(([v, label]) => (
        <button
          key={String(v)}
          role="radio"
          aria-checked={value === v}
          onClick={() => onChange(v)}
          className={clsx('rounded-lg border px-3 py-1.5 text-sm', value === v ? 'border-primary font-semibold text-primary' : 'border-border')}
        >
          {label}
        </button>
      ))}
    </div>
  )
}
