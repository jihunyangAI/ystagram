import { useEffect } from 'react'
import { useLocation } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { markRead, markUnsent, upsertMessage } from '../lib/dm'
import { connectRealtime, useRealtimeEvent } from '../lib/realtime'
import type { MessageView, NotificationPing, NotificationUnread } from '../lib/types'
import { toast } from './Toast'

/** 알림 한 줄 문구 (토스트·활동 화면 공통) */
export function notificationVerb(type: NotificationPing['type']): string {
  switch (type) {
    case 'LIKE':
      return '회원님의 게시물을 좋아합니다.'
    case 'COMMENT':
      return '댓글을 남겼습니다:'
    case 'REPLY':
      return '회원님의 댓글에 답글을 남겼습니다:'
    case 'COMMENT_LIKE':
      return '회원님의 댓글을 좋아합니다:'
    case 'MENTION':
      return '회원님을 언급했습니다:'
    case 'FOLLOW':
      return '회원님을 팔로우하기 시작했습니다.'
    case 'FOLLOW_REQUEST':
      return '회원님에게 팔로우를 요청했습니다.'
    case 'FOLLOW_ACCEPTED':
      return '회원님의 팔로우 요청을 수락했습니다.'
    case 'REPOST':
      return '회원님의 게시물을 리포스트했습니다.'
    case 'STORY_LIKE':
      return '회원님의 스토리를 좋아합니다.'
  }
}

/**
 * 로그인한 동안 실시간 연결을 유지하고, 받은 이벤트를 화면 캐시에 반영한다.
 * - 알림: 배지 숫자 갱신 + 토스트
 * - DM: 대화방 메시지·받은 메시지함·안 읽은 수
 * - 스토리: 트레이 새로고침
 */
export function RealtimeBridge({ meId }: { meId: number }) {
  const qc = useQueryClient()
  const { pathname } = useLocation()

  useEffect(() => {
    connectRealtime(meId)
  }, [meId])
  useEffect(() => () => connectRealtime(null), [])

  useRealtimeEvent((event) => {
    switch (event.type) {
      case 'notification:new': {
        const ping = event.data as NotificationPing
        qc.setQueryData<NotificationUnread>(['notif-unread'], (u) => ({ requests: u?.requests ?? 0, count: ping.unread }))
        qc.invalidateQueries({ queryKey: ['activity'] })
        if (ping.type === 'FOLLOW_REQUEST') {
          qc.invalidateQueries({ queryKey: ['notif-unread'] })
          qc.invalidateQueries({ queryKey: ['follow-requests'] })
        }
        if (!pathname.startsWith('/notifications')) {
          toast(`${ping.actor.username}님이 ${notificationVerb(ping.type)}${ping.body ? ` ${ping.body}` : ''}`)
        }
        break
      }
      case 'message:new': {
        const m = event.data as MessageView
        upsertMessage(qc, m)
        qc.invalidateQueries({ queryKey: ['inbox'] })
        qc.invalidateQueries({ queryKey: ['dm-unread'] })
        const viewing = pathname === `/direct/t/${m.conversationId}`
        if (m.sender.id !== meId && !viewing) {
          const text = m.body ?? (m.kind === 'IMAGE' ? '사진을 보냈습니다' : m.kind === 'POST' ? '게시물을 보냈습니다' : '메시지')
          toast(`💬 ${m.sender.username}: ${text}`)
        }
        break
      }
      case 'message:edit':
        upsertMessage(qc, event.data as MessageView)
        break
      case 'message:unsend': {
        const { conversationId, messageId } = event.data as { conversationId: number; messageId: number }
        markUnsent(qc, conversationId, messageId)
        qc.invalidateQueries({ queryKey: ['inbox'] })
        break
      }
      case 'message:read': {
        const { conversationId, userId, messageId } = event.data as { conversationId: number; userId: number; messageId: number }
        markRead(qc, conversationId, userId, messageId)
        if (userId === meId) qc.invalidateQueries({ queryKey: ['dm-unread'] })
        break
      }
      case 'story:new':
        qc.invalidateQueries({ queryKey: ['story-tray'] })
        break
    }
  })

  return null
}
