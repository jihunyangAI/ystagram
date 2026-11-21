import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { FlaskConical, RefreshCw, Search } from 'lucide-react'
import { LabUserList } from '../components/lab/LabUserList'
import { toast } from '../components/Toast'
import { api } from '../lib/api'
import { useSetSession } from '../lib/auth'
import type { LabStatus, SeedResult, SeedSize } from '../lib/types'

const SIZES: { value: SeedSize; label: string }[] = [
  { value: 'SMALL', label: 'small · 사용자 50명 (MVP)' },
  { value: 'MEDIUM', label: 'medium · 사용자 200명' },
  { value: 'LARGE', label: 'large · 사용자 500명' },
]

/** 연구 도구 화면: 데이터 현황, 시드 재생성, 계정 전환 */
export function LabPage() {
  const navigate = useNavigate()
  const setSession = useSetSession()
  const [size, setSize] = useState<SeedSize>('SMALL')
  const [seed, setSeed] = useState('42')
  const [query, setQuery] = useState('')

  const status = useQuery({ queryKey: ['lab-status'], queryFn: () => api.get<LabStatus>('/api/lab/status') })

  const reseed = useMutation({
    mutationFn: () => api.post<SeedResult>('/api/lab/seed', { size, seed: Number(seed) }),
    onSuccess: (r) => {
      toast(`사용자 ${r.users}명, 게시물 ${r.posts}개, 좋아요 ${r.likes}개를 만들었습니다 (${r.elapsedMillis}ms). 다시 로그인하세요.`)
      setSession(null)
      navigate('/accounts/login')
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
        { label: '가상 시계', value: s.clockOffsetMinutes === 0 ? '실제 시간' : `+${s.clockOffsetMinutes}분`, sub: new Date(s.now).toLocaleString('ko-KR') },
        { label: '가짜 계정 비밀번호', value: s.fakePassword, sub: '모든 가짜 계정 공통' },
      ]
    : []

  return (
    <div className="mx-auto w-full max-w-[760px] space-y-8 px-4 py-8">
      <header>
        <h1 className="flex items-center gap-2 text-2xl font-bold">
          <FlaskConical className="text-primary" /> Lab
        </h1>
        <p className="mt-1 text-sm text-muted">연구용 도구입니다. 실제 인스타그램에는 없는 화면입니다.</p>
      </header>

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
