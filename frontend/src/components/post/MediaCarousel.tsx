import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { ChevronLeft, ChevronRight, Heart, UserRound, Volume2, VolumeX } from 'lucide-react'
import clsx from 'clsx'
import type { MediaView, TagView } from '../../lib/types'

interface Props {
  media: MediaView[]
  /** 두 번 탭(클릭)하면 호출 — 좋아요 */
  onDoubleTap?: () => void
  /** 상세 화면처럼 높이를 화면에 맞출 때 */
  fit?: boolean
  className?: string
  /** 지금 보고 있는 사진 번호가 바뀔 때 (점 표시용) */
  onIndexChange?: (index: number) => void
  /** 사진 속 사람 태그: 한 번 탭하면 이름표가 보인다 */
  tags?: TagView[]
}

/** 게시물 비율: 첫 장 기준, 인스타그램 허용 범위(세로 3:4 ~ 가로 1.91:1)로 자른다 */
function ratioOf(m: MediaView): number {
  return Math.min(1.91, Math.max(0.75, m.width / m.height))
}

/**
 * 사진 여러 장 넘기기. 손가락으로 밀거나 화살표·점으로 이동, 두 번 탭하면 하트가 뜬다.
 */
export function MediaCarousel({ media, onDoubleTap, fit, className, onIndexChange, tags = [] }: Props) {
  const [showTags, setShowTags] = useState(false)
  const [muted, setMuted] = useState(true)
  const single = useRef<number | undefined>(undefined)
  const track = useRef<HTMLDivElement>(null)
  const [index, setIndex] = useState(0)
  const [hearts, setHearts] = useState<number[]>([])
  const lastTap = useRef(0)
  const ratio = media.length > 0 ? ratioOf(media[0]) : 0.8

  const go = (i: number) => {
    const el = track.current
    if (!el) return
    el.scrollTo({ left: i * el.clientWidth, behavior: 'smooth' })
  }

  const onScroll = () => {
    const el = track.current
    if (!el) return
    const i = Math.round(el.scrollLeft / el.clientWidth)
    if (i !== index) {
      setIndex(i)
      onIndexChange?.(i)
    }
  }

  const onClick = () => {
    const now = Date.now()
    if (now - lastTap.current < 300) {
      lastTap.current = 0
      window.clearTimeout(single.current)
      const id = now
      setHearts((h) => [...h, id])
      window.setTimeout(() => setHearts((h) => h.filter((x) => x !== id)), 950)
      onDoubleTap?.()
    } else {
      lastTap.current = now
      // 두 번째 탭이 오지 않으면 한 번 탭 = 사람 태그 보이기/숨기기
      single.current = window.setTimeout(() => setShowTags((v) => !v), 300)
    }
  }
  const currentTags = tags.filter((t) => t.mediaIndex === index)

  return (
    <div
      className={clsx('relative w-full select-none overflow-hidden bg-black', className)}
      style={fit ? { aspectRatio: String(ratio), maxHeight: '90dvh' } : { aspectRatio: String(ratio) }}
      aria-roledescription="carousel"
    >
      <div
        ref={track}
        onScroll={onScroll}
        onClick={onClick}
        className="no-scrollbar flex h-full w-full snap-x snap-mandatory overflow-x-auto"
      >
        {media.map((m, i) => (
          <div key={i} className="relative h-full w-full shrink-0 snap-center snap-always">
            {m.type === 'VIDEO' ? (
              <AutoVideo media={m} muted={muted} />
            ) : (
              <img
                src={m.url}
                alt={m.altText ?? `사진 ${i + 1}/${media.length}`}
                draggable={false}
                loading={i === 0 ? 'eager' : 'lazy'}
                className="h-full w-full object-cover"
              />
            )}
            {showTags &&
              tags
                .filter((t) => t.mediaIndex === i)
                .map((t) => (
                  <Link
                    key={t.user.id}
                    to={`/${t.user.username}`}
                    onClick={(e) => e.stopPropagation()}
                    className="absolute -translate-x-1/2 rounded-md bg-black/75 px-2 py-1 text-xs font-semibold whitespace-nowrap text-white"
                    style={{ left: `${t.x * 100}%`, top: `${t.y * 100}%` }}
                  >
                    {t.user.username}
                  </Link>
                ))}
          </div>
        ))}
      </div>

      {currentTags.length > 0 && (
        <button
          aria-label={showTags ? '태그 숨기기' : `태그된 사람 ${currentTags.length}명 보기`}
          onClick={() => setShowTags((v) => !v)}
          className="absolute bottom-3 left-3 rounded-full bg-black/70 p-1.5 text-white"
        >
          <UserRound size={14} className="fill-white" />
        </button>
      )}
      {media[index]?.type === 'VIDEO' && (
        <button
          aria-label={muted ? '소리 켜기' : '소리 끄기'}
          onClick={() => setMuted((m) => !m)}
          className="absolute right-3 bottom-3 rounded-full bg-black/70 p-1.5 text-white"
        >
          {muted ? <VolumeX size={14} /> : <Volume2 size={14} />}
        </button>
      )}

      {/* 더블탭 하트 */}
      {hearts.map((id) => (
        <div key={id} className="pointer-events-none absolute inset-0 flex items-center justify-center">
          <Heart size={96} className="animate-heart-pop fill-white text-white drop-shadow-lg" />
        </div>
      ))}

      {media.length > 1 && (
        <>
          <span className="absolute top-3 right-3 rounded-full bg-black/60 px-2 py-0.5 text-xs font-medium text-white">
            {index + 1}/{media.length}
          </span>
          {index > 0 && (
            <button
              aria-label="이전 사진"
              onClick={() => go(index - 1)}
              className="absolute top-1/2 left-2 hidden -translate-y-1/2 rounded-full bg-white/80 p-1 text-black shadow md:block"
            >
              <ChevronLeft size={18} />
            </button>
          )}
          {index < media.length - 1 && (
            <button
              aria-label="다음 사진"
              onClick={() => go(index + 1)}
              className="absolute top-1/2 right-2 hidden -translate-y-1/2 rounded-full bg-white/80 p-1 text-black shadow md:block"
            >
              <ChevronRight size={18} />
            </button>
          )}
        </>
      )}
    </div>
  )
}

/** 사진 아래 점 표시 (카드의 액션 줄 가운데) */
export function CarouselDots({ count, index }: { count: number; index: number }) {
  if (count < 2) return null
  return (
    <div className="flex gap-1" aria-hidden>
      {Array.from({ length: count }, (_, i) => (
        <span key={i} className={clsx('h-1.5 w-1.5 rounded-full', i === index ? 'bg-primary' : 'bg-border')} />
      ))}
    </div>
  )
}

/** 화면에 보이면 자동 재생, 벗어나면 멈추는 영상 (피드·상세의 릴스) */
export function AutoVideo({ media, muted, className }: { media: MediaView; muted: boolean; className?: string }) {
  const ref = useRef<HTMLVideoElement>(null)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const io = new IntersectionObserver(
      ([e]) => {
        if (e.isIntersecting && e.intersectionRatio > 0.6) void el.play().catch(() => undefined)
        else el.pause()
      },
      { threshold: [0, 0.6, 1] },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [])
  return (
    <video
      ref={ref}
      src={media.url}
      poster={media.thumbUrl}
      muted={muted}
      loop
      playsInline
      preload="metadata"
      aria-label={media.altText ?? '영상'}
      className={clsx('h-full w-full object-cover', className)}
    />
  )
}
