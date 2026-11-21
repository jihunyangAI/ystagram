import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useToggleLike } from '../../lib/posts'
import type { PostView } from '../../lib/types'
import { EditPostModal } from './EditPostModal'
import { CarouselDots, MediaCarousel } from './MediaCarousel'
import { Caption, CollabInviteBar, CommentInput, CommentItem, LikesLine, PostActions, PostHeader, PostMenu } from './PostParts'

/** 홈 피드의 게시물 카드: 머리 · 사진 · 액션 · 좋아요 · 캡션 · 댓글 미리보기 · 댓글 입력 */
export function PostCard({ post }: { post: PostView }) {
  const navigate = useNavigate()
  const like = useToggleLike()
  const [menu, setMenu] = useState(false)
  const [editing, setEditing] = useState(false)
  const [index, setIndex] = useState(0)

  return (
    <article className="border-b border-border pb-4 md:pb-5" aria-label={`${post.author.username}님의 게시물`}>
      <PostHeader post={post} onMenu={() => setMenu(true)} />
      <CollabInviteBar post={post} />
      <MediaCarousel
        media={post.media}
        tags={post.tags}
        className="md:rounded-md md:border md:border-border"
        onIndexChange={setIndex}
        // 더블탭은 좋아요만 한다 (이미 눌렀으면 그대로)
        onDoubleTap={() => !post.likedByMe && like.mutate({ post, like: true })}
      />
      <PostActions
        post={post}
        onComment={() => navigate(`/p/${post.shortcode}`)}
        center={<CarouselDots count={post.media.length} index={index} />}
      />
      <LikesLine post={post} />
      <Caption post={post} clamp />

      {post.commentCount > 0 && (
        <div className="space-y-0.5 px-3 pt-1 md:px-0">
          {post.commentCount > post.previewComments.length && (
            <Link to={`/p/${post.shortcode}`} className="block text-sm text-muted">
              댓글 {post.commentCount.toLocaleString('ko-KR')}개 모두 보기
            </Link>
          )}
          {post.previewComments.map((c) => (
            <CommentItem key={c.id} comment={c} shortcode={post.shortcode} compact />
          ))}
        </div>
      )}
      {!post.commentsDisabled && <CommentInput shortcode={post.shortcode} className="px-3 md:px-0" />}

      <PostMenu post={post} open={menu} onClose={() => setMenu(false)} onEdit={() => setEditing(true)} />
      {editing && <EditPostModal post={post} onClose={() => setEditing(false)} />}
    </article>
  )
}
