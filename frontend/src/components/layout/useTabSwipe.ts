import { useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router'

/**
 * 모바일에서 하단 탭 사이를 좌우로 밀어서 오간다 (기획서 §4.2 "탭 간 좌우 스와이프").
 * 탭의 첫 화면(홈·릴스·메시지함·탐색·내 프로필)에서만 작동하고, 사진 넘기기·스토리 트레이처럼 가로로 움직이는 곳
 * (data-no-swipe, 가로 스크롤 영역, 입력칸)에서 시작한 손짓은 무시한다.
 */
/** 마지막으로 탭을 바꾼 시각: 화면이 바뀌며 리스너를 다시 붙이는 사이 같은 손짓이 두 번 처리되지 않게 */
let lastSwipe = 0

export function useTabSwipe(tabs: string[]) {
  const navigate = useNavigate()
  const { pathname } = useLocation()

  useEffect(() => {
    const index = tabs.indexOf(pathname)
    if (index < 0) return
    let start: { x: number; y: number; t: number } | null = null

    const blocked = (target: EventTarget | null) => {
      let el = target instanceof Element ? target : null
      while (el && el !== document.body) {
        if (el.matches('[data-no-swipe], input, textarea, select, [contenteditable="true"], [role="dialog"]')) return true
        const style = getComputedStyle(el)
        if ((style.overflowX === 'auto' || style.overflowX === 'scroll') && el.scrollWidth > el.clientWidth + 4) return true
        el = el.parentElement
      }
      return false
    }
    const onStart = (e: TouchEvent) => {
      if (window.innerWidth >= 768 || e.touches.length !== 1 || blocked(e.target)) {
        start = null
        return
      }
      start = { x: e.touches[0].clientX, y: e.touches[0].clientY, t: Date.now() }
    }
    const onEnd = (e: TouchEvent) => {
      if (!start) return
      const t = e.changedTouches[0]
      const dx = t.clientX - start.x
      const dy = t.clientY - start.y
      const quick = Date.now() - start.t < 700
      start = null
      if (!quick || Math.abs(dx) < 70 || Math.abs(dx) < Math.abs(dy) * 2) return
      if (Date.now() - lastSwipe < 400) return
      lastSwipe = Date.now()
      const next = index + (dx < 0 ? 1 : -1)
      if (next >= 0 && next < tabs.length) navigate(tabs[next])
    }
    document.addEventListener('touchstart', onStart, { passive: true })
    document.addEventListener('touchend', onEnd, { passive: true })
    return () => {
      document.removeEventListener('touchstart', onStart)
      document.removeEventListener('touchend', onEnd)
    }
  }, [tabs, pathname, navigate])
}
