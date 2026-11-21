import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Star } from 'lucide-react'
import clsx from 'clsx'
import { api } from '../../lib/api'
import { timeAgo } from '../../lib/format'
import type { MessageView, Note, Notes, UserSummary } from '../../lib/types'
import { Avatar } from '../Avatar'
import { Modal } from '../Modal'
import { toast } from '../Toast'

const MAX = 60

export function useNotes() {
  return useQuery({ queryKey: ['notes'], queryFn: () => api.get<Notes>('/api/notes'), staleTime: 30_000 })
}

/**
 * 노트 (DM 받은 메시지함 위쪽): 60자, 24시간, 사람당 하나.
 * 서로 팔로우하는 사람(또는 친한 친구)의 노트가 보이고, 누르면 DM으로 답장한다.
 */
export function NotesTray({ me }: { me: UserSummary }) {
  const { data } = useNotes()
  const [editing, setEditing] = useState(false)
  const [replying, setReplying] = useState<Note | null>(null)

  return (
    <>
      <div className="no-scrollbar flex gap-3 overflow-x-auto px-4 pt-7 pb-2" aria-label="노트">
        <NoteBubble
          user={me}
          note={data?.mine ?? null}
          label="내 노트"
          onClick={() => setEditing(true)}
          placeholder="생각을 남겨 보세요…"
        />
        {data?.others.map((n) => (
          <NoteBubble key={n.user.id} user={n.user} note={n} label={n.user.username} onClick={() => setReplying(n)} />
        ))}
      </div>
      {editing && <NoteEditor current={data?.mine ?? null} onClose={() => setEditing(false)} />}
      {replying && <NoteReply note={replying} onClose={() => setReplying(null)} />}
    </>
  )
}

function NoteBubble({
  user,
  note,
  label,
  onClick,
  placeholder,
}: {
  user: UserSummary
  note: Note | null
  label: string
  onClick: () => void
  placeholder?: string
}) {
  return (
    <button className="relative flex w-[76px] shrink-0 flex-col items-center gap-1" onClick={onClick}>
      <span
        className={clsx(
          'absolute -top-5 left-1/2 z-10 line-clamp-2 w-max max-w-[88px] break-keep text-center -translate-x-1/2 rounded-2xl bg-surface px-2 py-1 text-[11px] leading-tight shadow-[0_1px_6px_rgba(0,0,0,0.18)]',
          !note && 'text-muted',
        )}
      >
        {note ? note.body : placeholder}
      </span>
      <span className="relative mt-4">
        <Avatar user={user} size={64} />
        {!note && placeholder && (
          <span className="absolute right-0 bottom-0 rounded-full border-2 border-bg bg-surface-2 p-0.5">
            <Plus size={12} />
          </span>
        )}
        {note?.audience === 'CLOSE_FRIENDS' && (
          <span className="absolute right-0 bottom-0 rounded-full border-2 border-bg bg-[#1fb141] p-0.5 text-white">
            <Star size={10} fill="currentColor" />
          </span>
        )}
      </span>
      <span className="w-full truncate text-center text-xs text-muted">{label}</span>
    </button>
  )
}

function NoteEditor({ current, onClose }: { current: Note | null; onClose: () => void }) {
  const qc = useQueryClient()
  const [body, setBody] = useState(current?.body ?? '')
  const [audience, setAudience] = useState<Note['audience']>(current?.audience ?? 'MUTUAL')
  const length = [...body].length
  const save = useMutation({
    mutationFn: () => api.put<Note>('/api/notes', { body, audience }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notes'] })
      toast('노트를 공유했습니다')
      onClose()
    },
    onError: (e) => toast((e as Error).message),
  })
  const remove = useMutation({
    mutationFn: () => api.delete('/api/notes'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notes'] })
      toast('노트를 삭제했습니다')
      onClose()
    },
  })
  return (
    <Modal open onClose={onClose} title={current ? '내 노트' : '새 노트'}>
      <div className="space-y-4 p-4">
        <div>
          <textarea
            className="input min-h-20 resize-none"
            value={body}
            maxLength={120}
            onChange={(e) => setBody(e.target.value)}
            placeholder="생각을 공유하세요…"
            aria-label="노트 내용"
            autoFocus
          />
          <p className={clsx('mt-1 text-right text-xs', length > MAX ? 'text-danger' : 'text-muted')}>
            {length}/{MAX}
          </p>
        </div>
        <fieldset className="space-y-2 text-sm">
          <legend className="mb-1 font-semibold">공유 대상</legend>
          <label className="flex items-center gap-2">
            <input type="radio" checked={audience === 'MUTUAL'} onChange={() => setAudience('MUTUAL')} />
            회원님이 팔로우하는 팔로워
          </label>
          <label className="flex items-center gap-2">
            <input type="radio" checked={audience === 'CLOSE_FRIENDS'} onChange={() => setAudience('CLOSE_FRIENDS')} />
            친한 친구
          </label>
        </fieldset>
        <p className="text-xs text-muted">노트는 24시간 동안 받은 메시지함 위쪽에 보입니다. 답장은 메시지로 받습니다.</p>
        {current && <p className="text-xs text-muted">{timeAgo(current.createdAt)}에 남김</p>}
        <div className="flex gap-2">
          {current && (
            <button className="btn flex-1 text-danger" disabled={remove.isPending} onClick={() => remove.mutate()}>
              노트 삭제
            </button>
          )}
          <button
            className="btn btn-primary flex-1"
            disabled={length === 0 || length > MAX || save.isPending}
            onClick={() => save.mutate()}
          >
            공유
          </button>
        </div>
      </div>
    </Modal>
  )
}

function NoteReply({ note, onClose }: { note: Note; onClose: () => void }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [body, setBody] = useState('')
  const send = useMutation({
    mutationFn: () => api.post<MessageView>(`/api/notes/${note.user.username}/reply`, { body }),
    onSuccess: (m) => {
      qc.invalidateQueries({ queryKey: ['inbox'] })
      toast('답장을 보냈습니다')
      onClose()
      navigate(`/direct/t/${m.conversationId}`)
    },
    onError: (e) => toast((e as Error).message),
  })
  return (
    <Modal open onClose={onClose} title={`${note.user.username}님의 노트`}>
      <div className="flex flex-col items-center gap-3 p-5">
        <Avatar user={note.user} size={72} />
        <p className="max-w-[260px] rounded-2xl bg-surface-2 px-4 py-2 text-center">{note.body}</p>
        <p className="text-xs text-muted">{timeAgo(note.createdAt)}</p>
        <form
          className="flex w-full gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            if (body.trim()) send.mutate()
          }}
        >
          <input
            className="input flex-1"
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder={`${note.user.username}님에게 답장…`}
            aria-label="노트에 답장"
            autoFocus
          />
          <button className="btn btn-primary" disabled={!body.trim() || send.isPending}>
            보내기
          </button>
        </form>
      </div>
    </Modal>
  )
}
