import { NavLink } from 'react-router'
import { FlaskConical } from 'lucide-react'
import clsx from 'clsx'

const TABS = [
  ['/lab', '현황·시간·시드'],
  ['/lab/algorithm', '알고리즘 실험실'],
  ['/lab/simulator', '활동 시뮬레이터'],
  ['/lab/events', '이벤트 로그'],
  ['/lab/flags', '기능 플래그'],
] as const

/** Lab 화면 공통 머리: 제목 + 도구 탭 */
export function LabHeader({ title, description }: { title?: string; description?: string }) {
  return (
    <header className="space-y-3">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-bold">
          <FlaskConical className="text-primary" /> Lab{title && <span className="font-normal text-muted">· {title}</span>}
        </h1>
        <p className="mt-1 text-sm text-muted">{description ?? '연구용 도구입니다. 실제 인스타그램에는 없는 화면입니다.'}</p>
      </div>
      <nav aria-label="Lab 도구" className="no-scrollbar -mx-4 flex gap-1 overflow-x-auto px-4">
        {TABS.map(([to, label]) => (
          <NavLink
            key={to}
            to={to}
            end
            className={({ isActive }) =>
              clsx(
                'shrink-0 rounded-full border px-3 py-1 text-sm whitespace-nowrap',
                isActive ? 'border-text bg-text font-semibold text-bg' : 'border-border hover:bg-surface-2',
              )
            }
          >
            {label}
          </NavLink>
        ))}
      </nav>
    </header>
  )
}
