import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { CheckCircle2, ChevronRight } from 'lucide-react'
import { api } from '../../lib/api'
import { Modal } from '../Modal'
import { toast } from '../Toast'

export type ReportTarget = { type: 'POST' | 'COMMENT' | 'USER'; target: string; label: string }

/**
 * 신고하기 (분석 문서 §21): 사유 고르기 → (선택) 자세한 내용 → 접수 안내.
 * 처리 결과는 설정 → 신고 내역에서 본다. 연구용이라 Lab 모더레이션 큐에서 처리한다.
 */
export function ReportModal({ target, onClose }: { target: ReportTarget; onClose: () => void }) {
  const { data: reasons } = useQuery({
    queryKey: ['report-reasons'],
    queryFn: () => api.get<Record<string, string>>('/api/reports/reasons'),
    staleTime: Infinity,
  })
  const [reason, setReason] = useState<string | null>(null)
  const [detail, setDetail] = useState('')
  const [done, setDone] = useState(false)
  const send = useMutation({
    mutationFn: () => api.post('/api/reports', { targetType: target.type, target: target.target, reason, detail }),
    onSuccess: () => setDone(true),
    onError: (e) => toast((e as Error).message),
  })

  return (
    <Modal open onClose={onClose} title="신고">
      {done ? (
        <div className="flex flex-col items-center gap-3 p-6 text-center">
          <CheckCircle2 size={56} strokeWidth={1.2} className="text-primary" />
          <p className="font-semibold">신고해 주셔서 감사합니다</p>
          <p className="text-sm text-muted">
            회원님의 의견은 Yangstagram 커뮤니티를 안전하게 유지하는 데 도움이 됩니다. 처리 결과는 설정 → 신고 내역에서 볼 수
            있어요.
          </p>
          <button className="btn btn-primary mt-2 w-full" onClick={onClose}>
            닫기
          </button>
        </div>
      ) : reason ? (
        <div className="space-y-3 p-4">
          <p className="text-sm">
            <span className="font-semibold">{reasons?.[reason]}</span> 사유로 {target.label}을(를) 신고합니다.
          </p>
          <textarea
            className="input min-h-20 resize-none"
            maxLength={300}
            value={detail}
            onChange={(e) => setDetail(e.target.value)}
            placeholder="자세한 내용 (선택)"
            aria-label="자세한 내용"
          />
          <div className="flex gap-2">
            <button className="btn btn-secondary flex-1" onClick={() => setReason(null)}>
              뒤로
            </button>
            <button className="btn btn-primary flex-1" disabled={send.isPending} onClick={() => send.mutate()}>
              신고 제출
            </button>
          </div>
        </div>
      ) : (
        <div>
          <p className="border-b border-border px-4 py-3 text-sm font-semibold">이 {target.label}을(를) 신고하는 이유</p>
          <ul>
            {reasons &&
              Object.entries(reasons).map(([key, label]) => (
                <li key={key}>
                  <button
                    className="flex w-full items-center justify-between px-4 py-3 text-left text-sm hover:bg-surface-2"
                    onClick={() => setReason(key)}
                  >
                    {label}
                    <ChevronRight size={16} className="text-muted" />
                  </button>
                </li>
              ))}
          </ul>
        </div>
      )}
    </Modal>
  )
}
