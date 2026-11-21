import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import {
  Archive,
  Ban,
  BarChart3,
  Bookmark,
  ChevronRight,
  CircleUserRound,
  EyeOff,
  FlaskConical,
  Lock,
  LogOut,
  MessageSquareOff,
  Moon,
  Sparkles,
  Star,
  UserRoundCheck,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import clsx from 'clsx'
import { useLogout, useMe } from '../../lib/auth'
import { getTheme, setTheme } from '../../lib/theme'
import type { Theme } from '../../lib/theme'

interface Item {
  to: string
  label: string
  icon: LucideIcon
  hint?: string
}

/** 설정 및 활동 (분석 문서 §23의 주요 메뉴) */
export function SettingsPage() {
  const { data: me } = useMe()
  const navigate = useNavigate()
  const logout = useLogout()
  const [theme, setThemeState] = useState<Theme>(getTheme())
  if (!me) return null

  const sections: { title: string; items: Item[] }[] = [
    {
      title: '내 계정',
      items: [
        { to: '/accounts/edit', label: '프로필 편집', icon: CircleUserRound },
        { to: '/accounts/edit#privacy', label: '계정 공개 범위', icon: Lock, hint: '비공개 계정·팔로우 요청' },
      ],
    },
    {
      title: '내 활동',
      items: [
        { to: `/${me.user.username}/saved/all`, label: '저장됨', icon: Bookmark },
        { to: '/archive/stories', label: '보관함', icon: Archive, hint: '지난 스토리' },
        { to: '/insights', label: '인사이트', icon: BarChart3, hint: '도달·노출·참여·팔로워 변화' },
      ],
    },
    {
      title: '내 콘텐츠를 볼 수 있는 사람',
      items: [
        { to: '/accounts/close-friends', label: '친한 친구', icon: Star, hint: '친한 친구에게만 스토리 공유' },
        { to: '/accounts/blocked', label: '차단된 계정', icon: Ban },
      ],
    },
    {
      title: '다른 사람과 소통하는 방식',
      items: [
        { to: '/accounts/restricted', label: '제한된 계정', icon: MessageSquareOff, hint: '댓글은 승인해야 공개, DM은 요청함으로' },
        { to: '/accounts/muted', label: '숨긴 계정', icon: EyeOff, hint: '팔로우는 유지하고 피드·스토리에서만 빼기' },
      ],
    },
    {
      title: '표시되는 내용',
      items: [
        { to: '/accounts/algorithm', label: '회원님의 알고리즘', icon: Sparkles, hint: '추천에 쓰이는 관심 주제 보기·추가·빼기' },
        { to: '/accounts/favorites', label: '즐겨찾기', icon: UserRoundCheck, hint: '홈의 "즐겨찾기"에 이 사람들의 글만' },
      ],
    },
  ]

  const choose = (t: Theme) => {
    setTheme(t)
    setThemeState(t)
  }

  return (
    <div className="mx-auto max-w-[600px] pb-10">
      <h1 className="px-4 pt-6 pb-2 text-xl font-bold md:pt-8">설정 및 활동</h1>
      {sections.map((s) => (
        <section key={s.title} className="border-b border-border py-2">
          <h2 className="px-4 pt-2 pb-1 text-sm text-muted">{s.title}</h2>
          {s.items.map(({ to, label, icon: Icon, hint }) => (
            <Link key={to} to={to} className="flex items-center gap-4 px-4 py-3 hover:bg-surface-2">
              <Icon size={22} strokeWidth={1.7} />
              <span className="min-w-0 flex-1 text-sm">
                {label}
                {hint && <span className="block text-xs text-muted">{hint}</span>}
              </span>
              <ChevronRight size={18} className="text-muted" />
            </Link>
          ))}
        </section>
      ))}

      <section className="border-b border-border py-2">
        <h2 className="flex items-center gap-2 px-4 pt-2 pb-2 text-sm text-muted">
          <Moon size={14} /> 화면 테마
        </h2>
        <div role="radiogroup" aria-label="화면 테마" className="flex gap-2 px-4 pb-3">
          {(
            [
              ['system', '시스템 설정'],
              ['light', '라이트'],
              ['dark', '다크'],
            ] as [Theme, string][]
          ).map(([value, label]) => (
            <button
              key={value}
              role="radio"
              aria-checked={theme === value}
              onClick={() => choose(value)}
              className={clsx(
                'flex-1 rounded-lg border py-2 text-sm',
                theme === value ? 'border-primary font-semibold text-primary' : 'border-border',
              )}
            >
              {label}
            </button>
          ))}
        </div>
      </section>

      <section className="py-2">
        <Link to="/lab" className="flex items-center gap-4 px-4 py-3 hover:bg-surface-2">
          <FlaskConical size={22} strokeWidth={1.7} className="text-primary" />
          <span className="flex-1 text-sm">Lab (연구 도구)</span>
        </Link>
        <button
          className="flex w-full items-center gap-4 px-4 py-3 text-left text-danger hover:bg-surface-2"
          onClick={() => logout.mutate(undefined, { onSuccess: () => navigate('/accounts/login') })}
        >
          <LogOut size={22} strokeWidth={1.7} />
          <span className="text-sm">로그아웃</span>
        </button>
      </section>
    </div>
  )
}
