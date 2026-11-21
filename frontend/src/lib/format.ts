import { nowMs } from './clock'

/** 숫자를 인스타그램 한국어 표기처럼 줄인다. 예: 1,234 / 1.2만 / 3.4억 */
export function formatCount(n: number): string {
  if (n < 10_000) return n.toLocaleString('ko-KR')
  if (n < 100_000_000) return trim(n / 10_000) + '만'
  return trim(n / 100_000_000) + '억'
}

function trim(v: number): string {
  return v >= 100 ? Math.floor(v).toString() : (Math.floor(v * 10) / 10).toString()
}

/**
 * 게시 시각을 인스타그램처럼 표시한다.
 * 짧게(피드 헤더): 방금 / 5분 / 3시간 / 2일 / 4주
 * 길게(게시물 아래): 5분 전 / 3시간 전 / 2일 전 / 11월 3일 / 2024년 11월 3일
 */
export function timeAgo(iso: string, style: 'short' | 'long' = 'short', now = nowMs()): string {
  const then = new Date(iso).getTime()
  const sec = Math.max(0, Math.floor((now - then) / 1000))
  const min = Math.floor(sec / 60)
  const hour = Math.floor(min / 60)
  const day = Math.floor(hour / 24)
  const suffix = style === 'long' ? ' 전' : ''
  if (sec < 60) return '방금'
  if (min < 60) return `${min}분${suffix}`
  if (hour < 24) return `${hour}시간${suffix}`
  if (day < 7) return `${day}일${suffix}`
  if (style === 'short') return `${Math.floor(day / 7)}주`
  const d = new Date(then)
  const sameYear = d.getFullYear() === new Date(now).getFullYear()
  return d.toLocaleDateString('ko-KR', sameYear ? { month: 'long', day: 'numeric' } : { year: 'numeric', month: 'long', day: 'numeric' })
}

/** 툴팁용 전체 날짜 */
export function fullDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', { dateStyle: 'long', timeStyle: 'short' })
}
