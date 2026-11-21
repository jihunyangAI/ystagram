import clsx from 'clsx'
import type { StoryRing, UserSummary } from '../../lib/types'
import { Avatar } from '../Avatar'

/**
 * 스토리 링이 있는 프로필 사진.
 * 안 본 스토리 = 노랑~분홍~보라 그라데이션, 다 봄 = 회색, 없음 = 링 없음.
 */
export function StoryRingAvatar({
  user,
  ring,
  size,
  className,
}: {
  user: Pick<UserSummary, 'username' | 'avatarUrl' | 'avatarSeed'>
  ring: StoryRing
  size: number
  className?: string
}) {
  if (ring === 'NONE') return <Avatar user={user} size={size} className={className} />
  const pad = size >= 60 ? 3 : 2
  return (
    <span
      className={clsx(
        'inline-flex shrink-0 rounded-full',
        ring === 'UNSEEN' ? 'bg-[conic-gradient(from_200deg,#feda75,#fa7e1e,#d62976,#962fbf,#4f5bd5,#feda75)]' : 'bg-border',
        className,
      )}
      style={{ padding: pad }}
    >
      <span className="rounded-full bg-bg" style={{ padding: pad }}>
        <Avatar user={user} size={size - pad * 4} className="border-0" />
      </span>
    </span>
  )
}
