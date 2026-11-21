import { useEffect, useRef, useState } from 'react'
import { CircleFadingPlus, Clapperboard, FlaskConical, Grid2x2Plus, LogOut, Menu, SquarePlus } from 'lucide-react'
import { Link, NavLink, useNavigate } from 'react-router'
import clsx from 'clsx'
import { useLogout } from '../../lib/auth'
import type { Me } from '../../lib/types'
import { Avatar } from '../Avatar'
import { Logo, LogoMark } from '../Logo'
import { DmBadge, NotificationDot } from './Badges'
import { SIDEBAR_ITEMS } from './nav'

const itemClass = (active: boolean) =>
  clsx(
    'flex w-full items-center gap-4 rounded-lg p-3 text-[15px] transition-colors hover:bg-surface-2',
    active && 'font-bold',
  )

/** 데스크톱 왼쪽 사이드바. md: 아이콘만, xl: 아이콘 + 글자 */
export function Sidebar({ me }: { me: Me }) {
  const navigate = useNavigate()
  const logout = useLogout()

  return (
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-[72px] flex-col border-r border-border bg-bg px-3 pt-6 pb-5 md:flex xl:w-[244px]">
      <Link to="/" className="mb-6 flex h-10 items-center px-2" aria-label="홈">
        <span className="xl:hidden">
          <LogoMark />
        </span>
        <Logo className="hidden xl:inline-flex" />
      </Link>

      <nav className="flex flex-1 flex-col gap-1">
        {SIDEBAR_ITEMS.map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} end className={({ isActive }) => itemClass(isActive)} title={label}>
            {({ isActive }) => (
              <>
                <span className="relative">
                  <Icon size={24} strokeWidth={isActive ? 2.6 : 1.8} />
                  {to === '/direct/inbox' && <DmBadge className="-top-2 -right-2.5" />}
                  {to === '/notifications' && <NotificationDot className="-top-0.5 -right-0.5" />}
                </span>
                <span className="hidden xl:inline">{label}</span>
              </>
            )}
          </NavLink>
        ))}
        <CreateMenu />
        <NavLink to={`/${me.user.username}`} className={({ isActive }) => itemClass(isActive)} title="프로필">
          <Avatar user={me.user} size={24} />
          <span className="hidden xl:inline">프로필</span>
        </NavLink>
      </nav>

      <div className="flex flex-col gap-1">
        <NavLink to="/accounts/settings" className={({ isActive }) => itemClass(isActive)} title="더 보기">
          <Menu size={24} strokeWidth={1.8} />
          <span className="hidden xl:inline">더 보기</span>
        </NavLink>
        <NavLink to="/lab" className={({ isActive }) => itemClass(isActive)} title="Lab">
          <FlaskConical size={24} strokeWidth={1.8} className="text-primary" />
          <span className="hidden xl:inline">Lab</span>
        </NavLink>
        <button
          className={itemClass(false)}
          title="로그아웃"
          disabled={logout.isPending}
          onClick={() => logout.mutate(undefined, { onSuccess: () => navigate('/accounts/login') })}
        >
          <LogOut size={24} strokeWidth={1.8} />
          <span className="hidden xl:inline">로그아웃</span>
        </button>
      </div>
    </aside>
  )
}

/** 만들기 → 게시물 / 스토리 */
function CreateMenu() {
  const [open, setOpen] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => {
      if (!box.current?.contains(e.target as Node)) setOpen(false)
    }
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', esc)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', esc)
    }
  }, [open])

  return (
    <div ref={box} className="relative">
      <button className={itemClass(open)} title="만들기" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <SquarePlus size={24} strokeWidth={open ? 2.6 : 1.8} />
        <span className="hidden xl:inline">만들기</span>
      </button>
      {open && (
        <div
          role="menu"
          className="absolute top-full left-0 z-40 mt-1 w-48 overflow-hidden rounded-xl border border-border bg-bg py-1 shadow-lg"
        >
          <Link role="menuitem" to="/create" onClick={() => setOpen(false)} className="flex items-center justify-between px-4 py-3 text-sm hover:bg-surface-2">
            게시물 <Grid2x2Plus size={18} />
          </Link>
          <Link role="menuitem" to="/create/story" onClick={() => setOpen(false)} className="flex items-center justify-between px-4 py-3 text-sm hover:bg-surface-2">
            스토리 <CircleFadingPlus size={18} />
          </Link>
          <Link role="menuitem" to="/create/reel" onClick={() => setOpen(false)} className="flex items-center justify-between px-4 py-3 text-sm hover:bg-surface-2">
            릴스 <Clapperboard size={18} />
          </Link>
        </div>
      )}
    </div>
  )
}
