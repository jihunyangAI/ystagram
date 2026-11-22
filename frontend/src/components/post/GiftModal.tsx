import { useState } from 'react'
import { Star } from 'lucide-react'
import clsx from 'clsx'
import { Modal } from '../Modal'
import { toast } from '../Toast'
import { api } from '../../lib/api'
import { GIFT_OPTIONS, STAR_PRICE, won } from '../../lib/shop'
import type { PostView } from '../../lib/types'

/** 릴스에 별 선물 (가짜 결제: 카드 정보를 받지 않고 버튼 한 번으로 끝) */
export function GiftModal({ post, open, onClose }: { post: PostView; open: boolean; onClose: () => void }) {
  const [stars, setStars] = useState(GIFT_OPTIONS[0])
  const [busy, setBusy] = useState(false)

  async function send() {
    setBusy(true)
    try {
      await api.post(`/api/posts/${post.shortcode}/gifts`, { stars })
      toast(`${post.author.username}님에게 별 ${stars}개를 보냈어요 ⭐`)
      onClose()
    } catch (e) {
      toast((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="선물 보내기">
      <div className="space-y-4 p-4 text-sm">
        <p className="text-muted">별을 보내 {post.author.username}님의 릴스를 응원하세요. 연구용 가짜 결제라 실제로 돈이 나가지 않습니다.</p>
        <div className="grid grid-cols-2 gap-2">
          {GIFT_OPTIONS.map((n) => (
            <button
              key={n}
              onClick={() => setStars(n)}
              className={clsx(
                'flex flex-col items-center gap-1 rounded-xl border px-3 py-3',
                stars === n ? 'border-primary bg-primary/5' : 'border-border hover:bg-surface-2',
              )}
            >
              <span className="flex items-center gap-1 font-semibold">
                <Star size={16} className="fill-amber-400 text-amber-400" /> {n}개
              </span>
              <span className="text-xs text-muted">{won(n * STAR_PRICE)}</span>
            </button>
          ))}
        </div>
        <button className="btn btn-primary w-full" disabled={busy} onClick={() => void send()}>
          {won(stars * STAR_PRICE)} 결제하고 보내기 (가짜)
        </button>
      </div>
    </Modal>
  )
}
