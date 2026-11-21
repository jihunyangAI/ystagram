import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { Check, ChevronLeft, Search } from 'lucide-react'
import clsx from 'clsx'
import { Avatar } from '../../components/Avatar'
import { toast } from '../../components/Toast'
import { useRelationAction, useRelationCandidates, useRelationList } from '../../lib/relations'
import type { RelationAction, RelationKind } from '../../lib/relations'

function Header({ title }: { title: string }) {
  const navigate = useNavigate()
  return (
    <header className="flex items-center gap-2 px-2 pt-4 pb-2 md:pt-8">
      <button aria-label="뒤로" onClick={() => navigate(-1)} className="p-2">
        <ChevronLeft size={26} />
      </button>
      <h1 className="text-lg font-bold">{title}</h1>
    </header>
  )
}

const LIST_TEXT: Record<'blocked' | 'restricted' | 'muted', { title: string; help: string; empty: string; button: string; undo: RelationAction }> = {
  blocked: {
    title: '차단된 계정',
    help: '차단한 사람은 회원님의 프로필·게시물·스토리를 볼 수 없고, 서로 팔로우와 메시지를 할 수 없습니다. 상대에게 알림은 가지 않습니다.',
    empty: '차단한 계정이 없습니다.',
    button: '차단 해제',
    undo: { op: 'block', on: false },
  },
  restricted: {
    title: '제한된 계정',
    help: '제한한 사람의 새 댓글은 그 사람과 회원님에게만 보이고, 승인하면 모두에게 보입니다. 메시지는 요청함으로 가고 읽음·활동 상태가 보이지 않습니다.',
    empty: '제한한 계정이 없습니다.',
    button: '제한 해제',
    undo: { op: 'restrict', on: false },
  },
  muted: {
    title: '숨긴 계정',
    help: '팔로우는 그대로 두고 홈 피드와 스토리 트레이에서만 뺍니다. 상대는 알 수 없습니다.',
    empty: '숨긴 계정이 없습니다.',
    button: '숨기기 해제',
    undo: { op: 'mute', posts: false, stories: false },
  },
}

/** 차단·제한·숨긴 계정 목록 */
export function RelationListPage({ kind }: { kind: 'blocked' | 'restricted' | 'muted' }) {
  const text = LIST_TEXT[kind]
  const list = useRelationList(kind as RelationKind)
  const action = useRelationAction()
  return (
    <div className="mx-auto max-w-[600px] pb-10">
      <Header title={text.title} />
      <p className="px-4 pb-3 text-sm text-muted">{text.help}</p>
      {list.isLoading && <p className="p-6 text-center text-sm text-muted">불러오는 중…</p>}
      {list.data?.length === 0 && <p className="p-10 text-center text-sm text-muted">{text.empty}</p>}
      <ul>
        {list.data?.map(({ user }) => (
          <li key={user.id} className="flex items-center gap-3 px-4 py-2">
            <Link to={`/${user.username}`} className="shrink-0">
              <Avatar user={user} size={44} />
            </Link>
            <Link to={`/${user.username}`} className="min-w-0 flex-1 text-sm leading-tight">
              <b className="block truncate">{user.username}</b>
              <span className="block truncate text-muted">{user.fullName}</span>
            </Link>
            <button
              className="rounded-lg bg-surface-2 px-4 py-1.5 text-sm font-semibold hover:bg-border"
              disabled={action.isPending}
              onClick={() =>
                action.mutate(
                  { username: user.username, action: text.undo },
                  { onSuccess: () => toast(`${user.username}님 ${text.button}`), onError: (e) => toast(e.message) },
                )
              }
            >
              {text.button}
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** 친한 친구·즐겨찾기 고르기: 검색 + 체크 */
export function PickListPage({ kind }: { kind: 'close-friends' | 'favorites' }) {
  const candidates = useRelationCandidates(kind)
  const action = useRelationAction()
  const [q, setQ] = useState('')
  const [local, setLocal] = useState<Record<number, boolean>>({})
  const op = kind === 'close-friends' ? 'close-friend' : 'favorite'
  const title = kind === 'close-friends' ? '친한 친구' : '즐겨찾기'
  const help =
    kind === 'close-friends'
      ? '친한 친구에게만 공개한 스토리는 이 사람들만 볼 수 있고, 초록색 링으로 보입니다. 친한 친구 목록은 다른 사람에게 알리지 않습니다.'
      : '즐겨찾기한 사람(최대 50명)의 게시물은 홈의 "즐겨찾기"에서 모아 볼 수 있습니다. 상대에게 알리지 않습니다.'

  const items = (candidates.data ?? []).filter(
    (c) => !q.trim() || c.user.username.includes(q.trim().toLowerCase()) || c.user.fullName.includes(q.trim()),
  )
  const selected = (id: number, server: boolean) => local[id] ?? server
  const count = (candidates.data ?? []).filter((c) => selected(c.user.id, c.selected)).length

  return (
    <div className="mx-auto max-w-[600px] pb-10">
      <Header title={`${title} ${count}명`} />
      <p className="px-4 pb-3 text-sm text-muted">{help}</p>
      <label className="mx-4 mb-2 flex items-center gap-2 rounded-lg bg-surface-2 px-3 py-2">
        <Search size={16} className="text-muted" />
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="검색"
          aria-label="사람 검색"
          className="min-w-0 flex-1 bg-transparent text-sm outline-none"
        />
      </label>
      {candidates.isLoading && <p className="p-6 text-center text-sm text-muted">불러오는 중…</p>}
      {candidates.data?.length === 0 && (
        <p className="p-10 text-center text-sm text-muted">
          {kind === 'close-friends' ? '회원님을 팔로우하는 사람이 없습니다.' : '팔로우하는 사람이 없습니다.'}
        </p>
      )}
      <ul>
        {items.map(({ user, selected: server }) => {
          const on = selected(user.id, server)
          return (
            <li key={user.id}>
              <button
                className="flex w-full items-center gap-3 px-4 py-2 text-left hover:bg-surface-2"
                aria-pressed={on}
                onClick={() => {
                  setLocal((l) => ({ ...l, [user.id]: !on }))
                  action.mutate(
                    { username: user.username, action: { op, on: !on } },
                    {
                      onError: (e) => {
                        setLocal((l) => ({ ...l, [user.id]: on }))
                        toast(e.message)
                      },
                    },
                  )
                }}
              >
                <Avatar user={user} size={44} />
                <span className="min-w-0 flex-1 text-sm leading-tight">
                  <b className="block truncate">{user.username}</b>
                  <span className="block truncate text-muted">{user.fullName}</span>
                </span>
                <span
                  className={clsx(
                    'flex h-6 w-6 items-center justify-center rounded-full border-2',
                    on ? (kind === 'close-friends' ? 'border-[#1fc25b] bg-[#1fc25b]' : 'border-primary bg-primary') + ' text-white' : 'border-muted',
                  )}
                  aria-hidden
                >
                  {on && <Check size={14} strokeWidth={3} />}
                </span>
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
