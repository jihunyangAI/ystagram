import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import clsx from 'clsx'

interface Props {
  open: boolean
  onClose: () => void
  title?: string
  children: ReactNode
  className?: string
  /** 제목 줄 없이 내용만 (메뉴 시트 등) */
  bare?: boolean
}

/**
 * 화면 가운데 뜨는 창. Esc·바깥 클릭으로 닫히고, 열려 있는 동안 키보드 포커스가 창 안에 머문다.
 */
export function Modal({ open, onClose, title, children, className, bare }: Props) {
  const panel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const previous = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    // 첫 번째 버튼·입력창으로 포커스
    const first = panel.current?.querySelector<HTMLElement>('input, textarea, button, a[href]')
    ;(first ?? panel.current)?.focus()

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation()
        onClose()
      }
      if (e.key === 'Tab' && panel.current) {
        const items = panel.current.querySelectorAll<HTMLElement>(
          'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select, [tabindex]:not([tabindex="-1"])',
        )
        if (items.length === 0) return
        const firstItem = items[0]
        const lastItem = items[items.length - 1]
        if (e.shiftKey && document.activeElement === firstItem) {
          e.preventDefault()
          lastItem.focus()
        } else if (!e.shiftKey && document.activeElement === lastItem) {
          e.preventDefault()
          firstItem.focus()
        }
      }
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = overflow
      previous?.focus?.()
    }
  }, [open, onClose])

  if (!open) return null
  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        className={clsx(
          'flex max-h-[85dvh] w-full max-w-[400px] flex-col overflow-hidden rounded-xl bg-surface text-text shadow-xl outline-none',
          className,
        )}
      >
        {!bare && (
          <div className="relative flex h-11 shrink-0 items-center justify-center border-b border-border">
            <h2 className="font-semibold">{title}</h2>
            <button className="absolute right-2 p-1.5" aria-label="닫기" onClick={onClose}>
              <X size={22} />
            </button>
          </div>
        )}
        {children}
      </div>
    </div>,
    document.body,
  )
}

/** 메뉴 시트 한 줄 (인스타그램 ⋯ 메뉴처럼 가운데 정렬 버튼 목록) */
export function SheetButton({
  children,
  onClick,
  danger,
  bold,
  disabled,
}: {
  children: ReactNode
  onClick: () => void
  danger?: boolean
  bold?: boolean
  disabled?: boolean
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={clsx(
        'min-h-12 w-full border-b border-border px-4 py-3 text-sm last:border-b-0 hover:bg-surface-2 disabled:opacity-50',
        danger && 'font-bold text-danger',
        bold && 'font-bold',
      )}
    >
      {children}
    </button>
  )
}

/** 확인 창 (브라우저 기본 confirm 대신) */
export function ConfirmModal({
  open,
  title,
  message,
  confirmLabel,
  onConfirm,
  onClose,
  pending,
  avatar,
}: {
  open: boolean
  title: string
  message?: string
  confirmLabel: string
  onConfirm: () => void
  onClose: () => void
  pending?: boolean
  avatar?: ReactNode
}) {
  return (
    <Modal open={open} onClose={onClose} bare title={title}>
      <div className="flex flex-col items-center gap-1 px-6 pt-7 pb-5 text-center">
        {avatar}
        <p className={clsx('text-lg font-semibold', avatar && 'mt-3 text-sm font-normal')}>{title}</p>
        {message && <p className="text-sm text-muted">{message}</p>}
      </div>
      <div className="border-t border-border">
        <SheetButton danger onClick={onConfirm} disabled={pending}>
          {confirmLabel}
        </SheetButton>
        <SheetButton onClick={onClose}>취소</SheetButton>
      </div>
    </Modal>
  )
}
