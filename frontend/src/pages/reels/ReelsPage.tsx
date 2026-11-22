import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import {
  Clapperboard,
  Gift,
  Heart,
  MessageCircle,
  MoreHorizontal,
  Music2,
  Pause,
  Plus,
  Repeat2,
  Send,
  Star,
  Volume2,
  VolumeX,
} from 'lucide-react'
import clsx from 'clsx'
import { Avatar } from '../../components/Avatar'
import { Modal, SheetButton } from '../../components/Modal'
import { toast } from '../../components/Toast'
import { ScoreBadge } from '../../components/ranking/ScoreBadge'
import { WhyModal } from '../../components/ranking/WhyModal'
import { ShareModal } from '../../components/dm/ShareModal'
import { RichText } from '../../components/post/RichText'
import { SaveButton } from '../../components/post/SaveButton'
import { GiftModal } from '../../components/post/GiftModal'
import { CommentsSheet } from '../../components/post/CommentsSheet'
import { AudioChip } from '../../components/music/MusicPicker'
import { GridTiles } from '../../components/post/Grid'
import { FollowButton } from '../../components/user/FollowButton'
import { api } from '../../lib/api'
import { track } from '../../lib/events'
import { formatCount } from '../../lib/format'
import { useToggleLike } from '../../lib/posts'
import { useToggleRepost } from '../../lib/social'
import type { GridItem, PostView, RankedPage } from '../../lib/types'

type Tab = 'recommended' | 'friends'

/** 이 화면에서 이미 조회로 센 릴스 (새로 고침 전까지) */
const counted = new Set<string>()

/**
 * 릴스 (분석 문서 §9): 세로 전체 화면을 위아래로 넘긴다. 보이는 영상만 재생, 기본은 소리 끔.
 * 추천: 팔로우하지 않는 사람 것도 (3분 이하), 친구: 팔로우하는 사람이 올렸거나 좋아요한 것.
 */
export function ReelsPage() {
  const { shortcode } = useParams()
  const [tab, setTab] = useState<Tab>('recommended')
  const [muted, setMuted] = useState(true)

  const first = useQuery({
    queryKey: ['post', shortcode],
    queryFn: () => api.get<PostView>(`/api/posts/${shortcode}`),
    enabled: !!shortcode,
  })
  const feed = useInfiniteQuery({
    queryKey: ['feed', 'reels', tab],
    queryFn: ({ pageParam }) =>
      api.get<RankedPage<PostView>>(
        `/api/reels?tab=${tab}&page=${pageParam.page}${pageParam.since ? `&since=${pageParam.since}` : ''}`,
      ),
    initialPageParam: { page: 0, since: 0 },
    getNextPageParam: (last) => (last.nextPage == null ? undefined : { page: last.nextPage, since: last.since }),
  })

  const list: PostView[] = []
  const seen = new Set<string>()
  const add = (p: PostView) => {
    if (!seen.has(p.shortcode) && p.type === 'REEL') {
      seen.add(p.shortcode)
      list.push(p)
    }
  }
  if (first.data) add(first.data)
  feed.data?.pages.forEach((pg) => pg.items.forEach(add))

  const sentinel = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const el = sentinel.current
    if (!el) return
    const io = new IntersectionObserver(
      ([e]) => {
        if (e.isIntersecting && feed.hasNextPage && !feed.isFetchingNextPage) void feed.fetchNextPage()
      },
      { rootMargin: '1200px 0px' },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [feed])

  return (
    <div className="relative h-[calc(100dvh-60px-32px)] bg-black md:h-[calc(100dvh-32px)] md:bg-bg">
      <div
        className="absolute top-3 left-1/2 z-20 flex -translate-x-1/2 gap-4 text-sm font-semibold md:top-6 md:left-6 md:translate-x-0 md:flex-col md:gap-2"
        role="tablist"
      >
        {(
          [
            ['recommended', '추천'],
            ['friends', '친구'],
          ] as [Tab, string][]
        ).map(([key, label]) => (
          <button
            key={key}
            role="tab"
            aria-selected={tab === key}
            onClick={() => setTab(key)}
            className={clsx('px-1 pb-0.5 drop-shadow', tab === key ? 'border-b-2 border-white text-white md:border-text md:text-text' : 'text-white/60 md:text-muted')}
          >
            {label}
          </button>
        ))}
      </div>
      <Link
        to="/create/reel"
        aria-label="릴스 만들기"
        className="absolute top-2.5 right-3 z-20 rounded-full p-1.5 text-white md:right-6 md:text-text"
      >
        <Plus size={24} />
      </Link>

      <div className="no-scrollbar h-full snap-y snap-mandatory overflow-y-auto">
        {feed.isLoading && !first.data && <p className="pt-24 text-center text-sm text-white/70 md:text-muted">불러오는 중…</p>}
        {feed.isSuccess && list.length === 0 && (
          <div className="flex h-full flex-col items-center justify-center gap-3 text-center text-white/80 md:text-muted">
            <Clapperboard size={44} strokeWidth={1.3} />
            <p className="text-sm">{tab === 'friends' ? '팔로우하는 사람의 릴스가 아직 없습니다.' : '볼 수 있는 릴스가 없습니다.'}</p>
          </div>
        )}
        {list.map((p) => (
          <ReelItem key={p.shortcode} post={p} muted={muted} onToggleMute={() => setMuted((m) => !m)} />
        ))}
        <div ref={sentinel} className="h-1" />
      </div>
    </div>
  )
}

function ReelItem({ post, muted, onToggleMute }: { post: PostView; muted: boolean; onToggleMute: () => void }) {
  const navigate = useNavigate()
  const video = useRef<HTMLVideoElement>(null)
  const box = useRef<HTMLDivElement>(null)
  const like = useToggleLike()
  const repost = useToggleRepost()
  const [paused, setPaused] = useState(false)
  const [heart, setHeart] = useState(0)
  const [sharing, setSharing] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const [more, setMore] = useState(false)
  const [why, setWhy] = useState(false)
  const [hidden, setHidden] = useState(false)
  const [gift, setGift] = useState(false)
  const [remixes, setRemixes] = useState(false)
  const [commenting, setCommenting] = useState(false)
  const original = useRef<HTMLVideoElement>(null)
  const remixOf = post.extras.remixOf?.available ? post.extras.remixOf : null
  const lastTap = useRef(0)
  const tapTimer = useRef<number | undefined>(undefined)
  const media = post.media[0]

  // 화면 가운데에 오면 재생 + 조회 1회 기록, 벗어나면 멈춤.
  // 이벤트: 들어올 때 노출, 나갈 때 시청 시간(2초 미만이면 건너뛰기도)
  useEffect(() => {
    const el = box.current
    const v = video.current
    if (!el || !v) return
    let enteredAt: number | null = null
    const leave = () => {
      if (enteredAt == null) return
      const watched = Math.round(performance.now() - enteredAt)
      enteredAt = null
      track({ type: 'VIEW_TIME', shortcode: post.shortcode, surface: 'REELS', valueMs: watched })
      if (watched < 2000) track({ type: 'SKIP', shortcode: post.shortcode, surface: 'REELS' })
    }
    const io = new IntersectionObserver(
      ([e]) => {
        if (e.intersectionRatio > 0.7) {
          v.currentTime = 0
          void v.play().catch(() => undefined)
          if (original.current) {
            original.current.currentTime = 0
            void original.current.play().catch(() => undefined)
          }
          setPaused(false)
          if (enteredAt == null) {
            enteredAt = performance.now()
            track({ type: 'IMPRESSION', shortcode: post.shortcode, surface: 'REELS' })
          }
          if (!counted.has(post.shortcode)) {
            counted.add(post.shortcode)
            void api.post(`/api/posts/${post.shortcode}/view`).catch(() => undefined)
          }
        } else {
          v.pause()
          original.current?.pause()
          leave()
        }
      },
      { threshold: [0, 0.7, 1] },
    )
    io.observe(el)
    return () => {
      io.disconnect()
      leave()
    }
  }, [post.shortcode])

  if (hidden) {
    return (
      <div className="flex h-full snap-start snap-always flex-col items-center justify-center gap-2 text-center text-sm text-white/80 md:text-muted">
        <p className="font-semibold">릴스를 숨겼습니다</p>
        <p>이 릴스는 다시 추천되지 않습니다.</p>
      </div>
    )
  }

  // 한 번 탭 = 멈춤/재생, 두 번 탭 = 좋아요
  const onTap = () => {
    const now = Date.now()
    if (now - lastTap.current < 300) {
      window.clearTimeout(tapTimer.current)
      lastTap.current = 0
      setHeart((h) => h + 1)
      if (!post.likedByMe) like.mutate({ post, like: true })
      return
    }
    lastTap.current = now
    tapTimer.current = window.setTimeout(() => {
      const v = video.current
      if (!v) return
      if (v.paused) {
        void v.play()
        void original.current?.play().catch(() => undefined)
        setPaused(false)
      } else {
        v.pause()
        original.current?.pause()
        setPaused(true)
      }
    }, 300)
  }

  return (
    <div ref={box} className="flex h-full snap-start snap-always items-center justify-center md:py-4">
      <div className="relative flex h-full items-end gap-4">
        <div className="relative h-full w-[100vw] overflow-hidden bg-black md:aspect-[9/16] md:w-auto md:rounded-lg">
          {/* 리믹스: 왼쪽 원본(소리 끔) + 오른쪽 리믹스 */}
          <div className="flex h-full w-full">
            {remixOf?.videoUrl && (
              <video
                ref={original}
                src={remixOf.videoUrl}
                poster={remixOf.thumbUrl ?? undefined}
                muted
                loop
                playsInline
                preload="metadata"
                onClick={onTap}
                aria-label="원본 릴스"
                className="h-full w-1/2 border-r border-black object-cover"
              />
            )}
            {media && (
              <video
                ref={video}
                src={media.url}
                poster={media.thumbUrl}
                muted={muted}
                loop
                playsInline
                preload="metadata"
                onClick={onTap}
                aria-label={`${post.author.username}님의 릴스`}
                className={clsx('h-full object-cover', remixOf?.videoUrl ? 'w-1/2' : 'w-full')}
              />
            )}
          </div>
          {paused && (
            <span className="pointer-events-none absolute inset-0 flex items-center justify-center">
              <Pause size={56} className="fill-white/80 text-white/80" />
            </span>
          )}
          {heart > 0 && (
            <span key={heart} className="pointer-events-none absolute inset-0 flex items-center justify-center">
              <Heart size={96} className="animate-heart-pop fill-white text-white drop-shadow-lg" />
            </span>
          )}
          <button
            aria-label={muted ? '소리 켜기' : '소리 끄기'}
            onClick={onToggleMute}
            className="absolute top-12 right-3 rounded-full bg-black/50 p-2 text-white md:top-3"
          >
            {muted ? <VolumeX size={16} /> : <Volume2 size={16} />}
          </button>
          <ScoreBadge ranking={post.ranking} className="absolute top-12 left-3 md:top-3" />

          {/* 아래: 작성자·캡션 */}
          <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/60 to-transparent p-4 pr-16 text-white md:pr-4">
            <div className="flex items-center gap-2">
              <Link to={`/${post.author.username}`} className="flex min-w-0 items-center gap-2">
                <Avatar user={post.author} size={32} />
                <b className="truncate text-sm">{post.author.username}</b>
              </Link>
              {post.authorRelationship === 'NONE' && (
                <FollowButton
                  user={post.author}
                  relationship={post.authorRelationship}
                  className="rounded-lg border border-white/70 bg-transparent px-2.5 py-0.5 text-xs text-white"
                />
              )}
            </div>
            {post.caption && (
              <p
                className={clsx('mt-2 text-sm break-words break-keep', !expanded && 'line-clamp-2')}
                onClick={() => setExpanded((v) => !v)}
              >
                <RichText text={post.caption} />
              </p>
            )}
            {remixOf?.shortcode && remixOf.author && (
              <Link to={`/reels/${remixOf.shortcode}`} className="mt-2 flex items-center gap-1.5 text-xs font-semibold">
                <Repeat2 size={13} /> {remixOf.author.username}님의 릴스를 리믹스함
              </Link>
            )}
            {post.extras.remixOf && !post.extras.remixOf.available && (
              <p className="mt-2 text-xs text-white/70">원본 릴스를 볼 수 없어요</p>
            )}
            {post.extras.audio ? (
              <AudioChip audio={post.extras.audio} className="mt-2 text-xs text-white/90" />
            ) : (
              <p className="mt-2 flex items-center gap-1.5 text-xs text-white/80">
                <Music2 size={12} /> {post.author.username} · 원본 오디오
              </p>
            )}
            {post.extras.remixCount > 0 && (
              <button className="mt-1 text-xs text-white/80 underline" onClick={() => setRemixes(true)}>
                리믹스 {formatCount(post.extras.remixCount)}개 보기
              </button>
            )}
          </div>

          {/* 오른쪽 세로 버튼 (모바일은 영상 위) */}
          <ActionColumn
            className="absolute right-2 bottom-20 text-white md:hidden"
            post={post}
            onLike={() => like.mutate({ post, like: !post.likedByMe })}
            onComment={() => setCommenting(true)}
            onRepost={() =>
              repost.mutate(
                { shortcode: post.shortcode, repost: !post.repostedByMe },
                { onSuccess: (r) => toast(r.reposted ? '리포스트했습니다' : '리포스트를 취소했습니다'), onError: (e) => toast(e.message) },
              )
            }
            onShare={() => setSharing(true)}
            onMore={() => setMore(true)}
            onGift={() => setGift(true)}
          />
        </div>
        <ActionColumn
          className="hidden pb-2 md:flex"
          post={post}
          onLike={() => like.mutate({ post, like: !post.likedByMe })}
          onComment={() => setCommenting(true)}
          onRepost={() =>
            repost.mutate(
              { shortcode: post.shortcode, repost: !post.repostedByMe },
              { onSuccess: (r) => toast(r.reposted ? '리포스트했습니다' : '리포스트를 취소했습니다'), onError: (e) => toast(e.message) },
            )
          }
          onShare={() => setSharing(true)}
          onMore={() => setMore(true)}
          onGift={() => setGift(true)}
        />
      </div>
      <GiftModal post={post} open={gift} onClose={() => setGift(false)} />
      <CommentsSheet post={post} open={commenting} onClose={() => setCommenting(false)} />
      {remixes && <RemixesModal shortcode={post.shortcode} onClose={() => setRemixes(false)} />}
      <ShareModal shortcode={post.shortcode} open={sharing} onClose={() => setSharing(false)} />
      <Modal open={more} onClose={() => setMore(false)} bare title="릴스 옵션">
        {post.ranking?.source === 'SUGGESTED' && (
          <SheetButton
            onClick={() => {
              setMore(false)
              track({ type: 'NOT_INTERESTED', shortcode: post.shortcode, surface: 'REELS' })
              setHidden(true)
            }}
          >
            관심 없음
          </SheetButton>
        )}
        {post.ranking && (
          <SheetButton
            onClick={() => {
              setMore(false)
              setWhy(true)
            }}
          >
            이 릴스가 표시되는 이유
          </SheetButton>
        )}
        {post.mine && (
          <SheetButton
            onClick={() => {
              setMore(false)
              navigate(`/p/${post.shortcode}/insights`)
            }}
          >
            인사이트 보기
          </SheetButton>
        )}
        {post.extras.allowRemix && (
          <SheetButton
            onClick={() => {
              setMore(false)
              navigate(`/reels/${post.shortcode}/remix`)
            }}
          >
            이 릴스 리믹스하기
          </SheetButton>
        )}
        {post.mine && (
          <SheetButton
            onClick={() => {
              setMore(false)
              void api
                .put(`/api/posts/${post.shortcode}/remix-setting`, { allow: !post.extras.allowRemix })
                .then(() => toast(post.extras.allowRemix ? '리믹스를 껐습니다' : '리믹스를 허용했습니다'))
                .catch((e) => toast((e as Error).message))
            }}
          >
            {post.extras.allowRemix ? '리믹스 끄기' : '리믹스 허용'}
          </SheetButton>
        )}
        <SheetButton
          onClick={() => {
            setMore(false)
            navigate(`/p/${post.shortcode}`)
          }}
        >
          게시물로 이동
        </SheetButton>
        <SheetButton onClick={() => setMore(false)}>취소</SheetButton>
      </Modal>
      {post.ranking && <WhyModal ranking={post.ranking} open={why} onClose={() => setWhy(false)} />}
    </div>
  )
}

function ActionColumn({
  post,
  className,
  onLike,
  onComment,
  onRepost,
  onShare,
  onMore,
  onGift,
}: {
  post: PostView
  className?: string
  onLike: () => void
  onComment: () => void
  onRepost: () => void
  onShare: () => void
  onMore: () => void
  onGift: () => void
}) {
  const canRepost = !post.mine && !post.author.isPrivate
  return (
    <div className={clsx('flex flex-col items-center gap-5 text-xs font-semibold', className)}>
      <button aria-label={post.likedByMe ? '좋아요 취소' : '좋아요'} aria-pressed={post.likedByMe} onClick={onLike} className="flex flex-col items-center gap-1">
        <Heart size={28} className={clsx(post.likedByMe && 'fill-danger text-danger')} />
        {post.likeCount !== null && formatCount(post.likeCount)}
      </button>
      {!post.commentsDisabled && (
        <button aria-label="댓글" onClick={onComment} className="flex flex-col items-center gap-1">
          <MessageCircle size={28} className="-scale-x-100" />
          {formatCount(post.commentCount)}
        </button>
      )}
      {canRepost && (
        <button aria-label="리포스트" aria-pressed={post.repostedByMe} onClick={onRepost} className="flex flex-col items-center gap-1">
          <Repeat2 size={28} className={clsx(post.repostedByMe && 'text-[#1fc25b]')} />
          {post.repostCount > 0 && formatCount(post.repostCount)}
        </button>
      )}
      <button aria-label="공유" onClick={onShare}>
        <Send size={26} />
      </button>
      {post.mine ? (
        post.extras.giftStars != null && post.extras.giftStars > 0 && (
          <span className="flex flex-col items-center gap-1" title="받은 별 (가짜 선물)">
            <Star size={26} className="fill-amber-400 text-amber-400" />
            {formatCount(post.extras.giftStars)}
          </span>
        )
      ) : (
        <button aria-label="선물 보내기" onClick={onGift}>
          <Gift size={26} />
        </button>
      )}
      <SaveButton post={post} />
      <button aria-label="옵션 더 보기" onClick={onMore}>
        <MoreHorizontal size={24} />
      </button>
      <span className="text-[11px] font-normal opacity-80">조회 {formatCount(post.viewCount)}</span>
    </div>
  )
}

/** 이 릴스를 리믹스한 릴스 */
function RemixesModal({ shortcode, onClose }: { shortcode: string; onClose: () => void }) {
  const { data } = useQuery({
    queryKey: ['remixes', shortcode],
    queryFn: () => api.get<GridItem[]>(`/api/posts/${shortcode}/remixes`),
  })
  return (
    <Modal open onClose={onClose} title="리믹스">
      <div className="max-h-[70vh] overflow-y-auto p-1">
        {data && data.length === 0 && <p className="p-6 text-center text-sm text-muted">아직 리믹스가 없어요.</p>}
        {data && <GridTiles items={data} variant="reels" />}
      </div>
    </Modal>
  )
}
