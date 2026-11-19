/** 숫자를 인스타그램 한국어 표기처럼 줄인다. 예: 1,234 / 1.2만 / 3.4억 */
export function formatCount(n: number): string {
  if (n < 10_000) return n.toLocaleString('ko-KR')
  if (n < 100_000_000) return trim(n / 10_000) + '만'
  return trim(n / 100_000_000) + '억'
}

function trim(v: number): string {
  return v >= 100 ? Math.floor(v).toString() : (Math.floor(v * 10) / 10).toString()
}
