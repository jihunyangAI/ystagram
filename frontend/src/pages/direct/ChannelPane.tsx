import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BarChart3, ChevronLeft, ImageIcon, Megaphone, MoreHorizontal, Plus, Trash2, Users, X } from 'lucide-react'
import clsx from 'clsx'
import { Avatar } from '../../components/Avatar'
import { ConfirmModal, Modal, SheetButton } from '../../components/Modal'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'
import { timeAgo } from '../../lib/format'
import { useRealtimeEvent } from '../../lib/realtime'
import type { UserSummary } from '../../lib/types'

export interface ChannelSummary {
  id: number
  name: string
  owner: UserSummary
  memberCount: number
  joined: boolean
  mine: boolean
  lastText: string | null
  lastAt: string | null
  unread: number
}

interface Poll {
  options: string[]
  /** 투표 전에는 null (결과 가림) */
  votes: (number | null)[]
  total: number
  myVote: number | null
}

interface ChannelMessage {
  id: number
  body: string | null
  mediaUrl: string | null
  mediaWidth: number | null
  mediaHeight: number | null
  poll: Poll | null
  reactions: { emoji: string; count: number; mine: boolean }[]
  createdAt: string
}

interface ChannelView {
  channel: ChannelSummary
  messages: ChannelMessage[]
  nextBefore: number | null
}

const EMOJIS = ['❤️', '🔥', '😂', '😮', '👏', '🥹']

export function useChannels() {
  return useQuery({ queryKey: ['channels'], queryFn: () => api.get<ChannelSummary[]>('/api/channels') })
}

/** 받은 메시지함 "채널" 탭 */
export function ChannelList({ activeId }: { activeId: number | null }) {
  const { data, isLoading } = useChannels()
  const [creating, setCreating] = useState(false)
  const hasMine = data?.some((c) => c.mine)
  return (
    <div className="min-h-0 flex-1 overflow-y-auto py-1">
      {!hasMine && data && (
        <button className="flex w-full items-center gap-3 px-5 py-2.5 text-left hover:bg-surface-2" onClick={() => setCreating(true)}>
          <span className="flex h-14 w-14 items-center justify-center rounded-full border border-border">
            <Plus size={24} />
          </span>
          <span className="text-sm">
            <span className="font-semibold">브로드캐스트 채널 만들기</span>
            <span className="block text-xs text-muted">팔로워에게 소식·사진·투표를 한 번에 보내요</span>
          </span>
        </button>
      )}
      {isLoading && <p className="p-6 text-center text-sm text-muted">불러오는 중…</p>}
      {data?.length === 0 && <p className="p-6 text-center text-sm text-muted">참여한 채널이 없습니다. 크리에이터 프로필에서 채널에 참여해 보세요.</p>}
      <ul>
        {data?.map((c) => (
          <li key={c.id}>
            <Link
              to={`/direct/channel/${c.id}`}
              className={clsx('flex items-center gap-3 px-5 py-2.5 hover:bg-surface-2', activeId === c.id && 'bg-surface-2')}
            >
              <span className="relative">
                <Avatar user={c.owner} size={56} />
                <span className="absolute -right-0.5 -bottom-0.5 rounded-full border-2 border-bg bg-primary p-1 text-white">
                  <Megaphone size={10} />
                </span>
              </span>
              <span className="min-w-0 flex-1 text-sm">
                <span className={clsx('block truncate', c.unread > 0 && 'font-semibold')}>{c.name}</span>
                <span className={clsx('block truncate text-xs', c.unread > 0 ? 'font-semibold text-text' : 'text-muted')}>
                  {c.lastText ?? `${c.owner.username}님의 채널`}
                  {c.lastAt && ` · ${timeAgo(c.lastAt)}`}
                </span>
              </span>
              {c.unread > 0 && <span className="h-2 w-2 rounded-full bg-primary" aria-label="안 읽음" />}
            </Link>
          </li>
        ))}
      </ul>
      {creating && <CreateChannelModal onClose={() => setCreating(false)} />}
    </div>
  )
}

export function CreateChannelModal({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const create = useMutation({
    mutationFn: () => api.post<ChannelSummary>('/api/channels', { name }),
    onSuccess: (c) => {
      void qc.invalidateQueries({ queryKey: ['channels'] })
      onClose()
      navigate(`/direct/channel/${c.id}`)
    },
    onError: (e) => toast((e as Error).message),
  })
  return (
    <Modal open onClose={onClose} title="브로드캐스트 채널 만들기">
      <div className="space-y-3 p-4 text-sm">
        <p className="text-muted">
          채널에는 회원님만 글을 올리고, 참여한 팔로워는 반응과 투표만 할 수 있어요. 프로필에 채널이 표시되어 팔로워가 참여할 수 있습니다.
        </p>
        <input className="input" maxLength={60} placeholder="채널 이름" value={name} onChange={(e) => setName(e.target.value)} autoFocus />
        <button className="btn btn-primary w-full" disabled={!name.trim() || create.isPending} onClick={() => create.mutate()}>
          만들기
        </button>
      </div>
    </Modal>
  )
}

/** 채널 화면: 주인의 글(사진·투표) + 반응. 주인만 아래 입력칸이 있다 */
export function ChannelPane({ id }: { id: number }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const key = ['channel', id]
  const { data, isLoading, isError, error } = useQuery({ queryKey: key, queryFn: () => api.get<ChannelView>(`/api/channels/${id}`) })
  const bottom = useRef<HTMLDivElement>(null)
  const [menu, setMenu] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  useRealtimeEvent((e) => {
    if (e.type !== 'channel:new') return
    void qc.invalidateQueries({ queryKey: ['channels'] })
    if ((e.data as { channelId: number }).channelId === id) void qc.invalidateQueries({ queryKey: key })
  })
  useEffect(() => {
    bottom.current?.scrollIntoView({ block: 'end' })
    if (data) void qc.invalidateQueries({ queryKey: ['channels'] }) // 읽음 반영
  }, [data?.messages.length]) // eslint-disable-line react-hooks/exhaustive-deps

  const update = (m: ChannelMessage) =>
    qc.setQueryData<ChannelView>(key, (v) => (v ? { ...v, messages: v.messages.map((x) => (x.id === m.id ? m : x)) } : v))
  const join = useMutation({
    mutationFn: (on: boolean) => (on ? api.put(`/api/channels/${id}/members/me`) : api.delete(`/api/channels/${id}/members/me`)),
    onSuccess: (_, on) => {
      void qc.invalidateQueries({ queryKey: key })
      void qc.invalidateQueries({ queryKey: ['channels'] })
      toast(on ? '채널에 참여했습니다' : '채널에서 나갔습니다')
      if (!on) navigate('/direct/inbox')
    },
    onError: (e) => toast((e as Error).message),
  })
  const remove = useMutation({
    mutationFn: () => api.delete(`/api/channels/${id}`),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['channels'] })
      navigate('/direct/inbox')
    },
  })

  if (isLoading) return <p className="m-auto text-sm text-muted">불러오는 중…</p>
  if (isError) return <p className="m-auto text-sm text-danger">{error.message}</p>
  if (!data) return null
  const c = data.channel

  return (
    <div className="flex h-full w-full flex-col">
      <header className="flex items-center gap-3 border-b border-border px-3 py-2.5 md:px-5">
        <button aria-label="뒤로" className="md:hidden" onClick={() => navigate('/direct/inbox')}>
          <ChevronLeft size={26} />
        </button>
        <Avatar user={c.owner} size={40} />
        <div className="min-w-0 flex-1 text-sm">
          <p className="truncate font-semibold">{c.name}</p>
          <p className="flex items-center gap-1 text-xs text-muted">
            <Users size={12} /> {c.memberCount.toLocaleString('ko-KR')}명 · <Link to={`/${c.owner.username}`}>{c.owner.username}</Link>
          </p>
        </div>
        <button aria-label="채널 옵션" onClick={() => setMenu(true)}>
          <MoreHorizontal size={22} />
        </button>
      </header>

      <div className="min-h-0 flex-1 space-y-4 overflow-y-auto px-3 py-4 md:px-5">
        <div className="mx-auto max-w-sm py-4 text-center text-sm">
          <Avatar user={c.owner} size={72} className="mx-auto" />
          <p className="mt-2 font-semibold">{c.name}</p>
          <p className="text-xs text-muted">브로드캐스트 채널 · {c.owner.username}님만 글을 올릴 수 있어요</p>
        </div>
        {data.messages.map((m) => (
          <MessageItem key={m.id} m={m} channel={c} onChange={update} />
        ))}
        <div ref={bottom} />
      </div>

      {c.mine ? (
        <OwnerComposer id={id} onPosted={() => void qc.invalidateQueries({ queryKey: key })} />
      ) : c.joined ? (
        <p className="border-t border-border px-4 py-3 text-center text-xs text-muted">이모지로 반응하거나 투표할 수 있어요</p>
      ) : (
        <div className="border-t border-border p-3">
          <button className="btn btn-primary w-full" disabled={join.isPending} onClick={() => join.mutate(true)}>
            채널 참여하기
          </button>
        </div>
      )}

      <Modal open={menu} onClose={() => setMenu(false)} bare title="채널 옵션">
        <SheetButton
          onClick={() => {
            setMenu(false)
            navigate(`/${c.owner.username}`)
          }}
        >
          프로필 보기
        </SheetButton>
        {c.mine ? (
          <SheetButton
            danger
            onClick={() => {
              setMenu(false)
              setConfirmDelete(true)
            }}
          >
            채널 삭제
          </SheetButton>
        ) : (
          c.joined && (
            <SheetButton
              danger
              onClick={() => {
                setMenu(false)
                join.mutate(false)
              }}
            >
              채널 나가기
            </SheetButton>
          )
        )}
        <SheetButton onClick={() => setMenu(false)}>취소</SheetButton>
      </Modal>
      <ConfirmModal
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title="채널을 삭제하시겠어요?"
        message="모든 글과 참여자가 사라지며 되돌릴 수 없습니다."
        confirmLabel="삭제"
        onConfirm={() => remove.mutate()}
      />
    </div>
  )
}

function MessageItem({ m, channel, onChange }: { m: ChannelMessage; channel: ChannelSummary; onChange: (m: ChannelMessage) => void }) {
  const [picker, setPicker] = useState(false)
  const qc = useQueryClient()
  const mine = m.reactions.find((r) => r.mine)?.emoji ?? null
  const react = (emoji: string | null) =>
    (emoji ? api.put<ChannelMessage>(`/api/channels/messages/${m.id}/reaction`, { emoji }) : api.delete<ChannelMessage>(`/api/channels/messages/${m.id}/reaction`))
      .then(onChange, (e: Error) => toast(e.message))
  const vote = (i: number) => api.put<ChannelMessage>(`/api/channels/messages/${m.id}/vote`, { option: i }).then(onChange, (e: Error) => toast(e.message))
  const remove = () =>
    api.delete(`/api/channels/messages/${m.id}`).then(() => qc.invalidateQueries({ queryKey: ['channel', channel.id] }), (e: Error) => toast(e.message))

  return (
    <div className="group flex items-start gap-2">
      <Avatar user={channel.owner} size={28} className="mt-1" />
      <div className="min-w-0 max-w-[75%] space-y-1">
        {m.mediaUrl && (
          <img
            src={m.mediaUrl}
            alt="채널 사진"
            className="max-h-80 rounded-2xl object-cover"
            style={m.mediaWidth && m.mediaHeight ? { aspectRatio: `${m.mediaWidth} / ${m.mediaHeight}` } : undefined}
          />
        )}
        {m.poll ? (
          <div className="w-72 max-w-full rounded-2xl bg-surface-2 p-3 text-sm">
            <p className="mb-2 flex items-center gap-1.5 font-semibold">
              <BarChart3 size={16} className="text-primary" /> {m.body}
            </p>
            <ul className="space-y-1.5">
              {m.poll.options.map((o, i) => {
                const votes = m.poll!.votes[i]
                const ratio = votes != null && m.poll!.total > 0 ? votes / m.poll!.total : 0
                const shown = votes != null
                return (
                  <li key={i}>
                    <button
                      disabled={m.poll!.myVote != null || channel.mine || !channel.joined}
                      onClick={() => vote(i)}
                      className={clsx(
                        'relative w-full overflow-hidden rounded-lg border px-3 py-2 text-left',
                        m.poll!.myVote === i ? 'border-primary' : 'border-border',
                        !shown && channel.joined && 'hover:bg-surface',
                      )}
                    >
                      {shown && <span className="absolute inset-y-0 left-0 bg-primary/15" style={{ width: `${ratio * 100}%` }} />}
                      <span className="relative flex justify-between gap-2">
                        <span>{o}</span>
                        {shown && <span className="tabular-nums text-muted">{Math.round(ratio * 100)}%</span>}
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
            <p className="mt-2 text-xs text-muted">
              {m.poll.total.toLocaleString('ko-KR')}명 투표
              {m.poll.myVote == null && !channel.mine && ' · 투표하면 결과가 보여요'}
            </p>
          </div>
        ) : (
          m.body && <p className="rounded-[20px] bg-surface-2 px-3.5 py-2 text-sm break-words whitespace-pre-line">{m.body}</p>
        )}
        <div className="flex flex-wrap items-center gap-1 text-xs text-muted">
          {m.reactions.map((r) => (
            <button
              key={r.emoji}
              disabled={!channel.joined || channel.mine}
              onClick={() => react(r.mine ? null : r.emoji)}
              className={clsx('rounded-full border px-1.5 py-0.5', r.mine ? 'border-primary text-text' : 'border-border')}
            >
              {r.emoji} {r.count}
            </button>
          ))}
          <span>{timeAgo(m.createdAt)}</span>
          {channel.joined && !channel.mine && (
            <span className="relative">
              <button className="rounded-full px-1.5 hover:bg-surface-2" aria-label="반응 남기기" onClick={() => setPicker((v) => !v)}>
                ☺︎+
              </button>
              {picker && (
                <span className="absolute bottom-6 left-0 z-10 flex gap-1 rounded-full bg-surface px-2 py-1 shadow">
                  {EMOJIS.map((e) => (
                    <button
                      key={e}
                      className="text-lg"
                      onClick={() => {
                        setPicker(false)
                        react(mine === e ? null : e)
                      }}
                    >
                      {e}
                    </button>
                  ))}
                </span>
              )}
            </span>
          )}
          {channel.mine && (
            <button aria-label="글 삭제" className="opacity-0 group-hover:opacity-100" onClick={remove}>
              <Trash2 size={12} />
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

function OwnerComposer({ id, onPosted }: { id: number; onPosted: () => void }) {
  const [body, setBody] = useState('')
  const [poll, setPoll] = useState<string[] | null>(null)
  const file = useRef<HTMLInputElement>(null)
  const send = useMutation({
    mutationFn: () => api.post(`/api/channels/${id}/messages`, { body, pollOptions: poll?.filter((o) => o.trim()) ?? null }),
    onSuccess: () => {
      setBody('')
      setPoll(null)
      onPosted()
    },
    onError: (e) => toast((e as Error).message),
  })
  const image = useMutation({
    mutationFn: (f: File) => {
      const form = new FormData()
      form.append('file', f)
      return api.upload('POST', `/api/channels/${id}/images`, form)
    },
    onSuccess: onPosted,
    onError: (e) => toast((e as Error).message),
  })
  return (
    <div className="border-t border-border px-3 pt-2 pb-3 md:px-5">
      {poll && (
        <div className="mb-2 space-y-1.5 rounded-xl border border-border p-2">
          <p className="flex items-center justify-between text-xs font-semibold">
            투표 보기 (2~4개)
            <button aria-label="투표 빼기" onClick={() => setPoll(null)}>
              <X size={14} />
            </button>
          </p>
          {poll.map((o, i) => (
            <input
              key={i}
              className="input py-1.5 text-sm"
              maxLength={40}
              placeholder={`보기 ${i + 1}`}
              value={o}
              onChange={(e) => setPoll(poll.map((x, k) => (k === i ? e.target.value : x)))}
            />
          ))}
          {poll.length < 4 && (
            <button className="text-xs font-semibold text-primary" onClick={() => setPoll([...poll, ''])}>
              + 보기 추가
            </button>
          )}
        </div>
      )}
      <form
        className="flex items-end gap-2 rounded-[22px] border border-border px-3 py-1.5"
        onSubmit={(e) => {
          e.preventDefault()
          if (body.trim()) send.mutate()
        }}
      >
        <textarea
          rows={1}
          value={body}
          maxLength={1000}
          onChange={(e) => setBody(e.target.value)}
          placeholder={poll ? '투표 질문…' : '채널에 보낼 메시지…'}
          aria-label="채널 메시지"
          className="max-h-32 min-h-[28px] min-w-0 flex-1 resize-none bg-transparent py-1 text-sm outline-none placeholder:text-muted"
        />
        {!poll && (
          <button type="button" aria-label="투표 만들기" className="p-1" onClick={() => setPoll(['', ''])}>
            <BarChart3 size={20} />
          </button>
        )}
        <input
          ref={file}
          type="file"
          accept="image/*"
          hidden
          onChange={(e) => {
            const f = e.target.files?.[0]
            if (f) image.mutate(f)
            e.target.value = ''
          }}
        />
        <button type="button" aria-label="사진 보내기" className="p-1" onClick={() => file.current?.click()}>
          <ImageIcon size={20} />
        </button>
        <button type="submit" disabled={!body.trim() || send.isPending} className="py-1 text-sm font-semibold text-primary disabled:opacity-40">
          보내기
        </button>
      </form>
    </div>
  )
}
