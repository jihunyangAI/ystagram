import { BadgeCheck, Lock } from 'lucide-react'
import type { UserSummary } from '../lib/types'

/** 사용자 이름 옆의 인증 배지·비공개 표시 */
export function UserBadges({ user, size = 14 }: { user: Pick<UserSummary, 'verified' | 'isPrivate'>; size?: number }) {
  return (
    <>
      {user.verified && (
        <BadgeCheck size={size} className="shrink-0 fill-primary text-surface" aria-label="인증된 계정" />
      )}
      {user.isPrivate && <Lock size={size - 2} className="shrink-0 text-muted" aria-label="비공개 계정" />}
    </>
  )
}
