import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Clock, RefreshCw, Search } from 'lucide-react'
import { LabHeader } from '../components/lab/LabNav'
import { LabUserList } from '../components/lab/LabUserList'
import { toast } from '../components/Toast'
import { api } from '../lib/api'
import { useSetSession } from '../lib/auth'
import { setClockOffset } from '../lib/clock'
import { useRealtimeConnected } from '../lib/realtime'
import type { LabStatus, SeedResult, SeedSize } from '../lib/types'

const SIZES: { value: SeedSize; label: string }[] = [
  { value: 'SMALL', label: 'small · 사용자 50명' },
  { value: 'MEDIUM', label: 'medium · 사용자 200명' },
  { value: 'LARGE', label: 'large · 사용자 500명' },
]

const CLOCK_STEPS: { label: string; minutes: number; hint?: string }[] = [
  { label: '+16분', minutes: 16, hint: '댓글·DM 15분 수정 제한' },
  { label: '+1시간', minutes: 60 },
  { label: '+6시간', minutes: 360 },
  { label: '+25시간', minutes: 1500, hint: '스토리 24시간 만료' },
]

/** 시간이 지나면 달라지는 화면들: 시계를 움직이면 새로 받는다 */
const TIME_DEPENDENT = ['lab-status', 'lab-clock', 'story-tray', 'stories', 'story-archive', 'feed', 'post', 'comments', 'replies', 'messages', 'profile', 'activity']

function formatOffset(minutes: number): string {
  const d = Math.floor(minutes / 1440)
  const h = Math.floor((minutes % 1440) / 60)
  const m = minutes % 60
  const parts = [d ? `${d}일` : '', h ? `${h}시간` : '', m ? `${m}분` : ''].filter(Boolean)
  return parts.join(' ') || '0분'
}

/** 연구 도구 화면: 데이터 현황, 시간 가속, 시드 재생성, 계정 전환 */
export function LabPage() {
  const navigate = useNavigate()
  const setSession = useSetSession()
  const qc = useQueryClient()
  const realtime = useRealtimeConnected()
  const [size, setSize] = useState<SeedSize>('MEDIUM')
  const [seed, setSeed] = useState('42')
  const [query, setQuery] = useState('')

  const status = useQuery({ queryKey: ['lab-status'], queryFn: () => api.get<LabStatus>('/api/lab/status') })

  const reseed = useMutation({
    mutationFn: () => api.post<SeedResult>('/api/lab/seed', { size, seed: Number(seed) }),
    onSuccess: (r) => {
      toast(
        `사용자 ${r.users}명, 게시물 ${r.posts}개, 스토리 ${r.stories}개, 메시지 ${r.messages}개를 만들었습니다 (${(r.elapsedMillis / 1000).toFixed(1)}초). 다시 로그인하세요.`,
      )
      setSession(null)
      navigate('/accounts/login')
    },
    onError: (e) => toast(e.message),
  })

  // 시간 가속: 서버의 가상 시계를 앞으로. 화면의 "N분 전" 표시도 같은 시계를 쓴다
  const clock = useMutation({
    mutationFn: (body: { addMinutes?: number; reset?: boolean }) =>
      api.post<{ now: string; offsetMinutes: number }>('/api/lab/clock', body),
    onSuccess: (r) => {
      setClockOffset(r.offsetMinutes)
      toast(r.offsetMinutes === 0 ? '실제 시간으로 되돌렸습니다' : `가상 시계: +${formatOffset(r.offsetMinutes)}`)
      TIME_DEPENDENT.forEach((k) => qc.invalidateQueries({ queryKey: [k] }))
    },
    onError: (e) => toast(e.message),
  })

  const confirmReseed = () => {
    if (window.confirm('모든 데이터를 지우고 가짜 데이터를 다시 만듭니다. 직접 가입한 계정도 삭제됩니다. 계속할까요?')) {
      reseed.mutate()
    }
  }

  const s = status.data
  const cards = s
    ? [
        { label: '사용자', value: s.users, sub: `가짜 ${s.fakeUsers}명` },
        { label: '팔로우', value: s.follows, sub: `승인 대기 ${s.pendingFollows}개` },
        { label: '게시물', value: s.posts, sub: `해시태그 ${s.hashtags}개` },
        { label: '좋아요 · 댓글', value: s.likes, sub: `댓글 ${s.comments.toLocaleString('ko-KR')}개` },
        { label: '활성 스토리', value: s.activeStories, sub: '24시간 안에 올린 것' },
        { label: 'DM 메시지', value: s.messages, sub: `대화방 ${s.conversations.toLocaleString('ko-KR')}개` },
        { label: '알림', value: s.notifications, sub: realtime ? '실시간 연결됨 🟢' : '실시간 연결 안 됨' },
        { label: '가짜 계정 비밀번호', value: s.fakePassword, sub: '모든 가짜 계정 공통' },
      ]
    : []

  return (
    <div className="mx-auto w-full max-w-[760px] space-y-8 px-4 py-8">
      <LabHeader />

      <section aria-labelledby="lab-status">
        <h2 id="lab-status" className="mb-3 font-semibold">데이터 현황</h2>
        {status.isError && <p className="text-sm text-danger">현황을 불러오지 못했습니다.</p>}
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {cards.map((c) => (
            <div key={c.label} className="rounded-xl border border-border p-4">
              <p className="text-xs text-muted">{c.label}</p>
              <p className="mt-1 text-xl font-bold">{typeof c.value === 'number' ? c.value.toLocaleString('ko-KR') : c.value}</p>
              <p className="mt-0.5 truncate text-xs text-muted">{c.sub}</p>
            </div>
          ))}
        </div>
      </section>

      <section aria-labelledby="lab-clock" className="rounded-xl border border-border p-5">
        <h2 id="lab-clock" className="flex items-center gap-2 font-semibold">
          <Clock size={18} /> 시간 가속
        </h2>
        <p className="mt-1 text-sm text-muted">
          서버의 가상 시계를 앞으로 돌려 시간 규칙을 바로 확인합니다. 지금:{' '}
          <b className="text-text">{s ? new Date(s.now).toLocaleString('ko-KR') : '…'}</b>
          {s && s.clockOffsetMinutes !== 0 && <span className="text-primary"> (+{formatOffset(s.clockOffsetMinutes)})</span>}
        </p>
        <div className="mt-4 flex flex-wrap gap-2">
          {CLOCK_STEPS.map((c) => (
            <button
              key={c.label}
              className="btn btn-secondary flex-col items-start gap-0 py-2"
              disabled={clock.isPending}
              onClick={() => clock.mutate({ addMinutes: c.minutes })}
            >
              <span>{c.label}</span>
              {c.hint && <span className="text-[11px] font-normal text-muted">{c.hint}</span>}
            </button>
          ))}
          <button
            className="btn btn-secondary"
            disabled={clock.isPending || s?.clockOffsetMinutes === 0}
            onClick={() => clock.mutate({ reset: true })}
          >
            실제 시간으로
          </button>
        </div>
        <p className="mt-3 text-xs text-muted">
          되돌리면 앞당긴 시간에 만든 글·메시지는 "미래"가 되어 잠시 목록에서 빠질 수 있습니다. 서버를 다시 시작하면 실제 시간으로
          돌아갑니다.
        </p>
      </section>

      <section aria-labelledby="lab-seed" className="rounded-xl border border-border p-5">
        <h2 id="lab-seed" className="font-semibold">가짜 데이터 다시 만들기</h2>
        <p className="mt-1 text-sm text-muted">같은 규모·시드값이면 항상 같은 데이터가 만들어집니다 (실험 재현용).</p>
        <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end">
          <label className="flex-1 text-sm">
            <span className="mb-1 block text-xs text-muted">규모</span>
            <select className="input" value={size} onChange={(e) => setSize(e.target.value as SeedSize)}>
              {SIZES.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm sm:w-32">
            <span className="mb-1 block text-xs text-muted">시드값</span>
            <input className="input" inputMode="numeric" value={seed} onChange={(e) => setSeed(e.target.value.replace(/\D/g, ''))} />
          </label>
          <button className="btn btn-primary" onClick={confirmReseed} disabled={reseed.isPending || seed === ''}>
            <RefreshCw size={16} className={reseed.isPending ? 'animate-spin' : ''} />
            {reseed.isPending ? '만드는 중…' : '다시 만들기'}
          </button>
        </div>
      </section>

      <section aria-labelledby="lab-users" className="overflow-hidden rounded-xl border border-border">
        <div className="border-b border-border p-4">
          <h2 id="lab-users" className="font-semibold">계정 전환</h2>
          <label className="relative mt-3 block">
            <Search size={16} className="absolute top-1/2 left-3 -translate-y-1/2 text-muted" />
            <input className="input pl-9" placeholder="사용자 이름 또는 이름 검색" value={query} onChange={(e) => setQuery(e.target.value)} />
          </label>
        </div>
        <div className="max-h-[480px] overflow-y-auto">
          <LabUserList query={query.trim()} />
        </div>
      </section>
    </div>
  )
}
