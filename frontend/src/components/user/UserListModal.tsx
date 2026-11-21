import { useInfiniteQuery } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { api } from '../../lib/api'
import type { UserListItem } from '../../lib/types'
import { Modal } from '../Modal'
import { UserRow } from './UserRow'

const PAGE = 30

interface Props {
  open: boolean
  onClose: () => void
  title: string
  /** 예: /api/users/minji/followers — 페이지 번호는 ?page=로 붙인다 */
  url: string
  queryKey: unknown[]
  empty: string
  /** 목록의 모두가 나를 팔로우하는 경우 (내 팔로워 목록) → "맞팔로우" 표시 */
  followsMe?: boolean
}

/** 팔로워·팔로잉·좋아요 목록 창. 끝까지 내리면 다음 30명을 불러온다. */
export function UserListModal({ open, onClose, title, url, queryKey, empty, followsMe }: Props) {
  const query = useInfiniteQuery({
    queryKey: ['user-list', ...queryKey],
    queryFn: ({ pageParam }) => api.get<UserListItem[]>(`${url}?page=${pageParam}`),
    initialPageParam: 0,
    getNextPageParam: (last, all) => (last.length === PAGE ? all.length : undefined),
    enabled: open,
  })
  const sentinel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = sentinel.current
    if (!el || !open) return
    const io = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting && query.hasNextPage && !query.isFetchingNextPage) query.fetchNextPage()
    })
    io.observe(el)
    return () => io.disconnect()
  }, [open, query])

  const items = query.data?.pages.flat() ?? []

  return (
    <Modal open={open} onClose={onClose} title={title}>
      <div className="min-h-[200px] overflow-y-auto py-2">
        {query.isLoading && <p className="p-6 text-center text-sm text-muted">불러오는 중…</p>}
        {query.isError && <p className="p-6 text-center text-sm text-danger">{query.error.message}</p>}
        {query.isSuccess && items.length === 0 && <p className="p-6 text-center text-sm text-muted">{empty}</p>}
        {items.map((item) => (
          <UserRow key={item.user.id} user={item.user} relationship={item.relationship} followsMe={followsMe} onNavigate={onClose} />
        ))}
        <div ref={sentinel} className="h-4" />
        {query.isFetchingNextPage && <p className="p-3 text-center text-xs text-muted">불러오는 중…</p>}
      </div>
    </Modal>
  )
}
