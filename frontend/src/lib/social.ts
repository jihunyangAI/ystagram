// M2 화면용 데이터 훅: 알림, 팔로우 요청, 검색, 저장·컬렉션, 리포스트, 댓글(답글·수정·고정·좋아요)

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { InfiniteData, QueryClient } from '@tanstack/react-query'
import { api } from './api'
import { patchPost } from './posts'
import type {
  Activity,
  CollectionView,
  CommentView,
  GridItem,
  LikeResult,
  NotificationUnread,
  PlacePage,
  RecentItem,
  RecentKind,
  Relationship,
  SavedOverview,
  SearchResult,
  Slice,
  TagPage,
  UserListItem,
} from './types'

// ------------------------------------------------------------ 알림

export function useNotificationUnread(enabled = true) {
  return useQuery({
    queryKey: ['notif-unread'],
    queryFn: () => api.get<NotificationUnread>('/api/notifications/unread'),
    enabled,
    staleTime: 30_000,
  })
}

export function useActivity() {
  return useQuery({ queryKey: ['activity'], queryFn: () => api.get<Activity>('/api/notifications') })
}

export function useMarkNotificationsRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<void>('/api/notifications/read'),
    onSuccess: () => qc.setQueryData<NotificationUnread>(['notif-unread'], (u) => (u ? { ...u, count: 0 } : u)),
  })
}

// ------------------------------------------------------------ 팔로우 요청

export function useFollowRequests() {
  return useQuery({
    queryKey: ['follow-requests'],
    queryFn: () => api.get<UserListItem[]>('/api/me/follow-requests'),
  })
}

export function useAnswerRequest() {
  const qc = useQueryClient()
  return useMutation({
    /** 승인하면 내 → 상대 관계(맞팔로우 버튼용), 거절하면 null */
    mutationFn: async ({ username, accept }: { username: string; accept: boolean }): Promise<Relationship | null> => {
      if (accept) return (await api.put<{ relationship: Relationship }>(`/api/me/follow-requests/${username}`)).relationship
      await api.delete<void>(`/api/me/follow-requests/${username}`)
      return null
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['follow-requests'] })
      qc.invalidateQueries({ queryKey: ['activity'] })
      qc.invalidateQueries({ queryKey: ['notif-unread'] })
      qc.invalidateQueries({ queryKey: ['profile'] })
    },
  })
}

// ------------------------------------------------------------ 검색

export function useSearch(q: string) {
  const term = q.trim()
  return useQuery({
    queryKey: ['search', term],
    queryFn: () => api.get<SearchResult>(`/api/search?q=${encodeURIComponent(term)}`),
    enabled: term.length > 0,
    placeholderData: (prev) => prev,
  })
}

export function useRecentSearches() {
  return useQuery({ queryKey: ['search-recent'], queryFn: () => api.get<RecentItem[]>('/api/search/recent') })
}

export function useRecentSearchActions() {
  const qc = useQueryClient()
  const refresh = () => qc.invalidateQueries({ queryKey: ['search-recent'] })
  return {
    add: (kind: RecentKind, term: string) => api.post<void>('/api/search/recent', { kind, term }).then(refresh),
    remove: (kind: RecentKind, term: string) =>
      api
        .delete<void>(`/api/search/recent?kind=${kind}&term=${encodeURIComponent(term)}`)
        .then(refresh),
    clear: () => api.delete<void>('/api/search/recent').then(refresh),
  }
}

export function useTagPage(name: string) {
  return useQuery({ queryKey: ['tag', name], queryFn: () => api.get<TagPage>(`/api/tags/${encodeURIComponent(name)}`) })
}

export function usePlacePage(id: string) {
  return useQuery({ queryKey: ['place', id], queryFn: () => api.get<PlacePage>(`/api/locations/${id}`) })
}

/** 커서 방식 그리드 (프로필·리포스트·저장됨·해시태그·장소 공통) */
export function useGrid(key: unknown[], url: string, enabled = true) {
  return useInfiniteQuery({
    queryKey: ['grid', ...key],
    queryFn: ({ pageParam }) =>
      api.get<Slice<GridItem>>(`${url}${pageParam ? `${url.includes('?') ? '&' : '?'}cursor=${pageParam}` : ''}`),
    initialPageParam: '',
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled,
  })
}

// ------------------------------------------------------------ 저장·컬렉션

export function useSavedOverview() {
  return useQuery({ queryKey: ['saved'], queryFn: () => api.get<SavedOverview>('/api/me/saved') })
}

export function useCollection(id: string) {
  return useQuery({
    queryKey: ['collection', id],
    queryFn: () => api.get<CollectionView>(`/api/me/collections/${id}`),
    enabled: id !== 'all', // "모든 게시물"은 컬렉션이 아니다
  })
}

export function usePostCollections(shortcode: string, enabled: boolean) {
  return useQuery({
    queryKey: ['post-collections', shortcode],
    queryFn: () => api.get<number[]>(`/api/me/saved/${shortcode}/collections`),
    enabled,
  })
}

function refreshSaved(qc: QueryClient) {
  qc.invalidateQueries({ queryKey: ['saved'] })
  qc.invalidateQueries({ queryKey: ['grid', 'saved'] })
  qc.invalidateQueries({ queryKey: ['collection'] })
  qc.invalidateQueries({ queryKey: ['post-collections'] })
}

export function useToggleSave() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ shortcode, save }: { shortcode: string; save: boolean }) =>
      save ? api.put<{ saved: boolean }>(`/api/posts/${shortcode}/save`) : api.delete<{ saved: boolean }>(`/api/posts/${shortcode}/save`),
    onMutate: ({ shortcode, save }) => patchPost(qc, shortcode, (p) => ({ ...p, savedByMe: save })),
    onError: (_e, { shortcode, save }) => patchPost(qc, shortcode, (p) => ({ ...p, savedByMe: !save })),
    onSettled: () => refreshSaved(qc),
  })
}

export function useCollectionActions() {
  const qc = useQueryClient()
  const done = () => refreshSaved(qc)
  return {
    create: (name: string, shortcode?: string) =>
      api.post<CollectionView>('/api/me/collections', { name, shortcode }).then((c) => {
        if (shortcode) patchPost(qc, shortcode, (p) => ({ ...p, savedByMe: true }))
        done()
        return c
      }),
    add: (id: number, shortcode: string) =>
      api.put<CollectionView>(`/api/me/collections/${id}/posts/${shortcode}`).then((c) => {
        patchPost(qc, shortcode, (p) => ({ ...p, savedByMe: true }))
        done()
        return c
      }),
    remove: (id: number, shortcode: string) =>
      api.delete<CollectionView>(`/api/me/collections/${id}/posts/${shortcode}`).then(done),
    rename: (id: number, name: string) => api.patch<CollectionView>(`/api/me/collections/${id}`, { name }).then(done),
    delete: (id: number) => api.delete<void>(`/api/me/collections/${id}`).then(done),
  }
}

// ------------------------------------------------------------ 리포스트

export function useToggleRepost() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ shortcode, repost, note }: { shortcode: string; repost: boolean; note?: string }) =>
      repost
        ? api.put<{ reposted: boolean; repostCount: number }>(`/api/posts/${shortcode}/repost`, { note: note ?? null })
        : api.delete<{ reposted: boolean; repostCount: number }>(`/api/posts/${shortcode}/repost`),
    onSuccess: (res, { shortcode }) => {
      patchPost(qc, shortcode, (p) => ({ ...p, repostedByMe: res.reposted, repostCount: res.repostCount }))
      qc.invalidateQueries({ queryKey: ['grid', 'reposts'] })
    },
  })
}

// ------------------------------------------------------------ 댓글 (답글·수정·고정·좋아요)

export function useReplies(commentId: number, enabled: boolean) {
  return useQuery({
    queryKey: ['replies', commentId],
    queryFn: () => api.get<CommentView[]>(`/api/comments/${commentId}/replies`),
    enabled,
  })
}

/** 댓글 목록·답글·피드 미리보기에 들어 있는 같은 댓글을 고친다 */
export function patchComment(qc: QueryClient, shortcode: string, id: number, update: (c: CommentView) => CommentView) {
  qc.setQueryData<InfiniteData<Slice<CommentView>>>(['comments', shortcode], (data) =>
    data
      ? {
          ...data,
          pages: data.pages.map((page) => ({ ...page, items: page.items.map((c) => (c.id === id ? update(c) : c)) })),
        }
      : data,
  )
  qc.setQueriesData<CommentView[]>({ queryKey: ['replies'] }, (list) =>
    list ? list.map((c) => (c.id === id ? update(c) : c)) : list,
  )
  patchPost(qc, shortcode, (p) => ({
    ...p,
    previewComments: p.previewComments.map((c) => (c.id === id ? update(c) : c)),
  }))
}

export function useCommentActions(shortcode: string) {
  const qc = useQueryClient()
  return {
    like: (c: CommentView) => {
      const like = !c.likedByMe
      patchComment(qc, shortcode, c.id, (x) => ({ ...x, likedByMe: like, likeCount: Math.max(0, x.likeCount + (like ? 1 : -1)) }))
      const req = like ? api.put<LikeResult>(`/api/comments/${c.id}/like`) : api.delete<LikeResult>(`/api/comments/${c.id}/like`)
      return req.then(
        (r) => patchComment(qc, shortcode, c.id, (x) => ({ ...x, likedByMe: r.liked, likeCount: r.likeCount })),
        (e) => {
          patchComment(qc, shortcode, c.id, () => c)
          throw e
        },
      )
    },
    edit: (c: CommentView, body: string) =>
      api.patch<CommentView>(`/api/comments/${c.id}`, { body }).then((updated) =>
        patchComment(qc, shortcode, c.id, (x) => ({ ...updated, replyCount: x.replyCount })),
      ),
    pin: (c: CommentView, pin: boolean) =>
      (pin ? api.put<CommentView>(`/api/comments/${c.id}/pin`) : api.delete<CommentView>(`/api/comments/${c.id}/pin`)).then(
        () => qc.invalidateQueries({ queryKey: ['comments', shortcode] }),
      ),
    reply: (parentId: number, body: string) =>
      api.post<CommentView>(`/api/posts/${shortcode}/comments`, { body, parentId }).then((reply) => {
        qc.setQueryData<CommentView[]>(['replies', parentId], (list) => (list ? [...list, reply] : list))
        patchComment(qc, shortcode, parentId, (x) => ({ ...x, replyCount: x.replyCount + 1 }))
        patchPost(qc, shortcode, (p) => ({ ...p, commentCount: p.commentCount + 1 }))
        return reply
      }),
  }
}
