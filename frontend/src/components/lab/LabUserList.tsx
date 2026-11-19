import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { api } from '../../lib/api'
import { useImpersonate, useMe } from '../../lib/auth'
import { formatCount } from '../../lib/format'
import type { LabUser } from '../../lib/types'
import { Avatar } from '../Avatar'
import { UserBadges } from '../UserBadges'
import { toast } from '../Toast'

const TYPE_LABEL: Record<string, string> = { CREATOR: '크리에이터', BUSINESS: '비즈니스' }

/** 가짜 사용자 목록. 누르면 그 사람으로 로그인한다. */
export function LabUserList({ query, onSwitched }: { query: string; onSwitched?: () => void }) {
  const navigate = useNavigate()
  const { data: me } = useMe()
  const impersonate = useImpersonate()
  const { data: users, isLoading, isError } = useQuery({
    queryKey: ['lab-users', query],
    queryFn: () => api.get<LabUser[]>(`/api/lab/users?limit=200&q=${encodeURIComponent(query)}`),
  })

  if (isLoading) return <p className="p-4 text-sm text-muted">불러오는 중…</p>
  if (isError) return <p className="p-4 text-sm text-danger">목록을 불러오지 못했습니다. 백엔드가 실행 중인지 확인하세요.</p>
  if (!users?.length) return <p className="p-4 text-sm text-muted">검색 결과가 없습니다.</p>

  const switchTo = (username: string) => {
    impersonate.mutate(username, {
      onSuccess: () => {
        toast(`@${username} 계정으로 전환했습니다`)
        onSwitched?.()
        navigate('/')
      },
      onError: (e) => toast(e.message),
    })
  }

  return (
    <ul className="divide-y divide-border">
      {users.map((u) => {
        const current = me?.user.id === u.user.id
        return (
          <li key={u.user.id}>
            <button
              onClick={() => switchTo(u.user.username)}
              disabled={impersonate.isPending || current}
              className="flex w-full items-center gap-3 px-4 py-2.5 text-left hover:bg-surface-2 disabled:cursor-default disabled:hover:bg-transparent"
            >
              <Avatar user={u.user} size={40} />
              <span className="min-w-0 flex-1">
                <span className="flex items-center gap-1 text-sm font-semibold">
                  <span className="truncate">{u.user.username}</span>
                  <UserBadges user={u.user} />
                  {TYPE_LABEL[u.accountType] && (
                    <span className="rounded bg-primary-soft px-1.5 py-px text-[10px] font-medium text-primary">
                      {TYPE_LABEL[u.accountType]}
                    </span>
                  )}
                </span>
                <span className="block truncate text-xs text-muted">
                  {u.user.fullName} · 팔로워 {formatCount(u.followersCount)} · 팔로잉 {formatCount(u.followingCount)}
                </span>
              </span>
              {current && <span className="text-xs font-semibold text-primary">현재</span>}
            </button>
          </li>
        )
      })}
    </ul>
  )
}
