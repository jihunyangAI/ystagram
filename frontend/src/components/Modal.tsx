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
  /** 모바일에서 아래에 붙는 시트(넓은 화면은 가운데 창) */
  sheet?: boolean
}

/**
 * 화면 가운데 뜨는 창. Esc·바깥 클릭으로 닫히고, 열려 있는 동안 키보드 포커스가 창 안에 머문다.
 */
export function Modal({ open, onClose, title, children, className, bare, sheet }: Props) {
  const panel = useRef<HTMLDivElement>(null)
  // onClose는 부모가 그릴 때마다 새 함수일 수 있다. 효과를 다시 돌리면 "닫으면 돌아갈 곳"이 창 안 요소로 바뀌고
  // 포커스가 첫 입력창으로 튀므로, 최신 onClose만 ref로 들고 효과는 열고 닫을 때만 돈다
  const close = useRef(onClose)
  useEffect(() => {
    close.current = onClose
  })
  // 닫으면 포커스를 돌려줄 요소. 효과 안에서 읽으면 이미 자식의 autoFocus가 포커스를 가져간 뒤라서, 열리는 순간 그릴 때 기억한다
  const opener = useRef<HTMLElement | null>(null)
  const wasOpen = useRef(false)
  if (open && !wasOpen.current) opener.current = document.activeElement as HTMLElement | null
  wasOpen.current = open

  useEffect(() => {
    if (!open) return
    const previous = opener.current
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    // 입력창이 있으면 입력창, 없으면 내용의 첫 버튼으로 포커스 (제목 줄의 닫기 버튼은 마지막 후보)
    const p = panel.current
    const first =
      p?.querySelector<HTMLElement>('input:not([type="hidden"]):not([disabled]), textarea:not([disabled]), select') ??
      p?.querySelector<HTMLElement>('button:not([disabled]):not([data-modal-close]), a[href]') ??
      p?.querySelector<HTMLElement>('[data-modal-close]')
    ;(first ?? p)?.focus()

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation()
        close.current()
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
  }, [open])

  if (!open) return null
  return createPortal(
    <div
      className={clsx(
        'fixed inset-0 z-50 flex justify-center bg-black/60',
        sheet ? 'items-end md:items-center md:p-4' : 'items-center p-4',
      )}
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
          sheet && 'max-w-none rounded-b-none md:rounded-b-xl',
          className,
        )}
      >
        {!bare && (
          <div className="relative flex h-11 shrink-0 items-center justify-center border-b border-border">
            <h2 className="font-semibold">{title}</h2>
            <button className="absolute right-2 p-1.5" aria-label="닫기" data-modal-close onClick={onClose}>
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
