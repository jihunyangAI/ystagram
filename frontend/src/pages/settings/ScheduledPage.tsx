import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarClock, ChevronLeft, Layers } from 'lucide-react'
import { ConfirmModal, Modal, SheetButton } from '../../components/Modal'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'
import { fromLocalInput, laterInput, scheduleLabel, toLocalInput } from '../../lib/datetime'
import type { PostView, ScheduledPost } from '../../lib/types'

/** 설정 → 예약된 콘텐츠: 예약한 게시물 목록, 지금 게시·시간 바꾸기·취소 */
export function ScheduledPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data, isLoading } = useQuery({
    queryKey: ['scheduled'],
    queryFn: () => api.get<ScheduledPost[]>('/api/me/scheduled'),
    refetchInterval: 10_000, // 게시 시각이 되면 상태가 바뀐다
  })
  const [menu, setMenu] = useState<ScheduledPost | null>(null)
  const [cancel, setCancel] = useState<ScheduledPost | null>(null)
  const [edit, setEdit] = useState<ScheduledPost | null>(null)
  const [time, setTime] = useState('')

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['scheduled'] })
    qc.invalidateQueries({ queryKey: ['feed'] })
    qc.invalidateQueries({ queryKey: ['grid'] })
    qc.invalidateQueries({ queryKey: ['profile'] })
  }
  const publish = useMutation({
    mutationFn: (id: number) => api.post<PostView>(`/api/me/scheduled/${id}/publish`),
    onSuccess: (post) => {
      refresh()
      toast('게시물이 공유되었습니다')
      navigate(`/p/${post.shortcode}`)
    },
    onError: (e) => toast((e as Error).message),
  })
  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/api/me/scheduled/${id}`),
    onSuccess: () => {
      refresh()
      toast('예약을 취소했습니다')
    },
    onError: (e) => toast((e as Error).message),
  })
  const reschedule = useMutation({
    mutationFn: ({ id, at }: { id: number; at: string }) =>
      api.patch<ScheduledPost>(`/api/me/scheduled/${id}`, { scheduledAt: new Date(fromLocalInput(at)).toISOString() }),
    onSuccess: (s) => {
      refresh()
      setEdit(null)
      toast(`${scheduleLabel(s.scheduledAt)}로 바꿨습니다`)
    },
    onError: (e) => toast((e as Error).message),
  })

  const pending = data?.filter((s) => s.status === 'PENDING') ?? []
  const done = data?.filter((s) => s.status !== 'PENDING') ?? []

  return (
    <div className="mx-auto max-w-[600px] pb-10">
      <header className="flex items-center gap-2 px-2 pt-4 pb-2 md:pt-8">
        <button aria-label="뒤로" className="p-2" onClick={() => navigate(-1)}>
          <ChevronLeft size={24} />
        </button>
        <h1 className="text-lg font-bold">예약된 콘텐츠</h1>
      </header>
      <p className="px-4 pb-3 text-sm text-muted">
        새 게시물 만들기 → 고급 설정 아래 "이 게시물 예약"으로 예약할 수 있습니다. Lab의 시간 가속으로 시각을 앞당기면 바로
        게시되는 것을 볼 수 있어요.
      </p>

      {isLoading && <p className="px-4 py-8 text-center text-sm text-muted">불러오는 중…</p>}
      {data && pending.length === 0 && (
        <div className="px-4 py-12 text-center">
          <CalendarClock size={48} strokeWidth={1.2} className="mx-auto text-muted" />
          <p className="mt-3 font-semibold">예약된 게시물이 없습니다</p>
          <Link to="/create" className="mt-2 inline-block text-sm font-semibold text-primary">
            새 게시물 만들기
          </Link>
        </div>
      )}

      {pending.length > 0 && <h2 className="px-4 pt-2 pb-1 text-sm font-semibold">대기 중 {pending.length}개</h2>}
      <ul>
        {pending.map((s) => (
          <ScheduledRow key={s.id} item={s} onMenu={() => setMenu(s)} />
        ))}
      </ul>

      {done.length > 0 && <h2 className="px-4 pt-6 pb-1 text-sm font-semibold text-muted">최근 7일 동안 게시됨</h2>}
      <ul>
        {done.map((s) => (
          <ScheduledRow key={s.id} item={s} />
        ))}
      </ul>

      <Modal open={!!menu} onClose={() => setMenu(null)} bare title="예약 옵션">
        <SheetButton
          onClick={() => {
            if (menu) publish.mutate(menu.id)
            setMenu(null)
          }}
        >
          지금 게시
        </SheetButton>
        <SheetButton
          onClick={() => {
            if (menu) {
              setTime(toLocalInput(new Date(menu.scheduledAt).getTime()))
              setEdit(menu)
            }
            setMenu(null)
          }}
        >
          시간 변경
        </SheetButton>
        <SheetButton
          danger
          onClick={() => {
            setCancel(menu)
            setMenu(null)
          }}
        >
          예약 취소
        </SheetButton>
        <SheetButton onClick={() => setMenu(null)}>닫기</SheetButton>
      </Modal>

      <Modal open={!!edit} onClose={() => setEdit(null)} title="게시 시각 변경">
        <div className="space-y-3 p-4">
          <input
            type="datetime-local"
            className="input"
            value={time}
            min={laterInput(10)}
            onChange={(e) => setTime(e.target.value)}
            aria-label="게시할 날짜와 시각"
          />
          <button
            className="btn btn-primary w-full"
            disabled={!time || reschedule.isPending}
            onClick={() => edit && reschedule.mutate({ id: edit.id, at: time })}
          >
            저장
          </button>
        </div>
      </Modal>

      <ConfirmModal
        open={!!cancel}
        onClose={() => setCancel(null)}
        title="예약을 취소하시겠어요?"
        message="예약한 게시물이 삭제되며 되돌릴 수 없습니다."
        confirmLabel="예약 취소"
        onConfirm={() => {
          if (cancel) remove.mutate(cancel.id)
          setCancel(null)
        }}
      />
    </div>
  )
}

function ScheduledRow({ item, onMenu }: { item: ScheduledPost; onMenu?: () => void }) {
  const body = (
    <>
      <div className="relative h-16 w-16 shrink-0 overflow-hidden rounded-md bg-surface-2">
        <img src={item.thumbUrl} alt="" className="h-full w-full object-cover" />
        {item.mediaCount > 1 && <Layers size={14} className="absolute top-1 right-1 text-white drop-shadow" />}
      </div>
      <div className="min-w-0 flex-1 text-sm">
        <p className="truncate">{item.captionStart ?? <span className="text-muted">문구 없음</span>}</p>
        <p className="mt-0.5 text-xs text-muted">
          {item.status === 'PENDING' && <>{scheduleLabel(item.scheduledAt)} 게시 예정</>}
          {item.status === 'PUBLISHED' && <>{scheduleLabel(item.scheduledAt)} 게시됨</>}
          {item.status === 'FAILED' && <span className="text-danger">게시하지 못함: {item.failure}</span>}
        </p>
      </div>
    </>
  )
  if (item.status === 'PUBLISHED' && item.shortcode) {
    return (
      <li>
        <Link to={`/p/${item.shortcode}`} className="flex items-center gap-3 px-4 py-2 hover:bg-surface-2">
          {body}
        </Link>
      </li>
    )
  }
  return (
    <li>
      <button
        className="flex w-full items-center gap-3 px-4 py-2 text-left hover:bg-surface-2 disabled:cursor-default"
        disabled={!onMenu}
        onClick={onMenu}
        aria-label="예약 옵션"
      >
        {body}
      </button>
    </li>
  )
}
