import { Heart, SquarePlus } from 'lucide-react'
import { Link, NavLink, useLocation } from 'react-router'
import clsx from 'clsx'
import type { Me } from '../../lib/types'
import { Avatar } from '../Avatar'
import { Logo } from '../Logo'
import { DmBadge, NotificationDot } from './Badges'
import { useFlagValues } from '../../lib/flags'
import { BOTTOM_ITEMS, CLASSIC_BOTTOM_ITEMS } from './nav'

/** 모바일 상단 바: 만들기(+) · 로고 · 알림 (홈 화면에서만 표시) */
export function MobileTopBar() {
  const { pathname } = useLocation()
  if (pathname !== '/') return null
  return (
    <header className="sticky top-0 z-30 flex h-[52px] items-center justify-between border-b border-border bg-bg px-3 md:hidden">
      <Link to="/create" aria-label="만들기" className="p-1">
        <SquarePlus size={26} strokeWidth={1.8} />
      </Link>
      <Link to="/" aria-label="홈">
        <Logo />
      </Link>
      <Link to="/notifications" aria-label="알림" className="relative p-1">
        <Heart size={26} strokeWidth={1.8} />
        <NotificationDot className="top-1 right-1" />
      </Link>
    </header>
  )
}

/** 모바일 하단 탭: 홈 / 릴스 / DM / 검색 / 프로필 (예전 배치 플래그면 홈 / 검색 / 만들기 / 릴스 / 프로필) */
export function BottomNav({ me }: { me: Me }) {
  const items = useFlagValues().navLayout === 'CLASSIC' ? CLASSIC_BOTTOM_ITEMS : BOTTOM_ITEMS
  return (
    <nav className="fixed inset-x-0 bottom-0 z-30 flex h-[50px] items-center justify-around border-t border-border bg-bg pb-[env(safe-area-inset-bottom)] md:hidden">
      {items.map(({ to, label, icon: Icon }) => (
        <NavLink key={to} to={to} end aria-label={label} className="flex flex-1 justify-center py-2">
          {({ isActive }) => (
            <span className="relative">
              <Icon size={26} strokeWidth={isActive ? 2.6 : 1.8} />
              {to === '/direct/inbox' && <DmBadge className="-top-1.5 -right-2.5" />}
            </span>
          )}
        </NavLink>
      ))}
      <NavLink to={`/${me.user.username}`} aria-label="프로필" className="flex flex-1 justify-center py-2">
        {({ isActive }) => (
          <span className={clsx('rounded-full', isActive && 'ring-2 ring-text ring-offset-1 ring-offset-bg')}>
            <Avatar user={me.user} size={26} />
          </span>
        )}
      </NavLink>
    </nav>
  )
}
