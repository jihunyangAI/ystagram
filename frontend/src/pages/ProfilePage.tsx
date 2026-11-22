import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import {
  Ban,
  Bookmark,
  Camera,
  Clapperboard,
  Crown,
  Grid3x3,
  Link2,
  Lock,
  Megaphone,
  Menu,
  MoreHorizontal,
  Repeat2,
  Settings,
  ShoppingBag,
  SquareUser,
  UserPlus,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import clsx from 'clsx'
import type { LucideIcon } from 'lucide-react'
import { UserBadges } from '../components/UserBadges'
import { toast } from '../components/Toast'
import { InfiniteGrid } from '../components/post/Grid'
import { HighlightsRow } from '../components/story/Highlights'
import { StoryRingAvatar } from '../components/story/StoryRingAvatar'
import { ProfileMenu } from '../components/user/ProfileMenu'
import { useRelationAction } from '../lib/relations'
import { FollowButton } from '../components/user/FollowButton'
import { UserListModal } from '../components/user/UserListModal'
import { api, ApiError } from '../lib/api'
import { openConversation } from '../lib/dm'
import { track } from '../lib/events'
import { formatCount } from '../lib/format'
import { useNotificationUnread, useSavedOverview } from '../lib/social'
import type { CollectionView, Profile } from '../lib/types'
import { NotFoundPage } from './NotFoundPage'
import { ExclusiveTab, ShopTab, SubscribeButton } from '../components/shop/ProfileCommerce'
import { useSubscription } from '../lib/shop'
import type { ProductView } from '../lib/types'
import type { ChannelSummary } from './direct/ChannelPane'

type TabKey = 'posts' | 'reels' | 'reposts' | 'saved' | 'tagged' | 'shop' | 'exclusive'

const TABS: { key: TabKey; label: string; icon: LucideIcon; stage?: string; meOnly?: boolean }[] = [
  { key: 'posts', label: '게시물', icon: Grid3x3 },
  { key: 'reels', label: '릴스', icon: Clapperboard },
  { key: 'reposts', label: '리포스트', icon: Repeat2 },
  { key: 'saved', label: '저장됨', icon: Bookmark, meOnly: true },
  { key: 'tagged', label: '태그됨', icon: SquareUser },
  { key: 'shop', label: '샵', icon: ShoppingBag },
  { key: 'exclusive', label: '구독자 전용', icon: Crown },
]

type ListKind = 'followers' | 'following' | null

export function ProfilePage() {
  const { username = '' } = useParams()
  const navigate = useNavigate()
  const [tab, setTab] = useState<TabKey>('posts')
  const [list, setList] = useState<ListKind>(null)
  const [opening, setOpening] = useState(false)
  const [menu, setMenu] = useState(false)
  const relation = useRelationAction()
  const { data: profile, isLoading, error } = useQuery({
    queryKey: ['profile', username],
    queryFn: () => api.get<Profile>(`/api/users/${encodeURIComponent(username)}`),
  })
  const unread = useNotificationUnread(!!profile?.me)
  const shop = useQuery({
    queryKey: ['shop', username],
    queryFn: () => api.get<ProductView[]>(`/api/users/${encodeURIComponent(username)}/products`),
    enabled: !!profile && !profile.flags.blocked,
  })
  const subscription = useSubscription(profile && !profile.flags.blocked ? username : undefined)

  useEffect(() => {
    if (profile) document.title = `${profile.user.fullName}(@${profile.user.username}) • Yangstagram`
    return () => {
      document.title = 'Yangstagram'
    }
  }, [profile])
  useEffect(() => setTab('posts'), [username])
  // 다른 사람 프로필 방문은 관계 친밀도·인사이트에 쓰인다
  const visitedId = profile && !profile.me ? profile.user.username : null
  useEffect(() => {
    if (visitedId) track({ type: 'PROFILE_VISIT', username: visitedId })
  }, [visitedId])

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


  const message = () => {
    setOpening(true)
    openConversation([user.username])
      .then((c) => navigate(`/direct/t/${c.id}`), (e: Error) => toast(e.message))
      .finally(() => setOpening(false))
  }

  const blockedActions = (
    <button
      className="btn btn-secondary flex-1 md:flex-none"
      disabled={relation.isPending}
      onClick={() => relation.mutate({ username: user.username, action: { op: 'block', on: false } })}
    >
      차단 해제
    </button>
  )

  const actions = profile.flags.blocked ? blockedActions : profile.me ? (
    <>
      <Link to="/accounts/edit" className="btn btn-secondary flex-1 md:flex-none">
        프로필 편집
      </Link>
      <Link to="/archive/stories" className="btn btn-secondary flex-1 md:flex-none">
        보관함 보기
      </Link>
      <Link to="/accounts/settings" className="btn btn-secondary hidden md:flex md:flex-none" aria-label="설정">
        <Settings size={18} />
      </Link>
      {user.isPrivate && (
        <Link to="/notifications/requests" className="btn btn-secondary relative md:flex-none" aria-label="팔로우 요청">
          <UserPlus size={18} />
          {!!unread.data?.requests && (
            <span className="absolute -top-1.5 -right-1.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-danger px-1 text-[11px] text-white">
              {unread.data.requests}
            </span>
          )}
        </Link>
      )}
    </>
  ) : (
    <>
      <FollowButton
        user={user}
        relationship={profile.relationship}
        followsMe={profile.followsMe}
        className="flex-1 md:flex-none"
      />
      <button className="btn btn-secondary flex-1 md:flex-none" disabled={opening} onClick={message}>
        메시지
      </button>
      <SubscribeButton username={user.username} />
      <button className="btn btn-secondary md:flex-none" aria-label="옵션" onClick={() => setMenu(true)}>
        <MoreHorizontal size={18} />
      </button>
    </>
  )

  const hasStory = profile.story !== 'NONE'
  const openStory = () => hasStory && navigate(`/stories/${user.username}`)
  const tabs = TABS.filter((t) => {
    if (t.meOnly && !profile.me) return false
    if (t.key === 'shop') return (shop.data?.length ?? 0) > 0 || (profile.me && profile.accountType !== 'PERSONAL')
    if (t.key === 'exclusive') return !!subscription.data && (subscription.data.price != null || subscription.data.exclusiveCount > 0)
    return true
  })

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
        {profile.me ? (
          <Link to="/accounts/settings" aria-label="설정">
            <Menu size={24} />
          </Link>
        ) : (
          <button aria-label="옵션" onClick={() => setMenu(true)}>
            <MoreHorizontal size={22} />
          </button>
        )}
      </header>

      {/* 프로필 헤더 */}
      <section className="px-4 pt-4 md:px-8 md:pt-10">
        <div className="flex items-center gap-6 md:items-start md:gap-20 md:px-12">
          <button
            onClick={openStory}
            className={clsx('shrink-0', !hasStory && 'cursor-default')}
            aria-label={hasStory ? `${user.username}님의 스토리 보기` : undefined}
            tabIndex={hasStory ? 0 : -1}
          >
            <span className="hidden md:inline-flex">
              <StoryRingAvatar user={user} ring={profile.story} size={150} />
            </span>
            <span className="inline-flex md:hidden">
              <StoryRingAvatar user={user} ring={profile.story} size={86} />
            </span>
          </button>

          <div className="min-w-0 flex-1">
            <div className="hidden flex-wrap items-center gap-3 md:flex">
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

      <ChannelCard username={user.username} />
      <HighlightsRow username={user.username} mine={profile.me} enabled={!locked} />

      {profile.flags.blocked ? (
        <div className="mt-8 border-t border-border px-6 py-14 text-center">
          <span className="mx-auto flex h-16 w-16 items-center justify-center rounded-full border-2 border-text">
            <Ban size={28} strokeWidth={1.5} />
          </span>
          <p className="mt-4 font-semibold">차단한 계정입니다</p>
          <p className="mt-1 text-sm text-muted">차단을 해제하면 이 사람의 게시물을 다시 볼 수 있습니다. 팔로우는 다시 해야 합니다.</p>
        </div>
      ) : locked ? (
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
            {tabs.map(({ key, label, icon: Icon }) => (
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
          {tab === 'posts' && (
            <InfiniteGrid
              queryKey={[user.username]}
              url={`/api/users/${user.username}/posts`}
              empty={<EmptyPosts mine={profile.me} />}
            />
          )}
          {tab === 'reposts' && (
            <InfiniteGrid
              queryKey={['reposts', user.username]}
              url={`/api/users/${user.username}/reposts`}
              empty={
                <EmptyTab
                  icon={Repeat2}
                  title="리포스트"
                  text={profile.me ? '다른 사람의 게시물을 리포스트하면 여기에 표시됩니다.' : '아직 리포스트한 게시물이 없습니다.'}
                />
              }
            />
          )}
          {tab === 'saved' && profile.me && <SavedTab username={user.username} />}
          {tab === 'shop' && <ShopTab username={user.username} mine={profile.me} />}
          {tab === 'exclusive' && <ExclusiveTab username={user.username} />}
          {tab === 'reels' && (
            <InfiniteGrid
              queryKey={['reels', user.username]}
              url={`/api/users/${user.username}/reels`}
              variant="reels"
              empty={<EmptyTab icon={Clapperboard} title="릴스" text={profile.me ? '릴스를 만들면 여기에 표시됩니다.' : '아직 릴스가 없습니다.'} />}
            />
          )}
          {tab === 'tagged' && (
            <InfiniteGrid
              queryKey={['tagged', user.username]}
              url={`/api/users/${user.username}/tagged`}
              empty={
                <EmptyTab
                  icon={SquareUser}
                  title="내가 나온 사진"
                  text={profile.me ? '사람들이 회원님을 사진에 태그하면 여기에 표시됩니다.' : '아직 태그된 사진이 없습니다.'}
                />
              }
            />
          )}
        </>
      )}

      {!profile.me && <ProfileMenu profile={profile} open={menu} onClose={() => setMenu(false)} />}
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

function EmptyPosts({ mine }: { mine: boolean }) {
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

function EmptyTab({ icon: Icon, title, text }: { icon: LucideIcon; title: string; text: string }) {
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-16 text-center">
      <span className="flex h-16 w-16 items-center justify-center rounded-full border-2 border-text">
        <Icon size={28} strokeWidth={1.5} />
      </span>
      <p className="text-2xl font-extrabold">{title}</p>
      <p className="text-sm text-muted">{text}</p>
    </div>
  )
}

/** 저장됨 탭: "모든 게시물" + 컬렉션 (본인만 볼 수 있음) */
function SavedTab({ username }: { username: string }) {
  const { data, isLoading } = useSavedOverview()
  if (isLoading) return <p className="p-10 text-center text-sm text-muted">불러오는 중…</p>
  if (!data) return null
  if (data.all.count === 0) {
    return <EmptyTab icon={Bookmark} title="저장" text="다시 보고 싶은 사진과 동영상을 저장하세요. 저장한 내용은 회원님만 볼 수 있습니다." />
  }
  return (
    <div className="px-1 pt-4 md:px-0">
      <p className="mb-3 px-3 text-xs text-muted md:px-0">저장한 내용은 회원님만 볼 수 있습니다</p>
      <div className="grid grid-cols-2 gap-1 md:grid-cols-3 md:gap-4">
        {[data.all, ...data.collections].map((c) => (
          <CollectionCard key={c.id ?? 'all'} c={c} to={`/${username}/saved/${c.id ?? 'all'}`} />
        ))}
      </div>
    </div>
  )
}

function CollectionCard({ c, to }: { c: CollectionView; to: string }) {
  return (
    <Link to={to} className="group relative block aspect-square overflow-hidden rounded-md bg-surface-2">
      {c.covers.length >= 4 ? (
        <span className="grid h-full grid-cols-2 grid-rows-2 gap-px">
          {c.covers.slice(0, 4).map((u) => (
            <img key={u} src={u} alt="" className="h-full w-full object-cover" />
          ))}
        </span>
      ) : c.covers[0] ? (
        <img src={c.covers[0]} alt="" className="h-full w-full object-cover" />
      ) : null}
      <span className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent" />
      <span className="absolute bottom-3 left-3 text-white">
        <b className="block text-base">{c.name}</b>
        <span className="text-xs text-white/80">게시물 {c.count}개</span>
      </span>
    </Link>
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

/** 프로필의 브로드캐스트 채널 (있을 때만) */
function ChannelCard({ username }: { username: string }) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const { data } = useQuery({
    queryKey: ['user-channel', username],
    queryFn: async () => (await api.get<ChannelSummary | undefined>(`/api/users/${username}/channel`)) ?? null,
  })
  const join = useMutation({
    mutationFn: () => api.put<ChannelSummary>(`/api/channels/${data!.id}/members/me`),
    onSuccess: (c) => {
      void qc.invalidateQueries({ queryKey: ['user-channel', username] })
      void qc.invalidateQueries({ queryKey: ['channels'] })
      navigate(`/direct/channel/${c.id}`)
    },
  })
  if (!data) return null
  return (
    <div className="mx-4 mb-3 flex items-center gap-3 rounded-xl border border-border px-3 py-2.5 md:mx-0">
      <Megaphone size={22} className="shrink-0 text-primary" />
      <div className="min-w-0 flex-1 text-sm">
        <p className="truncate font-semibold">{data.name}</p>
        <p className="text-xs text-muted">브로드캐스트 채널 · 멤버 {data.memberCount.toLocaleString('ko-KR')}명</p>
      </div>
      {data.joined || data.mine ? (
        <Link to={`/direct/channel/${data.id}`} className="btn btn-secondary px-3 py-1 text-sm">
          보기
        </Link>
      ) : (
        <button className="btn btn-primary px-3 py-1 text-sm" disabled={join.isPending} onClick={() => join.mutate()}>
          참여
        </button>
      )}
    </div>
  )
}
