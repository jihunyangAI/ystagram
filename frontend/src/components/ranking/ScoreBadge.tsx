import { useState } from 'react'
import clsx from 'clsx'
import type { Ranking } from '../../lib/types'
import { SOURCE_LABEL, fmtScore, useScoreOverlay } from '../../lib/ranking'
import { WhyModal } from './WhyModal'

/**
 * 점수 오버레이 (Lab L07). Lab에서 "점수 보기"를 켰을 때만 보인다.
 * compact: 탐색 칸처럼 좁은 곳에서는 점수만.
 */
export function ScoreBadge({ ranking, compact, className }: { ranking: Ranking | null; compact?: boolean; className?: string }) {
  const on = useScoreOverlay()
  const [open, setOpen] = useState(false)
  if (!on || !ranking) return null
  const top = [...ranking.parts].sort((a, b) => Math.abs(b.contribution) - Math.abs(a.contribution)).slice(0, 3)
  return (
    <>
      <button
        type="button"
        onClick={(e) => {
          e.preventDefault()
          e.stopPropagation()
          setOpen(true)
        }}
        className={clsx(
          'z-10 rounded-md bg-black/75 px-1.5 py-1 text-left font-mono text-[11px] leading-tight text-white backdrop-blur hover:bg-black/90',
          className,
        )}
        title="점수 내역 보기"
      >
        <div className="font-semibold">
          #{ranking.rank} · {fmtScore(ranking.score)}
          {!compact && <span className="ml-1 font-normal opacity-80">{SOURCE_LABEL[ranking.source]}</span>}
        </div>
        {!compact &&
          top.map((p) => (
            <div key={p.key} className="opacity-90">
              {p.label} {fmtScore(p.contribution)}
            </div>
          ))}
      </button>
      <WhyModal ranking={ranking} open={open} onClose={() => setOpen(false)} />
    </>
  )
}
