// <input type="datetime-local"> 값과 시각(ms) 사이 변환. 값은 브라우저 현지 시간 "YYYY-MM-DDTHH:mm".
import { nowMs } from './clock'

const pad = (n: number) => String(n).padStart(2, '0')

export function toLocalInput(ms: number): string {
  const d = new Date(ms)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function fromLocalInput(value: string): number {
  return new Date(value).getTime()
}

/** 지금(가상 시계)부터 minutes 뒤를 5분 단위로 올림 */
export function laterInput(minutes: number): string {
  const step = 5 * 60_000
  return toLocalInput(Math.ceil((nowMs() + minutes * 60_000) / step) * step)
}

/** "11월 23일 (일) 오후 3:00" */
export function scheduleLabel(iso: string | number): string {
  return new Date(iso).toLocaleString('ko-KR', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
    hour: 'numeric',
    minute: '2-digit',
  })
}
