import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Blend,
  CalendarClock,
  ChevronDown,
  ChevronLeft,
  CornerUpLeft,
  ImageIcon,
  Info,
  MoreHorizontal,
  Pencil,
  Phone,
  PhoneMissed,
  Smile,
  Undo2,
  Video,
  X,
} from 'lucide-react'
import clsx from 'clsx'
import { Avatar } from '../../components/Avatar'
import { ConfirmModal, Modal } from '../../components/Modal'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'
import { readConversation, useConversation, useMessageActions, useMessages, useSendImage, useSendMessage } from '../../lib/dm'
import { timeAgo } from '../../lib/format'
import { sendTyping, useRealtimeEvent } from '../../lib/realtime'
import type { ConversationView, MessageView, ScheduledMessage, UserSummary } from '../../lib/types'
import { fromLocalInput, laterInput, scheduleLabel } from '../../lib/datetime'
import { conversationName } from './DirectPage'
import { CALL_STATUS_LABEL, formatDuration, startCall } from '../../components/call/CallManager'
import {
  MessageMore,
  PinnedBar,
  ThreadInfoSheet,
  TranslationLine,
  VanishBanner,
  ViewOnceBubble,
  ViewOnceButton,
  VoiceBubble,
  VoiceRecorder,
} from './DmExtras'

const REACTIONS = ['❤️', '😂', '😮', '😢', '😡', '👍']
const GROUP_GAP_MS = 10 * 60_000

/** 대화방: 실시간 수신 · 입력 중 · 읽음 · 답장 · 반응 · 15분 수정 · 전송 취소 · 사진 */
export function ThreadPane({ id, me }: { id: number; me: UserSummary }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const conversation = useConversation(id)
  const messages = useMessages(id)
  const [typingUsers, setTypingUsers] = useState<Record<number, { user: UserSummary; at: number }>>({})
  const [replyTo, setReplyTo] = useState<MessageView | null>(null)
  const [editing, setEditing] = useState<MessageView | null>(null)
  const [unsending, setUnsending] = useState<MessageView | null>(null)
  const [info, setInfo] = useState(false)
  const actions = useMessageActions()

  // 최근 메시지가 pages[0]의 맨 앞 → 화면에는 오래된 순으로
  const list = (messages.data?.pages.flatMap((p) => p.items) ?? []).slice().reverse()
  const newest = list[list.length - 1]

  // 상대 메시지를 보면 읽음 처리 (창이 보일 때만)
  const lastReadSent = useRef(0)
  useEffect(() => {
    if (!newest || !conversation.data) return
    const mark = () => {
      if (document.visibilityState !== 'visible') return
      if (newest.id <= Math.max(conversation.data.myLastRead, lastReadSent.current)) return
      lastReadSent.current = newest.id
      readConversation(id, newest.id).then(() => {
        qc.invalidateQueries({ queryKey: ['dm-unread'] })
        qc.invalidateQueries({ queryKey: ['inbox'] })
      }, () => undefined)
    }
    mark()
    document.addEventListener('visibilitychange', mark)
    return () => document.removeEventListener('visibilitychange', mark)
  }, [newest, conversation.data, id, qc])

  // 입력 중 표시: 이벤트를 받으면 4초간 보여 준다. 그 사람의 메시지가 오면 바로 지운다
  useRealtimeEvent((e) => {
    if (e.type === 'typing') {
      const t = e.data as { conversationId: number; user: UserSummary }
      if (t.conversationId === id) setTypingUsers((m) => ({ ...m, [t.user.id]: { user: t.user, at: Date.now() } }))
    }
    if (e.type === 'message:new') {
      const m = e.data as MessageView
      if (m.conversationId === id) setTypingUsers((x) => {
        const { [m.sender.id]: _gone, ...rest } = x
        return rest
      })
    }
  })
  useEffect(() => {
    const t = setInterval(() => {
      setTypingUsers((m) => {
        const now = Date.now()
        const kept = Object.fromEntries(Object.entries(m).filter(([, v]) => now - v.at < 4000))
        return Object.keys(kept).length === Object.keys(m).length ? m : kept
      })
    }, 1000)
    return () => clearInterval(t)
  }, [])

  // 맨 아래 유지 (column-reverse 스크롤이라 새 메시지가 오면 자연스럽게 아래에 붙는다)
  const scroller = useRef<HTMLDivElement>(null)
  const onScroll = () => {
    const el = scroller.current
    if (!el || !messages.hasNextPage || messages.isFetchingNextPage) return
    // column-reverse: scrollTop이 음수로 커질수록 위쪽
    if (el.scrollHeight + el.scrollTop - el.clientHeight < 200) void messages.fetchNextPage()
  }

  const c = conversation.data
  if (conversation.isError) {
    return <p className="m-auto p-10 text-center text-sm text-muted">{conversation.error.message}</p>
  }
  if (!c) return <p className="m-auto text-sm text-muted">불러오는 중…</p>

  const name = conversationName({ isGroup: c.isGroup, title: c.title, members: c.members.map((m) => m.user) })
  const single = !c.isGroup ? c.members[0] : null
  const typingNames = Object.values(typingUsers).map((t) => t.user)

  return (
    <div className="flex h-full w-full flex-col">
      <header className="flex items-center gap-3 border-b border-border px-3 py-2.5 md:px-5">
        <button aria-label="뒤로" className="p-1 md:hidden" onClick={() => navigate('/direct/inbox')}>
          <ChevronLeft size={26} />
        </button>
        {single ? (
          <Link to={`/${single.user.username}`} className="flex min-w-0 items-center gap-3">
            <span className="relative">
              <Avatar user={single.user} size={44} />
              {single.online && <span className="absolute right-0 bottom-0 h-3 w-3 rounded-full border-2 border-bg bg-[#1fc25b]" />}
            </span>
            <span className="min-w-0 leading-tight">
              <b className="block truncate">{single.user.fullName || single.user.username}</b>
              <span className="text-xs text-muted">{presence(single)}</span>
            </span>
          </Link>
        ) : (
          <div className="min-w-0 leading-tight">
            <b className="block truncate">{name}</b>
            <span className="text-xs text-muted">{c.members.length + 1}명</span>
          </div>
        )}
        {single && <BlendButton conversationId={id} />}
        {single && (
          <>
            <button aria-label="음성 통화" title="음성 통화" className="p-1.5" onClick={() => startCall(id, false)}>
              <Phone size={22} />
            </button>
            <button aria-label="영상 통화" title="영상 통화" className="p-1.5" onClick={() => startCall(id, true)}>
              <Video size={24} />
            </button>
          </>
        )}
        <button aria-label="대화 정보" title="대화 정보" className={clsx('p-1.5', !single && 'ml-auto')} onClick={() => setInfo(true)}>
          <Info size={22} />
        </button>
      </header>
      {c.vanishMode && <VanishBanner />}
      <PinnedBar
        c={c}
        onJump={(mid) => document.getElementById(`msg-${mid}`)?.scrollIntoView({ block: 'center', behavior: 'smooth' })}
      />
      <ThreadInfoSheet c={c} open={info} onClose={() => setInfo(false)} />

      <div ref={scroller} onScroll={onScroll} className="flex min-h-0 flex-1 flex-col-reverse overflow-y-auto px-3 py-3 md:px-5">
        <div className="flex flex-col gap-0.5">
          {messages.isFetchingNextPage && <p className="py-2 text-center text-xs text-muted">이전 메시지 불러오는 중…</p>}
          {!messages.hasNextPage && messages.isSuccess && <ThreadIntro c={c} name={name} />}
          {list.map((m, i) => {
            const prev = list[i - 1]
            const gap = !prev || new Date(m.createdAt).getTime() - new Date(prev.createdAt).getTime() > GROUP_GAP_MS
            const sameSender = prev && prev.sender.id === m.sender.id && !gap
            return (
              <div key={m.id} id={`msg-${m.id}`}>
                {gap && (
                  <p className="py-3 text-center text-xs text-muted">
                    {new Date(m.createdAt).toLocaleString('ko-KR', { month: 'long', day: 'numeric', hour: 'numeric', minute: '2-digit' })}
                  </p>
                )}
                <Bubble
                  m={m}
                  mine={m.sender.id === me.id}
                  showName={c.isGroup && m.sender.id !== me.id && !sameSender}
                  meId={me.id}
                  onReply={() => {
                    setEditing(null)
                    setReplyTo(m)
                  }}
                  onEdit={() => {
                    setReplyTo(null)
                    setEditing(m)
                  }}
                  onUnsend={() => setUnsending(m)}
                  onReact={(emoji) => actions.react(m, emoji).catch((e: Error) => toast(e.message))}
                />
              </div>
            )
          })}
          <SeenLine c={c} list={list} meId={me.id} />
          {typingNames.length > 0 && (
            <div className="mt-1 flex items-center gap-2" aria-live="polite">
              <Avatar user={typingNames[0]} size={24} />
              <span className="rounded-full bg-surface-2 px-3 py-2 text-sm text-muted">
                {c.isGroup ? `${typingNames.map((u) => u.username).join(', ')}님이 ` : ''}입력 중
                <span className="animate-pulse">…</span>
              </span>
            </div>
          )}
        </div>
      </div>

      {c.folder === 'REQUEST' ? (
        <RequestBar c={c} name={name} />
      ) : (
        <Composer
          id={id}
          replyTo={replyTo}
          editing={editing}
          onCancel={() => {
            setReplyTo(null)
            setEditing(null)
          }}
          onDone={() => {
            setReplyTo(null)
            setEditing(null)
          }}
        />
      )}

      <ConfirmModal
        open={unsending !== null}
        onClose={() => setUnsending(null)}
        title="메시지를 전송 취소하시겠어요?"
        message="메시지가 모든 참여자의 대화에서 삭제됩니다."
        confirmLabel="전송 취소"
        onConfirm={() =>
          unsending &&
          actions.unsend(unsending).then(
            () => setUnsending(null),
            (e: Error) => toast(e.message),
          )
        }
      />
    </div>
  )
}

function presence(m: ConversationView['members'][number]): string {
  if (m.online) return '현재 활동 중'
  if (m.lastActiveAt) return `${timeAgo(m.lastActiveAt, 'long')} 활동`
  return m.user.username
}

function ThreadIntro({ c, name }: { c: ConversationView; name: string }) {
  const single = !c.isGroup ? c.members[0] : null
  return (
    <div className="flex flex-col items-center gap-1 py-8 text-center">
      {single ? <Avatar user={single.user} size={88} /> : null}
      <b className="mt-2 text-lg">{single ? single.user.fullName || single.user.username : name}</b>
      {single && <span className="text-sm text-muted">{single.user.username} · Yangstagram</span>}
      {single && (
        <Link to={`/${single.user.username}`} className="mt-3 rounded-lg bg-surface-2 px-4 py-1.5 text-sm font-semibold hover:bg-border">
          프로필 보기
        </Link>
      )}
    </div>
  )
}

/** 내 마지막 메시지 아래 "읽음" (1:1) / "읽음: a, b" (그룹) */
function SeenLine({ c, list, meId }: { c: ConversationView; list: MessageView[]; meId: number }) {
  const last = list[list.length - 1]
  if (!last || last.sender.id !== meId) return null
  const seen = c.members.filter((m) => m.lastReadMessageId >= last.id)
  if (seen.length === 0) return <p className="pr-1 text-right text-xs text-muted">전송됨</p>
  return (
    <p className="pr-1 text-right text-xs text-muted">
      {c.isGroup ? `읽음: ${seen.map((m) => m.user.username).join(', ')}` : '읽음'}
    </p>
  )
}

function Bubble({
  m,
  mine,
  showName,
  meId,
  onReply,
  onEdit,
  onUnsend,
  onReact,
}: {
  m: MessageView
  mine: boolean
  showName: boolean
  meId: number
  onReply: () => void
  onEdit: () => void
  onUnsend: () => void
  onReact: (emoji: string | null) => void
}) {
  const [picker, setPicker] = useState(false)
  const [more, setMore] = useState(false)
  const [translated, setTranslated] = useState<string | null>(null)
  const myReaction = m.reactions.find((r) => r.userId === meId)?.emoji ?? null

  const content = m.unsent ? (
    <span className="rounded-2xl border border-border px-3 py-2 text-sm text-muted italic">메시지를 보내지 않았습니다</span>
  ) : (
    <div className={clsx('flex max-w-full flex-col gap-1', mine ? 'items-end' : 'items-start')}>
      {m.replyTo && (
        <span className="max-w-[260px] truncate rounded-2xl bg-surface-2/70 px-3 py-1.5 text-xs text-muted">
          ↩ {m.replyTo.senderUsername}: {m.replyTo.text ?? '메시지를 보내지 않았습니다'}
        </span>
      )}
      {m.kind === 'CALL' && m.call && (
        <button
          className="flex w-56 items-center gap-3 rounded-2xl border border-border px-3 py-2.5 text-left hover:bg-surface-2"
          title="다시 걸기"
          onClick={() => startCall(m.conversationId, m.call!.video)}
        >
          <span
            className={clsx(
              'grid size-9 shrink-0 place-items-center rounded-full',
              m.call.status === 'ENDED' ? 'bg-surface-2' : 'bg-red-500/10 text-red-500',
            )}
          >
            {m.call.status === 'ENDED' ? m.call.video ? <Video size={18} /> : <Phone size={18} /> : <PhoneMissed size={18} />}
          </span>
          <span className="text-sm">
            <span className="block font-semibold">
              {m.call.status === 'ENDED'
                ? m.call.video ? '영상 통화' : '음성 통화'
                : m.call.status === 'MISSED' && !mine ? '부재중 통화' : CALL_STATUS_LABEL[m.call.status]}
            </span>
            <span className="block text-xs text-muted">
              {m.call.durationSec != null ? formatDuration(m.call.durationSec) : '다시 걸기'}
            </span>
          </span>
        </button>
      )}
      {m.kind === 'BLEND_INVITE' && m.blend && (
        <Link
          to={`/direct/blend/${m.blend.id}`}
          className="flex w-60 items-center gap-3 rounded-2xl border border-border px-3 py-2.5 hover:bg-surface-2"
        >
          <Blend size={28} className="shrink-0 text-primary" />
          <span className="text-sm">
            <span className="block font-semibold">{mine ? 'Blend에 초대했어요' : 'Blend 초대를 받았어요'}</span>
            <span className="block text-xs text-muted">
              {m.blend.status === 'ACTIVE' ? '함께 보는 릴스 보기' : mine ? '상대가 참여하면 시작돼요' : '눌러서 참여하기'}
            </span>
          </span>
        </Link>
      )}
      {m.kind === 'NOTE_REPLY' && (
        <>
          <span className="text-xs text-muted">{mine ? '노트에 답장함' : '회원님의 노트에 답장함'}</span>
          {m.quote && (
            <span className="max-w-[240px] rounded-2xl border border-border px-3 py-1.5 text-xs text-muted">“{m.quote}”</span>
          )}
        </>
      )}
      {m.kind === 'STORY_REPLY' && m.story && (
        <span className="text-xs text-muted">{mine ? `${m.story.author.username}님의 스토리에 답장함` : '회원님의 스토리에 답장함'}</span>
      )}
      {m.story && (
        m.story.mediaUrl ? (
          <img src={m.story.mediaUrl} alt="스토리" className="h-40 w-[90px] rounded-xl object-cover opacity-90" />
        ) : (
          <span className="rounded-xl border border-border px-3 py-2 text-xs text-muted">스토리를 더 이상 볼 수 없습니다</span>
        )
      )}
      {m.viewOnce && <ViewOnceBubble m={m} mine={mine} />}
      {m.kind === 'AUDIO' && <VoiceBubble m={m} mine={mine} />}
      {m.media && m.kind !== 'AUDIO' && (
        <a href={m.media.url} target="_blank" rel="noreferrer">
          <img
            src={m.media.url}
            alt="보낸 사진"
            className="max-h-72 max-w-[240px] rounded-2xl object-cover"
            style={{ aspectRatio: `${m.media.width} / ${m.media.height}` }}
          />
        </a>
      )}
      {m.post && <SharedPostCard post={m.post} />}
      {m.body && (
        <span
          className={clsx(
            'rounded-[20px] px-3.5 py-2 text-sm break-words break-keep whitespace-pre-line',
            mine ? 'bg-primary text-white' : 'bg-surface-2',
          )}
        >
          {m.body}
        </span>
      )}
      {translated && <TranslationLine text={translated} />}
      {(m.edited || m.pinned) && (
        <span className="px-1 text-[11px] text-muted">
          {[m.pinned && '고정됨', m.edited && '수정됨'].filter(Boolean).join(' · ')}
        </span>
      )}
    </div>
  )

  return (
    <div className={clsx('group relative flex items-end gap-2', mine ? 'flex-row-reverse' : 'flex-row', m.reactions.length > 0 && 'mb-3')}>
      {!mine && <span className="w-7 shrink-0">{showName && <Avatar user={m.sender} size={28} />}</span>}
      <div className={clsx('flex max-w-[75%] flex-col', mine ? 'items-end' : 'items-start')}>
        {showName && <span className="mb-0.5 px-2 text-xs text-muted">{m.sender.username}</span>}
        <div className="relative" title={new Date(m.createdAt).toLocaleString('ko-KR')}>
          {content}
          {m.reactions.length > 0 && (
            <span className={clsx('absolute -bottom-3.5 flex rounded-full border border-bg bg-surface-2 px-1 text-xs', mine ? 'right-2' : 'left-2')}>
              {[...new Set(m.reactions.map((r) => r.emoji))].join('')}
              {m.reactions.length > 1 && <span className="ml-0.5 text-muted">{m.reactions.length}</span>}
            </span>
          )}
        </div>
      </div>

      {!m.unsent && (
        <div className="relative flex items-center gap-0.5 self-center opacity-0 group-hover:opacity-100 focus-within:opacity-100">
          <button aria-label="반응" className="rounded-full p-1 text-muted hover:bg-surface-2" onClick={() => setPicker((p) => !p)}>
            <Smile size={16} />
          </button>
          <button aria-label="답장" className="rounded-full p-1 text-muted hover:bg-surface-2" onClick={onReply}>
            <CornerUpLeft size={16} />
          </button>
          {mine && m.canEdit && (
            <button aria-label="수정 (15분 안)" className="rounded-full p-1 text-muted hover:bg-surface-2" onClick={onEdit}>
              <Pencil size={15} />
            </button>
          )}
          {mine && (
            <button aria-label="전송 취소" className="rounded-full p-1 text-muted hover:bg-surface-2" onClick={onUnsend}>
              <Undo2 size={16} />
            </button>
          )}
          <button aria-label="메시지 더 보기" className="rounded-full p-1 text-muted hover:bg-surface-2" onClick={() => setMore(true)}>
            <MoreHorizontal size={16} />
          </button>
          <MessageMore m={m} open={more} onClose={() => setMore(false)} onTranslated={setTranslated} />
          {picker && (
            <div className={clsx('absolute bottom-8 z-20 flex gap-1 rounded-full border border-border bg-bg px-2 py-1 shadow-lg', mine ? 'right-0' : 'left-0')}>
              {REACTIONS.map((e) => (
                <button
                  key={e}
                  aria-label={`반응 ${e}`}
                  className={clsx('rounded-full px-1 text-lg hover:scale-125', myReaction === e && 'bg-surface-2')}
                  onClick={() => {
                    setPicker(false)
                    onReact(myReaction === e ? null : e)
                  }}
                >
                  {e}
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function SharedPostCard({ post }: { post: NonNullable<MessageView['post']> }) {
  if (!post.available || !post.shortcode) {
    return <span className="rounded-2xl border border-border px-3 py-2 text-xs text-muted">게시물을 볼 수 없습니다</span>
  }
  return (
    <Link to={`/p/${post.shortcode}`} className="block w-[220px] overflow-hidden rounded-2xl bg-surface-2">
      {post.author && (
        <span className="flex items-center gap-2 px-3 py-2 text-xs font-semibold">
          <Avatar user={post.author} size={24} />
          {post.author.username}
        </span>
      )}
      {post.thumbUrl && <img src={post.thumbUrl} alt="" className="aspect-[4/5] w-full object-cover" />}
      {post.caption && <span className="line-clamp-2 px-3 py-2 text-xs">{post.caption}</span>}
    </Link>
  )
}

function RequestBar({ c, name }: { c: ConversationView; name: string }) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [pending, setPending] = useState(false)
  const act = (accept: boolean) => {
    setPending(true)
    ;(accept ? api.put<ConversationView>(`/api/direct/conversations/${c.id}/accept`) : api.delete<void>(`/api/direct/conversations/${c.id}`))
      .then(() => {
        qc.invalidateQueries({ queryKey: ['inbox'] })
        qc.invalidateQueries({ queryKey: ['dm-unread'] })
        if (accept) qc.invalidateQueries({ queryKey: ['conversation', c.id] })
        else navigate('/direct/inbox')
      }, (e: Error) => toast(e.message))
      .finally(() => setPending(false))
  }
  return (
    <div className="border-t border-border p-4 text-center text-sm">
      <p className="mb-1 font-semibold">{name}님이 메시지를 보내려고 합니다</p>
      <p className="mb-3 text-xs text-muted">수락하면 기본 메시지함으로 옮겨지고, 상대방은 회원님이 메시지를 읽었는지 알 수 있습니다.</p>
      <div className="flex gap-2">
        <button disabled={pending} onClick={() => act(false)} className="flex-1 rounded-lg bg-surface-2 py-2 font-semibold text-danger hover:bg-border">
          삭제
        </button>
        <button disabled={pending} onClick={() => act(true)} className="flex-1 rounded-lg bg-primary py-2 font-semibold text-white hover:bg-primary-hover">
          수락
        </button>
      </div>
    </div>
  )
}

function Composer({
  id,
  replyTo,
  editing,
  onCancel,
  onDone,
}: {
  id: number
  replyTo: MessageView | null
  editing: MessageView | null
  onCancel: () => void
  onDone: () => void
}) {
  const [body, setBody] = useState('')
  const [scheduling, setScheduling] = useState(false)
  const send = useSendMessage(id)
  const image = useSendImage(id)
  const actions = useMessageActions()
  const input = useRef<HTMLTextAreaElement>(null)
  const file = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (editing) setBody(editing.body ?? '')
    if (editing || replyTo) input.current?.focus()
  }, [editing, replyTo])

  const submit = (e?: React.FormEvent) => {
    e?.preventDefault()
    const text = body.trim()
    if (!text) return
    if (editing) {
      actions.edit(editing, text).then(
        () => {
          setBody('')
          onDone()
        },
        (err: Error) => toast(err.message),
      )
      return
    }
    send.mutate(
      { body: text, replyToId: replyTo?.id },
      {
        onSuccess: () => {
          setBody('')
          onDone()
        },
        onError: (err) => toast(err.message),
      },
    )
  }

  return (
    <div className="px-3 pt-1 pb-3 md:px-5">
      <ScheduledList id={id} />
      {(replyTo || editing) && (
        <div className="mb-1 flex items-center gap-2 rounded-t-xl bg-surface-2 px-3 py-1.5 text-xs">
          <span className="min-w-0 flex-1 truncate">
            {editing ? '메시지 수정 (보낸 후 15분 안에만 가능)' : `${replyTo!.sender.username}님에게 답장: ${replyTo!.body ?? '첨부'}`}
          </span>
          <button
            aria-label="취소"
            onClick={() => {
              setBody('')
              onCancel()
            }}
          >
            <X size={14} />
          </button>
        </div>
      )}
      <form onSubmit={submit} className="flex items-end gap-2 rounded-[22px] border border-border px-3 py-1.5">
        <textarea
          ref={input}
          rows={1}
          value={body}
          maxLength={1000}
          onChange={(e) => {
            setBody(e.target.value)
            if (!editing) sendTyping(id)
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault()
              submit()
            }
          }}
          placeholder="메시지 입력..."
          aria-label="메시지 입력"
          className="max-h-32 min-h-[28px] min-w-0 flex-1 resize-none bg-transparent py-1 text-sm outline-none placeholder:text-muted"
        />
        {body.trim() ? (
          <>
            {!editing && !replyTo && (
              <button type="button" aria-label="메시지 예약" title="메시지 예약" className="p-1 text-muted hover:text-text" onClick={() => setScheduling(true)}>
                <CalendarClock size={20} />
              </button>
            )}
            <button type="submit" disabled={send.isPending} className="py-1 text-sm font-semibold text-primary hover:text-text">
              {editing ? '수정' : '보내기'}
            </button>
          </>
        ) : (
          <>
            <input
              ref={file}
              type="file"
              accept="image/*"
              hidden
              onChange={(e) => {
                const f = e.target.files?.[0]
                if (f) image.mutate(f, { onError: (err) => toast(err.message) })
                e.target.value = ''
              }}
            />
            <button type="button" aria-label="사진 보내기" disabled={image.isPending} onClick={() => file.current?.click()} className="p-1">
              <ImageIcon size={22} />
            </button>
            <ViewOnceButton id={id} />
            <VoiceRecorder id={id} />
          </>
        )}
      </form>
      {scheduling && (
        <ScheduleModal
          id={id}
          body={body.trim()}
          onClose={() => setScheduling(false)}
          onDone={() => {
            setBody('')
            setScheduling(false)
          }}
        />
      )}
    </div>
  )
}

// ------------------------------------------------------------ 예약 메시지 (보낸 사람에게만 보임)

function ScheduleModal({ id, body, onClose, onDone }: { id: number; body: string; onClose: () => void; onDone: () => void }) {
  const qc = useQueryClient()
  const [at, setAt] = useState(() => laterInput(60))
  const save = useMutation({
    mutationFn: () =>
      api.post<ScheduledMessage>(`/api/direct/conversations/${id}/scheduled`, {
        body,
        scheduledAt: new Date(fromLocalInput(at)).toISOString(),
      }),
    onSuccess: (m) => {
      qc.invalidateQueries({ queryKey: ['scheduled-messages', id] })
      toast(`${scheduleLabel(m.scheduledAt)}에 보내도록 예약했습니다`)
      onDone()
    },
    onError: (e) => toast((e as Error).message),
  })
  return (
    <Modal open onClose={onClose} title="메시지 예약">
      <div className="space-y-3 p-4">
        <p className="line-clamp-3 rounded-2xl bg-surface-2 px-3 py-2 text-sm">{body}</p>
        <input
          type="datetime-local"
          className="input"
          value={at}
          min={laterInput(1)}
          onChange={(e) => setAt(e.target.value)}
          aria-label="보낼 날짜와 시각"
        />
        <p className="text-xs text-muted">최대 29일 뒤까지 예약할 수 있습니다. 예약한 메시지는 회원님에게만 보입니다.</p>
        <button className="btn btn-primary w-full" disabled={!at || save.isPending} onClick={() => save.mutate()}>
          예약
        </button>
      </div>
    </Modal>
  )
}

function ScheduledList({ id }: { id: number }) {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const { data } = useQuery({
    queryKey: ['scheduled-messages', id],
    queryFn: () => api.get<ScheduledMessage[]>(`/api/direct/conversations/${id}/scheduled`),
    refetchInterval: 15_000,
  })
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['scheduled-messages', id] })
    qc.invalidateQueries({ queryKey: ['messages', id] })
  }
  const cancel = useMutation({
    mutationFn: (sid: number) => api.delete(`/api/direct/scheduled/${sid}`),
    onSuccess: refresh,
    onError: (e) => toast((e as Error).message),
  })
  const sendNow = useMutation({
    mutationFn: (sid: number) => api.post(`/api/direct/scheduled/${sid}/send`),
    onSuccess: refresh,
    onError: (e) => toast((e as Error).message),
  })
  if (!data || data.length === 0) return null
  return (
    <div className="mb-2 rounded-xl border border-border text-sm">
      <button className="flex w-full items-center gap-2 px-3 py-2" onClick={() => setOpen((v) => !v)} aria-expanded={open}>
        <CalendarClock size={16} className="text-primary" />
        <span className="flex-1 text-left font-semibold">예약된 메시지 {data.length}개</span>
        <ChevronDown size={16} className={clsx('transition-transform', open && 'rotate-180')} />
      </button>
      {open && (
        <ul className="max-h-48 overflow-y-auto border-t border-border">
          {data.map((m) => (
            <li key={m.id} className="flex items-center gap-2 px-3 py-2">
              <span className="min-w-0 flex-1">
                <span className="block truncate">{m.body}</span>
                <span className="text-xs text-muted">{scheduleLabel(m.scheduledAt)}</span>
              </span>
              <button className="text-xs font-semibold text-primary" onClick={() => sendNow.mutate(m.id)}>
                지금 보내기
              </button>
              <button className="text-xs text-danger" onClick={() => cancel.mutate(m.id)}>
                삭제
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** 1:1 대화방 머리의 Blend 버튼: 있으면 열고, 없으면 초대 */
function BlendButton({ conversationId }: { conversationId: number }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data } = useQuery({
    queryKey: ['conv-blend', conversationId],
    queryFn: async () =>
      (await api.get<{ id: number; status: string } | undefined>(`/api/direct/conversations/${conversationId}/blend`)) ?? null,
  })
  const invite = useMutation({
    mutationFn: () => api.post<{ id: number }>(`/api/direct/conversations/${conversationId}/blend`),
    onSuccess: (b) => {
      void qc.invalidateQueries({ queryKey: ['conv-blend', conversationId] })
      void qc.invalidateQueries({ queryKey: ['messages', conversationId] })
      navigate(`/direct/blend/${b.id}`)
    },
    onError: (e) => toast((e as Error).message),
  })
  return (
    <button
      className="ml-auto flex shrink-0 items-center gap-1 rounded-full border border-border px-2.5 py-1 text-xs font-semibold whitespace-nowrap hover:bg-surface-2"
      title="두 분을 위한 릴스 피드"
      aria-label={data?.id ? 'Blend' : 'Blend 초대'}
      onClick={() => (data?.id ? navigate(`/direct/blend/${data.id}`) : invite.mutate())}
      disabled={invite.isPending}
    >
      <Blend size={14} className="text-primary" />
      {/* 좁은 화면에서는 이름이 잘리지 않도록 "초대"를 뺀다 */}
      Blend{!data?.id && <span className="hidden sm:inline"> 초대</span>}
    </button>
  )
}
