import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfirmModal, Modal } from './Modal'

function Harness({ onClose = () => undefined }: { onClose?: () => void }) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button onClick={() => setOpen(true)}>열기</button>
      <button>바깥 버튼</button>
      <Modal
        open={open}
        title="공유"
        onClose={() => {
          onClose()
          setOpen(false)
        }}
      >
        <input aria-label="검색" />
        <button>보내기</button>
      </Modal>
    </>
  )
}

describe('Modal — 키보드만으로 쓸 수 있어야 한다 (§16.3)', () => {
  it('열면 첫 입력창으로 포커스가 가고 Tab이 창 안에서만 돈다', async () => {
    const user = userEvent.setup()
    render(<Harness />)
    await user.click(screen.getByText('열기'))

    expect(screen.getByRole('dialog', { name: '공유' })).toBeInTheDocument()
    expect(screen.getByLabelText('검색')).toHaveFocus()

    // 검색 → 보내기 → 닫기(제목 줄) → 다시 검색. 바깥 버튼으로 나가지 않는다
    // (DOM 순서: 제목 줄의 닫기 버튼이 먼저)
    const order: string[] = []
    for (let i = 0; i < 4; i++) {
      await user.tab()
      order.push(document.activeElement?.getAttribute('aria-label') ?? document.activeElement?.textContent ?? '')
    }
    expect(order).not.toContain('바깥 버튼')
    expect(order).toContain('닫기')

    await user.tab({ shift: true })
    expect(screen.getByRole('dialog').contains(document.activeElement)).toBe(true)
  })

  it('Esc로 닫히고 포커스가 연 버튼으로 돌아간다', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<Harness onClose={onClose} />)
    const opener = screen.getByText('열기')
    await user.click(opener)
    await user.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalledOnce()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(opener).toHaveFocus()
  })

  it('자식이 autoFocus를 쓰거나 부모가 다시 그려져도 닫으면 연 버튼으로 돌아간다', async () => {
    const user = userEvent.setup()
    function Parent() {
      const [open, setOpen] = useState(false)
      const [tick, setTick] = useState(0)
      return (
        <>
          <button onClick={() => setOpen(true)}>공유 열기</button>
          {/* 부모가 다시 그릴 때마다 onClose가 새 함수 */}
          <Modal open={open} title="공유" onClose={() => setOpen(false)}>
            <input aria-label="받는 사람" autoFocus />
            <input aria-label="메모" />
            <button onClick={() => setTick(tick + 1)}>다시 그리기 {tick}</button>
          </Modal>
        </>
      )
    }
    render(<Parent />)
    const opener = screen.getByText('공유 열기')
    await user.click(opener)
    expect(screen.getByLabelText('받는 사람')).toHaveFocus()

    // 메모를 쓰는 중에 부모가 다시 그려져도 포커스가 첫 입력창으로 튀지 않는다
    await user.click(screen.getByText(/다시 그리기/))
    await user.click(screen.getByLabelText('메모'))
    await user.click(screen.getByText(/다시 그리기/))
    await user.click(screen.getByLabelText('메모'))
    await user.keyboard('안녕')
    expect(screen.getByLabelText('메모')).toHaveFocus()
    expect(screen.getByLabelText('메모')).toHaveValue('안녕')

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(opener).toHaveFocus()
  })

  it('열려 있는 동안 뒤 페이지가 스크롤되지 않는다', async () => {
    const user = userEvent.setup()
    render(<Harness />)
    await user.click(screen.getByText('열기'))
    expect(document.body.style.overflow).toBe('hidden')
    await user.keyboard('{Escape}')
    expect(document.body.style.overflow).toBe('')
  })
})

describe('ConfirmModal', () => {
  it('Enter로 첫 버튼(확인)을 누를 수 있고 처리 중에는 막힌다', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    const { rerender } = render(
      <ConfirmModal open title="삭제할까요?" confirmLabel="삭제" onConfirm={onConfirm} onClose={() => undefined} />,
    )
    expect(screen.getByRole('button', { name: '삭제' })).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(onConfirm).toHaveBeenCalledOnce()

    rerender(<ConfirmModal open pending title="삭제할까요?" confirmLabel="삭제" onConfirm={onConfirm} onClose={() => undefined} />)
    expect(screen.getByRole('button', { name: '삭제' })).toBeDisabled()
  })
})
