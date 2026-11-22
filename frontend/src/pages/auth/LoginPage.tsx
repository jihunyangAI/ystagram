import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { FlaskConical, ShieldCheck } from 'lucide-react'
import { Logo, LogoMark } from '../../components/Logo'
import { openLabSwitcher } from '../../components/lab/LabSwitcher'
import { api } from '../../lib/api'
import { useLogin, useLoginTwoFactor, useMe } from '../../lib/auth'
import { toast } from '../../components/Toast'
import { AuthShell } from './AuthShell'

export function LoginPage() {
  const { data: me } = useMe()
  const login = useLogin()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [form, setForm] = useState({ login: '', password: '' })
  const [challenge, setChallenge] = useState<string | null>(null)

  const next = params.get('next') || '/'
  if (me) return <Navigate to={next} replace />

  const submit = (e: FormEvent) => {
    e.preventDefault()
    login.mutate(form, {
      onSuccess: (r) => {
        if ('twoFactorRequired' in r) setChallenge(r.username)
        else navigate(next, { replace: true })
      },
    })
  }
  const canSubmit = form.login.trim() !== '' && form.password.length >= 6 && !login.isPending

  if (challenge) {
    return <TwoFactorStep username={challenge} onBack={() => setChallenge(null)} onDone={() => navigate(next, { replace: true })} />
  }

  return (
    <AuthShell
      footer={
        <>
          계정이 없으신가요?{' '}
          <Link to="/accounts/signup" className="font-semibold text-primary">
            가입하기
          </Link>
        </>
      }
    >
      <div className="mb-8 flex flex-col items-center gap-3">
        <LogoMark size={48} />
        <Logo className="scale-125" />
      </div>

      <form onSubmit={submit} className="space-y-2">
        <input
          className="input"
          placeholder="사용자 이름 또는 이메일"
          autoComplete="username"
          value={form.login}
          onChange={(e) => setForm({ ...form, login: e.target.value })}
        />
        <input
          className="input"
          type="password"
          placeholder="비밀번호"
          autoComplete="current-password"
          value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })}
        />
        <button type="submit" className="btn btn-primary mt-2 w-full" disabled={!canSubmit}>
          {login.isPending ? '로그인 중…' : '로그인'}
        </button>
        {login.isError && <p className="pt-2 text-center text-sm text-danger">{login.error.message}</p>}
      </form>

      <div className="my-6 flex items-center gap-4 text-xs font-semibold text-muted">
        <span className="h-px flex-1 bg-border" />
        또는
        <span className="h-px flex-1 bg-border" />
      </div>

      <button type="button" className="btn btn-secondary w-full" onClick={openLabSwitcher}>
        <FlaskConical size={16} className="text-primary" />
        가짜 계정으로 바로 둘러보기
      </button>
      <p className="mt-3 text-center text-xs text-muted">
        가짜 계정 비밀번호는 모두 <code className="rounded bg-surface-2 px-1">yang1234</code> 입니다
      </p>
    </AuthShell>
  )
}

/** 비밀번호 다음 단계: 인증 앱의 6자리 코드 또는 백업 코드 */
function TwoFactorStep({ username, onBack, onDone }: { username: string; onBack: () => void; onDone: () => void }) {
  const verify = useLoginTwoFactor()
  const [code, setCode] = useState('')
  const submit = (e: FormEvent) => {
    e.preventDefault()
    verify.mutate(code, {
      onSuccess: onDone,
      onError: (err) => {
        if ((err as Error & { code?: string }).code === 'TWO_FACTOR_EXPIRED') onBack()
      },
    })
  }
  return (
    <AuthShell
      footer={
        <button className="font-semibold text-primary" onClick={onBack}>
          로그인으로 돌아가기
        </button>
      }
    >
      <div className="mb-6 flex flex-col items-center gap-3 text-center">
        <ShieldCheck size={56} strokeWidth={1.2} />
        <h1 className="text-lg font-semibold">보안 코드 입력</h1>
        <p className="text-sm text-muted">
          {username}님의 인증 앱에 표시된 6자리 코드를 입력하세요. 휴대폰이 없으면 백업 코드(예: abcd-efgh)를 써도 됩니다.
        </p>
      </div>
      <form onSubmit={submit} className="space-y-2">
        <input
          className="input text-center text-lg tracking-[0.3em]"
          inputMode="numeric"
          autoComplete="one-time-code"
          autoFocus
          maxLength={12}
          placeholder="보안 코드"
          aria-label="보안 코드"
          value={code}
          onChange={(e) => setCode(e.target.value)}
        />
        <button type="submit" className="btn btn-primary w-full" disabled={code.trim().length < 6 || verify.isPending}>
          {verify.isPending ? '확인 중…' : '확인'}
        </button>
        {verify.isError && <p className="pt-2 text-center text-sm text-danger">{verify.error.message}</p>}
      </form>
      <button
        type="button"
        className="btn btn-secondary mt-4 w-full"
        onClick={() =>
          api.get<{ code: string }>(`/api/lab/2fa/${username}/code`).then((r) => setCode(r.code), (e: Error) => toast(e.message))
        }
      >
        <FlaskConical size={16} className="text-primary" />
        Lab: 지금 코드 채우기 (휴대폰 없이 시험)
      </button>
    </AuthShell>
  )
}
