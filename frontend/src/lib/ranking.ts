// 점수 오버레이 (Lab L07): 켜 두면 피드 카드·탐색 칸·릴스에 점수와 구성 요소가 보인다.
// 보는 사람 브라우저에만 저장하는 연구용 표시 설정.
import { useSyncExternalStore } from 'react'

const KEY = 'yangstagram:score-overlay'
const listeners = new Set<() => void>()

function read(): boolean {
  try {
    return localStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}

export function setScoreOverlay(on: boolean) {
  try {
    localStorage.setItem(KEY, on ? '1' : '0')
  } catch {
    // 저장소를 못 쓰면 이번 화면에서만
  }
  listeners.forEach((l) => l())
}

export function useScoreOverlay(): boolean {
  return useSyncExternalStore(
    (cb) => {
      listeners.add(cb)
      return () => listeners.delete(cb)
    },
    read,
    () => false,
  )
}

export const SOURCE_LABEL = { FOLLOWING: '팔로우', SUGGESTED: '추천', REPOST: '리포스트' } as const
export const SURFACE_LABEL = { FEED: '홈 피드', EXPLORE: '탐색', REELS: '릴스' } as const

export function fmtScore(n: number) {
  return (n >= 0 ? '' : '−') + Math.abs(n).toFixed(2)
}
