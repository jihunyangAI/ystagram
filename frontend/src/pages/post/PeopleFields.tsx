import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { Avatar } from '../../components/Avatar'
import { useSearch } from '../../lib/social'
import type { UserSummary } from '../../lib/types'

/** 사람 태그 한 개 (사진 번호와 위치 0~1) */
export interface DraftTag {
  user: UserSummary
  mediaIndex: number
  x: number
  y: number
}

/** 사용자 검색 상자: 고르면 onPick */
export function UserSearchBox({
  onPick,
  placeholder,
  exclude = [],
  autoFocus,
}: {
  onPick: (u: UserSummary) => void
  placeholder: string
  exclude?: number[]
  autoFocus?: boolean
}) {
  const [q, setQ] = useState('')
  const [term, setTerm] = useState('')
  useEffect(() => {
    const t = setTimeout(() => setTerm(q), 200)
    return () => clearTimeout(t)
  }, [q])
  const search = useSearch(term.startsWith('@') ? term : term ? `@${term}` : '')
  const users = (search.data?.users ?? []).map((h) => h.user).filter((u) => !exclude.includes(u.id)).slice(0, 6)
  return (
    <div className="relative">
      <input
        autoFocus={autoFocus}
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        className="input"
      />
      {term.trim() && users.length > 0 && (
        <ul className="absolute inset-x-0 top-full z-20 mt-1 max-h-60 overflow-y-auto rounded-lg border border-border bg-bg shadow-lg">
          {users.map((u) => (
            <li key={u.id}>
              <button
                type="button"
                className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-surface-2"
                onClick={() => {
                  onPick(u)
                  setQ('')
                  setTerm('')
                }}
              >
                <Avatar user={u} size={28} />
                <span className="min-w-0 leading-tight">
                  <b className="block truncate">{u.username}</b>
                  <span className="block truncate text-xs text-muted">{u.fullName}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** 공동 작업자 초대 (최대 5명) */
export function CollaboratorField({ value, onChange, meId }: { value: UserSummary[]; onChange: (v: UserSummary[]) => void; meId: number }) {
  return (
    <div className="space-y-2">
      <p className="text-xs text-muted">
        초대받은 사람이 수락하면 그 사람의 프로필과 팔로워 피드에도 이 게시물이 보입니다. 최대 5명.
      </p>
      <div className="flex flex-wrap gap-1.5">
        {value.map((u) => (
          <span key={u.id} className="flex items-center gap-1 rounded-full bg-primary-soft px-2.5 py-1 text-xs font-semibold text-primary">
            {u.username}
            <button type="button" aria-label={`${u.username} 빼기`} onClick={() => onChange(value.filter((x) => x.id !== u.id))}>
              <X size={12} />
            </button>
          </span>
        ))}
      </div>
      {value.length < 5 && (
        <UserSearchBox
          placeholder="공동 작업자 검색"
          exclude={[meId, ...value.map((u) => u.id)]}
          onPick={(u) => onChange([...value, u])}
        />
      )}
    </div>
  )
}
