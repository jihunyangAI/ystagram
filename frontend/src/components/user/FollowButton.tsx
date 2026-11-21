import { useState } from 'react'
import clsx from 'clsx'
import { useToggleFollow } from '../../lib/posts'
import type { Relationship, UserSummary } from '../../lib/types'
import { Avatar } from '../Avatar'
import { ConfirmModal } from '../Modal'
import { toast } from '../Toast'

interface Props {
  user: UserSummary
  relationship: Relationship
  followsMe?: boolean
  /** button: 채워진 버튼 / link: 글자만 (피드 헤더·추천 목록) */
  variant?: 'button' | 'link'
  className?: string
}

/**
 * 팔로우 버튼. 문구는 인스타그램과 같다:
 * 팔로우 / 맞팔로우(상대가 나를 팔로우 중) / 팔로잉 / 요청됨(비공개 계정 승인 대기)
 */
export function FollowButton({ user, relationship, followsMe, variant = 'button', className }: Props) {
  const toggle = useToggleFollow()
  const [confirm, setConfirm] = useState(false)
  // 응답이 오기 전에도 바로 바뀐 것처럼 보이게 한다
  const [optimistic, setOptimistic] = useState<Relationship | null>(null)
  const rel = optimistic ?? relationship

  if (rel === 'SELF') return null

  const follow = () => {
    setOptimistic(user.isPrivate ? 'REQUESTED' : 'FOLLOWING')
    toggle.mutate(
      { username: user.username, follow: true },
      {
        onSuccess: (r) => setOptimistic(r.relationship),
        onError: (e) => {
          setOptimistic(null)
          toast(e.message)
        },
      },
    )
  }
  const unfollow = () => {
    setConfirm(false)
    setOptimistic('NONE')
    toggle.mutate(
      { username: user.username, follow: false },
      {
        onSuccess: (r) => setOptimistic(r.relationship),
        onError: (e) => {
          setOptimistic(null)
          toast(e.message)
        },
      },
    )
  }

  const label = rel === 'FOLLOWING' ? '팔로잉' : rel === 'REQUESTED' ? '요청됨' : followsMe ? '맞팔로우' : '팔로우'
  const active = rel === 'NONE'

  return (
    <>
      <button
        type="button"
        onClick={active ? follow : () => setConfirm(true)}
        className={clsx(
          variant === 'link'
            ? clsx('text-sm font-semibold', active ? 'text-primary hover:text-text' : 'text-text hover:text-muted')
            : clsx('btn', active ? 'btn-primary' : 'btn-secondary'),
          className,
        )}
      >
        {label}
      </button>
      <ConfirmModal
        open={confirm}
        onClose={() => setConfirm(false)}
        avatar={<Avatar user={user} size={90} />}
        title={rel === 'REQUESTED' ? `@${user.username}님에게 보낸 팔로우 요청을 취소하시겠어요?` : `@${user.username}님의 팔로우를 취소하시겠어요?`}
        message={user.isPrivate && rel === 'FOLLOWING' ? '비공개 계정이라 다시 팔로우하려면 요청을 보내야 합니다.' : undefined}
        confirmLabel={rel === 'REQUESTED' ? '요청 취소' : '팔로우 취소'}
        onConfirm={unfollow}
      />
    </>
  )
}
