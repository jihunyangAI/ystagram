import { Plus, Trash2 } from 'lucide-react'
import type { Overlay } from '../../lib/types'

const fmt = (ms: number) => `${(ms / 1000).toFixed(1)}초`

/**
 * 릴스 자르기·자막 (기획서 🟡 단순화): 영상을 다시 만들지 않고, 재생할 구간과 시간대별 글자만 저장한다.
 * 보는 화면이 그 구간만 반복 재생하고 글자를 얹는다.
 */
export function ReelEditFields({
  duration,
  trim,
  onTrim,
  overlays,
  onOverlays,
}: {
  duration: number
  trim: [number, number]
  onTrim: (t: [number, number]) => void
  overlays: Overlay[]
  onOverlays: (o: Overlay[]) => void
}) {
  const [start, end] = trim
  const update = (i: number, patch: Partial<Overlay>) => onOverlays(overlays.map((o, k) => (k === i ? { ...o, ...patch } : o)))
  return (
    <div className="space-y-4 rounded-lg border border-border p-3 text-sm">
      <div>
        <p className="font-semibold">
          자르기 <span className="font-normal text-muted">{fmt(start)} ~ {fmt(end)} ({fmt(end - start)})</span>
        </p>
        <label className="mt-1 flex items-center gap-2 text-xs text-muted">
          시작
          <input
            type="range"
            aria-label="자르기 시작"
            min={0}
            max={Math.max(0, duration - 1000)}
            step={100}
            value={start}
            onChange={(e) => onTrim([Math.min(Number(e.target.value), end - 1000), end])}
            className="flex-1"
          />
        </label>
        <label className="flex items-center gap-2 text-xs text-muted">
          끝
          <input
            type="range"
            aria-label="자르기 끝"
            min={1000}
            max={duration}
            step={100}
            value={end}
            onChange={(e) => onTrim([start, Math.max(Number(e.target.value), start + 1000)])}
            className="flex-1"
          />
        </label>
      </div>
      <div className="space-y-2">
        <p className="font-semibold">자막·글자</p>
        {overlays.map((o, i) => (
          <div key={i} className="space-y-1 rounded-md bg-surface-2 p-2">
            <div className="flex gap-2">
              <input
                className="input flex-1 py-1"
                aria-label={`${i + 1}번째 자막`}
                maxLength={80}
                value={o.text}
                placeholder="화면에 보일 글자"
                onChange={(e) => update(i, { text: e.target.value })}
              />
              <button aria-label="자막 빼기" onClick={() => onOverlays(overlays.filter((_, k) => k !== i))}>
                <Trash2 size={16} />
              </button>
            </div>
            <div className="flex items-center gap-2 text-xs text-muted">
              <input
                type="number"
                aria-label={`${i + 1}번째 자막 시작(초)`}
                className="input w-16 px-2 py-0.5"
                min={0}
                step={0.5}
                value={o.startMs / 1000}
                onChange={(e) => update(i, { startMs: Math.round(Number(e.target.value) * 1000) })}
              />
              <span className="whitespace-nowrap">초부터</span>
              <input
                type="number"
                aria-label={`${i + 1}번째 자막 끝(초)`}
                className="input w-16 px-2 py-0.5"
                min={0}
                step={0.5}
                value={o.endMs / 1000}
                onChange={(e) => update(i, { endMs: Math.round(Number(e.target.value) * 1000) })}
              />
              <span className="whitespace-nowrap">초까지</span>
              <select className="input w-auto min-w-0 flex-1 px-2 py-0.5" aria-label={`${i + 1}번째 자막 위치`} value={o.y} onChange={(e) => update(i, { y: Number(e.target.value) })}>
                <option value={0.15}>위</option>
                <option value={0.5}>가운데</option>
                <option value={0.75}>아래</option>
              </select>
            </div>
          </div>
        ))}
        {overlays.length < 10 && (
          <button
            className="flex items-center gap-1 text-xs font-semibold text-primary"
            onClick={() => onOverlays([...overlays, { text: '', startMs: start, endMs: Math.min(end, start + 3000), y: 0.75 }])}
          >
            <Plus size={14} /> 자막 추가
          </button>
        )}
      </div>
    </div>
  )
}

/** 재생 중인 시각에 맞는 자막을 영상 위에 얹는다 */
export function OverlayLayer({ overlays, timeMs }: { overlays: Overlay[]; timeMs: number }) {
  const shown = overlays.filter((o) => o.text.trim() && timeMs >= o.startMs && timeMs < o.endMs)
  if (!shown.length) return null
  return (
    <div className="pointer-events-none absolute inset-0 z-10" aria-label="자막">
      {shown.map((o, i) => (
        <p
          key={i}
          className="absolute inset-x-4 -translate-y-1/2 text-center text-lg font-bold text-white [text-shadow:0_1px_4px_rgba(0,0,0,0.8)]"
          style={{ top: `${o.y * 100}%` }}
        >
          {o.text}
        </p>
      ))}
    </div>
  )
}
