// 라이브 방송 데이터 (스토리 줄의 LIVE 표시, 방송 화면)

import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import type { UserSummary } from './types'

export interface LiveView {
  id: number
  host: UserSummary
  title: string | null
  /** CAMERA: 브라우저 카메라(WebRTC) / VIDEO: 가짜 라이브(영상 반복 재생) */
  kind: 'CAMERA' | 'VIDEO'
  videoUrl: string | null
  posterUrl: string | null
  status: 'LIVE' | 'ENDED'
  startedAt: string
  endedAt: string | null
  viewers: number
  peakViewers: number
  totalViewers: number
  heartCount: number
  commentCount: number
  badgeCount: number
  mine: boolean
  /** 게스트 (⚪ 흉내: 영상 없이 자리만) */
  guest: UserSummary | null
  guestStatus: 'INVITED' | 'JOINED' | null
}

export interface LiveComment {
  id: number
  liveId: number
  user: UserSummary
  kind: 'COMMENT' | 'JOIN' | 'BADGE'
  body: string | null
  badge: number | null
  createdAt: string
}

/** 지금 방송 중인 라이브 (30초마다 새로) */
export function useLives() {
  return useQuery({
    queryKey: ['lives'],
    queryFn: () => api.get<LiveView[]>('/api/lives'),
    refetchInterval: 30_000,
  })
}

/** 배지 단계별 가격 (가짜 결제) */
export const BADGE_PRICES = [0, 1100, 2200, 6600]

/**
 * 방송을 시작한 화면(/live/new)에서 방송 화면(/live/:id)으로 카메라를 넘겨주는 자리.
 * 새로 고침하면 사라지므로, 방송 화면은 없으면 카메라를 다시 켠다.
 */
export const hostMedia: { liveId: number | null; stream: MediaStream | null; synthetic: boolean } = {
  liveId: null,
  stream: null,
  synthetic: false,
}
