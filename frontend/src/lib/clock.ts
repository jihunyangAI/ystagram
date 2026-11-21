// Lab 시간 가속(서버의 가상 시계)과 화면의 "N분 전" 표시를 맞추기 위한 오프셋.
// 서버가 6시간 앞서 있으면 화면도 6시간 뒤의 시각을 "지금"으로 본다.

let offsetMs = 0

export function setClockOffset(minutes: number) {
  offsetMs = minutes * 60_000
}

/** 서버 가상 시계 기준 현재 시각(ms) */
export function nowMs(): number {
  return Date.now() + offsetMs
}
