import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Plus } from 'lucide-react'
import clsx from 'clsx'
import { api } from '../../lib/api'
import { useStoryArchive } from '../../lib/stories'
import type { HighlightView } from '../../lib/types'
import { Modal } from '../Modal'
import { toast } from '../Toast'

export function useHighlights(username: string, enabled: boolean) {
  return useQuery({
    queryKey: ['highlights', username],
    queryFn: () => api.get<HighlightView[]>(`/api/stories/of/${username}/highlights`),
    enabled,
  })
}

/** 프로필 아래 하이라이트 동그라미. 본인은 맨 앞에 "+ 새로 만들기" */
export function HighlightsRow({ username, mine, enabled }: { username: string; mine: boolean; enabled: boolean }) {
  const { data } = useHighlights(username, enabled)
  const navigate = useNavigate()
  const [creating, setCreating] = useState(false)
  if (!enabled || (!mine && !data?.length)) return null
  return (
    <div className="no-scrollbar mt-5 flex gap-4 overflow-x-auto px-4 pb-1 md:mt-10 md:gap-10 md:px-12" aria-label="하이라이트">
      {data?.map((h) => (
        <button
          key={h.id}
          onClick={() => navigate(`/stories/highlights/${h.id}`)}
          className="flex w-[64px] shrink-0 flex-col items-center gap-1.5 md:w-[86px]"
        >
          <span className="rounded-full border border-border p-[3px]">
            {h.coverUrl ? (
              <img src={h.coverUrl} alt="" className="h-14 w-14 rounded-full object-cover md:h-[77px] md:w-[77px]" />
            ) : (
              <span className="block h-14 w-14 rounded-full bg-surface-2 md:h-[77px] md:w-[77px]" />
            )}
          </span>
          <span className="w-full truncate text-center text-xs font-semibold">{h.title}</span>
        </button>
      ))}
      {mine && (
        <button onClick={() => setCreating(true)} className="flex w-[64px] shrink-0 flex-col items-center gap-1.5 md:w-[86px]">
          <span className="flex h-[62px] w-[62px] items-center justify-center rounded-full border border-border md:h-[83px] md:w-[83px]">
            <Plus size={28} strokeWidth={1.3} className="text-muted" />
          </span>
          <span className="text-xs font-semibold">새로 만들기</span>
        </button>
      )}
      {mine && <CreateHighlightModal open={creating} onClose={() => setCreating(false)} username={username} />}
    </div>
  )
}

/** 아카이브에서 스토리 고르기 → 이름 붙이기 */
function CreateHighlightModal({ open, onClose, username }: { open: boolean; onClose: () => void; username: string }) {
  const archive = useStoryArchive()
  const qc = useQueryClient()
  const [picked, setPicked] = useState<number[]>([])
  const [title, setTitle] = useState('')
  const [step, setStep] = useState<'pick' | 'name'>('pick')
  const items = archive.data?.pages.flatMap((p) => p.items) ?? []
  const close = () => {
    setPicked([])
    setTitle('')
    setStep('pick')
    onClose()
  }
  const save = () =>
    api.post<HighlightView>('/api/stories/highlights', { title: title.trim() || '하이라이트', storyIds: picked }).then(
      () => {
        qc.invalidateQueries({ queryKey: ['highlights', username] })
        toast('하이라이트를 만들었습니다')
        close()
      },
      (e: Error) => toast(e.message),
    )
  return (
    <Modal open={open} onClose={close} title={step === 'pick' ? '스토리 선택' : '하이라이트 이름'} className="flex max-h-[80vh] flex-col">
      {step === 'pick' ? (
        <>
          <div className="grid min-h-0 flex-1 grid-cols-3 gap-0.5 overflow-y-auto">
            {items.length === 0 && <p className="col-span-3 p-8 text-center text-sm text-muted">아카이브에 스토리가 없습니다.</p>}
            {items.map((s) => {
              const on = picked.includes(s.id)
              return (
                <button
                  key={s.id}
                  aria-pressed={on}
                  onClick={() => setPicked((p) => (on ? p.filter((x) => x !== s.id) : [...p, s.id]))}
                  className="relative aspect-[9/16] overflow-hidden bg-surface-2"
                >
                  <img src={s.mediaUrl} alt="" className={clsx('h-full w-full object-cover', on && 'opacity-60')} />
                  <span className="absolute top-1 left-1 rounded bg-black/50 px-1 text-[10px] text-white">
                    {new Date(s.createdAt).toLocaleDateString('ko-KR', { month: 'numeric', day: 'numeric' })}
                  </span>
                  <span
                    className={clsx(
                      'absolute right-1 bottom-1 flex h-5 w-5 items-center justify-center rounded-full border-2 border-white',
                      on && 'bg-primary',
                    )}
                  >
                    {on && <Check size={12} className="text-white" strokeWidth={3} />}
                  </span>
                </button>
              )
            })}
          </div>
          <div className="border-t border-border p-3">
            <button className="btn btn-primary w-full" disabled={!picked.length} onClick={() => setStep('name')}>
              다음 ({picked.length})
            </button>
          </div>
        </>
      ) : (
        <form
          className="flex flex-col gap-3 p-5"
          onSubmit={(e) => {
            e.preventDefault()
            void save()
          }}
        >
          <input
            autoFocus
            value={title}
            maxLength={30}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="하이라이트"
            aria-label="하이라이트 이름"
            className="input text-center"
          />
          <button type="submit" className="btn btn-primary">
            추가
          </button>
        </form>
      )}
    </Modal>
  )
}
