import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { InfiniteData, QueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { CommentView, LikeResult, PostView, Relationship, Slice } from './types'

// ------------------------------------------------------------ 조회

/** mode: following(팔로잉·리포스트) / favorites(즐겨찾기한 사람만) */
export function useFeed(mode: 'home' | 'following' | 'favorites' = 'home') {
  return useInfiniteQuery({
    queryKey: ['feed', mode],
    queryFn: ({ pageParam }) =>
      api.get<Slice<PostView>>(`/api/feed?mode=${mode}${pageParam ? `&cursor=${encodeURIComponent(pageParam)}` : ''}`),
    initialPageParam: '',
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })
}

export function usePost(shortcode: string) {
  return useQuery({
    queryKey: ['post', shortcode],
    queryFn: () => api.get<PostView>(`/api/posts/${shortcode}`),
  })
}

export function useComments(shortcode: string, enabled = true) {
  return useInfiniteQuery({
    queryKey: ['comments', shortcode],
    queryFn: ({ pageParam }) =>
      api.get<Slice<CommentView>>(`/api/posts/${shortcode}/comments${pageParam ? `?cursor=${pageParam}` : ''}`),
    initialPageParam: '',
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled,
  })
}

// ------------------------------------------------------------ 캐시 갱신 도우미

/** 피드·상세에 들어 있는 같은 게시물을 한꺼번에 고친다 (좋아요 등을 누르자마자 반영) */
export function patchPost(qc: QueryClient, shortcode: string, update: (p: PostView) => PostView) {
  qc.setQueryData<PostView>(['post', shortcode], (p) => (p ? update(p) : p))
  qc.setQueriesData<InfiniteData<{ items: PostView[] }>>({ queryKey: ['feed'] }, (data) =>
    data
      ? {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            items: page.items.map((p) => (p.shortcode === shortcode ? update(p) : p)),
          })),
        }
      : data,
  )
}

function removePost(qc: QueryClient, shortcode: string) {
  qc.removeQueries({ queryKey: ['post', shortcode] })
  qc.setQueriesData<InfiniteData<{ items: PostView[] }>>({ queryKey: ['feed'] }, (data) =>
    data
      ? { ...data, pages: data.pages.map((page) => ({ ...page, items: page.items.filter((p) => p.shortcode !== shortcode) })) }
      : data,
  )
}

// ------------------------------------------------------------ 좋아요

/** 누르자마자 하트가 바뀌고(낙관적 갱신), 실패하면 되돌린다. */
export function useToggleLike() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ post, like }: { post: PostView; like: boolean }) =>
      like
        ? api.put<LikeResult>(`/api/posts/${post.shortcode}/like`)
        : api.delete<LikeResult>(`/api/posts/${post.shortcode}/like`),
    onMutate: ({ post, like }) => {
      patchPost(qc, post.shortcode, (p) => ({
        ...p,
        likedByMe: like,
        likeCount: p.likeCount === null ? null : Math.max(0, p.likeCount + (like ? 1 : -1)),
      }))
      return { before: post }
    },
    onError: (_e, { post }) => patchPost(qc, post.shortcode, () => post),
    onSuccess: (res, { post }) =>
      patchPost(qc, post.shortcode, (p) => ({
        ...p,
        likedByMe: res.liked,
        likeCount: p.likeCount === null ? null : res.likeCount,
      })),
  })
}

// ------------------------------------------------------------ 댓글

export function useAddComment(shortcode: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: string) => api.post<CommentView>(`/api/posts/${shortcode}/comments`, { body }),
    onSuccess: (comment) => {
      patchPost(qc, shortcode, (p) => ({
        ...p,
        commentCount: p.commentCount + 1,
        previewComments: [...p.previewComments, comment].slice(-2),
      }))
      qc.setQueryData<InfiniteData<Slice<CommentView>>>(['comments', shortcode], (data) => {
        if (!data) return data
        const pages = [...data.pages]
        const last = pages[pages.length - 1]
        // 다음 페이지가 남아 있으면 끝에 붙이지 않는다 (불러올 때 서버 순서대로 나옴)
        if (last.nextCursor === null) pages[pages.length - 1] = { ...last, items: [...last.items, comment] }
        return { ...data, pages }
      })
    },
  })
}

export function useDeleteComment(shortcode: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.delete<void>(`/api/comments/${id}`),
    onSuccess: (_r, id) => {
      // 답글이 함께 지워질 수 있으므로 정확한 수는 서버에서 다시 받는다
      qc.invalidateQueries({ queryKey: ['comments', shortcode] })
      qc.invalidateQueries({ queryKey: ['post', shortcode] })
      patchPost(qc, shortcode, (p) => ({
        ...p,
        commentCount: Math.max(0, p.commentCount - 1),
        previewComments: p.previewComments.filter((c) => c.id !== id),
      }))
    },
  })
}

// ------------------------------------------------------------ 게시물 수정·삭제

export function useDeletePost() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (shortcode: string) => api.delete<void>(`/api/posts/${shortcode}`),
    onSuccess: (_r, shortcode) => {
      removePost(qc, shortcode)
      qc.invalidateQueries({ queryKey: ['grid'] })
      qc.invalidateQueries({ queryKey: ['profile'] })
    },
  })
}

export interface PostPatch {
  caption?: string
  location?: string
  hideLikeCount?: boolean
  commentsDisabled?: boolean
}

export function useUpdatePost(shortcode: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (patch: PostPatch) => api.patch<PostView>(`/api/posts/${shortcode}`, patch),
    onSuccess: (post) => patchPost(qc, shortcode, () => post),
  })
}

// ------------------------------------------------------------ 팔로우

/** 팔로우/언팔로우. 프로필·목록·추천을 새로 받는다. */
export function useToggleFollow() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ username, follow }: { username: string; follow: boolean }) =>
      follow
        ? api.put<{ relationship: Relationship }>(`/api/users/${username}/follow`)
        : api.delete<{ relationship: Relationship }>(`/api/users/${username}/follow`),
    onSuccess: (res, { username }) => {
      // 피드 카드의 작성자 관계도 바꿔 준다
      qc.setQueriesData<InfiniteData<{ items: PostView[] }>>({ queryKey: ['feed'] }, (data) =>
        data
          ? {
              ...data,
              pages: data.pages.map((page) => ({
                ...page,
                items: page.items.map((p) =>
                  p.author.username === username ? { ...p, authorRelationship: res.relationship } : p,
                ),
              })),
            }
          : data,
      )
      qc.invalidateQueries({ queryKey: ['profile'] })
      qc.invalidateQueries({ queryKey: ['grid', username] })
      qc.invalidateQueries({ queryKey: ['user-list'] })
      qc.invalidateQueries({ queryKey: ['likes'] })
      qc.invalidateQueries({ queryKey: ['post'] })
    },
  })
}
