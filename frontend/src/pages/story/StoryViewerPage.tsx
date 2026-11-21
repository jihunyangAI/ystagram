import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Eye, Heart, MoreHorizontal, Send, X } from 'lucide-react'
import clsx from 'clsx'
import { Avatar } from '../../components/Avatar'
import { ConfirmModal, Modal, SheetButton } from '../../components/Modal'
import { toast } from '../../components/Toast'
import { timeAgo } from '../../lib/format'
import { storyApi, useStoryViewers, useUserStories } from '../../lib/stories'
import type { StoryView } from '../../lib/types'

const DURATION = 5000

/**
 * 스토리 전체 화면 뷰어 (분석 문서 §8).
 * 진행 바 · 왼쪽/오른쪽 탭으로 이전/다음 · 길게 누르면 멈춤 · 끝나면 트레이의 다음 사람으로.
 */
export function StoryViewerPage() {
  const { username = '' } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const qc = useQueryClient()
  const queue = ((location.state as { queue?: string[] } | null)?.queue ?? []) as string[]
  const { data: stories, isLoading, isError } = useUserStories(username)

  const [index, setIndex] = useState(0)
  const [progress, setProgress] = useState(0)
  const [held, setHeld] = useState(false)
  const [typing, setTyping] = useState(false)
  const [modal, setModal] = useState<'menu' | 'delete' | 'viewers' | null>(null)
  const [liked, setLiked] = useState<Record<number, boolean>>({})
  const started = useRef(false)

  const paused = held || typing || modal !== null

  const close = useCallback(() => {
    qc.invalidateQueries({ queryKey: ['story-tray'] })
    qc.invalidateQueries({ queryKey: ['profile'] })
    qc.invalidateQueries({ queryKey: ['feed'] })
    if (location.key !== 'default') navigate(-1)
    else navigate('/')
  }, [qc, navigate, location.key])

  const nextUser = useCallback(() => {
    const at = queue.indexOf(username)
    const next = at >= 0 ? queue[at + 1] : undefined
    if (next) navigate(`/stories/${next}`, { replace: true, state: { queue } })
    else close()
  }, [queue, username, navigate, close])

  const prevUser = useCallback(() => {
    const at = queue.indexOf(username)
    const prev = at > 0 ? queue[at - 1] : undefined
    if (prev) navigate(`/stories/${prev}`, { replace: true, state: { queue } })
  }, [queue, username, navigate])

  // 사람이 바뀌면 처음 안 본 스토리부터
  useEffect(() => {
    started.current = false
    setIndex(0)
    setProgress(0)
  }, [username])
  useEffect(() => {
    if (stories && !started.current) {
      started.current = true
      const firstUnseen = stories.findIndex((s) => !s.seen && !s.mine)
      setIndex(firstUnseen > 0 ? firstUnseen : 0)
    }
  }, [stories])

  const story: StoryView | undefined = stories?.[index]

  const next = useCallback(() => {
    if (!stories) return
    setProgress(0)
    if (index < stories.length - 1) setIndex(index + 1)
    else nextUser()
  }, [stories, index, nextUser])

  const prev = useCallback(() => {
    setProgress(0)
    if (index > 0) setIndex(index - 1)
    else prevUser()
  }, [index, prevUser])

  // 본 것으로 기록 (내 스토리는 제외)
  useEffect(() => {
    if (story && !story.mine && !story.seen) void storyApi.view(story.id).catch(() => undefined)
  }, [story])

  // 진행 바 (멈췄다가 다시 시작하면 이어서)
  const progressRef = useRef(0)
  useEffect(() => {
    progressRef.current = progress
  }, [progress])
  useEffect(() => {
    if (!story || paused) return
    let last = performance.now()
    let frame = 0
    const tick = (t: number) => {
      const value = progressRef.current + (t - last) / DURATION
      last = t
      if (value >= 1) {
        progressRef.current = 0
        next()
        return
      }
      progressRef.current = value
      setProgress(value)
      frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [story, paused, next])

  // 키보드
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (typing || modal) return
      if (e.key === 'ArrowRight') next()
      if (e.key === 'ArrowLeft') prev()
      if (e.key === 'Escape') close()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [next, prev, close, typing, modal])

  // 화면을 누르고 있는 동안 멈춤, 짧게 누르면 좌/우 이동
  const pressStart = useRef(0)
  const onPointerDown = () => {
    pressStart.current = Date.now()
    setHeld(true)
  }
  const onPointerUp = (e: React.PointerEvent<HTMLDivElement>) => {
    setHeld(false)
    if (Date.now() - pressStart.current > 250) return
    const rect = e.currentTarget.getBoundingClientRect()
    if (e.clientX - rect.left < rect.width / 3) prev()
    else next()
  }

  const isLiked = story ? (liked[story.id] ?? story.liked) : false
  const toggleLike = () => {
    if (!story) return
    const value = !isLiked
    setLiked((l) => ({ ...l, [story.id]: value }))
    storyApi.like(story.id, value).catch((e: Error) => {
      setLiked((l) => ({ ...l, [story.id]: !value }))
      toast(e.message)
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#1a1a1a]" role="dialog" aria-label={`${username}님의 스토리`}>
      <button aria-label="닫기" onClick={close} className="absolute top-4 right-4 z-10 hidden p-2 text-white md:block">
        <X size={30} />
      </button>
      {queue.indexOf(username) > 0 && (
        <button aria-label="이전 사람" onClick={prevUser} className="absolute left-[calc(50%-260px)] z-10 hidden rounded-full bg-white/80 p-1 text-black md:block">
          <ChevronLeft size={22} />
        </button>
      )}
      {queue.indexOf(username) >= 0 && queue.indexOf(username) < queue.length - 1 && (
        <button aria-label="다음 사람" onClick={nextUser} className="absolute right-[calc(50%-260px)] z-10 hidden rounded-full bg-white/80 p-1 text-black md:block">
          <ChevronRight size={22} />
        </button>
      )}

      <div className="relative flex h-full w-full flex-col overflow-hidden bg-black md:h-[min(92dvh,820px)] md:w-auto md:aspect-[9/16] md:rounded-lg">
        {isLoading && <p className="m-auto text-sm text-white/70">불러오는 중…</p>}
        {(isError || (stories && stories.length === 0)) && (
          <div className="m-auto flex flex-col items-center gap-3 text-center text-sm text-white/80">
            <p>볼 수 있는 스토리가 없습니다.</p>
            <p className="text-white/50">24시간이 지나 사라졌을 수 있습니다.</p>
            <button onClick={close} className="mt-2 rounded-lg bg-white/15 px-4 py-2 text-white">
              닫기
            </button>
          </div>
        )}

        {story && stories && (
          <>
            <img
              key={story.id}
              src={story.mediaUrl}
              alt={story.altText ?? `${story.author.username}님의 스토리`}
              className="absolute inset-0 h-full w-full object-cover select-none"
              draggable={false}
            />
            <div className="absolute inset-x-0 top-0 h-28 bg-gradient-to-b from-black/50 to-transparent" />

            {/* 진행 바 */}
            <div className="absolute inset-x-2 top-2 z-10 flex gap-1">
              {stories.map((s, i) => (
                <div key={s.id} className="h-[2px] flex-1 overflow-hidden rounded-full bg-white/35">
                  <div
                    className="h-full bg-white"
                    style={{ width: `${i < index ? 100 : i === index ? progress * 100 : 0}%` }}
                  />
                </div>
              ))}
            </div>

            {/* 머리 */}
            <div className="absolute inset-x-0 top-4 z-10 flex items-center gap-2 px-3 text-white">
              <Link to={`/${story.author.username}`} className="flex min-w-0 items-center gap-2">
                <Avatar user={story.author} size={32} />
                <span className="truncate text-sm font-semibold">{story.author.username}</span>
              </Link>
              <span className="text-sm text-white/70">{timeAgo(story.createdAt)}</span>
              <span className="flex-1" />
              {story.mine && (
                <button aria-label="옵션" className="p-1" onClick={() => setModal('menu')}>
                  <MoreHorizontal size={22} />
                </button>
              )}
              <button aria-label="닫기" className="p-1 md:hidden" onClick={close}>
                <X size={26} />
              </button>
            </div>

            {/* 탭 영역 */}
            <div
              className="absolute inset-x-0 top-16 bottom-20 z-0 touch-none"
              onPointerDown={onPointerDown}
              onPointerUp={onPointerUp}
              onPointerLeave={() => setHeld(false)}
            />

            {/* 아래 */}
            <div className="absolute inset-x-0 bottom-0 z-10 bg-gradient-to-t from-black/50 to-transparent px-3 pt-8 pb-4">
              {story.mine ? (
                <button onClick={() => setModal('viewers')} className="flex items-center gap-2 text-sm font-semibold text-white">
                  <Eye size={20} /> 활동 {story.viewerCount ?? 0}
                </button>
              ) : (
                <StoryReply
                  story={story}
                  liked={isLiked}
                  onLike={toggleLike}
                  onFocusChange={setTyping}
                />
              )}
            </div>
          </>
        )}
      </div>

      {story?.mine && (
        <>
          <Modal open={modal === 'menu'} onClose={() => setModal(null)} bare title="스토리 옵션">
            <SheetButton danger onClick={() => setModal('delete')}>
              삭제
            </SheetButton>
            <SheetButton onClick={() => navigate('/archive/stories')}>아카이브 보기</SheetButton>
            <SheetButton onClick={() => setModal(null)}>취소</SheetButton>
          </Modal>
          <ConfirmModal
            open={modal === 'delete'}
            onClose={() => setModal(null)}
            title="스토리를 삭제하시겠어요?"
            message="삭제한 스토리는 아카이브에서도 사라집니다."
            confirmLabel="삭제"
            onConfirm={() =>
              storyApi.remove(story.id).then(
                () => {
                  toast('스토리를 삭제했습니다')
                  setModal(null)
                  qc.invalidateQueries({ queryKey: ['stories', username] })
                  if (stories && stories.length <= 1) close()
                  else setIndex((i) => Math.max(0, Math.min(i, stories!.length - 2)))
                },
                (e: Error) => toast(e.message),
              )
            }
          />
          <ViewersModal storyId={story.id} open={modal === 'viewers'} onClose={() => setModal(null)} />
        </>
      )}
    </div>
  )
}

function StoryReply({
  story,
  liked,
  onLike,
  onFocusChange,
}: {
  story: StoryView
  liked: boolean
  onLike: () => void
  onFocusChange: (focused: boolean) => void
}) {
  const [body, setBody] = useState('')
  const [sending, setSending] = useState(false)
  const send = (e: React.FormEvent) => {
    e.preventDefault()
    if (!body.trim() || sending) return
    setSending(true)
    storyApi.reply(story.id, body).then(
      () => {
        setBody('')
        toast('메시지를 보냈습니다')
        ;(document.activeElement as HTMLElement | null)?.blur()
      },
      (err: Error) => toast(err.message),
    ).finally(() => setSending(false))
  }
  return (
    <form onSubmit={send} className="flex items-center gap-3">
      <input
        value={body}
        onChange={(e) => setBody(e.target.value)}
        onFocus={() => onFocusChange(true)}
        onBlur={() => onFocusChange(false)}
        placeholder={`${story.author.username}님에게 답장하기...`}
        aria-label="스토리에 답장"
        maxLength={1000}
        className="min-w-0 flex-1 rounded-full border border-white/60 bg-transparent px-4 py-2.5 text-sm text-white outline-none placeholder:text-white/80"
      />
      <button type="button" aria-label={liked ? '좋아요 취소' : '좋아요'} aria-pressed={liked} onClick={onLike} className="text-white">
        <Heart size={26} className={clsx(liked && 'fill-danger text-danger')} />
      </button>
      <button type="submit" aria-label="보내기" disabled={!body.trim() || sending} className="text-white disabled:opacity-50">
        <Send size={24} />
      </button>
    </form>
  )
}

function ViewersModal({ storyId, open, onClose }: { storyId: number; open: boolean; onClose: () => void }) {
  const { data, isLoading } = useStoryViewers(storyId, open)
  return (
    <Modal open={open} onClose={onClose} title={`조회 ${data?.length ?? 0}명`}>
      <div className="max-h-[60vh] min-h-[160px] overflow-y-auto py-2">
        {isLoading && <p className="p-6 text-center text-sm text-muted">불러오는 중…</p>}
        {data?.length === 0 && <p className="p-6 text-center text-sm text-muted">아직 본 사람이 없습니다.</p>}
        {data?.map((v) => (
          <Link key={v.user.id} to={`/${v.user.username}`} className="flex items-center gap-3 px-4 py-2 hover:bg-surface-2">
            <Avatar user={v.user} size={40} />
            <span className="min-w-0 flex-1 text-sm leading-tight">
              <b className="block truncate">{v.user.username}</b>
              <span className="text-muted">{timeAgo(v.viewedAt, 'long')}</span>
            </span>
            {v.liked && <Heart size={18} className="fill-danger text-danger" aria-label="좋아요" />}
          </Link>
        ))}
      </div>
    </Modal>
  )
}
