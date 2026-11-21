import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { api } from './api'
import type { Slice, StoryView, StoryViewer, TrayItem } from './types'

export function useStoryTray() {
  return useQuery({ queryKey: ['story-tray'], queryFn: () => api.get<TrayItem[]>('/api/stories/tray') })
}

export function useUserStories(username: string) {
  return useQuery({
    queryKey: ['stories', username],
    queryFn: () => api.get<StoryView[]>(`/api/stories/users/${username}`),
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
  create: (file: Blob, altText?: string) => {
    const form = new FormData()
    form.append('file', file, 'story.jpg')
    if (altText) form.append('altText', altText)
    return api.upload<StoryView>('POST', '/api/stories', form)
  },
}
