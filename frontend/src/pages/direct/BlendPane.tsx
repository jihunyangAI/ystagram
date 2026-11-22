import { useNavigate } from 'react-router'
import { Link } from 'react-router'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Clapperboard, Play } from 'lucide-react'
import { Avatar } from '../../components/Avatar'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'
import { formatCount } from '../../lib/format'
import type { PostView, UserSummary } from '../../lib/types'

export interface BlendView {
  id: number
  conversationId: number
  status: 'PENDING' | 'ACTIVE'
  inviter: UserSummary
  invitee: UserSummary
  invitedMe: boolean
  createdAt: string
}

interface BlendItem {
  post: PostView
  forUsers: string[]
  reason: string
}

interface BlendPage {
  items: BlendItem[]
  nextPage: number | null
  since: number
}

/** 두 사람 아바타 겹치기 */
function Pair({ a, b, size = 40 }: { a: UserSummary; b: UserSummary; size?: number }) {
  return (
    <span className="relative inline-flex" style={{ width: size * 1.6, height: size }}>
      <Avatar user={a} size={size} className="absolute left-0" />
      <Avatar user={b} size={size} className="absolute right-0 border-2 border-bg" />
    </span>
  )
}

/**
 * Blend (분석 문서 §15 P3): 두 사람의 관심사를 섞은 릴스 피드.
 * 릴스마다 "누구를 위한 추천인지"(관심 주제·좋아요)를 보여 준다.
 */
export function BlendPane({ id, meId }: { id: number; meId: number }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data: blend, isError, error } = useQuery({ queryKey: ['blend', id], queryFn: () => api.get<BlendView>(`/api/blends/${id}`) })
  const active = blend?.status === 'ACTIVE'
  const reels = useInfiniteQuery({
    queryKey: ['blend-reels', id],
    enabled: active,
    initialPageParam: { page: 0, since: undefined as number | undefined },
    queryFn: ({ pageParam }) =>
      api.get<BlendPage>(`/api/blends/${id}/reels?page=${pageParam.page}${pageParam.since ? `&since=${pageParam.since}` : ''}`),
    getNextPageParam: (last) => (last.nextPage == null ? undefined : { page: last.nextPage, since: last.since }),
  })
  const accept = useMutation({
    mutationFn: () => api.post<BlendView>(`/api/blends/${id}/accept`),
    onSuccess: (b) => {
      qc.setQueryData(['blend', id], b)
      void qc.invalidateQueries({ queryKey: ['messages', b.conversationId] })
      toast('Blend에 참여했습니다')
    },
    onError: (e) => toast((e as Error).message),
  })
  const leave = useMutation({
    mutationFn: () => api.delete(`/api/blends/${id}`),
    onSuccess: () => {
      toast('Blend를 나갔습니다')
      navigate(blend ? `/direct/t/${blend.conversationId}` : '/direct/inbox')
    },
  })

  if (isError) return <p className="m-auto text-sm text-danger">{error.message}</p>
  if (!blend) return null
  const other = blend.inviter.id === meId ? blend.invitee : blend.inviter
  const me = blend.inviter.id === meId ? blend.inviter : blend.invitee
  const items = reels.data?.pages.flatMap((p) => p.items) ?? []

  return (
    <div className="flex h-full w-full flex-col">
      <header className="flex items-center gap-3 border-b border-border px-3 py-2.5 md:px-5">
        <button aria-label="대화로 돌아가기" onClick={() => navigate(`/direct/t/${blend.conversationId}`)}>
          <ChevronLeft size={26} />
        </button>
        <Pair a={me} b={other} size={32} />
        <div className="min-w-0 flex-1 text-sm">
          <p className="font-semibold">Blend</p>
          <p className="truncate text-xs text-muted">회원님과 {other.username}님을 위한 릴스</p>
        </div>
        <button className="text-sm text-danger" onClick={() => leave.mutate()}>
          나가기
        </button>
      </header>

      {!active ? (
        <div className="m-auto max-w-sm space-y-3 p-6 text-center">
          <Pair a={me} b={other} size={64} />
          <p className="font-semibold">{blend.invitedMe ? `${other.username}님이 Blend에 초대했어요` : `${other.username}님의 참여를 기다리는 중`}</p>
          <p className="text-sm text-muted">
            Blend는 두 분의 관심사와 좋아한 릴스를 섞어 함께 보는 피드예요. 어떤 릴스가 누구 때문에 추천됐는지도 보여 드려요.
          </p>
          {blend.invitedMe && (
            <button className="btn btn-primary w-full" disabled={accept.isPending} onClick={() => accept.mutate()}>
              Blend 참여하기
            </button>
          )}
        </div>
      ) : (
        <div className="min-h-0 flex-1 overflow-y-auto p-3 md:p-5">
          {reels.isLoading && <p className="py-8 text-center text-sm text-muted">불러오는 중…</p>}
          {!reels.isLoading && items.length === 0 && (
            <p className="py-8 text-center text-sm text-muted">아직 두 분을 위한 릴스가 없어요.</p>
          )}
          <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {items.map(({ post, forUsers, reason }) => (
              <li key={post.id}>
                <Link to={`/reels/${post.shortcode}`} className="group relative block overflow-hidden rounded-xl bg-surface-2" style={{ aspectRatio: '9 / 16' }}>
                  <img src={post.media[0]?.thumbUrl} alt="" className="h-full w-full object-cover" />
                  <span className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent p-2 pt-8 text-white">
                    <span className="flex items-center gap-1 text-xs">
                      <Play size={12} fill="currentColor" /> {formatCount(post.viewCount)}
                    </span>
                    <span className="mt-1 line-clamp-2 text-[11px] leading-snug">{reason}</span>
                  </span>
                  {forUsers.length > 0 && (
                    <span className="absolute top-2 left-2 flex -space-x-2">
                      {forUsers.map((u) => (
                        <Avatar key={u} user={u === me.username ? me : other} size={22} className="border-2 border-white" />
                      ))}
                    </span>
                  )}
                </Link>
              </li>
            ))}
          </ul>
          {reels.hasNextPage && (
            <button className="btn btn-secondary mx-auto mt-4 block" disabled={reels.isFetchingNextPage} onClick={() => reels.fetchNextPage()}>
              더 보기
            </button>
          )}
          <p className="mt-6 flex items-center justify-center gap-1 text-xs text-muted">
            <Clapperboard size={12} /> 두 분의 관심 주제와 좋아요를 바탕으로 추천해요
          </p>
        </div>
      )}
    </div>
  )
}
