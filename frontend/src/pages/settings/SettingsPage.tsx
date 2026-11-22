import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import {
  Archive,
  Ban,
  BarChart3,
  Bookmark,
  CalendarClock,
  ChevronRight,
  CircleUserRound,
  Crown,
  ShoppingBag,
  Store,
  EyeOff,
  Flag,
  FlaskConical,
  KeyRound,
  Lock,
  LogOut,
  MessageSquareOff,
  Moon,
  ShieldCheck,
  Sparkles,
  Star,
  UserRoundCheck,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import clsx from 'clsx'
import { useLogout, useMe } from '../../lib/auth'
import { getTheme, setTheme } from '../../lib/theme'
import type { Theme } from '../../lib/theme'

/** 10대 계정 기본 보호 (만 18세 미만) */
function TeenProtection() {
  const rules = [
    ['비공개 계정으로 시작', '승인한 사람만 게시물을 볼 수 있습니다.'],
    ['메시지 제한', '회원님이 팔로우하는 사람만 메시지를 보낼 수 있습니다.'],
    ['태그·멘션 제한', '회원님이 팔로우하는 사람만 회원님을 태그하거나 멘션할 수 있습니다.'],
    ['광고 타기팅 제한', '관심사에 맞춘 광고 대신 누구에게나 보이는 광고만 보입니다.'],
  ]
  return (
    <section className="mx-4 my-2 rounded-xl border border-border p-4">
      <h2 className="flex items-center gap-2 text-sm font-semibold">
        <ShieldCheck size={18} className="text-primary" /> 10대 계정 보호가 적용되어 있어요
      </h2>
      <ul className="mt-3 space-y-2 text-sm">
        {rules.map(([title, desc]) => (
          <li key={title}>
            <span className="font-semibold">{title}</span>
            <span className="block text-xs text-muted">{desc}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

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
        { to: '/accounts/two-factor', label: '2단계 인증', icon: KeyRound, hint: '로그인할 때 인증 앱 코드도 확인' },
      ],
    },
    {
      title: '내 활동',
      items: [
        { to: `/${me.user.username}/saved/all`, label: '저장됨', icon: Bookmark },
        { to: '/archive/stories', label: '보관함', icon: Archive, hint: '지난 스토리' },
        { to: '/insights', label: '인사이트', icon: BarChart3, hint: '도달·노출·참여·팔로워 변화' },
        { to: '/accounts/scheduled', label: '예약된 콘텐츠', icon: CalendarClock, hint: '예약한 게시물 보기·지금 게시·취소' },
      ],
    },
    {
      title: '쇼핑·구독 (가짜 결제)',
      items: [
        { to: '/accounts/shop', label: '쇼핑', icon: ShoppingBag, hint: '위시리스트·주문 내역' },
        { to: '/accounts/subscriptions', label: '구독', icon: Crown, hint: '구독 중인 크리에이터, 내 구독 가격·구독자' },
        ...(me.accountType !== 'PERSONAL'
          ? [{ to: '/shop/manage', label: '제품 관리', icon: Store, hint: '프로필 "샵" 탭에 보일 제품 등록·삭제' }]
          : []),
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
      title: '도움말',
      items: [{ to: '/accounts/reports', label: '신고 내역', icon: Flag, hint: '내가 신고한 콘텐츠와 처리 결과' }],
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
      {me.teen && <TeenProtection />}
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
