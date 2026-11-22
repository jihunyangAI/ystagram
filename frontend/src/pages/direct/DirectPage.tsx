import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Map as MapIcon, MessageCirclePlus, Send } from 'lucide-react'
import clsx from 'clsx'
import { Avatar } from '../../components/Avatar'
import { Modal } from '../../components/Modal'
import { toast } from '../../components/Toast'
import { UserPicker } from '../../components/dm/UserPicker'
import { useMe } from '../../lib/auth'
import { openConversation, useDmUnread, useInbox } from '../../lib/dm'
import { timeAgo } from '../../lib/format'
import type { DmFolder, InboxItem, UserSummary } from '../../lib/types'
import { ThreadPane } from './ThreadPane'
import { ChannelList, ChannelPane, useChannels } from './ChannelPane'
import { BlendPane } from './BlendPane'
import { NotesTray } from '../../components/dm/NotesTray'

const FOLDERS: { key: DmFolder; label: string }[] = [
  { key: 'PRIMARY', label: '기본' },
  { key: 'GENERAL', label: '일반' },
  { key: 'REQUEST', label: '요청' },
]

/** 대화방 이름: 그룹 제목 → 참여자 이름 나열 */
export function conversationName(c: { isGroup: boolean; title: string | null; members: UserSummary[] }): string {
  if (c.title) return c.title
  if (!c.isGroup) return c.members[0]?.username ?? '(알 수 없음)'
  return c.members.map((m) => m.username).join(', ')
}

/**
 * DM (분석 문서 §15). 데스크톱은 왼쪽 받은 메시지함 + 오른쪽 대화, 모바일은 한 번에 하나.
 * 기본: 내가 팔로우하는 사람 / 요청: 그 외 (수락하거나 답장하면 기본으로)
 */
export function DirectPage() {
  const { id, channelId: channelParam, blendId: blendParam } = useParams()
  const conversationId = id ? Number(id) : null
  const channelId = channelParam ? Number(channelParam) : null
  const blendId = blendParam ? Number(blendParam) : null
  const open = conversationId ?? channelId ?? blendId
  const { data: me } = useMe()
  const [folder, setFolder] = useState<DmFolder | 'CHANNELS'>(channelId !== null ? 'CHANNELS' : 'PRIMARY')
  const [composing, setComposing] = useState(false)
  if (!me) return null

  return (
    <div className="flex h-[calc(100dvh-60px-32px)] md:h-[calc(100dvh-32px)]">
      <section
        className={clsx(
          'flex w-full flex-col border-r border-border md:w-[360px] md:shrink-0',
          open !== null && 'hidden md:flex',
        )}
        aria-label="받은 메시지함"
      >
        <header className="flex items-center justify-between px-5 pt-5 pb-3">
          <h1 className="text-xl font-bold">{me.user.username}</h1>
          <span className="flex items-center gap-3">
            <Link to="/direct/map" aria-label="지도" title="친구 지도" className="p-1">
              <MapIcon size={24} />
            </Link>
            <button aria-label="새 메시지" onClick={() => setComposing(true)} className="p-1">
              <MessageCirclePlus size={26} />
            </button>
          </span>
        </header>
        <NotesTray me={me.user} />
        <FolderTabs folder={folder} onChange={setFolder} />
        {folder === 'CHANNELS' ? (
          <ChannelList activeId={channelId} />
        ) : (
          <InboxList folder={folder} activeId={conversationId} meId={me.user.id} />
        )}
      </section>

      <section className={clsx('min-w-0 flex-1', open === null && 'hidden md:flex')}>
        {conversationId !== null ? (
          <ThreadPane key={conversationId} id={conversationId} me={me.user} />
        ) : channelId !== null ? (
          <ChannelPane key={channelId} id={channelId} />
        ) : blendId !== null ? (
          <BlendPane key={blendId} id={blendId} meId={me.user.id} />
        ) : (
          <div className="m-auto flex flex-col items-center gap-3 text-center">
            <span className="rounded-full border-2 border-text p-5">
              <Send size={44} strokeWidth={1.3} />
            </span>
            <p className="text-xl">내 메시지</p>
            <p className="text-sm text-muted">친구나 그룹에 비공개 사진과 메시지를 보내보세요.</p>
            <button
              onClick={() => setComposing(true)}
              className="mt-2 rounded-lg bg-primary px-4 py-1.5 text-sm font-semibold text-white hover:bg-primary-hover"
            >
              메시지 보내기
            </button>
          </div>
        )}
      </section>

      <NewMessageModal open={composing} onClose={() => setComposing(false)} meId={me.user.id} />
    </div>
  )
}

function FolderTabs({ folder, onChange }: { folder: DmFolder | 'CHANNELS'; onChange: (f: DmFolder | 'CHANNELS') => void }) {
  const { data: unread } = useDmUnread()
  const { data: channels } = useChannels()
  const channelUnread = channels?.filter((c) => c.unread > 0).length ?? 0
  const count = (f: DmFolder) => (unread ? (f === 'PRIMARY' ? unread.primary : f === 'GENERAL' ? unread.general : unread.requests) : 0)
  return (
    <div role="tablist" className="flex border-b border-border">
      {FOLDERS.map((f) => (
        <button
          key={f.key}
          role="tab"
          aria-selected={folder === f.key}
          onClick={() => onChange(f.key)}
          className={clsx(
            'flex-1 border-b-2 py-2.5 text-sm font-semibold',
            folder === f.key ? 'border-text' : 'border-transparent text-muted',
          )}
        >
          {f.label}
          {count(f.key) > 0 && <span className="ml-1 text-primary">{count(f.key)}</span>}
        </button>
      ))}
      <button
        role="tab"
        aria-selected={folder === 'CHANNELS'}
        onClick={() => onChange('CHANNELS')}
        className={clsx('flex-1 border-b-2 py-2.5 text-sm font-semibold', folder === 'CHANNELS' ? 'border-text' : 'border-transparent text-muted')}
      >
        채널
        {channelUnread > 0 && <span className="ml-1 text-primary">{channelUnread}</span>}
      </button>
    </div>
  )
}

function InboxList({ folder, activeId, meId }: { folder: DmFolder; activeId: number | null; meId: number }) {
  const { data, isLoading, isError, error } = useInbox(folder)
  return (
    <ul className="min-h-0 flex-1 overflow-y-auto py-1">
      {isLoading && <li className="p-6 text-center text-sm text-muted">불러오는 중…</li>}
      {isError && <li className="p-6 text-center text-sm text-danger">{error.message}</li>}
      {data?.length === 0 && (
        <li className="p-10 text-center text-sm text-muted">
          {folder === 'REQUEST' ? '메시지 요청이 없습니다. 팔로우하지 않는 사람이 보낸 메시지가 여기에 표시됩니다.' : '대화가 없습니다.'}
        </li>
      )}
      {data?.map((c) => (
        <InboxRow key={c.id} c={c} active={c.id === activeId} meId={meId} />
      ))}
    </ul>
  )
}

function preview(c: InboxItem, meId: number): string {
  const l = c.last
  const who = l.senderId === meId ? '나: ' : c.isGroup ? `${c.members.find((m) => m.id === l.senderId)?.username ?? ''}: ` : ''
  if (l.text === null && l.kind === 'TEXT') return `${who}메시지를 보내지 않았습니다`
  switch (l.kind) {
    case 'IMAGE':
      return `${who}사진을 보냈습니다`
    case 'POST':
      return `${who}게시물을 보냈습니다`
    case 'STORY_REPLY':
      return `${who}스토리에 답장: ${l.text ?? ''}`
    case 'BLEND_INVITE':
      return `${who}Blend 초대`
    case 'CALL':
      return `${who}통화`
    case 'NOTE_REPLY':
      return `${who}노트에 답장: ${l.text ?? ''}`
    default:
      return `${who}${l.text ?? ''}`
  }
}

function InboxRow({ c, active, meId }: { c: InboxItem; active: boolean; meId: number }) {
  const [a, b] = c.members
  return (
    <li>
      <Link
        to={`/direct/t/${c.id}`}
        className={clsx('flex items-center gap-3 px-5 py-2.5 hover:bg-surface-2', active && 'bg-surface-2')}
      >
        <span className="relative shrink-0" style={{ width: 56, height: 56 }}>
          {c.isGroup && b ? (
            <>
              <Avatar user={b} size={40} className="absolute top-0 right-0" />
              <Avatar user={a} size={40} className="absolute bottom-0 left-0 ring-2 ring-bg" />
            </>
          ) : (
            a && <Avatar user={a} size={56} />
          )}
          {c.online && <span className="absolute right-0.5 bottom-0.5 h-3.5 w-3.5 rounded-full border-2 border-bg bg-[#1fc25b]" aria-label="활동 중" />}
        </span>
        <span className="min-w-0 flex-1 text-sm leading-snug">
          <span className={clsx('block truncate', c.unread && 'font-bold')}>{conversationName(c)}</span>
          <span className={clsx('flex gap-1 text-muted', c.unread && 'font-semibold text-text')}>
            <span className="truncate">{preview(c, meId)}</span>
            <span className="shrink-0">· {timeAgo(c.last.createdAt)}</span>
          </span>
        </span>
        {c.unread && <span className="h-2 w-2 shrink-0 rounded-full bg-primary" aria-label="안 읽음" />}
      </Link>
    </li>
  )
}

function NewMessageModal({ open, onClose, meId }: { open: boolean; onClose: () => void; meId: number }) {
  const navigate = useNavigate()
  const [selected, setSelected] = useState<UserSummary[]>([])
  const [title, setTitle] = useState('')
  const [pending, setPending] = useState(false)
  const start = () => {
    if (!selected.length) return
    setPending(true)
    openConversation(selected.map((u) => u.username), selected.length > 1 ? title : undefined)
      .then((c) => {
        onClose()
        setSelected([])
        setTitle('')
        navigate(`/direct/t/${c.id}`)
      }, (e: Error) => toast(e.message))
      .finally(() => setPending(false))
  }
  return (
    <Modal open={open} onClose={onClose} title="새 메시지" className="flex h-[70vh] flex-col">
      {open && <UserPicker selected={selected} onChange={setSelected} meId={meId} />}
      <div className="border-t border-border p-3">
        {selected.length > 1 && (
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="그룹 이름 (선택)"
            maxLength={100}
            className="mb-2 w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none"
          />
        )}
        <button
          onClick={start}
          disabled={!selected.length || pending}
          className="w-full rounded-lg bg-primary py-2.5 text-sm font-semibold text-white hover:bg-primary-hover disabled:opacity-40"
        >
          채팅
        </button>
      </div>
    </Modal>
  )
}
