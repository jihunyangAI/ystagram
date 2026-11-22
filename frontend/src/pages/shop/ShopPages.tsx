import { useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bookmark, CheckCircle2, ChevronLeft, ImagePlus, Minus, Plus, ShoppingBag, Trash2 } from 'lucide-react'
import clsx from 'clsx'
import { Avatar } from '../../components/Avatar'
import { ConfirmModal, Modal } from '../../components/Modal'
import { toast } from '../../components/Toast'
import { GridTiles } from '../../components/post/Grid'
import { api } from '../../lib/api'
import { useMe } from '../../lib/auth'
import { fullDate } from '../../lib/format'
import { type OrderView, useWish, won } from '../../lib/shop'
import type { GridItem, ProductView } from '../../lib/types'

interface ProductDetail {
  product: ProductView
  posts: GridItem[]
  more: ProductView[]
}

function Header({ title }: { title: string }) {
  const navigate = useNavigate()
  return (
    <header className="sticky top-0 z-20 flex items-center gap-3 border-b border-border bg-bg px-3 py-3">
      <button aria-label="뒤로" onClick={() => navigate(-1)}>
        <ChevronLeft size={26} />
      </button>
      <h1 className="font-semibold">{title}</h1>
    </header>
  )
}

/** 제품 카드 (샵 탭·위시리스트·같은 판매자의 다른 제품) */
export function ProductCard({ p }: { p: ProductView }) {
  return (
    <Link to={`/shop/p/${p.id}`} className="block">
      <img src={p.thumbUrl} alt="" className="aspect-square w-full rounded-md bg-surface-2 object-cover" loading="lazy" />
      <p className="mt-1 truncate text-sm">{p.name}</p>
      <p className="text-sm font-semibold">{won(p.price)}</p>
    </Link>
  )
}

/** 제품 페이지 (/shop/p/:id): 사진·가격·설명, 위시리스트, 가짜 결제, 이 제품이 태그된 게시물 */
export function ProductPage() {
  const { id } = useParams()
  const qc = useQueryClient()
  const { data, isError, error } = useQuery({
    queryKey: ['product', Number(id)],
    queryFn: () => api.get<ProductDetail>(`/api/products/${id}`),
  })
  const wish = useWish()
  const [checkout, setCheckout] = useState(false)

  if (isError) return <p className="p-10 text-center text-sm text-danger">{error.message}</p>
  if (!data) return <p className="p-10 text-center text-sm text-muted">불러오는 중…</p>
  const p = data.product

  return (
    <div className="mx-auto max-w-[935px] pb-10">
      <Header title={p.seller.username} />
      <div className="md:flex md:gap-8 md:p-6">
        <img src={p.imageUrl} alt={p.name} className="aspect-square w-full bg-surface-2 object-cover md:w-1/2 md:rounded-lg" />
        <div className="space-y-4 p-4 md:flex-1 md:p-0">
          <Link to={`/${p.seller.username}`} className="flex items-center gap-2 text-sm font-semibold">
            <Avatar user={p.seller} size={28} /> {p.seller.username}
          </Link>
          <div>
            <h2 className="text-xl font-semibold">{p.name}</h2>
            <p className="mt-1 text-lg">{won(p.price)}</p>
          </div>
          {p.description && <p className="text-sm whitespace-pre-line text-muted">{p.description}</p>}
          {!p.mine && (
            <div className="flex gap-2">
              <button className="btn btn-primary flex-1" onClick={() => setCheckout(true)}>
                <ShoppingBag size={18} /> 구매하기
              </button>
              <button
                aria-label={p.wishlisted ? '위시리스트에서 빼기' : '위시리스트에 담기'}
                className="btn btn-secondary"
                onClick={() =>
                  wish.mutate(
                    { id: p.id, on: !p.wishlisted },
                    { onSuccess: () => toast(p.wishlisted ? '위시리스트에서 뺐습니다' : '위시리스트에 담았습니다') },
                  )
                }
              >
                <Bookmark size={18} className={clsx(p.wishlisted && 'fill-current')} />
              </button>
            </div>
          )}
          {p.mine && <p className="rounded-lg bg-surface-2 p-3 text-sm text-muted">내 제품입니다. 게시물 ⋯ 메뉴의 "제품 태그"로 사진에 붙일 수 있어요.</p>}
          <p className="text-xs text-muted">연구용 가짜 상점입니다. 실제로 결제·배송되지 않습니다.</p>
        </div>
      </div>

      {data.posts.length > 0 && (
        <section className="mt-6 px-0 md:px-6">
          <h3 className="px-4 pb-2 text-sm font-semibold md:px-0">이 제품이 나온 게시물</h3>
          <GridTiles items={data.posts} />
        </section>
      )}
      {data.more.length > 0 && (
        <section className="mt-6 px-4 md:px-6">
          <h3 className="pb-2 text-sm font-semibold">{p.seller.username}의 다른 제품</h3>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {data.more.map((m) => (
              <ProductCard key={m.id} p={m} />
            ))}
          </div>
        </section>
      )}
      {checkout && (
        <CheckoutModal
          product={p}
          onClose={() => setCheckout(false)}
          onDone={() => void qc.invalidateQueries({ queryKey: ['orders'] })}
        />
      )}
    </div>
  )
}

/** 가짜 결제: 카드 정보를 받지 않는다. 수량을 고르고 버튼을 누르면 "결제 완료" */
function CheckoutModal({ product, onClose, onDone }: { product: ProductView; onClose: () => void; onDone: () => void }) {
  const [qty, setQty] = useState(1)
  const [order, setOrder] = useState<OrderView | null>(null)
  const pay = useMutation({
    mutationFn: () => api.post<OrderView>(`/api/products/${product.id}/orders`, { quantity: qty }),
    onSuccess: (o) => {
      setOrder(o)
      onDone()
    },
    onError: (e) => toast((e as Error).message),
  })
  return (
    <Modal open onClose={onClose} title={order ? '주문 완료' : '결제'}>
      {order ? (
        <div className="space-y-3 p-6 text-center text-sm">
          <CheckCircle2 size={48} className="mx-auto text-green-500" />
          <p className="font-semibold">결제가 완료되었어요 (가짜)</p>
          <p className="text-muted">
            {product.name} × {order.quantity} · {won(order.amount)}
          </p>
          <Link to="/accounts/shop" className="btn btn-secondary w-full" onClick={onClose}>
            주문 내역 보기
          </Link>
        </div>
      ) : (
        <div className="space-y-4 p-4 text-sm">
          <div className="flex items-center gap-3">
            <img src={product.thumbUrl} alt="" className="size-16 rounded object-cover" />
            <div className="min-w-0 flex-1">
              <p className="truncate font-semibold">{product.name}</p>
              <p className="text-muted">{product.seller.username}</p>
            </div>
          </div>
          <div className="flex items-center justify-between">
            <span>수량</span>
            <span className="flex items-center gap-3">
              <button aria-label="하나 빼기" className="rounded-full border border-border p-1" disabled={qty <= 1} onClick={() => setQty(qty - 1)}>
                <Minus size={14} />
              </button>
              <b className="w-4 text-center tabular-nums">{qty}</b>
              <button aria-label="하나 더" className="rounded-full border border-border p-1" disabled={qty >= 10} onClick={() => setQty(qty + 1)}>
                <Plus size={14} />
              </button>
            </span>
          </div>
          <div className="flex items-center justify-between border-t border-border pt-3 text-base">
            <span>합계</span>
            <b>{won(product.price * qty)}</b>
          </div>
          <p className="rounded-lg bg-surface-2 p-3 text-xs text-muted">
            연구용 가짜 결제입니다. 카드 정보를 받지 않고, 실제로 돈이 나가거나 물건이 배송되지 않습니다.
          </p>
          <button className="btn btn-primary w-full" disabled={pay.isPending} onClick={() => pay.mutate()}>
            {won(product.price * qty)} 결제하기 (가짜)
          </button>
        </div>
      )}
    </Modal>
  )
}

/** 설정 → 쇼핑 (/accounts/shop): 위시리스트·주문 내역 */
export function MyShopPage() {
  const [tab, setTab] = useState<'wishlist' | 'orders'>('wishlist')
  const wishlist = useQuery({ queryKey: ['wishlist'], queryFn: () => api.get<ProductView[]>('/api/me/wishlist') })
  const orders = useQuery({ queryKey: ['orders'], queryFn: () => api.get<OrderView[]>('/api/me/orders') })
  return (
    <div className="mx-auto max-w-[640px] pb-10">
      <Header title="쇼핑" />
      <div className="flex border-b border-border">
        {(
          [
            ['wishlist', `위시리스트 ${wishlist.data?.length ?? ''}`],
            ['orders', `주문 내역 ${orders.data?.length ?? ''}`],
          ] as const
        ).map(([k, label]) => (
          <button
            key={k}
            onClick={() => setTab(k)}
            className={clsx('flex-1 py-3 text-sm font-semibold', tab === k ? 'border-b-2 border-text' : 'text-muted')}
          >
            {label}
          </button>
        ))}
      </div>
      {tab === 'wishlist' ? (
        <div className="grid grid-cols-2 gap-3 p-4 sm:grid-cols-3">
          {wishlist.data?.length === 0 && <p className="col-span-full py-10 text-center text-sm text-muted">제품 페이지의 책갈피를 눌러 담아 보세요.</p>}
          {wishlist.data?.map((p) => (
            <ProductCard key={p.id} p={p} />
          ))}
        </div>
      ) : (
        <ul className="divide-y divide-border">
          {orders.data?.length === 0 && <li className="py-10 text-center text-sm text-muted">주문 내역이 없습니다.</li>}
          {orders.data?.map((o) => (
            <li key={o.id}>
              <Link to={`/shop/p/${o.product.id}`} className="flex items-center gap-3 px-4 py-3 hover:bg-surface-2">
                <img src={o.product.thumbUrl} alt="" className="size-14 rounded object-cover" />
                <div className="min-w-0 flex-1 text-sm">
                  <p className="truncate font-semibold">{o.product.name}</p>
                  <p className="text-muted">
                    {o.product.seller.username} · {o.quantity}개 · {won(o.amount)}
                  </p>
                  <p className="text-xs text-muted">{fullDate(o.createdAt)} · 결제 완료 (가짜)</p>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** 제품 관리 (/shop/manage): 크리에이터·비즈니스 계정이 제품을 올리고 지운다 */
export function ManageProductsPage() {
  const { data: me } = useMe()
  const username = me?.user.username ?? ''
  const qc = useQueryClient()
  const { data } = useQuery({
    queryKey: ['shop', username],
    queryFn: () => api.get<ProductView[]>(`/api/users/${username}/products`),
    enabled: !!username,
  })
  const [name, setName] = useState('')
  const [price, setPrice] = useState('')
  const [description, setDescription] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [removing, setRemoving] = useState<ProductView | null>(null)
  const input = useRef<HTMLInputElement>(null)

  const create = useMutation({
    mutationFn: () => {
      const form = new FormData()
      form.append('image', file!, file!.name)
      const body = { name, price: Number(price.replace(/[^0-9]/g, '')), description }
      form.append('data', new Blob([JSON.stringify(body)], { type: 'application/json' }))
      return api.upload<ProductView>('POST', '/api/products', form)
    },
    onSuccess: () => {
      toast('제품을 등록했습니다')
      setName('')
      setPrice('')
      setDescription('')
      setFile(null)
      setPreview(null)
      void qc.invalidateQueries({ queryKey: ['shop', username] })
    },
    onError: (e) => toast((e as Error).message),
  })
  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/api/products/${id}`),
    onSuccess: () => {
      setRemoving(null)
      void qc.invalidateQueries({ queryKey: ['shop', username] })
    },
  })

  return (
    <div className="mx-auto max-w-[640px] pb-10">
      <Header title="제품 관리" />
      <section className="space-y-3 border-b border-border p-4">
        <h2 className="text-sm font-semibold">새 제품</h2>
        <input
          ref={input}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          hidden
          onChange={(e) => {
            const f = e.target.files?.[0]
            if (!f) return
            setFile(f)
            setPreview(URL.createObjectURL(f))
          }}
        />
        <div className="flex gap-3">
          <button
            onClick={() => input.current?.click()}
            className="flex size-24 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-dashed border-border text-muted"
            aria-label="제품 사진 고르기"
          >
            {preview ? <img src={preview} alt="" className="h-full w-full object-cover" /> : <ImagePlus size={28} />}
          </button>
          <div className="min-w-0 flex-1 space-y-2">
            <input className="input" placeholder="제품 이름" maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
            <input className="input" placeholder="가격 (원)" inputMode="numeric" value={price} onChange={(e) => setPrice(e.target.value)} />
          </div>
        </div>
        <textarea className="input min-h-20" placeholder="설명 (선택)" maxLength={1000} value={description} onChange={(e) => setDescription(e.target.value)} />
        <button className="btn btn-primary w-full" disabled={!file || !name.trim() || !price || create.isPending} onClick={() => create.mutate()}>
          등록
        </button>
      </section>
      <ul className="divide-y divide-border">
        {data?.length === 0 && <li className="py-10 text-center text-sm text-muted">등록한 제품이 없습니다.</li>}
        {data?.map((p) => (
          <li key={p.id} className="flex items-center gap-3 px-4 py-3">
            <Link to={`/shop/p/${p.id}`} className="flex min-w-0 flex-1 items-center gap-3">
              <img src={p.thumbUrl} alt="" className="size-14 rounded object-cover" />
              <span className="min-w-0 text-sm">
                <span className="block truncate font-semibold">{p.name}</span>
                <span className="text-muted">
                  {won(p.price)} · 태그된 게시물 {p.taggedPosts}개
                </span>
              </span>
            </Link>
            <button aria-label="제품 지우기" className="p-2 text-muted" onClick={() => setRemoving(p)}>
              <Trash2 size={18} />
            </button>
          </li>
        ))}
      </ul>
      <ConfirmModal
        open={!!removing}
        title="제품을 지울까요?"
        message="게시물에 붙은 제품 태그도 함께 사라집니다."
        confirmLabel="지우기"
        pending={remove.isPending}
        onConfirm={() => removing && remove.mutate(removing.id)}
        onClose={() => setRemoving(null)}
      />
    </div>
  )
}

/** 게시물 ⋯ → 제품 태그: 내 제품(또는 검색)에서 최대 5개 골라 사진 가운데쯤에 붙인다 */
export function TagProductsModal({
  shortcode,
  current,
  onClose,
}: {
  shortcode: string
  current: number[]
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [q, setQ] = useState('')
  const [picked, setPicked] = useState<number[]>(current)
  const { data } = useQuery({
    queryKey: ['product-search', q],
    queryFn: () => api.get<ProductView[]>(`/api/products?q=${encodeURIComponent(q)}`),
  })
  const save = useMutation({
    mutationFn: () =>
      api.put(`/api/posts/${shortcode}/products`, {
        products: picked.map((id, i) => ({ productId: id, mediaIndex: 0, x: 0.3 + (i % 3) * 0.2, y: 0.35 + Math.floor(i / 3) * 0.2 })),
      }),
    onSuccess: () => {
      toast('제품 태그를 저장했습니다')
      void qc.invalidateQueries({ queryKey: ['post', shortcode] })
      void qc.invalidateQueries({ queryKey: ['feed'] })
      onClose()
    },
    onError: (e) => toast((e as Error).message),
  })
  const toggle = (id: number) =>
    setPicked((list) => (list.includes(id) ? list.filter((x) => x !== id) : list.length >= 5 ? list : [...list, id]))

  return (
    <Modal open onClose={onClose} title="제품 태그">
      <div className="space-y-3 p-4">
        <input className="input" placeholder="제품·판매자 검색 (비우면 내 제품)" value={q} onChange={(e) => setQ(e.target.value)} />
        <ul className="max-h-[50vh] divide-y divide-border overflow-y-auto">
          {data?.length === 0 && <li className="py-6 text-center text-sm text-muted">제품이 없습니다.</li>}
          {data?.map((p) => (
            <li key={p.id}>
              <label className="flex cursor-pointer items-center gap-3 py-2">
                <input type="checkbox" checked={picked.includes(p.id)} onChange={() => toggle(p.id)} />
                <img src={p.thumbUrl} alt="" className="size-10 rounded object-cover" />
                <span className="min-w-0 text-sm">
                  <span className="block truncate font-semibold">{p.name}</span>
                  <span className="text-muted">
                    {p.seller.username} · {won(p.price)}
                  </span>
                </span>
              </label>
            </li>
          ))}
        </ul>
        <p className="text-xs text-muted">{picked.length}/5개 선택</p>
        <button className="btn btn-primary w-full" disabled={save.isPending} onClick={() => save.mutate()}>
          저장
        </button>
      </div>
    </Modal>
  )
}
