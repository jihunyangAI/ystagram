import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CaptionField, MAX_CAPTION, countHashtags } from './CaptionField'
import type { Flags } from '../../lib/flags'
import { DEFAULT_FLAGS } from '../../lib/flags'

describe('countHashtags — 백엔드 Hashtags.extract와 같은 규칙', () => {
  it.each([
    ['', 0],
    ['#여행 #맛집', 2],
    ['#Travel #travel #TRAVEL', 1], // 대소문자 무시·중복 제외
    ['#카페#디저트', 2], // 붙여 써도 각각
    ['# 빈칸 #', 0],
    ['#hello_world #2026 #서울_맛집', 3],
    ['이메일 a#b 도 태그', 1],
  ])('%j → %i개', (text, n) => {
    expect(countHashtags(text)).toBe(n)
  })
})

function renderCaption(initial = '', flags: Partial<Flags> = {}) {
  const qc = new QueryClient()
  qc.setQueryData(['flags'], { ...DEFAULT_FLAGS, ...flags })
  function Harness() {
    const [v, setV] = useState(initial)
    return <CaptionField value={v} onChange={setV} />
  }
  render(
    <QueryClientProvider client={qc}>
      <Harness />
    </QueryClientProvider>,
  )
  return screen.getByRole('textbox', { name: '문구' })
}

describe('CaptionField — 입력 제한 표시', () => {
  it('해시태그를 셀 때마다 바뀌고 6개째에 경고', async () => {
    const box = renderCaption()
    await userEvent.type(box, '#a #b #c #d #e')
    expect(screen.getByText('해시태그 5/5')).toBeInTheDocument()
    expect(box).toHaveAttribute('aria-invalid', 'false')

    await userEvent.type(box, ' #f')
    expect(screen.getByText(/해시태그 6\/5/)).toHaveTextContent('해시태그는 5개까지 쓸 수 있습니다')
    expect(box).toHaveAttribute('aria-invalid', 'true')
  })

  it('Lab 기능 플래그로 해시태그 한도가 바뀐다', () => {
    renderCaption('#a #b #c #d #e #f', { hashtagLimit: 30 })
    expect(screen.getByText('해시태그 6/30')).toBeInTheDocument()
  })

  it('2,200자를 넘으면 글자 수가 경고로 바뀐다', () => {
    const box = renderCaption('가'.repeat(MAX_CAPTION))
    expect(screen.getByText('2,200/2,200')).not.toHaveClass('text-danger')
    expect(box).toHaveAttribute('aria-invalid', 'false')
  })

  it('2,201자는 경고', () => {
    const box = renderCaption('가'.repeat(MAX_CAPTION + 1))
    expect(screen.getByText('2,201/2,200')).toHaveClass('text-danger')
    expect(box).toHaveAttribute('aria-invalid', 'true')
  })
})
