import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import { useInfiniteQuery } from '@tanstack/react-query'
import { Clapperboard, Copy, Heart, MessageCircle, Search } from 'lucide-react'
import clsx from 'clsx'
import { api } from '../../lib/api'
import { formatCount } from '../../lib/format'
import type { GridItem } from '../../lib/types'

/**
 * 탐색 (분석 문서 §11): 팔로우하지 않는 공개 계정의 게시물·릴스를 점수 순으로.
 * 5칸씩 묶어 그중 하나(릴스 우선)를 세로로 긴 큰 칸으로, 왼쪽·오른쪽을 번갈아 배치한다.
 */
export function ExplorePage() {
  const query = useInfiniteQuery({
    queryKey: ['explore'],
    queryFn: ({ pageParam }) => api.get<{ items: GridItem[]; nextPage: number | null }>(`/api/explore?page=${pageParam}`),
    initialPageParam: 0,
    getNextPageParam: (last) => last.nextPage ?? undefined,
  })
  const sentinel = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const el = sentinel.current
    if (!el) return
    const io = new IntersectionObserver(
      ([e]) => {
        if (e.isIntersecting && query.hasNextPage && !query.isFetchingNextPage) void query.fetchNextPage()
      },
      { rootMargin: '800px 0px' },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [query])

  const items = query.data?.pages.flatMap((p) => p.items) ?? []
  const blocks: GridItem[][] = []
  for (let i = 0; i < items.length; i += 5) blocks.push(items.slice(i, i + 5))

  return (
    <div className="mx-auto max-w-[935px] pb-8 md:pt-6">
      <Link to="/explore/search" className="mx-3 my-3 flex items-center gap-2 rounded-lg bg-surface-2 px-3 py-2 text-sm text-muted md:hidden">
        <Search size={16} /> 검색
      </Link>
      {query.isLoading && (
        <div className="grid grid-cols-3 gap-1" aria-hidden>
          {Array.from({ length: 9 }, (_, i) => (
            <div key={i} className="aspect-[3/4] animate-pulse bg-surface-2" />
          ))}
        </div>
      )}
      {query.isError && <p className="p-10 text-center text-sm text-danger">{query.error.message}</p>}
      {query.isSuccess && items.length === 0 && <p className="p-10 text-center text-sm text-muted">탐색할 게시물이 없습니다.</p>}
      <div className="flex flex-col gap-0.5 md:gap-1">
        {blocks.map((block, b) => {
          const tallIndex = Math.max(0, block.findIndex((x) => x.type === 'REEL'))
          const tall = block[tallIndex]
          const rest = block.filter((_, i) => i !== tallIndex)
          const right = b % 2 === 1
          return block.length < 5 ? (
            <div key={b} className="grid grid-cols-3 gap-0.5 md:gap-1">
              {block.map((it) => (
                <Tile key={it.shortcode} item={it} className="aspect-[3/4]" />
              ))}
            </div>
          ) : (
            <div key={b} className="grid aspect-[9/8] grid-cols-3 grid-rows-[1fr_1fr] gap-0.5 md:gap-1">
              <Tile item={tall} className={clsx('row-span-2', right ? 'col-start-3 row-start-1' : 'col-start-1 row-start-1')} />
              {rest.map((it) => (
                <Tile key={it.shortcode} item={it} />
              ))}
            </div>
          )
        })}
      </div>
      <div ref={sentinel} className="h-4" />
      {query.isFetchingNextPage && <p className="py-4 text-center text-xs text-muted">불러오는 중…</p>}
    </div>
  )
}

function Tile({ item, className }: { item: GridItem; className?: string }) {
  const reel = item.type === 'REEL'
  return (
    <Link
      to={reel ? `/reels/${item.shortcode}` : `/p/${item.shortcode}`}
      className={clsx('group relative block min-h-0 overflow-hidden bg-surface-2', className)}
      aria-label={reel ? '릴스 보기' : '게시물 보기'}
    >
      <img src={item.thumbUrl} alt="" loading="lazy" className="absolute inset-0 h-full w-full object-cover" />
      {reel ? (
        <Clapperboard size={18} className="absolute top-2 right-2 text-white drop-shadow" aria-hidden />
      ) : (
        item.mediaCount > 1 && <Copy size={18} className="absolute top-2 right-2 rotate-180 text-white drop-shadow" aria-hidden />
      )}
      <div className="absolute inset-0 hidden items-center justify-center gap-5 bg-black/35 font-bold text-white group-hover:flex">
        {item.likeCount !== null && (
          <span className="flex items-center gap-1.5">
            <Heart size={20} className="fill-white" /> {formatCount(item.likeCount)}
          </span>
        )}
        <span className="flex items-center gap-1.5">
          <MessageCircle size={20} className="-scale-x-100 fill-white" /> {formatCount(item.commentCount)}
        </span>
      </div>
    </Link>
  )
}
