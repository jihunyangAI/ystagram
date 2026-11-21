import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Copy, Heart, MessageCircle } from 'lucide-react'
import { formatCount } from '../../lib/format'
import { useGrid } from '../../lib/social'
import type { GridItem } from '../../lib/types'

/** 3:4 그리드 칸들 (프로필·리포스트·저장됨·해시태그·장소 공통) */
export function GridTiles({ items }: { items: GridItem[] }) {
  return (
    <div className="grid grid-cols-3 gap-0.5 md:gap-1">
      {items.map((it) => (
        <Link
          key={it.shortcode}
          to={`/p/${it.shortcode}`}
          className="group relative block aspect-[3/4] overflow-hidden bg-surface-2"
          aria-label={`게시물 보기${it.mediaCount > 1 ? ` (사진 ${it.mediaCount}장)` : ''}`}
        >
          <img src={it.thumbUrl} alt="" loading="lazy" className="h-full w-full object-cover" />
          {it.mediaCount > 1 && (
            <Copy size={18} className="absolute top-2 right-2 rotate-180 text-white drop-shadow" aria-hidden />
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
export function InfiniteGrid({ queryKey, url, empty }: { queryKey: unknown[]; url: string; empty: ReactNode }) {
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
          <div key={i} className="aspect-[3/4] animate-pulse bg-surface-2" />
        ))}
      </div>
    )
  }
  if (query.isError) return <p className="p-10 text-center text-sm text-danger">{query.error.message}</p>
  if (items.length === 0) return <>{empty}</>

  return (
    <>
      <GridTiles items={items} />
      <div ref={sentinel} className="h-4" />
      {query.isFetchingNextPage && <p className="py-4 text-center text-xs text-muted">불러오는 중…</p>}
    </>
  )
}
