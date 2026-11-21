import { useEffect, useState } from 'react'
import { Check, X } from 'lucide-react'
import clsx from 'clsx'
import { useInbox } from '../../lib/dm'
import { useSearch } from '../../lib/social'
import type { UserSummary } from '../../lib/types'
import { Avatar } from '../Avatar'

/** 받는 사람 고르기 (새 메시지·게시물 공유 공통). 검색어가 없으면 최근 대화 상대를 보여 준다 */
export function UserPicker({
  selected,
  onChange,
  meId,
}: {
  selected: UserSummary[]
  onChange: (users: UserSummary[]) => void
  meId: number
}) {
  const [q, setQ] = useState('')
  const [term, setTerm] = useState('')
  useEffect(() => {
    const t = setTimeout(() => setTerm(q), 200)
    return () => clearTimeout(t)
  }, [q])
  const search = useSearch(term)
  const inbox = useInbox('PRIMARY')

  const recent: UserSummary[] = []
  for (const c of inbox.data ?? []) {
    for (const m of c.members) if (!recent.some((r) => r.id === m.id)) recent.push(m)
    if (recent.length >= 12) break
  }
  const list = term.trim() ? (search.data?.users.map((h) => h.user) ?? []) : recent
  const toggle = (u: UserSummary) =>
    onChange(selected.some((s) => s.id === u.id) ? selected.filter((s) => s.id !== u.id) : [...selected, u])

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex flex-wrap items-center gap-1.5 border-b border-border px-4 py-2">
        <span className="text-sm font-semibold">받는 사람:</span>
        {selected.map((u) => (
          <button
            key={u.id}
            onClick={() => toggle(u)}
            className="flex items-center gap-1 rounded-full bg-primary-soft px-2.5 py-1 text-xs font-semibold text-primary"
          >
            {u.username} <X size={12} />
          </button>
        ))}
        <input
          autoFocus
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="검색..."
          aria-label="받는 사람 검색"
          className="min-w-[120px] flex-1 bg-transparent py-1 text-sm outline-none placeholder:text-muted"
        />
      </div>
      <div className="min-h-[200px] flex-1 overflow-y-auto py-1">
        {!term.trim() && <p className="px-4 pt-2 pb-1 text-sm font-semibold">추천</p>}
        {term.trim() && search.isLoading && <p className="p-4 text-sm text-muted">검색 중…</p>}
        {list.length === 0 && !search.isLoading && <p className="p-4 text-sm text-muted">계정을 찾을 수 없습니다.</p>}
        {list
          .filter((u) => u.id !== meId)
          .map((u) => {
            const on = selected.some((s) => s.id === u.id)
            return (
              <button key={u.id} onClick={() => toggle(u)} className="flex w-full items-center gap-3 px-4 py-2 text-left hover:bg-surface-2">
                <Avatar user={u} size={44} />
                <span className="min-w-0 flex-1 text-sm leading-tight">
                  <b className="block truncate">{u.fullName}</b>
                  <span className="block truncate text-muted">{u.username}</span>
                </span>
                <span
                  className={clsx(
                    'flex h-6 w-6 items-center justify-center rounded-full border-2',
                    on ? 'border-primary bg-primary text-white' : 'border-muted',
                  )}
                  aria-hidden
                >
                  {on && <Check size={14} strokeWidth={3} />}
                </span>
              </button>
            )
          })}
      </div>
    </div>
  )
}
