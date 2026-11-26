import { useEffect, useRef, useState } from 'react'
import { FlaskConical, Search, X } from 'lucide-react'
import { Link, useLocation } from 'react-router'
import { LabUserList } from './LabUserList'

const OPEN_EVENT = 'yangstagram:open-lab-switcher'

/** 다른 화면에서 계정 전환기를 열 때 사용 */
export function openLabSwitcher() {
  window.dispatchEvent(new Event(OPEN_EVENT))
}

/**
 * 연구 도구: 가짜 계정 전환기.
 * 화면 오른쪽 아래 버튼으로 어디서든 열 수 있다.
 */
export function LabSwitcher() {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const onOpen = () => setOpen(true)
    window.addEventListener(OPEN_EVENT, onOpen)
    return () => window.removeEventListener(OPEN_EVENT, onOpen)
  }, [])

  useEffect(() => {
    if (!open) return
    inputRef.current?.focus()
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  const { pathname } = useLocation()

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className={`fixed z-40 flex h-11 items-center justify-center bg-primary text-white shadow-lg hover:bg-primary-hover ${
          // DM·스토리·릴스는 화면 구석마다 버튼이 있어서, 모바일에서는 왼쪽 가장자리의 작은 탭으로(데스크톱은 오른쪽 위).
          // Lab은 데스크톱에서만 위로(결과 표를 가리지 않게)
          // 화면 아래에 버튼이 있거나 꽉 찬 화면은 왼쪽 가장자리의 얇은 탭으로 (만들기 화면의 "레이아웃"·"공유" 버튼을 가리지 않게)
          ['/direct', '/stories', '/reels', '/create'].some((p) => pathname.startsWith(p))
            ? 'top-1/2 left-0 w-6 -translate-y-1/2 rounded-r-full opacity-70 hover:opacity-100 md:top-12 md:right-4 md:left-auto md:w-11 md:translate-y-0 md:rounded-full md:opacity-100'
            : pathname.startsWith('/lab')
              ? 'right-4 bottom-20 w-11 rounded-full md:top-12 md:bottom-auto'
              : 'right-4 bottom-20 w-11 rounded-full md:bottom-6'
        }`}
        aria-label="Lab: 계정 전환"
        title="Lab: 계정 전환"
      >
        <FlaskConical size={20} />
      </button>

      {open && (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/50 md:items-center" onClick={() => setOpen(false)}>
          <div
            role="dialog"
            aria-modal="true"
            aria-label="계정 전환"
            className="flex max-h-[85dvh] w-full flex-col overflow-hidden rounded-t-2xl bg-surface md:max-w-md md:rounded-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-border px-4 py-3">
              <div>
                <h2 className="flex items-center gap-1.5 font-semibold">
                  <FlaskConical size={16} className="text-primary" /> 계정 전환
                </h2>
                <p className="text-xs text-muted">가짜 사용자를 골라 그 사람 시점으로 둘러보세요</p>
              </div>
              <button onClick={() => setOpen(false)} aria-label="닫기" className="rounded p-1 hover:bg-surface-2">
                <X size={20} />
              </button>
            </div>
            <div className="border-b border-border p-3">
              <label className="relative block">
                <Search size={16} className="absolute top-1/2 left-3 -translate-y-1/2 text-muted" />
                <input
                  ref={inputRef}
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="사용자 이름 또는 이름 검색"
                  className="input pl-9"
                />
              </label>
            </div>
            <div className="flex-1 overflow-y-auto">
              <LabUserList query={query.trim()} onSwitched={() => setOpen(false)} />
            </div>
            <Link
              to="/lab"
              onClick={() => setOpen(false)}
              className="border-t border-border px-4 py-3 text-center text-sm font-semibold text-primary hover:bg-surface-2"
            >
              Lab 열기 (데이터 현황 · 시드 재생성)
            </Link>
          </div>
        </div>
      )}
    </>
  )
}
