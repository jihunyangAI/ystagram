import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Clapperboard, Copy, Heart, MessageCircle, Play } from 'lucide-react'
import clsx from 'clsx'
import { formatCount } from '../../lib/format'
import { useGrid } from '../../lib/social'
import type { GridItem } from '../../lib/types'

/**
 * 그리드 칸들 (프로필·리포스트·저장됨·해시태그·장소 공통).
 * variant="reels": 9:16 칸에 조회수 (프로필 릴스 탭)
 */
export function GridTiles({ items, variant = 'posts' }: { items: GridItem[]; variant?: 'posts' | 'reels' }) {
  return (
    <div className="grid grid-cols-3 gap-0.5 md:gap-1">
      {items.map((it) => (
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
          {it.type === 'REEL' && variant !== 'reels' && (
            <Clapperboard size={18} className="absolute top-2 right-2 text-white drop-shadow" aria-hidden />
          )}
          {it.type !== 'REEL' && it.mediaCount > 1 && (
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
}: {
  queryKey: unknown[]
  url: string
  empty: ReactNode
  variant?: 'posts' | 'reels'
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
      <GridTiles items={items} variant={variant} />
      <div ref={sentinel} className="h-4" />
      {query.isFetchingNextPage && <p className="py-4 text-center text-xs text-muted">불러오는 중…</p>}
    </>
  )
}
