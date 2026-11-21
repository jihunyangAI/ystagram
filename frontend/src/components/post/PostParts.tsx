import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { Bookmark, Heart, MessageCircle, MoreHorizontal, Send, Trash2 } from 'lucide-react'
import clsx from 'clsx'
import { formatCount, fullDate, timeAgo } from '../../lib/format'
import { useAddComment, useDeleteComment, useDeletePost, useToggleFollow, useToggleLike } from '../../lib/posts'
import type { CommentView, PostView } from '../../lib/types'
import { Avatar } from '../Avatar'
import { ConfirmModal, Modal, SheetButton } from '../Modal'
import { toast } from '../Toast'
import { UserBadges } from '../UserBadges'
import { UserListModal } from '../user/UserListModal'
import { RichText } from './RichText'

// ------------------------------------------------------------ 머리 (작성자)

export function PostHeader({ post, onMenu }: { post: PostView; onMenu: () => void }) {
  const toggle = useToggleFollow()
  const showFollow = post.authorRelationship === 'NONE'
  return (
    <header className="flex items-center gap-3 px-3 py-2.5 md:px-0">
      <Link to={`/${post.author.username}`} className="shrink-0">
        <Avatar user={post.author} size={32} />
      </Link>
      <div className="min-w-0 flex-1 text-sm leading-tight">
        <div className="flex items-center gap-1">
          <Link to={`/${post.author.username}`} className="truncate font-semibold">
            {post.author.username}
          </Link>
          <UserBadges user={{ verified: post.author.verified, isPrivate: false }} size={13} />
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
        {post.location && <p className="truncate text-xs">{post.location.name}</p>}
      </div>
      <button aria-label="옵션 더 보기" className="p-1" onClick={onMenu}>
        <MoreHorizontal size={22} />
      </button>
    </header>
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
  const toggleLike = () => {
    setBounce((b) => b + 1)
    like.mutate({ post, like: !post.likedByMe })
  }
  return (
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
        <button
          aria-label="공유"
          className="p-0.5 hover:opacity-60"
          onClick={() => copyLink(post.shortcode)}
        >
          <Send size={22} />
        </button>
      </div>
      {center && <div className="absolute left-1/2 -translate-x-1/2">{center}</div>}
      <button aria-label="저장" className="p-0.5 hover:opacity-60" onClick={() => toast('저장·컬렉션은 M2 단계에서 추가됩니다')}>
        <Bookmark size={24} />
      </button>
    </div>
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

export function CommentItem({
  comment,
  shortcode,
  compact,
}: {
  comment: CommentView
  shortcode: string
  compact?: boolean
}) {
  const remove = useDeleteComment(shortcode)
  const [confirm, setConfirm] = useState(false)

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
  return (
    <div className={clsx('group flex gap-3 py-2', comment.parentId && 'pl-11')}>
      <Link to={`/${comment.author.username}`} className="shrink-0">
        <Avatar user={comment.author} size={32} />
      </Link>
      <div className="min-w-0 flex-1 text-sm">
        <p className="break-words break-keep whitespace-pre-line">
          <Link to={`/${comment.author.username}`} className="mr-1.5 font-semibold">
            {comment.author.username}
          </Link>
          <RichText text={comment.body} />
        </p>
        <div className="mt-1 flex items-center gap-3 text-xs text-muted">
          <time dateTime={comment.createdAt} title={fullDate(comment.createdAt)}>
            {timeAgo(comment.createdAt)}
          </time>
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

export function CommentInput({
  shortcode,
  inputRef,
  className,
}: {
  shortcode: string
  inputRef?: React.Ref<HTMLInputElement>
  className?: string
}) {
  const [body, setBody] = useState('')
  const add = useAddComment(shortcode)
  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!body.trim() || add.isPending) return
    add.mutate(body, {
      onSuccess: () => setBody(''),
      onError: (err) => toast(err.message),
    })
  }
  return (
    <form onSubmit={submit} className={clsx('flex items-center gap-2', className)}>
      <input
        ref={inputRef}
        value={body}
        onChange={(e) => setBody(e.target.value)}
        maxLength={2200}
        placeholder="댓글 달기…"
        aria-label="댓글 달기"
        className="min-w-0 flex-1 bg-transparent py-2 text-sm outline-none placeholder:text-muted"
      />
      {body.trim() && (
        <button type="submit" disabled={add.isPending} className="text-sm font-semibold text-primary hover:text-text">
          게시
        </button>
      )}
    </form>
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
