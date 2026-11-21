import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { Archive, ChevronLeft, X } from 'lucide-react'
import { nowMs } from '../../lib/clock'
import { useStoryArchive } from '../../lib/stories'
import type { StoryView } from '../../lib/types'

/** 내 스토리 아카이브: 24시간이 지나 사라진 스토리도 여기에 남는다 (나만 볼 수 있음) */
export function StoryArchivePage() {
  const navigate = useNavigate()
  const archive = useStoryArchive()
  const [open, setOpen] = useState<StoryView | null>(null)
  const sentinel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = sentinel.current
    if (!el) return
    const io = new IntersectionObserver((e) => {
      if (e[0].isIntersecting && archive.hasNextPage && !archive.isFetchingNextPage) archive.fetchNextPage()
    })
    io.observe(el)
    return () => io.disconnect()
  }, [archive])

  const items = archive.data?.pages.flatMap((p) => p.items) ?? []

  return (
    <div className="mx-auto max-w-[935px] pb-8">
      <header className="flex items-center gap-2 px-2 pt-4 pb-3 md:pt-8">
        <button aria-label="뒤로" onClick={() => navigate(-1)} className="p-2">
          <ChevronLeft size={26} />
        </button>
        <h1 className="text-lg font-bold">스토리 아카이브</h1>
      </header>
      <p className="px-4 pb-3 text-sm text-muted">
        스토리는 24시간이 지나면 다른 사람에게 보이지 않지만, 아카이브에는 나만 볼 수 있게 남습니다.
      </p>
      {archive.isLoading && <p className="p-6 text-center text-sm text-muted">불러오는 중…</p>}
      {archive.isSuccess && items.length === 0 && (
        <div className="flex flex-col items-center gap-3 py-16 text-center text-muted">
          <Archive size={44} strokeWidth={1.3} />
          <p className="text-sm">아직 올린 스토리가 없습니다.</p>
        </div>
      )}
      <ul className="grid grid-cols-3 gap-0.5 md:gap-1">
        {items.map((s) => {
          const d = new Date(s.createdAt)
          const active = !s.expired && new Date(s.expiresAt).getTime() > nowMs()
          return (
            <li key={s.id}>
              <button onClick={() => setOpen(s)} className="relative block aspect-[9/16] w-full overflow-hidden bg-surface-2">
                <img src={s.mediaUrl} alt={s.altText ?? '스토리'} className="h-full w-full object-cover" loading="lazy" />
                <span className="absolute top-2 left-2 rounded-md bg-bg/90 px-1.5 py-0.5 text-center text-xs leading-tight font-semibold text-text">
                  {d.getDate()}
                  <br />
                  {d.getMonth() + 1}월
                </span>
                {active && (
                  <span className="absolute right-2 bottom-2 rounded bg-primary px-1.5 py-0.5 text-[10px] font-semibold text-white">
                    게시 중
                  </span>
                )}
              </button>
            </li>
          )
        })}
      </ul>
      <div ref={sentinel} className="h-4" />

      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/90" role="dialog" aria-label="스토리 보기" onClick={() => setOpen(null)}>
          <button aria-label="닫기" className="absolute top-4 right-4 p-2 text-white" onClick={() => setOpen(null)}>
            <X size={28} />
          </button>
          <img src={open.mediaUrl} alt={open.altText ?? '스토리'} className="max-h-[90dvh] max-w-[95vw] rounded-lg object-contain" />
          <p className="absolute bottom-6 text-sm text-white/80">
            {new Date(open.createdAt).toLocaleString('ko-KR', { dateStyle: 'long', timeStyle: 'short' })}
            {open.viewerCount !== null && ` · 조회 ${open.viewerCount}명`}
          </p>
        </div>
      )}
    </div>
  )
}
