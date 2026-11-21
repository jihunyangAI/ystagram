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
          <Flag title="홈 피드 광고" hint="한 쪽(약 10개)에 끼우는 가짜 광고 수. 광고 성과는 Lab → 광고 탭에서 봅니다.">
            <Choice
              value={draft.adsPerPage}
              options={[0, 1, 2, 3].map((n) => [n, n === 0 ? '끄기' : `${n}개`] as [number, string])}
              onChange={(v) => setDraft({ ...draft, adsPerPage: v })}
            />
          </Flag>
          <Flag title="DM 자동 응답 봇" hint="가짜 사용자에게 메시지를 보내면 몇 초 뒤 읽고, 입력 중 표시 후 미리 만든 문장으로 답합니다.">
            <Choice
              value={draft.autoReply}
              options={[
                [true, '켜기'],
                [false, '끄기'],
              ]}
              onChange={(v) => setDraft({ ...draft, autoReply: v })}
            />
          </Flag>
          <Flag title="AI 생성 프로필 추천" hint="기본 규칙: AI로 만든 가상 인물의 게시물은 팔로우하지 않으면 홈 추천·탐색·릴스에 나오지 않습니다.">
            <Choice
              value={draft.recommendAiProfiles}
              options={[
                [false, '추천에서 빼기'],
                [true, '추천에 넣기'],
              ]}
              onChange={(v) => setDraft({ ...draft, recommendAiProfiles: v })}
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
