import { useState } from 'react'
import { CircleFadingPlus, Clapperboard, Grid2x2Plus, Heart, Radio, SquarePlus } from 'lucide-react'
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
      <MobileCreateMenu />
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

/** 만들기(+) → 게시물 / 스토리 / 릴스 / 라이브 */
function MobileCreateMenu() {
  const [open, setOpen] = useState(false)
  const items = [
    { to: '/create', label: '게시물', icon: Grid2x2Plus },
    { to: '/create/story', label: '스토리', icon: CircleFadingPlus },
    { to: '/create/reel', label: '릴스', icon: Clapperboard },
    { to: '/live/new', label: '라이브', icon: Radio },
  ]
  return (
    <div className="relative">
      <button aria-label="만들기" aria-expanded={open} className="p-1" onClick={() => setOpen((o) => !o)}>
        <SquarePlus size={26} strokeWidth={open ? 2.6 : 1.8} />
      </button>
      {open && (
        <>
          <button aria-label="닫기" className="fixed inset-0 z-30 cursor-default" onClick={() => setOpen(false)} />
          <div role="menu" className="absolute top-full left-0 z-40 mt-1 w-44 overflow-hidden rounded-xl border border-border bg-bg py-1 shadow-lg">
            {items.map(({ to, label, icon: Icon }) => (
              <Link
                key={to}
                role="menuitem"
                to={to}
                onClick={() => setOpen(false)}
                className="flex items-center justify-between px-4 py-3 text-sm hover:bg-surface-2"
              >
                {label} <Icon size={18} />
              </Link>
            ))}
          </div>
        </>
      )}
    </div>
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
