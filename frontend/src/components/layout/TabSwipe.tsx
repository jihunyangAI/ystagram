import { useMemo } from 'react'
import { useFlagValues } from '../../lib/flags'
import { BOTTOM_ITEMS, CLASSIC_BOTTOM_ITEMS } from './nav'
import { useTabSwipe } from './useTabSwipe'

/** 하단 탭 순서대로 좌우 스와이프 (만들기는 건너뜀, 마지막은 내 프로필) */
export function TabSwipe({ username }: { username: string }) {
  const layout = useFlagValues().navLayout
  const tabs = useMemo(
    () => [...(layout === 'CLASSIC' ? CLASSIC_BOTTOM_ITEMS : BOTTOM_ITEMS).map((i) => i.to).filter((to) => to !== '/create'), `/${username}`],
    [layout, username],
  )
  useTabSwipe(tabs)
  return null
}
