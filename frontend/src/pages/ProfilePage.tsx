import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router'
import { Camera, ChevronDown, Clapperboard, Grid3x3, Lock, Menu, Repeat2, SquareUser } from 'lucide-react'
import { useState } from 'react'
import clsx from 'clsx'
import type { LucideIcon } from 'lucide-react'
import { Avatar } from '../components/Avatar'
import { UserBadges } from '../components/UserBadges'
import { toast } from '../components/Toast'
import { api, ApiError } from '../lib/api'
import { formatCount } from '../lib/format'
import type { Profile } from '../lib/types'
import { NotFoundPage } from './NotFoundPage'

const TABS: { key: string; label: string; icon: LucideIcon }[] = [
  { key: 'posts', label: '게시물', icon: Grid3x3 },
  { key: 'reels', label: '릴스', icon: Clapperboard },
  { key: 'reposts', label: '리포스트', icon: Repeat2 },
  { key: 'tagged', label: '태그됨', icon: SquareUser },
]

const later = () => toast('M1 단계에서 추가됩니다')

export function ProfilePage() {
  const { username = '' } = useParams()
  const [tab, setTab] = useState('posts')
  const { data: profile, isLoading, error } = useQuery({
    queryKey: ['profile', username],
    queryFn: () => api.get<Profile>(`/api/users/${encodeURIComponent(username)}`),
  })

  if (isLoading) return <div className="p-10 text-center text-sm text-muted">불러오는 중…</div>
  if (error instanceof ApiError && error.status === 404) return <NotFoundPage />
  if (error || !profile) return <div className="p-10 text-center text-sm text-danger">프로필을 불러오지 못했습니다.</div>

  const { user } = profile
  // M0에는 팔로우 관계를 아직 조회하지 않으므로, 다른 사람의 비공개 계정은 잠금 화면으로 보여 준다
  const locked = user.isPrivate && !profile.me

  const stats = [
    { label: '게시물', value: profile.postsCount },
    { label: '팔로워', value: profile.followersCount },
    { label: '팔로잉', value: profile.followingCount },
  ]

  const actions = profile.me ? (
    <>
      <button className="btn btn-secondary flex-1 md:flex-none" onClick={later}>
        프로필 편집
      </button>
      <button className="btn btn-secondary flex-1 md:flex-none" onClick={later}>
        프로필 공유
      </button>
    </>
  ) : (
    <>
      <button className="btn btn-primary flex-1 md:flex-none" onClick={later}>
        팔로우
      </button>
      <button className="btn btn-secondary flex-1 md:flex-none" onClick={later}>
        메시지
      </button>
      <button className="btn btn-secondary px-2" aria-label="추천 계정" onClick={later}>
        <ChevronDown size={16} />
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
        <button aria-label="메뉴" onClick={later}>
          <Menu size={24} />
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
              {stats.map((s) => (
                <li key={s.label} className="flex flex-col items-center md:flex-row md:gap-1">
                  <span className="font-semibold">{formatCount(s.value)}</span>
                  <span className="text-sm text-muted md:text-base md:text-text">{s.label}</span>
                </li>
              ))}
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

      {/* 탭 */}
      {locked ? (
        <div className="mt-8 border-t border-border px-6 py-14 text-center">
          <span className="mx-auto flex h-16 w-16 items-center justify-center rounded-full border-2 border-text">
            <Lock size={28} strokeWidth={1.5} />
          </span>
          <p className="mt-4 font-semibold">비공개 계정입니다</p>
          <p className="mt-1 text-sm text-muted">사진 및 동영상을 보려면 팔로우하세요.</p>
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
          <div className="flex flex-col items-center gap-3 px-6 py-16 text-center">
            <span className="flex h-16 w-16 items-center justify-center rounded-full border-2 border-text">
              <Camera size={28} strokeWidth={1.5} />
            </span>
            <p className="text-2xl font-extrabold">
              {profile.me ? '사진 공유' : '아직 게시물이 없습니다'}
            </p>
            {profile.me && <p className="text-sm text-muted">게시물 올리기는 M1 단계에서 추가됩니다.</p>}
          </div>
        </>
      )}
    </div>
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
      {profile.aiProfile && (
        <p className="mt-1 inline-block rounded bg-surface-2 px-1.5 py-0.5 text-xs text-muted">AI 생성 프로필</p>
      )}
    </div>
  )
}
