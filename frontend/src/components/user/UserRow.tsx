import { Link } from 'react-router'
import type { ReactNode } from 'react'
import type { Relationship, UserSummary } from '../../lib/types'
import { Avatar } from '../Avatar'
import { UserBadges } from '../UserBadges'
import { FollowButton } from './FollowButton'

interface Props {
  user: UserSummary
  relationship?: Relationship
  followsMe?: boolean
  /** 이름 아래 작은 글씨 (기본: 이름) */
  subtitle?: ReactNode
  size?: number
  onNavigate?: () => void
  followVariant?: 'button' | 'link'
}

/** 사용자 한 줄: 사진 · 사용자 이름 · 이름 · 팔로우 버튼 */
export function UserRow({ user, relationship, followsMe, subtitle, size = 44, onNavigate, followVariant = 'button' }: Props) {
  return (
    <div className="flex items-center gap-3 px-4 py-2">
      <Link to={`/${user.username}`} onClick={onNavigate} className="shrink-0">
        <Avatar user={user} size={size} />
      </Link>
      <div className="min-w-0 flex-1 text-sm leading-tight">
        <Link to={`/${user.username}`} onClick={onNavigate} className="flex items-center gap-1 font-semibold">
          <span className="truncate">{user.username}</span>
          <UserBadges user={{ verified: user.verified, isPrivate: false }} size={13} />
        </Link>
        <p className="truncate text-muted">{subtitle ?? user.fullName}</p>
      </div>
      {relationship && relationship !== 'SELF' && (
        <FollowButton
          user={user}
          relationship={relationship}
          followsMe={followsMe}
          variant={followVariant}
          className={followVariant === 'button' ? 'px-4 py-1.5' : undefined}
        />
      )}
    </div>
  )
}
