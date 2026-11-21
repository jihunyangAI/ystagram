import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { CircleCheck, FlaskConical } from 'lucide-react'
import { Avatar } from '../components/Avatar'
import { openLabSwitcher } from '../components/lab/LabSwitcher'
import { PostCard } from '../components/post/PostCard'
import { UserRow } from '../components/user/UserRow'
import { api } from '../lib/api'
import { useMe } from '../lib/auth'
import { useFeed } from '../lib/posts'
import type { SuggestedUser } from '../lib/types'

/** 홈: 팔로우한 사람들의 게시물(시간순) + 오른쪽 추천 계정 */
export function HomePage() {
  const { data: me } = useMe()
  const feed = useFeed()
  const sentinel = useRef<HTMLDivElement>(null)

  // 화면 끝에 닿으면 다음 페이지
  useEffect(() => {
    const el = sentinel.current
    if (!el) return
    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && feed.hasNextPage && !feed.isFetchingNextPage) feed.fetchNextPage()
      },
      { rootMargin: '800px 0px' },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [feed])

  if (!me) return null
  const posts = feed.data?.pages.flatMap((p) => p.items) ?? []

  return (
    <div className="mx-auto flex w-full max-w-[935px] justify-center gap-16 md:pt-6">
      <div className="w-full max-w-[470px]">
        {feed.isLoading && <FeedSkeleton />}
        {feed.isError && <p className="p-10 text-center text-sm text-danger">피드를 불러오지 못했습니다. {feed.error.message}</p>}
        {feed.isSuccess && posts.length === 0 && <EmptyFeed />}

        <div className="flex flex-col gap-2 md:gap-4">
          {posts.map((post) => (
            <PostCard key={post.shortcode} post={post} />
          ))}
        </div>

        <div ref={sentinel} />
        {feed.isFetchingNextPage && <FeedSkeleton count={1} />}
        {feed.isSuccess && posts.length > 0 && !feed.hasNextPage && <AllCaughtUp />}

        {/* 좁은 화면에서는 피드 끝에 추천을 보여 준다 */}
        <div className="mt-4 lg:hidden">
          <Suggestions title="회원님을 위한 추천" />
        </div>
      </div>

      <aside className="hidden w-[320px] shrink-0 pt-4 lg:block">
        <div className="sticky top-8">
          <div className="flex items-center gap-3 px-4">
            <Link to={`/${me.user.username}`}>
              <Avatar user={me.user} size={44} />
            </Link>
            <div className="min-w-0 flex-1 text-sm leading-tight">
              <Link to={`/${me.user.username}`} className="font-semibold">
                {me.user.username}
              </Link>
              <p className="truncate text-muted">{me.user.fullName}</p>
            </div>
            <button className="flex items-center gap-1 text-xs font-semibold text-primary" onClick={openLabSwitcher}>
              <FlaskConical size={14} /> 전환
            </button>
          </div>
          <div className="mt-6">
            <Suggestions title="회원님을 위한 추천" compact />
          </div>
          <p className="mt-8 px-4 text-xs text-muted">© 2026 Yangstagram · 연구용 데모 · 모든 사용자와 게시물은 가짜입니다</p>
        </div>
      </aside>
    </div>
  )
}

function Suggestions({ title, compact }: { title: string; compact?: boolean }) {
  const { data } = useQuery({
    queryKey: ['suggestions'],
    queryFn: () => api.get<SuggestedUser[]>('/api/suggestions/users?limit=5'),
    staleTime: 5 * 60_000,
  })
  if (!data || data.length === 0) return null
  return (
    <section aria-label={title} className={compact ? '' : 'rounded-xl border border-border py-2'}>
      <h2 className="px-4 py-2 text-sm font-semibold text-muted">{title}</h2>
      {data.map((s) => (
        <UserRow
          key={s.user.id}
          user={s.user}
          relationship="NONE"
          followsMe={s.followsMe}
          size={compact ? 36 : 44}
          followVariant="link"
          subtitle={s.followsMe ? '회원님을 팔로우합니다' : s.mutualCount > 0 ? `함께 아는 친구 ${s.mutualCount}명` : '회원님을 위한 추천'}
        />
      ))}
    </section>
  )
}

function EmptyFeed() {
  return (
    <div className="px-6 py-14 text-center">
      <p className="text-xl font-bold">Yangstagram에 오신 것을 환영합니다</p>
      <p className="mt-2 text-sm text-muted">사람들을 팔로우하면 그들이 공유한 사진과 동영상이 여기에 표시됩니다.</p>
      <div className="mt-6 text-left">
        <Suggestions title="팔로우할 계정 찾기" />
      </div>
    </div>
  )
}

function AllCaughtUp() {
  return (
    <div className="flex flex-col items-center gap-1 border-b border-border py-10 text-center">
      <CircleCheck size={56} strokeWidth={1.2} className="text-primary" />
      <p className="mt-2 text-lg">모두 확인했습니다</p>
      <p className="text-sm text-muted">최근 게시물을 모두 확인했습니다.</p>
    </div>
  )
}

function FeedSkeleton({ count = 2 }: { count?: number }) {
  return (
    <div aria-hidden className="space-y-6 py-2">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="animate-pulse">
          <div className="flex items-center gap-3 px-3 py-2 md:px-0">
            <div className="h-8 w-8 rounded-full bg-surface-2" />
            <div className="h-3 w-32 rounded bg-surface-2" />
          </div>
          <div className="aspect-[4/5] w-full bg-surface-2 md:rounded-md" />
          <div className="mt-3 h-3 w-24 rounded bg-surface-2" />
        </div>
      ))}
    </div>
  )
}
