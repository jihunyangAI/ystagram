// 행동 이벤트 수집 (기획서 §9.3).
// 노출·시청 시간·프로필 방문·건너뛰기·관심 없음을 모아 두었다가 3초마다(또는 페이지를 떠날 때) 한 번에 보낸다.
// 좋아요·댓글·저장·공유·팔로우는 서버가 직접 기록한다.
import { useEffect, useRef } from 'react'
import { api } from './api'

export type EventType = 'IMPRESSION' | 'VIEW_TIME' | 'PROFILE_VISIT' | 'SKIP' | 'NOT_INTERESTED'
export type Surface = 'HOME' | 'EXPLORE' | 'REELS' | 'PROFILE' | 'POST' | 'SEARCH'

interface ClientEvent {
  type: EventType
  shortcode?: string
  username?: string
  surface?: Surface
  valueMs?: number
}

const queue: ClientEvent[] = []
let timer: number | null = null

export function track(event: ClientEvent) {
  queue.push(event)
  if (queue.length >= 50) {
    flush()
    return
  }
  if (timer == null) timer = window.setTimeout(flush, 3000)
}

export function flush() {
  if (timer != null) {
    window.clearTimeout(timer)
    timer = null
  }
  if (queue.length === 0) return
  const events = queue.splice(0, 100)
  // 로그 수집 실패는 화면에 알리지 않는다
  api.post('/api/events', { events }).catch(() => undefined)
}

if (typeof window !== 'undefined') {
  window.addEventListener('pagehide', flush)
  document.addEventListener('visibilitychange', () => document.visibilityState === 'hidden' && flush())
}

/**
 * 요소가 화면에 절반 이상 1초 넘게 보이면 노출 1회를 기록한다 (마운트당 한 번).
 */
export function useImpression<T extends Element>(shortcode: string | undefined, surface: Surface) {
  const ref = useRef<T>(null)
  useEffect(() => {
    const el = ref.current
    if (!el || !shortcode) return
    let shown: number | null = null
    let done = false
    const obs = new IntersectionObserver(
      ([entry]) => {
        if (done) return
        if (entry.isIntersecting) {
          shown = window.setTimeout(() => {
            done = true
            track({ type: 'IMPRESSION', shortcode, surface })
            obs.disconnect()
          }, 1000)
        } else if (shown != null) {
          window.clearTimeout(shown)
          shown = null
        }
      },
      { threshold: 0.5 },
    )
    obs.observe(el)
    return () => {
      obs.disconnect()
      if (shown != null) window.clearTimeout(shown)
    }
  }, [shortcode, surface])
  return ref
}
