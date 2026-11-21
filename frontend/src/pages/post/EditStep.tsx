import { useState } from 'react'
import clsx from 'clsx'
import { ADJUSTMENTS, FILTERS, cssFilter, vignetteStyle } from './filters'
import type { Adjust } from './filters'

export interface Editable {
  id: string
  url: string
  filter: string
  adjust: Adjust
}

/**
 * ③ 편집: 사진마다 필터 1개 + 조정(밝기·대비·채도·온도·흐리게·비네트).
 * 미리보기는 CSS 필터, 올릴 때는 같은 값으로 canvas에 그린다.
 */
export function EditStep({
  items,
  current,
  setCurrent,
  update,
  preview,
}: {
  items: Editable[]
  current: number
  setCurrent: (i: number) => void
  update: (index: number, patch: Partial<Editable>) => void
  /** 잘린 모양 그대로 보여 주는 미리보기 (필터를 입혀서) */
  preview: (style: React.CSSProperties, overlay: React.CSSProperties | undefined) => React.ReactNode
}) {
  const [tab, setTab] = useState<'filter' | 'adjust'>('filter')
  const p = items[current]
  const style = { filter: cssFilter(p.filter, p.adjust) }

  return (
    <div className="flex flex-col md:flex-row">
      <div className="md:w-[60%] md:shrink-0">
        {preview(style, vignetteStyle(p.adjust.vignette))}
        {items.length > 1 && (
          <div className="no-scrollbar flex gap-2 overflow-x-auto p-2">
            {items.map((it, i) => (
              <button
                key={it.id}
                onClick={() => setCurrent(i)}
                aria-label={`${i + 1}번째 사진 편집`}
                className={clsx('h-14 w-14 shrink-0 overflow-hidden rounded', i === current && 'ring-2 ring-primary')}
              >
                <img src={it.url} alt="" className="h-full w-full object-cover" style={{ filter: cssFilter(it.filter, it.adjust) }} />
              </button>
            ))}
          </div>
        )}
      </div>
      <div className="flex-1 md:border-l md:border-border">
        <div role="tablist" className="flex border-b border-border">
          {(
            [
              ['filter', '필터'],
              ['adjust', '조정'],
            ] as const
          ).map(([key, label]) => (
            <button
              key={key}
              role="tab"
              aria-selected={tab === key}
              onClick={() => setTab(key)}
              className={clsx('flex-1 border-b-2 py-3 text-sm font-semibold', tab === key ? 'border-text' : 'border-transparent text-muted')}
            >
              {label}
            </button>
          ))}
        </div>
        {tab === 'filter' ? (
          <div className="grid grid-cols-3 gap-3 p-4">
            {FILTERS.map((f) => (
              <button key={f.key} onClick={() => update(current, { filter: f.key })} className="flex flex-col items-center gap-1 text-xs">
                <img
                  src={p.url}
                  alt=""
                  className={clsx('aspect-square w-full rounded object-cover', p.filter === f.key && 'ring-2 ring-primary')}
                  style={{ filter: f.css || 'none' }}
                />
                <span className={clsx(p.filter === f.key && 'font-semibold text-primary')}>{f.label}</span>
              </button>
            ))}
          </div>
        ) : (
          <div className="space-y-5 p-4">
            {ADJUSTMENTS.map((a) => (
              <label key={a.key} className="block text-sm">
                <span className="mb-1 flex justify-between">
                  <span>{a.label}</span>
                  {p.adjust[a.key] !== 0 && (
                    <button className="text-xs font-semibold text-primary" onClick={() => update(current, { adjust: { ...p.adjust, [a.key]: 0 } })}>
                      초기화 ({p.adjust[a.key]})
                    </button>
                  )}
                </span>
                <input
                  type="range"
                  min={a.min}
                  max={100}
                  value={p.adjust[a.key]}
                  onChange={(e) => update(current, { adjust: { ...p.adjust, [a.key]: Number(e.target.value) } })}
                  className="w-full"
                />
              </label>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
