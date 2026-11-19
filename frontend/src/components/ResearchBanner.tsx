import { useState } from 'react'
import { X } from 'lucide-react'

/** 연구용 프로젝트임을 항상 알리는 얇은 배너 (닫아도 새로고침하면 다시 보임). */
export function ResearchBanner() {
  const [open, setOpen] = useState(true)
  if (!open) return null
  return (
    <div className="flex items-center justify-center gap-2 bg-banner px-4 py-1.5 text-center text-xs text-banner-text">
      <span>연구용 데모 · 모든 사용자와 콘텐츠는 가짜입니다</span>
      <button onClick={() => setOpen(false)} aria-label="배너 닫기" className="rounded p-0.5 hover:opacity-70">
        <X size={14} />
      </button>
    </div>
  )
}
