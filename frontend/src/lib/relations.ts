// 차단·제한·숨기기·친한 친구·즐겨찾기 (분석 문서 §13)

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { RelationFlags, UserListItem, UserSummary } from './types'

export type RelationKind = 'blocked' | 'restricted' | 'muted' | 'close-friends' | 'favorites'

export function useRelationList(kind: RelationKind) {
  return useQuery({ queryKey: ['relations', kind], queryFn: () => api.get<UserListItem[]>(`/api/me/relations/${kind}`) })
}

export function useRelationCandidates(kind: 'close-friends' | 'favorites') {
  return useQuery({
    queryKey: ['relations', kind, 'candidates'],
    queryFn: () => api.get<{ user: UserSummary; selected: boolean }[]>(`/api/me/relations/${kind}/candidates`),
  })
}

/** 관계가 바뀌면 다시 받아야 하는 화면들 */
function refresh(qc: QueryClient) {
  ;['relations', 'profile', 'feed', 'story-tray', 'search', 'user-list', 'post', 'comments'].forEach((k) =>
    qc.invalidateQueries({ queryKey: [k] }),
  )
}

export type RelationAction =
  | { op: 'block' | 'restrict' | 'close-friend' | 'favorite'; on: boolean }
  | { op: 'mute'; posts: boolean; stories: boolean }

export function useRelationAction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ username, action }: { username: string; action: RelationAction }) => {
      const base = `/api/users/${username}`
      if (action.op === 'mute') return api.put<RelationFlags>(`${base}/mute`, { posts: action.posts, stories: action.stories })
      return action.on ? api.put<RelationFlags>(`${base}/${action.op}`) : api.delete<RelationFlags>(`${base}/${action.op}`)
    },
    onSuccess: () => refresh(qc),
  })
}

export function removeFollower(username: string) {
  return api.delete<void>(`/api/me/followers/${username}`)
}
