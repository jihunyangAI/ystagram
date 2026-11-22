import { Link } from 'react-router'
import { Crown, ShoppingBag } from 'lucide-react'
import { won } from '../../lib/shop'
import type { PostView } from '../../lib/types'

/** 사진 아래: 구독자 전용 표시 + 태그된 제품 (누르면 제품 페이지) */
export function PostCommerceBar({ post }: { post: PostView }) {
  const { products, subscribersOnly } = post.extras
  if (!subscribersOnly && products.length === 0) return null
  return (
    <div className="space-y-2 px-3 pt-2 md:px-0">
      {subscribersOnly && (
        <span className="inline-flex items-center gap-1 rounded-full bg-violet-500/10 px-2 py-0.5 text-xs font-semibold text-violet-600 dark:text-violet-300">
          <Crown size={12} /> 구독자 전용
        </span>
      )}
      {products.length > 0 && (
        <ul className="no-scrollbar flex gap-2 overflow-x-auto" aria-label="태그된 제품">
          {products.map((p) => (
            <li key={p.id} className="shrink-0">
              <Link to={`/shop/p/${p.id}`} className="flex w-52 items-center gap-2 rounded-lg border border-border p-1.5 pr-3 hover:bg-surface-2">
                <img src={p.thumbUrl} alt="" className="size-11 rounded object-cover" />
                <span className="min-w-0 text-xs">
                  <span className="block truncate font-semibold">{p.name}</span>
                  <span className="text-muted">{won(p.price)}</span>
                </span>
                <ShoppingBag size={14} className="ml-auto shrink-0 text-muted" />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
