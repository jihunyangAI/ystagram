import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Clapperboard, Copy, Heart, MessageCircle, Pin, Play } from 'lucide-react'
import clsx from 'clsx'
import { formatCount } from '../../lib/format'
import { useGrid } from '../../lib/social'
import type { GridItem } from '../../lib/types'

/**
 * 그리드 칸들 (프로필·리포스트·저장됨·해시태그·장소 공통).
 * variant="reels": 9:16 칸에 조회수 (프로필 릴스 탭)
 */
export function GridTiles({
  items,
  variant = 'posts',
  onMove,
}: {
  items: GridItem[]
  variant?: 'posts' | 'reels'
  /** 그리드 순서 바꾸기 모드: 칸마다 ◀ ▶ (고정한 글은 제외) */
  onMove?: (item: GridItem, direction: 'FRONT' | 'BACK') => void
}) {
  return (
    <div className="grid grid-cols-3 gap-0.5 md:gap-1">
      {items.map((it) => (
        <div key={it.shortcode} className="relative">
        {onMove && !it.pinned && (
          <div className="absolute inset-x-0 bottom-0 z-10 flex justify-between bg-black/40 p-1">
            <button aria-label="앞으로" className="rounded bg-white/90 px-2 text-sm font-bold text-black" onClick={() => onMove(it, 'FRONT')}>
              ◀
            </button>
            <button aria-label="뒤로" className="rounded bg-white/90 px-2 text-sm font-bold text-black" onClick={() => onMove(it, 'BACK')}>
              ▶
            </button>
          </div>
        )}
        <Link
          key={it.shortcode}
          to={it.type === 'REEL' && variant === 'reels' ? `/reels/${it.shortcode}` : `/p/${it.shortcode}`}
          className={clsx(
            'group relative block overflow-hidden bg-surface-2',
            variant === 'reels' ? 'aspect-[9/16]' : 'aspect-[3/4]',
          )}
          aria-label={`${it.type === 'REEL' ? '릴스' : '게시물'} 보기${it.mediaCount > 1 ? ` (사진 ${it.mediaCount}장)` : ''}`}
        >
          <img src={it.thumbUrl} alt="" loading="lazy" className="h-full w-full object-cover" />
          {it.pinned && (
            <Pin size={18} aria-label="고정됨" className="absolute top-2 right-2 rotate-45 fill-white text-white drop-shadow" />
          )}
          {!it.pinned && it.type === 'REEL' && variant !== 'reels' && (
            <Clapperboard size={18} className="absolute top-2 right-2 text-white drop-shadow" aria-hidden />
          )}
          {!it.pinned && it.type !== 'REEL' && it.mediaCount > 1 && (
            <Copy size={18} className="absolute top-2 right-2 rotate-180 text-white drop-shadow" aria-hidden />
          )}
          {variant === 'reels' && (
            <span className="absolute bottom-2 left-2 flex items-center gap-1 text-sm font-semibold text-white drop-shadow">
              <Play size={14} className="fill-white" /> {formatCount(it.viewCount)}
            </span>
          )}
          <div className="absolute inset-0 hidden items-center justify-center gap-6 bg-black/35 font-bold text-white group-hover:flex">
            {it.likeCount !== null && (
              <span className="flex items-center gap-1.5">
                <Heart size={20} className="fill-white" /> {formatCount(it.likeCount)}
              </span>
            )}
            <span className="flex items-center gap-1.5">
              <MessageCircle size={20} className="-scale-x-100 fill-white" /> {formatCount(it.commentCount)}
            </span>
          </div>
        </Link>
        </div>
      ))}
    </div>
  )
}

/** 끝까지 내리면 다음 12칸을 불러오는 그리드 */
export function InfiniteGrid({
  queryKey,
  url,
  empty,
  variant,
  onMove,
}: {
  queryKey: unknown[]
  url: string
  empty: ReactNode
  variant?: 'posts' | 'reels'
  onMove?: (item: GridItem, direction: 'FRONT' | 'BACK') => void
}) {
  const query = useGrid(queryKey, url)
  const sentinel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = sentinel.current
    if (!el) return
    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && query.hasNextPage && !query.isFetchingNextPage) query.fetchNextPage()
      },
      { rootMargin: '600px 0px' },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [query])

  const items = query.data?.pages.flatMap((p) => p.items) ?? []

  if (query.isLoading) {
    return (
      <div className="grid grid-cols-3 gap-0.5" aria-hidden>
        {Array.from({ length: 6 }, (_, i) => (
          <div key={i} className={clsx('animate-pulse bg-surface-2', variant === 'reels' ? 'aspect-[9/16]' : 'aspect-[3/4]')} />
        ))}
      </div>
    )
  }
  if (query.isError) return <p className="p-10 text-center text-sm text-danger">{query.error.message}</p>
  if (items.length === 0) return <>{empty}</>

  return (
    <>
      <GridTiles items={items} variant={variant} onMove={onMove} />
      <div ref={sentinel} className="h-4" />
      {query.isFetchingNextPage && <p className="py-4 text-center text-xs text-muted">불러오는 중…</p>}
    </>
  )
}
