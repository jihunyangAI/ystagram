import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { Logo } from '../../components/Logo'
import { ApiError, api } from '../../lib/api'
import { useMe, useSignup } from '../../lib/auth'
import type { SignupInput } from '../../lib/auth'
import { AuthShell } from './AuthShell'

type Field = keyof SignupInput

const FIELDS: { name: Field; placeholder: string; type?: string; autoComplete?: string }[] = [
  { name: 'email', placeholder: '이메일 주소', type: 'email', autoComplete: 'email' },
  { name: 'password', placeholder: '비밀번호 (6자 이상)', type: 'password', autoComplete: 'new-password' },
  { name: 'fullName', placeholder: '성명', autoComplete: 'name' },
  { name: 'username', placeholder: '사용자 이름', autoComplete: 'username' },
]

// 서버 오류 코드 → 어느 입력칸 아래에 보여 줄지
const CODE_FIELD: Record<string, Field> = {
  INVALID_USERNAME: 'username',
  USERNAME_TAKEN: 'username',
  EMAIL_TAKEN: 'email',
  TOO_YOUNG: 'birthDate',
}

export function SignupPage() {
  const { data: me } = useMe()
  const signup = useSignup()
  const navigate = useNavigate()
  const [form, setForm] = useState<SignupInput>({ email: '', password: '', fullName: '', username: '', birthDate: '' })
  // 가입 인증 코드: 첫 "가입"은 코드를 보내고, 코드를 넣은 뒤 다시 누르면 가입
  const [codeSentTo, setCodeSentTo] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const [sending, setSending] = useState(false)
  const [codeError, setCodeError] = useState<string | null>(null)
  const sendCode = async () => {
    setSending(true)
    setCodeError(null)
    try {
      const r = await api.post<{ sentTo: string }>('/api/auth/signup/code', { email: form.email })
      setCodeSentTo(r.sentTo)
      setCode('')
    } catch (e) {
      setCodeError((e as Error).message)
    } finally {
      setSending(false)
    }
  }

  if (me) return <Navigate to="/" replace />

  const error = signup.error instanceof ApiError ? signup.error : null
  const fieldError = (name: Field): string | undefined =>
    error?.fieldErrors[name] ?? (error && CODE_FIELD[error.code] === name ? error.message : undefined)
  const generalError = error && !CODE_FIELD[error.code] && Object.keys(error.fieldErrors).length === 0 ? error.message : null

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!codeSentTo || codeSentTo !== form.email.trim().toLowerCase()) {
      void sendCode()
      return
    }
    signup.mutate({ ...form, code }, { onSuccess: () => navigate('/', { replace: true }) })
  }
  const filled = [form.email, form.password, form.fullName, form.username, form.birthDate].every((v) => v.trim() !== '')
  const waitingCode = !!codeSentTo && codeSentTo === form.email.trim().toLowerCase()

  return (
    <AuthShell
      footer={
        <>
          계정이 있으신가요?{' '}
          <Link to="/accounts/login" className="font-semibold text-primary">
            로그인
          </Link>
        </>
      }
    >
      <div className="mb-4 flex flex-col items-center gap-3 text-center">
        <Logo className="scale-125" />
        <p className="font-semibold text-muted">친구들의 사진과 동영상을 보려면 가입하세요.</p>
        <p className="text-xs text-muted">연구용 서비스입니다. 실제 개인정보를 입력하지 마세요.</p>
      </div>

      <form onSubmit={submit} className="space-y-2" noValidate>
        {FIELDS.map((f) => (
          <div key={f.name}>
            <input
              className="input"
              name={f.name}
              type={f.type ?? 'text'}
              placeholder={f.placeholder}
              autoComplete={f.autoComplete}
              value={form[f.name]}
              onChange={(e) =>
                setForm({ ...form, [f.name]: f.name === 'username' ? e.target.value.toLowerCase().replace(/\s/g, '') : e.target.value })
              }
              aria-invalid={!!fieldError(f.name)}
            />
            {fieldError(f.name) && <p className="mt-1 text-xs text-danger">{fieldError(f.name)}</p>}
          </div>
        ))}
        <div>
          <label className="mb-1 block text-xs text-muted" htmlFor="birthDate">
            생년월일 (만 13세 이상)
          </label>
          <input
            id="birthDate"
            className="input"
            type="date"
            value={form.birthDate}
            onChange={(e) => setForm({ ...form, birthDate: e.target.value })}
            aria-invalid={!!fieldError('birthDate')}
          />
          {fieldError('birthDate') && <p className="mt-1 text-xs text-danger">{fieldError('birthDate')}</p>}
        </div>

        {waitingCode && (
          <div className="rounded-lg bg-surface-2 p-3 text-sm">
            <p>
              <b>{codeSentTo}</b>(으)로 보낸 6자리 코드를 입력하세요.
            </p>
            <p className="mt-1 text-xs text-muted">연구용: 실제 메일은 가지 않고 Lab → 인증 코드에 보입니다.</p>
            <input
              className="input mt-2"
              inputMode="numeric"
              maxLength={6}
              placeholder="인증 코드"
              aria-label="인증 코드"
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            />
            <button type="button" className="mt-1 text-xs font-semibold text-primary" onClick={sendCode} disabled={sending}>
              코드 다시 받기
            </button>
          </div>
        )}
        {codeError && <p className="text-center text-sm text-danger">{codeError}</p>}
        <button
          type="submit"
          className="btn btn-primary mt-3 w-full"
          disabled={!filled || signup.isPending || sending || (waitingCode && code.length !== 6)}
        >
          {signup.isPending ? '가입 중…' : sending ? '코드 보내는 중…' : waitingCode ? '가입' : '인증 코드 받기'}
        </button>
        {generalError && <p className="pt-2 text-center text-sm text-danger">{generalError}</p>}
      </form>
    </AuthShell>
  )
}
