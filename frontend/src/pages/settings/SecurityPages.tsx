import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Flag, ShieldCheck } from 'lucide-react'
import QRCode from 'qrcode'
import clsx from 'clsx'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'
import { timeAgo } from '../../lib/format'

function Header({ title }: { title: string }) {
  const navigate = useNavigate()
  return (
    <header className="flex items-center gap-2 px-2 pt-4 pb-2 md:pt-8">
      <button aria-label="뒤로" className="p-2" onClick={() => navigate(-1)}>
        <ChevronLeft size={24} />
      </button>
      <h1 className="text-lg font-bold">{title}</h1>
    </header>
  )
}

// ------------------------------------------------------------ 2단계 인증

interface Status {
  enabled: boolean
  backupCodesLeft: number
}

interface Setup {
  secret: string
  otpauthUri: string
}

/** 설정 → 2단계 인증: 인증 앱(QR) 등록 → 코드 확인 → 백업 코드 */
export function TwoFactorPage() {
  const qc = useQueryClient()
  const { data: status } = useQuery({ queryKey: ['2fa'], queryFn: () => api.get<Status>('/api/me/2fa') })
  const [setup, setSetup] = useState<Setup | null>(null)
  const [qr, setQr] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const [backups, setBackups] = useState<string[] | null>(null)

  useEffect(() => {
    if (!setup) return
    QRCode.toDataURL(setup.otpauthUri, { margin: 1, width: 200 }).then(setQr, () => setQr(null))
  }, [setup])

  const refresh = () => qc.invalidateQueries({ queryKey: ['2fa'] })
  const start = useMutation({
    mutationFn: () => api.post<Setup>('/api/me/2fa/setup'),
    onSuccess: setSetup,
    onError: (e) => toast((e as Error).message),
  })
  const enable = useMutation({
    mutationFn: () => api.post<{ backupCodes: string[] }>('/api/me/2fa/enable', { code }),
    onSuccess: (r) => {
      setBackups(r.backupCodes)
      setSetup(null)
      setCode('')
      refresh()
      toast('2단계 인증을 켰습니다')
    },
    onError: (e) => toast((e as Error).message),
  })
  const disable = useMutation({
    mutationFn: () => api.post('/api/me/2fa/disable', { code }),
    onSuccess: () => {
      setCode('')
      setBackups(null)
      refresh()
      toast('2단계 인증을 껐습니다')
    },
    onError: (e) => toast((e as Error).message),
  })
  const regenerate = useMutation({
    mutationFn: () => api.post<{ backupCodes: string[] }>('/api/me/2fa/backup-codes', { code }),
    onSuccess: (r) => {
      setBackups(r.backupCodes)
      setCode('')
      refresh()
    },
    onError: (e) => toast((e as Error).message),
  })

  const codeInput = (
    <input
      className="input text-center tracking-[0.3em]"
      inputMode="numeric"
      maxLength={12}
      placeholder="6자리 코드"
      aria-label="인증 앱 코드"
      value={code}
      onChange={(e) => setCode(e.target.value)}
    />
  )

  return (
    <div className="mx-auto max-w-[600px] pb-10">
      <Header title="2단계 인증" />
      <div className="space-y-4 px-4">
        <div className="flex items-start gap-3 rounded-xl border border-border p-4">
          <ShieldCheck size={28} className={clsx('shrink-0', status?.enabled ? 'text-primary' : 'text-muted')} />
          <div className="text-sm">
            <p className="font-semibold">{status?.enabled ? '2단계 인증이 켜져 있습니다' : '2단계 인증이 꺼져 있습니다'}</p>
            <p className="mt-1 text-muted">
              로그인할 때 비밀번호와 함께 인증 앱(Google Authenticator, 1Password 등)의 코드를 입력해 계정을 보호합니다.
            </p>
            {status?.enabled && <p className="mt-1 text-muted">남은 백업 코드 {status.backupCodesLeft}개</p>}
          </div>
        </div>

        {backups && (
          <div className="rounded-xl border border-primary p-4">
            <p className="font-semibold">백업 코드</p>
            <p className="mt-1 text-sm text-muted">휴대폰을 잃어버렸을 때 한 번씩 쓸 수 있습니다. 이 화면을 벗어나면 다시 볼 수 없으니 저장해 두세요.</p>
            <ul className="mt-3 grid grid-cols-2 gap-2 font-mono text-sm">
              {backups.map((b) => (
                <li key={b} className="rounded bg-surface-2 px-2 py-1 text-center">
                  {b}
                </li>
              ))}
            </ul>
            <button
              className="btn btn-secondary mt-3 w-full"
              onClick={() => navigator.clipboard?.writeText(backups.join('\n')).then(() => toast('복사했습니다'))}
            >
              복사
            </button>
          </div>
        )}

        {!status?.enabled && !setup && (
          <button className="btn btn-primary w-full" disabled={start.isPending} onClick={() => start.mutate()}>
            인증 앱으로 설정하기
          </button>
        )}

        {setup && (
          <div className="space-y-3 rounded-xl border border-border p-4 text-sm">
            <p className="font-semibold">1. 인증 앱으로 QR 코드를 찍으세요</p>
            {qr && <img src={qr} alt="인증 앱 등록 QR 코드" className="mx-auto h-48 w-48" />}
            <p className="text-muted">
              찍을 수 없으면 이 키를 직접 입력하세요:{' '}
              <code className="rounded bg-surface-2 px-1 break-all">{setup.secret}</code>
            </p>
            <p className="pt-2 font-semibold">2. 앱에 표시된 6자리 코드를 입력하세요</p>
            {codeInput}
            <button className="btn btn-primary w-full" disabled={code.trim().length < 6 || enable.isPending} onClick={() => enable.mutate()}>
              확인하고 켜기
            </button>
          </div>
        )}

        {status?.enabled && (
          <div className="space-y-3 rounded-xl border border-border p-4 text-sm">
            <p className="text-muted">끄거나 백업 코드를 새로 만들려면 지금 코드를 입력하세요.</p>
            {codeInput}
            <div className="flex gap-2">
              <button className="btn btn-secondary flex-1" disabled={code.trim().length < 6 || regenerate.isPending} onClick={() => regenerate.mutate()}>
                백업 코드 새로 만들기
              </button>
              <button className="btn flex-1 text-danger" disabled={code.trim().length < 6 || disable.isPending} onClick={() => disable.mutate()}>
                끄기
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

// ------------------------------------------------------------ 신고 내역

interface MyReport {
  id: number
  targetType: 'POST' | 'COMMENT' | 'USER'
  targetLabel: string | null
  reasonLabel: string
  status: 'OPEN' | 'REMOVED' | 'DISMISSED'
  createdAt: string
  resolvedAt: string | null
}

const STATUS: Record<MyReport['status'], [string, string]> = {
  OPEN: ['검토 중', 'text-muted'],
  REMOVED: ['조치됨 · 콘텐츠를 삭제했거나 계정을 제한했습니다', 'text-primary'],
  DISMISSED: ['검토 완료 · 가이드라인 위반이 아닙니다', 'text-muted'],
}

const TYPE: Record<MyReport['targetType'], string> = { POST: '게시물', COMMENT: '댓글', USER: '계정' }

/** 설정 → 신고 내역 (지원 요청함) */
export function ReportsPage() {
  const { data, isLoading } = useQuery({ queryKey: ['my-reports'], queryFn: () => api.get<MyReport[]>('/api/me/reports') })
  return (
    <div className="mx-auto max-w-[600px] pb-10">
      <Header title="신고 내역" />
      {isLoading && <p className="p-8 text-center text-sm text-muted">불러오는 중…</p>}
      {data?.length === 0 && (
        <div className="px-4 py-12 text-center">
          <Flag size={44} strokeWidth={1.2} className="mx-auto text-muted" />
          <p className="mt-3 font-semibold">신고한 내용이 없습니다</p>
          <p className="mt-1 text-sm text-muted">게시물·댓글·계정의 ⋯ 메뉴에서 신고할 수 있어요.</p>
        </div>
      )}
      <ul>
        {data?.map((r) => (
          <li key={r.id} className="border-b border-border px-4 py-3 text-sm">
            <p>
              <span className="font-semibold">{TYPE[r.targetType]}</span>
              {r.targetType === 'POST' && r.targetLabel ? (
                <Link to={`/p/${r.targetLabel}`} className="ml-1 text-primary">
                  보기
                </Link>
              ) : (
                r.targetLabel && <span className="ml-1 text-muted">“{r.targetLabel}”</span>
              )}
              <span className="ml-2 text-muted">· {r.reasonLabel}</span>
            </p>
            <p className={clsx('mt-0.5 text-xs', STATUS[r.status][1])}>{STATUS[r.status][0]}</p>
            <p className="mt-0.5 text-xs text-muted">{timeAgo(r.createdAt)}에 신고</p>
          </li>
        ))}
      </ul>
    </div>
  )
}
