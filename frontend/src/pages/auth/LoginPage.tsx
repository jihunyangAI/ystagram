import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { FlaskConical } from 'lucide-react'
import { Logo, LogoMark } from '../../components/Logo'
import { openLabSwitcher } from '../../components/lab/LabSwitcher'
import { useLogin, useMe } from '../../lib/auth'
import { AuthShell } from './AuthShell'

export function LoginPage() {
  const { data: me } = useMe()
  const login = useLogin()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [form, setForm] = useState({ login: '', password: '' })

  const next = params.get('next') || '/'
  if (me) return <Navigate to={next} replace />

  const submit = (e: FormEvent) => {
    e.preventDefault()
    login.mutate(form, { onSuccess: () => navigate(next, { replace: true }) })
  }
  const canSubmit = form.login.trim() !== '' && form.password.length >= 6 && !login.isPending

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
