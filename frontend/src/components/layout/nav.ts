import { Clapperboard, Compass, Heart, House, Search, Send } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
}

/** 데스크톱 사이드바 메뉴 (만들기·프로필·더보기는 별도) */
export const SIDEBAR_ITEMS: NavItem[] = [
  { to: '/', label: '홈', icon: House },
  { to: '/explore/search', label: '검색', icon: Search },
  { to: '/explore', label: '탐색', icon: Compass },
  { to: '/reels', label: '릴스', icon: Clapperboard },
  { to: '/direct/inbox', label: '메시지', icon: Send },
  { to: '/notifications', label: '알림', icon: Heart },
]

/** 모바일 하단 탭 (2025년 개편 레이아웃: 홈 / 릴스 / DM / 검색 / 프로필) */
export const BOTTOM_ITEMS: NavItem[] = [
  { to: '/', label: '홈', icon: House },
  { to: '/reels', label: '릴스', icon: Clapperboard },
  { to: '/direct/inbox', label: '메시지', icon: Send },
  { to: '/explore', label: '검색', icon: Search },
]
