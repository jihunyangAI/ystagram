// 사진 필터·조정 (분석 문서 §6 편집). 브라우저 canvas의 filter(CSS 필터 문법)로 그려서 올린다.

export interface Adjust {
  brightness: number // -100 ~ 100
  contrast: number
  saturate: number
  warmth: number
  fade: number // 0 ~ 100
  vignette: number // 0 ~ 100
}

export const NO_ADJUST: Adjust = { brightness: 0, contrast: 0, saturate: 0, warmth: 0, fade: 0, vignette: 0 }

/** 인스타그램 필터 이름을 빌린 근사치 (색감만 비슷하게) */
export const FILTERS: { key: string; label: string; css: string }[] = [
  { key: 'normal', label: '원본', css: '' },
  { key: 'clarendon', label: 'Clarendon', css: 'contrast(1.2) saturate(1.35)' },
  { key: 'gingham', label: 'Gingham', css: 'brightness(1.05) hue-rotate(-10deg) sepia(0.04)' },
  { key: 'moon', label: 'Moon', css: 'grayscale(1) contrast(1.1) brightness(1.1)' },
  { key: 'lark', label: 'Lark', css: 'contrast(0.9) brightness(1.1) saturate(1.1)' },
  { key: 'reyes', label: 'Reyes', css: 'sepia(0.22) brightness(1.1) contrast(0.85) saturate(0.75)' },
  { key: 'juno', label: 'Juno', css: 'saturate(1.4) contrast(1.15) sepia(0.1)' },
  { key: 'slumber', label: 'Slumber', css: 'saturate(0.66) brightness(1.05) sepia(0.2)' },
  { key: 'crema', label: 'Crema', css: 'sepia(0.3) contrast(0.9) brightness(1.05)' },
  { key: 'ludwig', label: 'Ludwig', css: 'contrast(1.05) saturate(1.2) brightness(1.03)' },
  { key: 'aden', label: 'Aden', css: 'hue-rotate(-20deg) contrast(0.9) saturate(0.85) brightness(1.2)' },
  { key: 'perpetua', label: 'Perpetua', css: 'contrast(1.1) brightness(1.25) saturate(1.1)' },
]

export const ADJUSTMENTS: { key: keyof Adjust; label: string; min: number }[] = [
  { key: 'brightness', label: '밝기', min: -100 },
  { key: 'contrast', label: '대비', min: -100 },
  { key: 'saturate', label: '채도', min: -100 },
  { key: 'warmth', label: '온도', min: -100 },
  { key: 'fade', label: '흐리게', min: 0 },
  { key: 'vignette', label: '비네트', min: 0 },
]

/** 필터 + 조정을 하나의 CSS filter 문자열로 */
export function cssFilter(filter: string, a: Adjust): string {
  const preset = FILTERS.find((f) => f.key === filter)?.css ?? ''
  const parts = [preset]
  if (a.brightness) parts.push(`brightness(${1 + a.brightness / 200})`)
  if (a.contrast) parts.push(`contrast(${1 + a.contrast / 200})`)
  if (a.saturate) parts.push(`saturate(${1 + a.saturate / 100})`)
  if (a.warmth > 0) parts.push(`sepia(${a.warmth / 250})`)
  if (a.warmth < 0) parts.push(`hue-rotate(${a.warmth / 6}deg)`)
  if (a.fade) parts.push(`contrast(${1 - a.fade / 300}) brightness(${1 + a.fade / 400})`)
  return parts.filter(Boolean).join(' ') || 'none'
}

/** 가장자리를 어둡게 (canvas에 마지막으로 덧그린다) */
export function drawVignette(ctx: CanvasRenderingContext2D, W: number, H: number, amount: number) {
  if (!amount) return
  const g = ctx.createRadialGradient(W / 2, H / 2, Math.min(W, H) * 0.35, W / 2, H / 2, Math.max(W, H) * 0.75)
  g.addColorStop(0, 'rgba(0,0,0,0)')
  g.addColorStop(1, `rgba(0,0,0,${amount / 140})`)
  ctx.fillStyle = g
  ctx.fillRect(0, 0, W, H)
}

/** 화면 미리보기용 비네트 (CSS) */
export function vignetteStyle(amount: number): React.CSSProperties | undefined {
  if (!amount) return undefined
  return { background: `radial-gradient(ellipse at center, rgba(0,0,0,0) 45%, rgba(0,0,0,${amount / 140}) 100%)` }
}
