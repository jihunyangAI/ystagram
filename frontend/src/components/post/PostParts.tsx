import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { Heart, MessageCircle, MoreHorizontal, Pin, Repeat2, Send, Trash2, X } from 'lucide-react'
import clsx from 'clsx'
import { useMe } from '../../lib/auth'
import { nowMs } from '../../lib/clock'
import { formatCount, fullDate, timeAgo } from '../../lib/format'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { patchPost, useAddComment, useDeleteComment, useDeletePost, useToggleFollow, useToggleLike } from '../../lib/posts'
import { useCommentActions, useReplies, useToggleRepost } from '../../lib/social'
import type { CommentView, PostView } from '../../lib/types'
import { Avatar } from '../Avatar'
import { ConfirmModal, Modal, SheetButton } from '../Modal'
import { toast } from '../Toast'
import { UserBadges } from '../UserBadges'
import { ShareModal } from '../dm/ShareModal'
import { StoryRingAvatar } from '../story/StoryRingAvatar'
import { UserListModal } from '../user/UserListModal'
import { RichText } from './RichText'
import { SaveButton } from './SaveButton'

// ------------------------------------------------------------ 머리 (작성자)

export function PostHeader({ post, onMenu }: { post: PostView; onMenu: () => void }) {
  const toggle = useToggleFollow()
  const navigate = useNavigate()
  const showFollow = post.authorRelationship === 'NONE'
  const hasStory = post.authorStory !== 'NONE'
  return (
    <>
      {post.repost && (
        <p className="flex items-center gap-1.5 px-3 pt-2 text-xs text-muted md:px-0">
          <Repeat2 size={14} />
          <Link to={`/${post.repost.by.username}`} className="font-semibold text-text">
            {post.repost.by.username}
          </Link>
          님이 리포스트함{post.repost.note && <span className="truncate">· “{post.repost.note}”</span>}
        </p>
      )}
      <header className="flex items-center gap-3 px-3 py-2.5 md:px-0">
        <button
          className="shrink-0"
          aria-label={hasStory ? `${post.author.username}님의 스토리 보기` : `${post.author.username}님의 프로필`}
          onClick={() => navigate(hasStory ? `/stories/${post.author.username}` : `/${post.author.username}`)}
        >
          <StoryRingAvatar user={post.author} ring={post.authorStory} size={hasStory ? 38 : 32} />
        </button>
        <div className="min-w-0 flex-1 text-sm leading-tight">
          <div className="flex items-center gap-1">
            <Link to={`/${post.author.username}`} className="truncate font-semibold">
              {post.author.username}
            </Link>
            <UserBadges user={{ verified: post.author.verified, isPrivate: false }} size={13} />
            {post.collaborators.length > 0 && (
              <span className="truncate">
                <span className="text-muted"> 및 </span>
                <Link to={`/${post.collaborators[0].username}`} className="font-semibold">
                  {post.collaborators[0].username}
                </Link>
                {post.collaborators.length > 1 && <span className="text-muted"> 외 {post.collaborators.length - 1}명</span>}
              </span>
            )}
            <span className="text-muted">·</span>
            <Link to={`/p/${post.shortcode}`} className="shrink-0 text-muted" title={fullDate(post.createdAt)}>
              <time dateTime={post.createdAt}>{timeAgo(post.createdAt)}</time>
            </Link>
            {showFollow && (
              <>
                <span className="text-muted">·</span>
                <button
                  className="shrink-0 font-semibold text-primary hover:text-text"
                  disabled={toggle.isPending}
                  onClick={() => toggle.mutate({ username: post.author.username, follow: true })}
                >
                  팔로우
                </button>
              </>
            )}
          </div>
          {post.location && (
            <Link to={`/explore/locations/${post.location.id}`} className="block truncate text-xs hover:underline">
              {post.location.name}
            </Link>
          )}
        </div>
        <button aria-label="옵션 더 보기" className="p-1" onClick={onMenu}>
          <MoreHorizontal size={22} />
        </button>
      </header>
    </>
  )
}

/** 공동 작업 초대를 받은 게시물: 수락하면 내 프로필과 내 팔로워 피드에도 보인다 */
export function CollabInviteBar({ post }: { post: PostView }) {
  const qc = useQueryClient()
  const [pending, setPending] = useState(false)
  if (!post.collabInvite) return null
  const answer = (accept: boolean) => {
    setPending(true)
    ;(accept ? api.put<PostView>(`/api/posts/${post.shortcode}/collab`) : api.delete<PostView>(`/api/posts/${post.shortcode}/collab`))
      .then((p) => {
        patchPost(qc, post.shortcode, () => p)
        qc.invalidateQueries({ queryKey: ['grid'] })
        toast(accept ? '공동 작업을 수락했습니다. 내 프로필에도 표시됩니다' : '초대를 거절했습니다')
      }, (e: Error) => toast(e.message))
      .finally(() => setPending(false))
  }
  return (
    <div className="mx-3 mb-2 flex items-center gap-3 rounded-lg bg-primary-soft px-3 py-2 text-sm md:mx-0">
      <span className="min-w-0 flex-1">
        <b>{post.author.username}</b>님이 이 게시물의 공동 작업자로 초대했습니다
      </span>
      <button disabled={pending} onClick={() => answer(false)} className="text-muted">
        거절
      </button>
      <button disabled={pending} onClick={() => answer(true)} className="font-semibold text-primary">
        수락
      </button>
    </div>
  )
}

// ------------------------------------------------------------ 액션 줄

export function PostActions({
  post,
  onComment,
  center,
}: {
  post: PostView
  onComment: () => void
  center?: React.ReactNode
}) {
  const like = useToggleLike()
  const [bounce, setBounce] = useState(0)
  const [sharing, setSharing] = useState(false)
  const toggleLike = () => {
    setBounce((b) => b + 1)
    like.mutate({ post, like: !post.likedByMe })
  }
  return (
    <>
    {/* 캐러셀 점: 아이콘이 4개라 겹치지 않게 아이콘 줄 위에 따로 둔다 */}
    {center && post.media.length > 1 && <div className="flex h-3 justify-center pt-2">{center}</div>}
    <div className="relative flex items-center px-3 pt-2 md:px-0">
      <div className="flex flex-1 items-center gap-3">
        <button
          aria-label={post.likedByMe ? '좋아요 취소' : '좋아요'}
          aria-pressed={post.likedByMe}
          onClick={toggleLike}
          className="-ml-0.5 p-0.5 hover:opacity-60"
        >
          <Heart
            key={bounce}
            size={24}
            className={clsx(bounce > 0 && 'animate-like-bounce', post.likedByMe && 'fill-danger text-danger')}
          />
        </button>
        {!post.commentsDisabled && (
          <button aria-label="댓글 달기" onClick={onComment} className="p-0.5 hover:opacity-60">
            <MessageCircle size={24} className="-scale-x-100" />
          </button>
        )}
        {!post.mine && !post.author.isPrivate && <RepostButton post={post} />}
        <button aria-label="공유" className="p-0.5 hover:opacity-60" onClick={() => setSharing(true)}>
          <Send size={22} />
        </button>
      </div>
      <SaveButton post={post} />
      <ShareModal shortcode={post.shortcode} open={sharing} onClose={() => setSharing(false)} />
    </div>
    </>
  )
}

/** 리포스트: 처음 누르면 메모(선택)를 쓰는 창, 이미 했으면 바로 취소 */
function RepostButton({ post }: { post: PostView }) {
  const repost = useToggleRepost()
  const [open, setOpen] = useState(false)
  const [note, setNote] = useState('')
  const click = () => {
    if (post.repostedByMe) {
      repost.mutate(
        { shortcode: post.shortcode, repost: false },
        { onSuccess: () => toast('리포스트를 취소했습니다'), onError: (e) => toast(e.message) },
      )
    } else setOpen(true)
  }
  return (
    <>
      <button
        aria-label={post.repostedByMe ? '리포스트 취소' : '리포스트'}
        aria-pressed={post.repostedByMe}
        onClick={click}
        className={clsx('flex items-center gap-1 p-0.5 hover:opacity-60', post.repostedByMe && 'text-[#1fc25b]')}
      >
        <Repeat2 size={24} />
        {post.repostCount > 0 && <span className="text-sm font-semibold">{formatCount(post.repostCount)}</span>}
      </button>
      <Modal open={open} onClose={() => setOpen(false)} title="리포스트">
        <form
          className="flex flex-col gap-3 p-4"
          onSubmit={(e) => {
            e.preventDefault()
            repost.mutate(
              { shortcode: post.shortcode, repost: true, note: note.trim() || undefined },
              {
                onSuccess: () => {
                  setOpen(false)
                  setNote('')
                  toast('리포스트했습니다. 팔로워의 피드와 내 프로필 "리포스트" 탭에 보입니다')
                },
                onError: (err) => toast(err.message),
              },
            )
          }}
        >
          <p className="text-sm text-muted">
            <b className="text-text">{post.author.username}</b>님의 게시물을 팔로워에게 공유합니다.
          </p>
          <input
            value={note}
            onChange={(e) => setNote(e.target.value)}
            maxLength={60}
            placeholder="메모 추가 (선택, 60자)"
            aria-label="리포스트 메모"
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-muted"
          />
          <button
            type="submit"
            disabled={repost.isPending}
            className="rounded-lg bg-primary py-2 text-sm font-semibold text-white hover:bg-primary-hover disabled:opacity-50"
          >
            리포스트
          </button>
        </form>
      </Modal>
    </>
  )
}

export function copyLink(shortcode: string) {
  const url = `${window.location.origin}/p/${shortcode}`
  navigator.clipboard?.writeText(url).then(
    () => toast('링크를 복사했습니다'),
    () => toast(url),
  )
}

// ------------------------------------------------------------ 좋아요 줄

export function LikesLine({ post }: { post: PostView }) {
  const [open, setOpen] = useState(false)
  const list = (
    <UserListModal
      open={open}
      onClose={() => setOpen(false)}
      title="좋아요"
      url={`/api/posts/${post.shortcode}/likes`}
      queryKey={['likes', post.shortcode]}
      empty="아직 좋아요가 없습니다."
    />
  )

  // 좋아요 수를 숨긴 게시물: 다른 사람에게는 "OOO님 외 여러 명"만 보인다
  if (post.likeCount === null) {
    return (
      <div className="px-3 pt-1.5 text-sm md:px-0">
        {post.likedBy ? (
          <button onClick={() => setOpen(true)} className="text-left">
            <b>{post.likedBy.username}</b>님 <b>외 여러 명</b>이 좋아합니다
          </button>
        ) : (
          <button onClick={() => setOpen(true)} className="font-semibold">
            좋아요 누른 사람 보기
          </button>
        )}
        {list}
      </div>
    )
  }
  if (post.likeCount === 0) {
    return (
      <p className="px-3 pt-1.5 text-sm md:px-0">
        가장 먼저 <b>좋아요</b>를 눌러 보세요
      </p>
    )
  }
  return (
    <div className="flex items-center gap-1.5 px-3 pt-1.5 text-sm md:px-0">
      {post.likedBy && post.likeCount > 1 ? (
        <>
          <Avatar user={post.likedBy} size={18} />
          <button onClick={() => setOpen(true)} className="text-left">
            <b>{post.likedBy.username}</b>님 <b>외 {formatCount(post.likeCount - 1)}명</b>이 좋아합니다
          </button>
        </>
      ) : (
        <button onClick={() => setOpen(true)} className="font-semibold">
          좋아요 {formatCount(post.likeCount)}개
        </button>
      )}
      {list}
    </div>
  )
}

// ------------------------------------------------------------ 캡션

export function Caption({ post, clamp }: { post: PostView; clamp?: boolean }) {
  const [expanded, setExpanded] = useState(!clamp)
  if (!post.caption) return null
  const long = post.caption.length > 120 || post.caption.split('\n').length > 2
  return (
    <div className="px-3 pt-1 text-sm md:px-0">
      <p className={clsx('whitespace-pre-line break-words break-keep', !expanded && long && 'line-clamp-2')}>
        <Link to={`/${post.author.username}`} className="mr-1.5 font-semibold">
          {post.author.username}
        </Link>
        <RichText text={post.caption} />
      </p>
      {!expanded && long && (
        <button className="text-muted" onClick={() => setExpanded(true)}>
          더 보기
        </button>
      )}
    </div>
  )
}

// ------------------------------------------------------------ 댓글

export interface ReplyTarget {
  id: number
  username: string
}

/**
 * 댓글 한 개. compact는 피드 카드 미리보기(한 줄).
 * 상세에서는 좋아요 · 답글 달기 · 수정(15분) · 고정(게시물 주인) · 삭제 · "답글 보기(N개)"
 */
export function CommentItem({
  comment,
  shortcode,
  compact,
  onReply,
}: {
  comment: CommentView
  shortcode: string
  compact?: boolean
  onReply?: (target: ReplyTarget) => void
}) {
  const remove = useDeleteComment(shortcode)
  const actions = useCommentActions(shortcode)
  const { data: me } = useMe()
  const [confirm, setConfirm] = useState(false)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(comment.body)
  const [showReplies, setShowReplies] = useState(false)
  const replies = useReplies(comment.id, showReplies)
  const [, rerender] = useState(0)

  // 15분 제한이 지나면 "수정" 버튼을 숨기기 위해 1분마다 다시 그린다
  useEffect(() => {
    if (!comment.canEdit) return
    const t = setInterval(() => rerender((n) => n + 1), 60_000)
    return () => clearInterval(t)
  }, [comment.canEdit])

  if (compact) {
    return (
      <p className="text-sm break-words break-keep">
        <Link to={`/${comment.author.username}`} className="mr-1.5 font-semibold">
          {comment.author.username}
        </Link>
        <RichText text={comment.body} />
      </p>
    )
  }

  const toParent: ReplyTarget = { id: comment.parentId ?? comment.id, username: comment.author.username }

  return (
    <div className={clsx(comment.parentId && 'pl-11')}>
      <div className="group flex gap-3 py-2">
        <Link to={`/${comment.author.username}`} className="shrink-0">
          <Avatar user={comment.author} size={32} />
        </Link>
        <div className="min-w-0 flex-1 text-sm">
          {comment.pinned && (
            <p className="mb-0.5 flex items-center gap-1 text-xs text-muted">
              <Pin size={12} /> 고정됨
            </p>
          )}
          {comment.restricted && (
            <p className="mb-0.5 flex items-center gap-2 text-xs text-muted">
              {me?.user.id === comment.author.id ? '회원님과 게시물 주인에게만 보이는 댓글입니다' : '제한된 계정의 댓글 · 다른 사람에게 보이지 않음'}
              {me?.user.id !== comment.author.id && comment.canDelete && (
                <button
                  className="font-semibold text-primary"
                  onClick={() => actions.approve(comment).then(() => toast('댓글을 승인했습니다'), (e: Error) => toast(e.message))}
                >
                  승인
                </button>
              )}
            </p>
          )}
          {editing ? (
            <form
              className="flex flex-col gap-1"
              onSubmit={(e) => {
                e.preventDefault()
                if (!draft.trim()) return
                actions.edit(comment, draft.trim()).then(
                  () => setEditing(false),
                  (err: Error) => toast(err.message),
                )
              }}
            >
              <textarea
                autoFocus
                value={draft}
                maxLength={2200}
                onChange={(e) => setDraft(e.target.value)}
                rows={2}
                aria-label="댓글 수정"
                className="w-full resize-none rounded-md border border-border bg-surface px-2 py-1 outline-none"
              />
              <span className="flex gap-3 text-xs font-semibold">
                <button type="submit" className="text-primary">
                  저장
                </button>
                <button type="button" className="text-muted" onClick={() => setEditing(false)}>
                  취소
                </button>
              </span>
            </form>
          ) : (
            <p className="break-words break-keep whitespace-pre-line">
              <Link to={`/${comment.author.username}`} className="mr-1.5 font-semibold">
                {comment.author.username}
              </Link>
              <RichText text={comment.body} />
            </p>
          )}
          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
            <time dateTime={comment.createdAt} title={fullDate(comment.createdAt)}>
              {timeAgo(comment.createdAt)}
            </time>
            {comment.edited && <span>수정됨</span>}
            {comment.likeCount > 0 && <span className="font-semibold">좋아요 {formatCount(comment.likeCount)}개</span>}
            {onReply && (
              <button className="font-semibold hover:text-text" onClick={() => onReply(toParent)}>
                답글 달기
              </button>
            )}
            {comment.canEdit && !editing && editableNow(comment.createdAt) && (
              <button className="font-semibold hover:text-text" onClick={() => {
                setDraft(comment.body)
                setEditing(true)
              }}>
                수정
              </button>
            )}
            {comment.canPin && (
              <button
                className="font-semibold hover:text-text"
                onClick={() =>
                  actions.pin(comment, !comment.pinned).then(
                    () => toast(comment.pinned ? '고정을 해제했습니다' : '댓글을 고정했습니다'),
                    (e: Error) => toast(e.message),
                  )
                }
              >
                {comment.pinned ? '고정 해제' : '고정'}
              </button>
            )}
            {comment.canDelete && (
              <button
                className="flex items-center gap-0.5 opacity-100 hover:text-danger md:opacity-0 md:group-hover:opacity-100 md:focus:opacity-100"
                onClick={() => setConfirm(true)}
              >
                <Trash2 size={12} /> 삭제
              </button>
            )}
          </div>
        </div>
        <button
          aria-label={comment.likedByMe ? '댓글 좋아요 취소' : '댓글 좋아요'}
          aria-pressed={comment.likedByMe}
          className="self-center p-1"
          onClick={() => actions.like(comment).catch((e: Error) => toast(e.message))}
        >
          <Heart size={13} className={clsx(comment.likedByMe ? 'fill-danger text-danger' : 'text-muted')} />
        </button>
      </div>

      {comment.replyCount > 0 && !comment.parentId && (
        <div className="pl-11">
          <button className="flex items-center gap-3 py-1 text-xs font-semibold text-muted" onClick={() => setShowReplies((s) => !s)}>
            <span className="h-px w-6 bg-muted" />
            {showReplies ? '답글 숨기기' : `답글 보기(${comment.replyCount}개)`}
          </button>
          {showReplies && replies.isLoading && <p className="py-1 text-xs text-muted">불러오는 중…</p>}
        </div>
      )}
      {showReplies &&
        replies.data?.map((r) => <CommentItem key={r.id} comment={r} shortcode={shortcode} onReply={onReply} />)}

      <ConfirmModal
        open={confirm}
        onClose={() => setConfirm(false)}
        title="댓글을 삭제하시겠어요?"
        message={comment.parentId ? undefined : '이 댓글에 달린 답글도 함께 삭제됩니다.'}
        confirmLabel="삭제"
        pending={remove.isPending}
        onConfirm={() =>
          remove.mutate(comment.id, {
            onSuccess: () => {
              setConfirm(false)
              toast('댓글을 삭제했습니다')
            },
            onError: (e) => toast(e.message),
          })
        }
      />
    </div>
  )
}

/** 서버가 최종 판단하지만, 화면에서도 15분이 지나면 "수정"을 숨긴다 (Lab 시간 가속 포함) */
function editableNow(createdAt: string): boolean {
  return nowMs() - new Date(createdAt).getTime() < 15 * 60_000
}

export function CommentInput({
  shortcode,
  inputRef,
  className,
  replyTo,
  onCancelReply,
}: {
  shortcode: string
  inputRef?: React.Ref<HTMLInputElement>
  className?: string
  replyTo?: ReplyTarget | null
  onCancelReply?: () => void
}) {
  const [body, setBody] = useState('')
  const add = useAddComment(shortcode)
  const actions = useCommentActions(shortcode)
  const [sending, setSending] = useState(false)

  // 답글을 누르면 "@사용자 "로 시작
  useEffect(() => {
    if (replyTo) setBody(`@${replyTo.username} `)
  }, [replyTo])

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!body.trim() || add.isPending || sending) return
    if (replyTo) {
      setSending(true)
      actions
        .reply(replyTo.id, body)
        .then(() => {
          setBody('')
          onCancelReply?.()
        }, (err: Error) => toast(err.message))
        .finally(() => setSending(false))
      return
    }
    add.mutate(body, {
      onSuccess: () => setBody(''),
      onError: (err) => toast(err.message),
    })
  }
  return (
    <div className={className}>
      {replyTo && (
        <div className="flex items-center justify-between pt-2 text-xs text-muted">
          <span>
            <b className="text-text">{replyTo.username}</b>님에게 답글 남기는 중
          </span>
          <button
            aria-label="답글 취소"
            onClick={() => {
              setBody('')
              onCancelReply?.()
            }}
          >
            <X size={14} />
          </button>
        </div>
      )}
      <form onSubmit={submit} className="flex items-center gap-2">
        <input
          ref={inputRef}
          value={body}
          onChange={(e) => setBody(e.target.value)}
          maxLength={2200}
          placeholder={replyTo ? '답글 달기…' : '댓글 달기…'}
          aria-label={replyTo ? '답글 달기' : '댓글 달기'}
          className="min-w-0 flex-1 bg-transparent py-2 text-sm outline-none placeholder:text-muted"
        />
        {body.trim() && (
          <button
            type="submit"
            disabled={add.isPending || sending}
            className="text-sm font-semibold text-primary hover:text-text"
          >
            게시
          </button>
        )}
      </form>
    </div>
  )
}

// ------------------------------------------------------------ ⋯ 메뉴

export function PostMenu({
  post,
  open,
  onClose,
  onEdit,
}: {
  post: PostView
  open: boolean
  onClose: () => void
  onEdit?: () => void
}) {
  const navigate = useNavigate()
  const remove = useDeletePost()
  const unfollow = useToggleFollow()
  const [confirmDelete, setConfirmDelete] = useState(false)

  return (
    <>
      <Modal open={open && !confirmDelete} onClose={onClose} bare title="게시물 옵션">
        {post.mine ? (
          <>
            <SheetButton danger onClick={() => setConfirmDelete(true)}>
              삭제
            </SheetButton>
            {onEdit && (
              <SheetButton
                onClick={() => {
                  onClose()
                  onEdit()
                }}
              >
                수정
              </SheetButton>
            )}
          </>
        ) : (
          post.authorRelationship === 'FOLLOWING' && (
            <SheetButton
              danger
              onClick={() => {
                unfollow.mutate({ username: post.author.username, follow: false })
                onClose()
                toast(`@${post.author.username}님의 팔로우를 취소했습니다`)
              }}
            >
              팔로우 취소
            </SheetButton>
          )
        )}
        <SheetButton
          onClick={() => {
            onClose()
            navigate(`/p/${post.shortcode}`)
          }}
        >
          게시물로 이동
        </SheetButton>
        <SheetButton
          onClick={() => {
            onClose()
            copyLink(post.shortcode)
          }}
        >
          링크 복사
        </SheetButton>
        <SheetButton
          onClick={() => {
            onClose()
            navigate(`/${post.author.username}`)
          }}
        >
          이 계정 정보
        </SheetButton>
        <SheetButton onClick={onClose}>취소</SheetButton>
      </Modal>
      <ConfirmModal
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title="게시물을 삭제하시겠어요?"
        message="삭제한 게시물은 프로필과 피드에서 사라집니다."
        confirmLabel="삭제"
        pending={remove.isPending}
        onConfirm={() =>
          remove.mutate(post.shortcode, {
            onSuccess: () => {
              setConfirmDelete(false)
              onClose()
              toast('게시물을 삭제했습니다')
              if (window.location.pathname.startsWith('/p/')) navigate(`/${post.author.username}`)
            },
            onError: (e) => toast(e.message),
          })
        }
      />
    </>
  )
}
