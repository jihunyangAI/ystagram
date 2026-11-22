import { createAvatar } from '@dicebear/core'
import * as notionists from '@dicebear/notionists'
import { useMemo } from 'react'
import clsx from 'clsx'
import type { UserSummary } from '../lib/types'

interface Props {
  user: Pick<UserSummary, 'username' | 'avatarUrl' | 'avatarSeed'>
  size?: number
  className?: string
}

/** 프로필 사진 주소: 직접 올린 사진, 없으면 avatarSeed로 만든 일러스트 (지도 핀 등 img 밖에서도 쓴다) */
export function avatarSrc(user: Pick<UserSummary, 'avatarUrl' | 'avatarSeed'>): string {
  if (user.avatarUrl) return user.avatarUrl
  return createAvatar(notionists, { seed: user.avatarSeed, backgroundColor: ['e3f3ee', 'f4efe6', 'e8edf7', 'f6e7ea', 'eef3df'] }).toDataUri()
}

/** 프로필 사진. 직접 올린 사진이 없으면 avatarSeed로 일러스트 아바타를 만든다 (오프라인, 무료). */
export function Avatar({ user, size = 32, className }: Props) {
  const { avatarUrl, avatarSeed } = user
  const src = useMemo(() => avatarSrc({ avatarUrl, avatarSeed }), [avatarUrl, avatarSeed])

  return (
    <img
      src={src}
      alt={`${user.username}님의 프로필 사진`}
      width={size}
      height={size}
      className={clsx('shrink-0 rounded-full border border-border bg-surface-2 object-cover', className)}
      style={{ width: size, height: size }}
    />
  )
}
