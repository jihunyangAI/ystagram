import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { CheckCircle2, ChevronRight } from 'lucide-react'
import { track, useImpression } from '../../lib/events'
import { useToggleLike } from '../../lib/posts'
import type { PostView } from '../../lib/types'
import { ScoreBadge } from '../ranking/ScoreBadge'
import { EditPostModal } from './EditPostModal'
import { CarouselDots, MediaCarousel } from './MediaCarousel'
import { Caption, CollabInviteBar, CommentInput, CommentItem, LikesLine, PostActions, PostHeader, PostMenu } from './PostParts'
import { PostCommerceBar } from '../shop/PostCommerceBar'
import { PostNoteBubbles } from './PostNotes'

/** 홈 피드의 게시물 카드: 머리 · 사진 · 액션 · 좋아요 · 캡션 · 댓글 미리보기 · 댓글 입력 */
export function PostCard({ post }: { post: PostView }) {
  const navigate = useNavigate()
  const like = useToggleLike()
  const [menu, setMenu] = useState(false)
  const [editing, setEditing] = useState(false)
  const [index, setIndex] = useState(0)
  const [hidden, setHidden] = useState<false | 'post' | 'ad'>(false)
  const ref = useImpression<HTMLElement>(post.shortcode, post.ad ? 'AD' : 'HOME')
  const suggested = post.ranking?.source === 'SUGGESTED'

  if (hidden === 'ad') {
    return (
      <div className="border-b border-border px-4 py-6 text-center text-sm">
        <p className="font-semibold">광고를 숨겼습니다</p>
        <p className="mt-1 text-muted">이 광고는 다시 보이지 않습니다.</p>
      </div>
    )
  }
  if (hidden) {
    return (
      <div className="border-b border-border px-4 py-6 text-center text-sm">
        <p className="font-semibold">게시물을 숨겼습니다</p>
        <p className="mt-1 text-muted">이 게시물은 다시 추천되지 않습니다.</p>
        <Link to="/accounts/algorithm" className="mt-2 inline-block font-semibold text-primary">
          관심 주제 관리
        </Link>
      </div>
    )
  }

  return (
    <>
      {post.ranking?.caughtUp && <CaughtUp />}
      <article ref={ref} className="border-b border-border pb-4 md:pb-5" aria-label={`${post.author.username}님의 게시물`}>
      {suggested && <p className="px-3 pt-2 text-xs font-semibold text-muted md:px-0">회원님을 위한 추천</p>}
      <PostHeader post={post} onMenu={() => setMenu(true)} />
      <CollabInviteBar post={post} />
      <div className="relative">
        <PostNoteBubbles post={post} />
        <MediaCarousel
          media={post.media}
          tags={post.tags}
          products={post.extras.products}
          className="md:rounded-md md:border md:border-border"
          onIndexChange={setIndex}
          // 더블탭은 좋아요만 한다 (이미 눌렀으면 그대로)
          onDoubleTap={() => !post.likedByMe && like.mutate({ post, like: true })}
        />
        <ScoreBadge ranking={post.ranking} className="absolute top-2 left-2" />
      </div>
      <PostCommerceBar post={post} />
      {post.ad && (
        <button
          className="flex w-full items-center justify-between border-b border-border px-3 py-3 text-left text-sm font-semibold text-primary hover:bg-surface-2 md:rounded-b-md"
          onClick={() => {
            track({ type: 'AD_CLICK', shortcode: post.shortcode, surface: 'AD' })
            navigate(`/${post.author.username}`)
          }}
        >
          <span className="min-w-0 truncate">
            {post.ad.cta}
            <span className="ml-2 font-normal text-muted">{post.ad.headline}</span>
          </span>
          <ChevronRight size={18} className="shrink-0" />
        </button>
      )}
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
      {!post.commentsDisabled && post.extras.canComment && <CommentInput shortcode={post.shortcode} className="px-3 md:px-0" />}

      <PostMenu
        post={post}
        open={menu}
        onClose={() => setMenu(false)}
        onEdit={() => setEditing(true)}
        onNotInterested={
          suggested
            ? () => {
                track({ type: 'NOT_INTERESTED', shortcode: post.shortcode, surface: 'HOME' })
                setHidden('post')
              }
            : undefined
        }
        onHideAd={() => {
          track({ type: 'AD_HIDE', shortcode: post.shortcode, surface: 'AD' })
          setHidden('ad')
        }}
      />
      {editing && <EditPostModal post={post} onClose={() => setEditing(false)} />}
      </article>
    </>
  )
}

/** 팔로우 게시물을 다 본 뒤 (분석 문서 §5 "모두 확인했습니다") */
function CaughtUp() {
  return (
    <div className="border-b border-border px-4 py-8 text-center">
      <CheckCircle2 size={56} strokeWidth={1.2} className="mx-auto text-primary" />
      <p className="mt-3 text-lg font-semibold">모두 확인했습니다</p>
      <p className="mt-1 text-sm text-muted">최근 3일 동안 새로 올라온 팔로우 게시물을 모두 확인했습니다.</p>
      <p className="mt-6 text-left text-sm font-semibold">추천 게시물</p>
    </div>
  )
}
