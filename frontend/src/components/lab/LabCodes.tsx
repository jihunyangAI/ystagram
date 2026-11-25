import { useQuery } from '@tanstack/react-query'
import { Mail } from 'lucide-react'
import { api } from '../../lib/api'

interface LabCode {
  purpose: 'SIGNUP' | 'RESET'
  target: string
  username: string | null
  code: string
  createdAt: string
  expiresAt: string
}

/** Lab: "이메일로 보낸" 가입·비밀번호 재설정 코드 (실제 메일 발송 없음) */
export function LabCodes() {
  const { data } = useQuery({
    queryKey: ['lab-codes'],
    queryFn: () => api.get<LabCode[]>('/api/lab/codes'),
    refetchInterval: 3000,
  })
  return (
    <section aria-labelledby="lab-codes" className="rounded-xl border border-border p-5">
      <h2 id="lab-codes" className="flex items-center gap-2 font-semibold">
        <Mail size={18} /> 인증 코드
      </h2>
      <p className="mt-1 text-sm text-muted">가입·비밀번호 재설정 때 "이메일로 보낸" 코드입니다. 10분 동안 쓸 수 있습니다.</p>
      {data?.length === 0 && <p className="mt-3 text-sm text-muted">아직 보낸 코드가 없습니다.</p>}
      <ul className="mt-3 space-y-1.5">
        {data?.map((c) => (
          <li key={`${c.purpose}-${c.target}-${c.createdAt}`} className="flex items-center gap-3 text-sm">
            <span className="w-24 shrink-0 text-xs text-muted">{c.purpose === 'SIGNUP' ? '가입' : '비밀번호 재설정'}</span>
            <span className="min-w-0 flex-1 truncate">{c.purpose === 'SIGNUP' ? c.target : c.username}</span>
            <code className="rounded bg-surface-2 px-2 py-0.5 font-semibold tracking-widest" aria-label={`코드 ${c.code}`}>
              {c.code}
            </code>
          </li>
        ))}
      </ul>
    </section>
  )
}
