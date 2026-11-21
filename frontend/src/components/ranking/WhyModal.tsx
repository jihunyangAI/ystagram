import { Link } from 'react-router'
import clsx from 'clsx'
import type { Ranking } from '../../lib/types'
import { SOURCE_LABEL, SURFACE_LABEL, fmtScore } from '../../lib/ranking'
import { Modal } from '../Modal'

/** 사람이 읽는 한 줄 이유: 기여도가 큰 양수 구성 요소 두 개 */
function summary(r: Ranking): string {
  const top = [...r.parts]
    .filter((p) => p.contribution > 0.05)
    .sort((a, b) => b.contribution - a.contribution)
    .slice(0, 2)
    .map((p) => p.label)
  const lead =
    r.source === 'FOLLOWING'
      ? '회원님이 팔로우하는 계정의 게시물입니다.'
      : r.source === 'REPOST'
        ? '회원님이 팔로우하는 사람이 리포스트한 게시물입니다.'
        : '팔로우하지 않는 계정이지만 회원님이 좋아할 만한 게시물로 추천되었습니다.'
  return top.length ? `${lead} 순서를 정할 때 가장 크게 작용한 것은 ${top.join(', ')}입니다.` : lead
}

/**
 * "이 게시물이 표시되는 이유" (분석 문서 §21의 투명성 기능 + Lab L07 점수 내역).
 */
export function WhyModal({ ranking, open, onClose }: { ranking: Ranking; open: boolean; onClose: () => void }) {
  const max = Math.max(0.01, ...ranking.parts.map((p) => Math.abs(p.contribution)))
  return (
    <Modal open={open} onClose={onClose} title="이 게시물이 표시되는 이유" className="md:max-w-md">
      <div className="space-y-4 p-4 text-sm">
        <p>{summary(ranking)}</p>
        <div className="flex flex-wrap gap-2 text-xs">
          <span className="rounded-full bg-surface-2 px-2 py-0.5">{SURFACE_LABEL[ranking.surface]}</span>
          <span className="rounded-full bg-surface-2 px-2 py-0.5">{SOURCE_LABEL[ranking.source]}</span>
          <span className="rounded-full bg-surface-2 px-2 py-0.5">{ranking.rank}번째</span>
          <span className="rounded-full bg-surface-2 px-2 py-0.5 font-semibold">점수 {fmtScore(ranking.score)}</span>
        </div>

        <table className="w-full text-xs">
          <caption className="mb-2 text-left text-muted">점수 = 가중치 × 값의 합 (이미 본 횟수는 뺀다)</caption>
          <thead className="text-muted">
            <tr>
              <th className="pb-1 text-left font-normal">구성 요소</th>
              <th className="pb-1 text-right font-normal">값</th>
              <th className="pb-1 text-right font-normal">가중치</th>
              <th className="w-28 pb-1 text-right font-normal">기여</th>
            </tr>
          </thead>
          <tbody>
            {ranking.parts.map((p) => (
              <tr key={p.key} className="border-t border-border" title={p.description}>
                <td className="py-1.5">
                  <div>{p.label}</div>
                  <div className="text-[11px] leading-tight text-muted">{p.description}</div>
                </td>
                <td className="py-1.5 text-right tabular-nums">{p.value.toFixed(2)}</td>
                <td className="py-1.5 text-right tabular-nums">×{p.weight}</td>
                <td className="py-1.5 pl-2">
                  <div className="flex items-center justify-end gap-1.5">
                    <span className={clsx('tabular-nums', p.contribution < 0 && 'text-danger')}>
                      {fmtScore(p.contribution)}
                    </span>
                    <span className="h-1.5 w-12 overflow-hidden rounded-full bg-surface-2">
                      <span
                        className={clsx('block h-full rounded-full', p.contribution < 0 ? 'bg-danger' : 'bg-primary')}
                        style={{ width: `${(Math.abs(p.contribution) / max) * 100}%` }}
                      />
                    </span>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="flex flex-col gap-1 border-t border-border pt-3">
          <Link to="/accounts/algorithm" onClick={onClose} className="font-semibold text-primary">
            회원님의 알고리즘에서 관심 주제 관리
          </Link>
          <Link to="/lab/algorithm" onClick={onClose} className="text-xs text-muted hover:underline">
            Lab: 가중치를 바꿔 결과 비교하기
          </Link>
        </div>
      </div>
    </Modal>
  )
}
