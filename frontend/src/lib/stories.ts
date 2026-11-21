import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { api } from './api'
import type { Slice, Sticker, StoryView, StoryViewer, TrayItem } from './types'

export function useStoryTray() {
  return useQuery({ queryKey: ['story-tray'], queryFn: () => api.get<TrayItem[]>('/api/stories/tray') })
}

/** 한 사람의 활성 스토리, 또는 highlightId가 있으면 그 하이라이트의 스토리 */
export function useUserStories(username: string, highlightId?: string) {
  return useQuery({
    queryKey: highlightId ? ['highlight-stories', highlightId] : ['stories', username],
    queryFn: () =>
      api.get<StoryView[]>(highlightId ? `/api/stories/highlights/${highlightId}` : `/api/stories/users/${username}`),
    staleTime: 0,
  })
}

export function useStoryViewers(id: number, enabled: boolean) {
  return useQuery({
    queryKey: ['story-viewers', id],
    queryFn: () => api.get<StoryViewer[]>(`/api/stories/${id}/viewers`),
    enabled,
  })
}

export function useStoryArchive() {
  return useInfiniteQuery({
    queryKey: ['story-archive'],
    queryFn: ({ pageParam }) => api.get<Slice<StoryView>>(`/api/stories/archive${pageParam ? `?cursor=${pageParam}` : ''}`),
    initialPageParam: '',
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })
}

export const storyApi = {
  view: (id: number) => api.post<void>(`/api/stories/${id}/view`),
  like: (id: number, like: boolean) =>
    like ? api.put<StoryView>(`/api/stories/${id}/like`) : api.delete<StoryView>(`/api/stories/${id}/like`),
  reply: (id: number, body: string) => api.post<unknown>(`/api/stories/${id}/reply`, { body }),
  remove: (id: number) => api.delete<void>(`/api/stories/${id}`),
  respond: (id: number, stickerId: string, value: string) =>
    api.post<StoryView>(`/api/stories/${id}/stickers/${stickerId}`, { value }),
  /** 사진(글자는 이미 그려 넣음) + 공개 범위 + 스티커 */
  create: (file: Blob, data: { altText?: string; audience: 'PUBLIC' | 'CLOSE_FRIENDS'; stickers: Omit<Sticker, 'id'>[] }) => {
    const form = new FormData()
    form.append('file', file, 'story.jpg')
    form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
    return api.upload<StoryView>('POST', '/api/stories', form)
  },
}
