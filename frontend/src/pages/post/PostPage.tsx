import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ChevronLeft, Lock } from 'lucide-react'
import { Avatar } from '../../components/Avatar'
import { EditPostModal } from '../../components/post/EditPostModal'
import { CarouselDots, MediaCarousel } from '../../components/post/MediaCarousel'
import { CommentInput, CommentItem, LikesLine, PostActions, PostHeader, PostMenu } from '../../components/post/PostParts'
import type { ReplyTarget } from '../../components/post/PostParts'
import { RichText } from '../../components/post/RichText'
import { ApiError } from '../../lib/api'
import { fullDate, timeAgo } from '../../lib/format'
import { useComments, usePost, useToggleLike } from '../../lib/posts'
import type { PostView } from '../../lib/types'
import { NotFoundPage } from '../NotFoundPage'

/**
 * 게시물 상세 (/p/:shortcode).
 * 데스크톱: 왼쪽 사진, 오른쪽 캡션·댓글·액션 / 모바일: 위아래로
 */
export function PostPage() {
  const { shortcode = '' } = useParams()
  const navigate = useNavigate()
  const { data: post, isLoading, error } = usePost(shortcode)

  if (isLoading) return <p className="p-10 text-center text-sm text-muted">불러오는 중…</p>
  if (error instanceof ApiError && error.status === 404) return <NotFoundPage />
  if (error instanceof ApiError && error.code === 'PRIVATE_ACCOUNT') {
    return (
      <div className="flex flex-col items-center gap-3 px-6 py-20 text-center">
        <span className="flex h-16 w-16 items-center justify-center rounded-full border-2 border-text">
          <Lock size={28} strokeWidth={1.5} />
        </span>
        <p className="font-semibold">비공개 계정의 게시물입니다</p>
        <p className="text-sm text-muted">{error.message}</p>
      </div>
    )
  }
  if (error || !post) return <p className="p-10 text-center text-sm text-danger">게시물을 불러오지 못했습니다.</p>

  return (
    <div className="mx-auto w-full max-w-[935px] md:px-4 md:py-6">
      <header className="sticky top-0 z-20 flex h-11 items-center border-b border-border bg-bg px-2 md:hidden">
        <button aria-label="뒤로" onClick={() => navigate(-1)} className="p-1">
          <ChevronLeft size={28} />
        </button>
        <h1 className="flex-1 pr-9 text-center font-semibold">게시물</h1>
      </header>
      <PostDetail post={post} />
      <p className="mt-8 hidden text-center text-sm md:block">
        <Link to={`/${post.author.username}`} className="text-muted hover:text-text">
          {post.author.username}님의 게시물 더 보기
        </Link>
      </p>
    </div>
  )
}

function PostDetail({ post }: { post: PostView }) {
  const like = useToggleLike()
  const comments = useComments(post.shortcode)
  const [menu, setMenu] = useState(false)
  const [editing, setEditing] = useState(false)
  const [index, setIndex] = useState(0)
  const [replyTo, setReplyTo] = useState<ReplyTarget | null>(null)
  const input = useRef<HTMLInputElement>(null)
  const startReply = (target: ReplyTarget) => {
    setReplyTo(target)
    input.current?.focus()
  }
  const list = comments.data?.pages.flatMap((p) => p.items) ?? []

  useEffect(() => {
    document.title = `${post.author.fullName}님의 게시물 • Yangstagram`
    return () => {
      document.title = 'Yangstagram'
    }
  }, [post.author.fullName])

  return (
    <article className="flex flex-col md:flex-row md:overflow-hidden md:rounded md:border md:border-border">
      <div className="flex items-center bg-black md:w-[58%] md:shrink-0">
        <MediaCarousel
          media={post.media}
          fit
          onIndexChange={setIndex}
          onDoubleTap={() => !post.likedByMe && like.mutate({ post, like: true })}
        />
      </div>

      <div className="flex min-w-0 flex-1 flex-col md:max-h-[90dvh] md:min-h-[480px]">
        <div className="md:hidden">
          <PostHeader post={post} onMenu={() => setMenu(true)} />
        </div>
        <div className="hidden border-b border-border px-4 md:block">
          <PostHeader post={post} onMenu={() => setMenu(true)} />
        </div>

        {/* 캡션 + 댓글 */}
        <div className="order-last px-3 md:order-none md:flex-1 md:overflow-y-auto md:px-4">
          {post.caption && (
            <div className="flex gap-3 py-3">
              <Link to={`/${post.author.username}`} className="shrink-0">
                <Avatar user={post.author} size={32} />
              </Link>
              <div className="min-w-0 text-sm">
                <p className="break-words break-keep whitespace-pre-line">
                  <Link to={`/${post.author.username}`} className="mr-1.5 font-semibold">
                    {post.author.username}
                  </Link>
                  <RichText text={post.caption} />
                </p>
                <time className="mt-1 block text-xs text-muted" dateTime={post.createdAt}>
                  {timeAgo(post.createdAt)}
                </time>
              </div>
            </div>
          )}
          {post.commentsDisabled && <p className="py-6 text-center text-sm text-muted">댓글 기능이 해제되었습니다.</p>}
          {comments.isLoading && <p className="py-6 text-center text-sm text-muted">댓글을 불러오는 중…</p>}
          {!post.commentsDisabled && comments.isSuccess && list.length === 0 && (
            <div className="py-10 text-center">
              <p className="text-xl font-bold">아직 댓글이 없습니다.</p>
              <p className="mt-1 text-sm text-muted">댓글을 남겨 보세요.</p>
            </div>
          )}
          {list.map((c) => (
            <CommentItem
              key={c.id}
              comment={c}
              shortcode={post.shortcode}
              onReply={post.commentsDisabled ? undefined : startReply}
            />
          ))}
          {comments.hasNextPage && (
            <button
              className="my-2 w-full rounded-lg py-2 text-sm text-muted hover:bg-surface-2"
              disabled={comments.isFetchingNextPage}
              onClick={() => comments.fetchNextPage()}
            >
              {comments.isFetchingNextPage ? '불러오는 중…' : '댓글 더 보기'}
            </button>
          )}
        </div>

        {/* 액션 · 좋아요 · 시각 · 입력 */}
        <div className="md:border-t md:border-border md:px-4">
          <PostActions
            post={post}
            onComment={() => input.current?.focus()}
            center={<CarouselDots count={post.media.length} index={index} />}
          />
          <LikesLine post={post} />
          <time className="block px-3 pt-1 pb-2 text-xs text-muted md:px-0" dateTime={post.createdAt} title={fullDate(post.createdAt)}>
            {timeAgo(post.createdAt, 'long')}
          </time>
          {!post.commentsDisabled && (
            <CommentInput
              shortcode={post.shortcode}
              inputRef={input}
              replyTo={replyTo}
              onCancelReply={() => setReplyTo(null)}
              className="border-t border-border px-3 md:px-0"
            />
          )}
        </div>
      </div>

      <PostMenu post={post} open={menu} onClose={() => setMenu(false)} onEdit={() => setEditing(true)} />
      {editing && <EditPostModal post={post} onClose={() => setEditing(false)} />}
    </article>
  )
}
