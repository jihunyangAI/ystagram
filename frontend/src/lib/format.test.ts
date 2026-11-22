import { afterEach, describe, expect, it } from 'vitest'
import { editableNow, formatCount, timeAgo } from './format'
import { nowMs, setClockOffset } from './clock'
import { laterInput, toLocalInput } from './datetime'

const NOW = new Date('2026-11-22T12:00:00+09:00').getTime()
const ago = (ms: number) => new Date(NOW - ms).toISOString()
const MIN = 60_000

describe('formatCount — 인스타그램 한국어 숫자 표기', () => {
  it.each([
    [0, '0'],
    [999, '999'],
    [9_999, '9,999'],
    [10_000, '1만'],
    [12_345, '1.2만'],
    [99_999, '9.9만'],
    [1_234_567, '123만'],
    [123_456_789, '1.2억'],
  ])('%i → %s', (n, text) => {
    expect(formatCount(n)).toBe(text)
  })
})

describe('timeAgo — 게시 시각', () => {
  it('짧게: 방금 / 분 / 시간 / 일 / 주', () => {
    expect(timeAgo(ago(30_000), 'short', NOW)).toBe('방금')
    expect(timeAgo(ago(5 * MIN), 'short', NOW)).toBe('5분')
    expect(timeAgo(ago(3 * 60 * MIN), 'short', NOW)).toBe('3시간')
    expect(timeAgo(ago(2 * 24 * 60 * MIN), 'short', NOW)).toBe('2일')
    expect(timeAgo(ago(15 * 24 * 60 * MIN), 'short', NOW)).toBe('2주')
  })

  it('길게: "전"이 붙고 일주일이 넘으면 날짜', () => {
    expect(timeAgo(ago(5 * MIN), 'long', NOW)).toBe('5분 전')
    expect(timeAgo(ago(30 * 24 * 60 * MIN), 'long', NOW)).toMatch(/10월 23일/)
    expect(timeAgo(ago(400 * 24 * 60 * MIN), 'long', NOW)).toMatch(/2025년/)
  })

  it('미래 시각(시계 어긋남)은 방금', () => {
    expect(timeAgo(ago(-5 * MIN), 'short', NOW)).toBe('방금')
  })
})

describe('editableNow — 댓글·메시지 수정은 15분까지', () => {
  it('14분 59초는 되고 15분은 안 된다', () => {
    expect(editableNow(ago(15 * MIN - 1000), NOW)).toBe(true)
    expect(editableNow(ago(15 * MIN), NOW)).toBe(false)
  })
})

describe('Lab 시간 가속 (화면 시계 오프셋)', () => {
  afterEach(() => setClockOffset(0))

  it('서버가 25시간 앞서면 화면의 "지금"도 25시간 뒤', () => {
    const before = nowMs()
    setClockOffset(25 * 60)
    expect(nowMs() - before).toBeGreaterThanOrEqual(25 * 60 * MIN)
    expect(nowMs() - before).toBeLessThan(25 * 60 * MIN + 1000)
  })

  it('가속하면 방금 쓴 댓글도 수정할 수 없다', () => {
    const justNow = new Date(nowMs()).toISOString()
    expect(editableNow(justNow)).toBe(true)
    setClockOffset(16)
    expect(editableNow(justNow)).toBe(false)
  })

  it('예약 시각 기본값은 가상 시계 기준 5분 단위로 올림', () => {
    setClockOffset(60)
    const value = laterInput(30)
    expect(value).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d[05]$/)
    const ms = new Date(value).getTime()
    expect(ms - nowMs()).toBeGreaterThanOrEqual(30 * MIN - MIN)
    expect(ms - nowMs()).toBeLessThan(35 * MIN)
    expect(toLocalInput(ms)).toBe(value)
  })
})
