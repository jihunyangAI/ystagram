import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, KeyRound, Laptop, Smartphone, UserPlus } from 'lucide-react'
import { api } from '../../lib/api'
import { useMe, useSetSession } from '../../lib/auth'
import type { Me, UserSummary } from '../../lib/types'
import { timeAgo } from '../../lib/format'
import { Avatar } from '../../components/Avatar'
import { ConfirmModal, Modal, SheetButton } from '../../components/Modal'
import { toast } from '../../components/Toast'
import { AuthShell } from '../auth/AuthShell'

function Header({ title }: { title: string }) {
  const navigate = useNavigate()
  return (
    <header className="flex items-center gap-2 px-2 pt-4 pb-3 md:pt-8">
      <button aria-label="뒤로" onClick={() => navigate(-1)} className="p-2">
        <ChevronLeft size={26} />
      </button>
      <h1 className="text-lg font-bold">{title}</h1>
    </header>
  )
}

// ------------------------------------------------------------ 로그인 활동

interface LoginRow {
  id: number
  method: string
  userAgent: string | null
  ip: string | null
  createdAt: string
  lastSeenAt: string
  current: boolean
}

/** User-Agent → "Chrome · Windows" */
export function deviceLabel(ua: string | null): { label: string; mobile: boolean } {
  if (!ua) return { label: '알 수 없는 기기', mobile: false }
  const browser = /Edg\//.test(ua) ? 'Edge' : /Chrome\//.test(ua) ? 'Chrome' : /Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : '브라우저'
  const os = /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS' : /Windows/.test(ua) ? 'Windows' : /Mac OS/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : ''
  return { label: os ? `${browser} · ${os}` : browser, mobile: /Android|iPhone|iPad|Mobile/.test(ua) }
}

const METHOD: Record<string, string> = {
  PASSWORD: '비밀번호',
  SIGNUP: '가입',
  TWO_FACTOR: '2단계 인증',
  LAB: 'Lab 전환',
  ADD: '계정 추가',
}

export function LoginActivityPage() {
  const qc = useQueryClient()
  const { data, isLoading } = useQuery({ queryKey: ['login-sessions'], queryFn: () => api.get<LoginRow[]>('/api/me/sessions') })
  const [all, setAll] = useState(false)
  const refresh = () => qc.invalidateQueries({ queryKey: ['login-sessions'] })
  const others = (data ?? []).filter((s) => !s.current)
  return (
    <div className="mx-auto max-w-[600px] pb-8">
      <Header title="로그인 활동" />
      <p className="px-4 pb-3 text-sm text-muted">
        지금 로그인되어 있는 곳입니다. 모르는 곳이 있으면 로그아웃시키고 비밀번호를 바꾸세요.
      </p>
      {isLoading && <p className="p-6 text-center text-sm text-muted">불러오는 중…</p>}
      <ul>
        {data?.map((s) => {
          const d = deviceLabel(s.userAgent)
          return (
            <li key={s.id} className="flex items-center gap-3 border-b border-border px-4 py-3">
              {d.mobile ? <Smartphone size={26} className="shrink-0" /> : <Laptop size={26} className="shrink-0" />}
              <div className="min-w-0 flex-1 text-sm">
                <p className="font-semibold">{d.label}</p>
                <p className="text-xs text-muted">
                  {s.current ? <span className="font-semibold text-[#1fc25b]">지금 이 기기</span> : `${timeAgo(s.lastSeenAt, 'long')} 활동`}
                  {' · '}
                  {METHOD[s.method] ?? s.method}로 로그인 · {new Date(s.createdAt).toLocaleDateString('ko-KR')}
                  {s.ip && ` · ${s.ip}`}
                </p>
              </div>
              {!s.current && (
                <button
                  className="btn btn-secondary shrink-0 px-3 text-xs"
                  onClick={() =>
                    api.delete<void>(`/api/me/sessions/${s.id}`).then(
                      () => {
                        toast('그 기기에서 로그아웃시켰습니다')
                        void refresh()
                      },
                      (e: Error) => toast(e.message),
                    )
                  }
                >
                  로그아웃
                </button>
              )}
            </li>
          )
        })}
      </ul>
      {others.length > 0 && (
        <button className="mx-4 mt-4 text-sm font-semibold text-danger" onClick={() => setAll(true)}>
          이 기기만 남기고 모두 로그아웃
        </button>
      )}
      <ConfirmModal
        open={all}
        onClose={() => setAll(false)}
        title="다른 곳에서 모두 로그아웃할까요?"
        message={`이 기기를 뺀 ${others.length}곳에서 로그아웃됩니다.`}
        confirmLabel="모두 로그아웃"
        onConfirm={() =>
          api.delete<{ revoked: number }>('/api/me/sessions').then(
            (r) => {
              setAll(false)
              toast(`${r.revoked}곳에서 로그아웃시켰습니다`)
              void refresh()
            },
            (e: Error) => toast(e.message),
          )
        }
      />
    </div>
  )
}

// ------------------------------------------------------------ 계정 전환·추가

export function AccountSwitcher({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const setSession = useSetSession()
  const navigate = useNavigate()
  const { data } = useQuery({
    queryKey: ['linked-accounts'],
    queryFn: () => api.get<{ currentId: number; accounts: UserSummary[] }>('/api/auth/accounts'),
    enabled: open,
  })
  const [adding, setAdding] = useState(false)
  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)

  const done = (me: Me, msg: string) => {
    setSession(me)
    qc.clear()
    toast(msg)
    onClose()
    setAdding(false)
    navigate('/')
  }
  const add = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    try {
      const me = await api.post<Me>('/api/auth/accounts', { login, password })
      setLogin('')
      setPassword('')
      done(me, `${me.user.username}(으)로 전환했습니다`)
    } catch (err) {
      toast((err as Error).message)
    } finally {
      setBusy(false)
    }
  }
  return (
    <Modal open={open} onClose={onClose} title="계정 전환">
      {!adding ? (
        <div className="py-1">
          {data?.accounts.map((u) => (
            <button
              key={u.id}
              className="flex w-full items-center gap-3 px-4 py-2.5 text-left hover:bg-surface-2"
              onClick={() =>
                u.id === data.currentId
                  ? onClose()
                  : api.post<Me>(`/api/auth/accounts/${u.id}/switch`).then(
                      (me) => done(me, `${me.user.username}(으)로 전환했습니다`),
                      (err: Error) => toast(err.message),
                    )
              }
            >
              <Avatar user={u} size={44} />
              <span className="min-w-0 flex-1">
                <b className="block truncate text-sm">{u.username}</b>
                <span className="block truncate text-xs text-muted">{u.fullName}</span>
              </span>
              {u.id === data.currentId && <span className="size-4 rounded-full border-[5px] border-primary" aria-label="지금 계정" />}
            </button>
          ))}
          <SheetButton bold onClick={() => setAdding(true)}>
            <span className="flex items-center justify-center gap-2 text-primary">
              <UserPlus size={18} /> 계정 추가
            </span>
          </SheetButton>
          <p className="px-4 pb-3 text-xs text-muted">
            추가한 계정은 이 브라우저에서 비밀번호 없이 오갈 수 있습니다(최대 5개). 로그아웃하면 모두 로그아웃됩니다.
          </p>
        </div>
      ) : (
        <form onSubmit={add} className="space-y-2 p-4">
          <input className="input" placeholder="사용자 이름 또는 이메일" aria-label="추가할 계정" value={login} onChange={(e) => setLogin(e.target.value)} />
          <input className="input" type="password" placeholder="비밀번호" aria-label="추가할 계정 비밀번호" value={password} onChange={(e) => setPassword(e.target.value)} />
          <button className="btn btn-primary w-full" disabled={!login || !password || busy}>
            로그인
          </button>
          <button type="button" className="w-full py-2 text-sm text-muted" onClick={() => setAdding(false)}>
            취소
          </button>
        </form>
      )}
    </Modal>
  )
}

// ------------------------------------------------------------ 비활성화·삭제

export function RemoveAccountPage() {
  const { data: me } = useMe()
  const setSession = useSetSession()
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [mode, setMode] = useState<'DEACTIVATE' | 'DELETE'>('DEACTIVATE')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState(false)
  const [busy, setBusy] = useState(false)
  const go = async () => {
    setBusy(true)
    try {
      if (mode === 'DEACTIVATE') {
        await api.post<void>('/api/me/deactivate', { password })
        toast('계정을 비활성화했습니다. 다시 로그인하면 되살아납니다')
      } else {
        const r = await api.post<{ deleteAt: string }>('/api/me/delete', { password })
        toast(`${new Date(r.deleteAt).toLocaleDateString('ko-KR')}에 영구 삭제됩니다. 그 전에 로그인하면 취소됩니다`)
      }
      qc.clear()
      setSession(null)
      navigate('/accounts/login', { replace: true })
    } catch (e) {
      toast((e as Error).message)
      setConfirm(false)
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="mx-auto max-w-[600px] pb-8">
      <Header title="계정 비활성화 또는 삭제" />
      <div className="space-y-3 px-4">
        <p className="text-sm text-muted">{me?.user.username} 계정</p>
        {(
          [
            ['DEACTIVATE', '계정 비활성화', '잠시 쉬고 싶을 때. 프로필·게시물·댓글·좋아요가 다른 사람에게 보이지 않고, 다시 로그인하면 그대로 돌아옵니다.'],
            ['DELETE', '계정 삭제', '30일 뒤 계정과 게시물·메시지 등 모든 것이 영구 삭제됩니다. 30일 안에 로그인하면 삭제가 취소됩니다.'],
          ] as const
        ).map(([key, title, desc]) => (
          <label key={key} className="flex cursor-pointer gap-3 rounded-lg border border-border p-3 has-[:checked]:border-primary">
            <input type="radio" name="remove" checked={mode === key} onChange={() => setMode(key)} className="mt-1 accent-[var(--color-primary)]" />
            <span>
              <b className="block text-sm">{title}</b>
              <span className="block text-xs text-muted">{desc}</span>
            </span>
          </label>
        ))}
        <input className="input" type="password" placeholder="비밀번호를 다시 입력하세요" aria-label="비밀번호 확인" value={password} onChange={(e) => setPassword(e.target.value)} />
        <button className="btn btn-primary w-full" disabled={!password} onClick={() => setConfirm(true)}>
          계속
        </button>
      </div>
      <ConfirmModal
        open={confirm}
        onClose={() => setConfirm(false)}
        title={mode === 'DEACTIVATE' ? '계정을 비활성화할까요?' : '계정 삭제를 요청할까요?'}
        message={mode === 'DEACTIVATE' ? '모든 기기에서 로그아웃됩니다.' : '모든 기기에서 로그아웃되고, 30일 뒤 영구 삭제됩니다.'}
        confirmLabel={mode === 'DEACTIVATE' ? '비활성화' : '삭제 요청'}
        pending={busy}
        onConfirm={go}
      />
    </div>
  )
}

// ------------------------------------------------------------ 비밀번호 재설정 (로그인 전)

export function PasswordResetPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<'ASK' | 'CODE'>('ASK')
  const [login, setLogin] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const ask = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.post('/api/auth/password/code', { login })
      setStep('CODE')
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }
  const reset = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.post('/api/auth/password/reset', { login, code, password })
      toast('비밀번호를 바꿨습니다. 새 비밀번호로 로그인하세요')
      navigate('/accounts/login', { replace: true })
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }
  return (
    <AuthShell
      footer={
        <Link to="/accounts/login" className="font-semibold text-primary">
          로그인으로 돌아가기
        </Link>
      }
    >
      <div className="mb-4 flex flex-col items-center gap-3 text-center">
        <span className="grid size-20 place-items-center rounded-full border-2 border-text">
          <KeyRound size={36} />
        </span>
        <h1 className="font-semibold">로그인하는 데 문제가 있나요?</h1>
        <p className="text-sm text-muted">
          {step === 'ASK'
            ? '사용자 이름이나 이메일을 입력하면 비밀번호를 바꿀 수 있는 코드를 보내 드립니다.'
            : '받은 6자리 코드와 새 비밀번호를 입력하세요. (연구용: 실제 메일은 가지 않고 Lab → 인증 코드에 보입니다)'}
        </p>
      </div>
      {step === 'ASK' ? (
        <form onSubmit={ask} className="space-y-2">
          <input className="input" placeholder="사용자 이름 또는 이메일" aria-label="사용자 이름 또는 이메일" value={login} onChange={(e) => setLogin(e.target.value)} />
          <button className="btn btn-primary w-full" disabled={!login.trim() || busy}>
            코드 보내기
          </button>
        </form>
      ) : (
        <form onSubmit={reset} className="space-y-2">
          <input className="input" inputMode="numeric" maxLength={6} placeholder="6자리 코드" aria-label="인증 코드" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
          <input className="input" type="password" placeholder="새 비밀번호 (6자 이상)" aria-label="새 비밀번호" value={password} onChange={(e) => setPassword(e.target.value)} />
          <button className="btn btn-primary w-full" disabled={code.length !== 6 || password.length < 6 || busy}>
            비밀번호 바꾸기
          </button>
          <button type="button" className="w-full py-1 text-xs text-muted" onClick={ask}>
            코드 다시 받기
          </button>
        </form>
      )}
      {error && <p className="pt-2 text-center text-sm text-danger">{error}</p>}
    </AuthShell>
  )
}
