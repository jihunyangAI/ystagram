// DM 데이터 훅과 실시간 이벤트 → 캐시 반영

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { InfiniteData, QueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { ConversationView, DmFolder, DmUnread, InboxItem, MessagePage, MessageView } from './types'

export function useInbox(folder: DmFolder) {
  return useQuery({ queryKey: ['inbox', folder], queryFn: () => api.get<InboxItem[]>(`/api/direct/inbox?folder=${folder}`) })
}

export function useDmUnread(enabled = true) {
  return useQuery({ queryKey: ['dm-unread'], queryFn: () => api.get<DmUnread>('/api/direct/unread'), enabled, staleTime: 30_000 })
}

export function useConversation(id: number) {
  return useQuery({ queryKey: ['conversation', id], queryFn: () => api.get<ConversationView>(`/api/direct/conversations/${id}`) })
}

/** 최근 메시지부터 30개씩. pages[0]이 가장 최근 */
export function useMessages(id: number) {
  return useInfiniteQuery({
    queryKey: ['messages', id],
    queryFn: ({ pageParam }) =>
      api.get<MessagePage>(`/api/direct/conversations/${id}/messages${pageParam ? `?before=${pageParam}` : ''}`),
    initialPageParam: 0,
    getNextPageParam: (last) => last.nextBefore ?? undefined,
  })
}

type Pages = InfiniteData<MessagePage>

/** 새 메시지를 맨 앞(가장 최근)에 넣는다. 이미 있으면 바꾼다 */
export function upsertMessage(qc: QueryClient, m: MessageView) {
  qc.setQueryData<Pages>(['messages', m.conversationId], (data) => {
    if (!data) return data
    const exists = data.pages.some((p) => p.items.some((x) => x.id === m.id))
    if (exists) {
      return { ...data, pages: data.pages.map((p) => ({ ...p, items: p.items.map((x) => (x.id === m.id ? m : x)) })) }
    }
    const [first, ...rest] = data.pages
    return { ...data, pages: [{ ...first, items: [m, ...first.items] }, ...rest] }
  })
}

export function markUnsent(qc: QueryClient, conversationId: number, messageId: number) {
  qc.setQueryData<Pages>(['messages', conversationId], (data) =>
    data
      ? {
          ...data,
          pages: data.pages.map((p) => ({
            ...p,
            items: p.items.map((x) =>
              x.id === messageId ? { ...x, unsent: true, body: null, media: null, post: null, story: null, reactions: [] } : x,
            ),
          })),
        }
      : data,
  )
}

export function markRead(qc: QueryClient, conversationId: number, userId: number, messageId: number) {
  qc.setQueryData<ConversationView>(['conversation', conversationId], (c) =>
    c
      ? {
          ...c,
          members: c.members.map((m) =>
            m.user.id === userId ? { ...m, lastReadMessageId: Math.max(m.lastReadMessageId, messageId) } : m,
          ),
        }
      : c,
  )
}

export function useSendMessage(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ body, replyToId }: { body: string; replyToId?: number }) =>
      api.post<MessageView>(`/api/direct/conversations/${id}/messages`, { body, replyToId }),
    onSuccess: (m) => {
      upsertMessage(qc, m)
      qc.invalidateQueries({ queryKey: ['inbox'] })
      qc.invalidateQueries({ queryKey: ['conversation', id] })
    },
  })
}

export function useSendImage(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (file: Blob) => {
      const form = new FormData()
      form.append('file', file, 'photo.jpg')
      return api.upload<MessageView>('POST', `/api/direct/conversations/${id}/images`, form)
    },
    onSuccess: (m) => {
      upsertMessage(qc, m)
      qc.invalidateQueries({ queryKey: ['inbox'] })
    },
  })
}

export function useMessageActions() {
  const qc = useQueryClient()
  return {
    edit: (m: MessageView, body: string) => api.patch<MessageView>(`/api/direct/messages/${m.id}`, { body }).then((x) => upsertMessage(qc, x)),
    unsend: (m: MessageView) =>
      api.delete<void>(`/api/direct/messages/${m.id}`).then(() => {
        markUnsent(qc, m.conversationId, m.id)
        qc.invalidateQueries({ queryKey: ['inbox'] })
      }),
    react: (m: MessageView, emoji: string | null) =>
      (emoji
        ? api.put<MessageView>(`/api/direct/messages/${m.id}/reaction`, { emoji })
        : api.delete<MessageView>(`/api/direct/messages/${m.id}/reaction`)
      ).then((x) => upsertMessage(qc, x)),
  }
}

/** 받는 사람 이름들로 대화방 열기 (1명이면 1:1, 기존 방 재사용) */
export function openConversation(usernames: string[], title?: string) {
  return api.post<ConversationView>('/api/direct/conversations', { usernames, title })
}

export function readConversation(id: number, messageId: number) {
  return api.put<void>(`/api/direct/conversations/${id}/read`, { messageId })
}

export function sharePost(shortcode: string, usernames: string[], body?: string) {
  return api.post<{ conversations: number[] }>('/api/direct/share', { shortcode, usernames, body })
}
