import { useRef, useState } from 'react'
import { Modal } from '../Modal'
import { useComments } from '../../lib/posts'
import type { PostView } from '../../lib/types'
import { CommentInput, CommentItem } from './PostParts'
import type { ReplyTarget } from './PostParts'

/**
 * 릴스의 댓글 창. 모바일은 아래에서 올라오는 시트, 넓은 화면은 가운데 창.
 * 게시물 화면으로 넘어가지 않아서 보던 릴스가 그대로 남는다.
 */
export function CommentsSheet({ post, open, onClose }: { post: PostView; open: boolean; onClose: () => void }) {
  const comments = useComments(post.shortcode, open)
  const [replyTo, setReplyTo] = useState<ReplyTarget | null>(null)
  const input = useRef<HTMLInputElement>(null)
  const list = comments.data?.pages.flatMap((p) => p.items) ?? []

  return (
    <Modal open={open} onClose={onClose} title="댓글" sheet className="h-[75dvh] md:h-[70dvh] md:max-w-[480px]">
      <div className="min-h-0 flex-1 overflow-y-auto px-4">
        {comments.isLoading && <p className="py-6 text-center text-sm text-muted">댓글을 불러오는 중…</p>}
        {comments.isSuccess && list.length === 0 && (
          <div className="py-10 text-center">
            <p className="text-xl font-bold">아직 댓글이 없습니다.</p>
            <p className="mt-1 text-sm text-muted">대화를 시작하세요.</p>
          </div>
        )}
        {list.map((c) => (
          <CommentItem
            key={c.id}
            comment={c}
            shortcode={post.shortcode}
            onReply={(t) => {
              setReplyTo(t)
              input.current?.focus()
            }}
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
      {!post.extras.canComment ? (
        <p className="shrink-0 border-t border-border px-4 py-3 text-center text-xs text-muted">
          게시물 주인이 댓글을 달 수 있는 사람을 제한했습니다.
        </p>
      ) : (
      <CommentInput
        shortcode={post.shortcode}
        inputRef={input}
        replyTo={replyTo}
        onCancelReply={() => setReplyTo(null)}
        className="shrink-0 border-t border-border px-4 pb-1"
      />
      )}
    </Modal>
  )
}
