import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { Camera, Clapperboard, Copy, Grid3x3, Heart, Link2, Lock, Menu, MessageCircle, Repeat2, SquareUser } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import clsx from 'clsx'
import type { LucideIcon } from 'lucide-react'
import { Avatar } from '../components/Avatar'
import { UserBadges } from '../components/UserBadges'
import { toast } from '../components/Toast'
import { FollowButton } from '../components/user/FollowButton'
import { UserListModal } from '../components/user/UserListModal'
import { api, ApiError } from '../lib/api'
import { formatCount } from '../lib/format'
import type { GridItem, Profile, Slice } from '../lib/types'
import { NotFoundPage } from './NotFoundPage'

const TABS: { key: string; label: string; icon: LucideIcon; stage?: string }[] = [
  { key: 'posts', label: '게시물', icon: Grid3x3 },
  { key: 'reels', label: '릴스', icon: Clapperboard, stage: 'M3' },
  { key: 'reposts', label: '리포스트', icon: Repeat2, stage: 'M2' },
  { key: 'tagged', label: '태그됨', icon: SquareUser, stage: 'M3' },
]

type ListKind = 'followers' | 'following' | null

export function ProfilePage() {
  const { username = '' } = useParams()
  const [tab, setTab] = useState('posts')
  const [list, setList] = useState<ListKind>(null)
  const { data: profile, isLoading, error } = useQuery({
    queryKey: ['profile', username],
    queryFn: () => api.get<Profile>(`/api/users/${encodeURIComponent(username)}`),
  })

  useEffect(() => {
    if (profile) document.title = `${profile.user.fullName}(@${profile.user.username}) • Yangstagram`
    return () => {
      document.title = 'Yangstagram'
    }
  }, [profile])

  if (isLoading) return <div className="p-10 text-center text-sm text-muted">불러오는 중…</div>
  if (error instanceof ApiError && error.status === 404) return <NotFoundPage />
  if (error || !profile) return <div className="p-10 text-center text-sm text-danger">프로필을 불러오지 못했습니다.</div>

  const { user } = profile
  const locked = !profile.canViewContent

  const stats: { label: string; value: number; kind?: ListKind }[] = [
    { label: '게시물', value: profile.postsCount },
    { label: '팔로워', value: profile.followersCount, kind: 'followers' },
    { label: '팔로잉', value: profile.followingCount, kind: 'following' },
  ]

  const shareProfile = () => {
    const url = `${window.location.origin}/${user.username}`
    navigator.clipboard?.writeText(url).then(
      () => toast('프로필 링크를 복사했습니다'),
      () => toast(url),
    )
  }

  const actions = profile.me ? (
    <>
      <Link to="/accounts/edit" className="btn btn-secondary flex-1 md:flex-none">
        프로필 편집
      </Link>
      <button className="btn btn-secondary flex-1 md:flex-none" onClick={shareProfile}>
        프로필 공유
      </button>
    </>
  ) : (
    <>
      <FollowButton
        user={user}
        relationship={profile.relationship}
        followsMe={profile.followsMe}
        className="flex-1 md:flex-none"
      />
      <button className="btn btn-secondary flex-1 md:flex-none" onClick={() => toast('메시지(DM)는 M2 단계에서 추가됩니다')}>
        메시지
      </button>
    </>
  )

  return (
    <div className="mx-auto w-full max-w-[935px]">
      {/* 모바일 상단 바 */}
      <header className="sticky top-0 z-20 flex h-[44px] items-center justify-between border-b border-border bg-bg px-4 md:hidden">
        <span className="w-6" />
        <h1 className="flex items-center gap-1 font-bold">
          {user.isPrivate && <Lock size={14} />}
          {user.username}
          {user.verified && <UserBadges user={{ verified: true, isPrivate: false }} />}
        </h1>
        <button aria-label="프로필 링크 복사" onClick={shareProfile}>
          {profile.me ? <Menu size={24} /> : <Copy size={20} />}
        </button>
      </header>

      {/* 프로필 헤더 */}
      <section className="px-4 pt-4 md:px-8 md:pt-10">
        <div className="flex items-center gap-6 md:items-start md:gap-20 md:px-12">
          <Avatar user={user} size={150} className="hidden md:block" />
          <Avatar user={user} size={80} className="md:hidden" />

          <div className="min-w-0 flex-1">
            <div className="hidden items-center gap-3 md:flex">
              <h1 className="flex items-center gap-1.5 text-xl">
                {user.username} <UserBadges user={user} size={18} />
              </h1>
              <div className="flex gap-2">{actions}</div>
            </div>

            <ul className="flex justify-around md:mt-5 md:justify-start md:gap-10">
              {stats.map((s) => {
                const clickable = s.kind && !locked
                const content = (
                  <>
                    <span className="font-semibold">{formatCount(s.value)}</span>
                    <span className="text-sm text-muted md:text-base md:text-text">{s.label}</span>
                  </>
                )
                return (
                  <li key={s.label}>
                    {clickable ? (
                      <button className="flex flex-col items-center md:flex-row md:gap-1" onClick={() => setList(s.kind!)}>
                        {content}
                      </button>
                    ) : (
                      <span className="flex flex-col items-center md:flex-row md:gap-1">{content}</span>
                    )}
                  </li>
                )
              })}
            </ul>

            <div className="mt-5 hidden md:block">
              <Bio profile={profile} />
            </div>
          </div>
        </div>

        <div className="mt-3 md:hidden">
          <Bio profile={profile} />
        </div>
        <div className="mt-4 flex gap-2 md:hidden">{actions}</div>
      </section>

      {locked ? (
        <div className="mt-8 border-t border-border px-6 py-14 text-center">
          <span className="mx-auto flex h-16 w-16 items-center justify-center rounded-full border-2 border-text">
            <Lock size={28} strokeWidth={1.5} />
          </span>
          <p className="mt-4 font-semibold">비공개 계정입니다</p>
          <p className="mt-1 text-sm text-muted">
            {profile.relationship === 'REQUESTED'
              ? '팔로우 요청을 보냈습니다. 상대가 승인하면 사진과 동영상을 볼 수 있습니다.'
              : '사진 및 동영상을 보려면 팔로우하세요.'}
          </p>
        </div>
      ) : (
        <>
          <nav className="mt-6 flex border-t border-border md:justify-center md:gap-14" role="tablist">
            {TABS.map(({ key, label, icon: Icon }) => (
              <button
                key={key}
                role="tab"
                aria-selected={tab === key}
                aria-label={label}
                onClick={() => setTab(key)}
                className={clsx(
                  '-mt-px flex flex-1 items-center justify-center gap-1.5 border-t py-3 text-xs font-semibold tracking-wider md:flex-none',
                  tab === key ? 'border-text text-text' : 'border-transparent text-muted',
                )}
              >
                <Icon size={22} className="md:size-3.5" />
                <span className="hidden md:inline">{label}</span>
              </button>
            ))}
          </nav>
          {tab === 'posts' ? (
            <PostGrid username={user.username} mine={profile.me} />
          ) : (
            <div className="px-6 py-16 text-center text-sm text-muted">
              {TABS.find((t) => t.key === tab)?.label} 탭은 {TABS.find((t) => t.key === tab)?.stage} 단계에서 추가됩니다.
            </div>
          )}
        </>
      )}

      <UserListModal
        open={list === 'followers'}
        onClose={() => setList(null)}
        title="팔로워"
        url={`/api/users/${user.username}/followers`}
        queryKey={['followers', user.username]}
        empty="아직 팔로워가 없습니다."
        followsMe={profile.me}
      />
      <UserListModal
        open={list === 'following'}
        onClose={() => setList(null)}
        title="팔로잉"
        url={`/api/users/${user.username}/following`}
        queryKey={['following', user.username]}
        empty="아직 팔로우하는 사람이 없습니다."
      />
    </div>
  )
}

/** 게시물 그리드: 3열, 3:4 비율 (2025년 개편), 끝까지 내리면 더 불러온다 */
function PostGrid({ username, mine }: { username: string; mine: boolean }) {
  const query = useInfiniteQuery({
    queryKey: ['grid', username],
    queryFn: ({ pageParam }) =>
      api.get<Slice<GridItem>>(`/api/users/${username}/posts${pageParam ? `?cursor=${pageParam}` : ''}`),
    initialPageParam: '',
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })
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
  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-3 px-6 py-16 text-center">
        <span className="flex h-16 w-16 items-center justify-center rounded-full border-2 border-text">
          <Camera size={28} strokeWidth={1.5} />
        </span>
        <p className="text-2xl font-extrabold">{mine ? '사진 공유' : '게시물 없음'}</p>
        {mine && (
          <>
            <p className="text-sm text-muted">사진을 공유하면 회원님의 프로필에 표시됩니다.</p>
            <Link to="/create" className="text-sm font-semibold text-primary">
              첫 사진 공유하기
            </Link>
          </>
        )}
      </div>
    )
  }

  return (
    <>
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
      <div ref={sentinel} className="h-4" />
      {query.isFetchingNextPage && <p className="py-4 text-center text-xs text-muted">불러오는 중…</p>}
    </>
  )
}

function Bio({ profile }: { profile: Profile }) {
  return (
    <div className="text-sm leading-relaxed">
      <p className="font-semibold">
        {profile.user.fullName}
        {profile.pronouns && <span className="ml-1.5 font-normal text-muted">{profile.pronouns}</span>}
      </p>
      {profile.category && <p className="text-muted">{profile.category}</p>}
      {profile.bio && <p className="whitespace-pre-line">{profile.bio}</p>}
      {profile.website && (
        <a
          href={profile.website}
          target="_blank"
          rel="noopener noreferrer nofollow"
          className="flex items-center gap-1 font-semibold text-primary hover:underline"
        >
          <Link2 size={14} className="-rotate-45" />
          {profile.website.replace(/^https?:\/\//, '').replace(/\/$/, '')}
        </a>
      )}
      {profile.aiProfile && (
        <p className="mt-1 inline-block rounded bg-surface-2 px-1.5 py-0.5 text-xs text-muted">AI 생성 프로필</p>
      )}
    </div>
  )
}
