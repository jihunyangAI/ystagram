import { FlaskConical, LogOut, SquarePlus } from 'lucide-react'
import { Link, NavLink, useNavigate } from 'react-router'
import clsx from 'clsx'
import { useLogout } from '../../lib/auth'
import type { Me } from '../../lib/types'
import { Avatar } from '../Avatar'
import { Logo, LogoMark } from '../Logo'
import { SIDEBAR_ITEMS } from './nav'

const itemClass = (active: boolean) =>
  clsx(
    'flex items-center gap-4 rounded-lg p-3 text-[15px] transition-colors hover:bg-surface-2',
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
                <Icon size={24} strokeWidth={isActive ? 2.6 : 1.8} />
                <span className="hidden xl:inline">{label}</span>
              </>
            )}
          </NavLink>
        ))}
        <NavLink to="/create" className={({ isActive }) => itemClass(isActive)} title="만들기">
          {({ isActive }) => (
            <>
              <SquarePlus size={24} strokeWidth={isActive ? 2.6 : 1.8} />
              <span className="hidden xl:inline">만들기</span>
            </>
          )}
        </NavLink>
        <NavLink to={`/${me.user.username}`} className={({ isActive }) => itemClass(isActive)} title="프로필">
          <Avatar user={me.user} size={24} />
          <span className="hidden xl:inline">프로필</span>
        </NavLink>
      </nav>

      <div className="flex flex-col gap-1">
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
