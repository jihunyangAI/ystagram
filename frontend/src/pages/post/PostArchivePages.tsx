import { useState } from 'react'
import { NavLink, useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { Archive, ChevronLeft, Trash2 } from 'lucide-react'
import clsx from 'clsx'
import { api } from '../../lib/api'
import { useGrid } from '../../lib/social'
import type { GridItem, PostView } from '../../lib/types'
import { InfiniteGrid } from '../../components/post/Grid'
import { Modal, SheetButton } from '../../components/Modal'
import { toast } from '../../components/Toast'

/** 보관함 위쪽 탭: 스토리 / 게시물 */
export function ArchiveTabs() {
  const tab = (to: string, label: string) => (
    <NavLink
      to={to}
      replace
      className={({ isActive }) =>
        clsx('flex-1 border-b py-2.5 text-center text-sm font-semibold', isActive ? 'border-text' : 'border-border text-muted')
      }
    >
      {label}
    </NavLink>
  )
  return (
    <nav aria-label="보관함 종류" className="mb-3 flex">
      {tab('/archive/stories', '스토리')}
      {tab('/archive/posts', '게시물')}
    </nav>
  )
}

function Header({ title }: { title: string }) {
  const navigate = useNavigate()
  return (
    <header className="flex items-center gap-2 px-2 pt-4 pb-3 md:pt-8">
      <button aria-label="뒤로" onClick={() => navigate(-1)} className="p-2">
        <ChevronLeft size={26} />
      </button>
      <h1 className="text-lg font-bold">{title}</h1>
    </header>
  )
}

/** 게시물 보관함: 보관한 게시물은 나만 본다. 게시물의 ⋯ → "보관 취소"로 프로필에 되돌린다 */
export function PostArchivePage() {
  return (
    <div className="mx-auto max-w-[935px] pb-8">
      <Header title="보관함" />
      <ArchiveTabs />
      <p className="px-4 pb-3 text-sm text-muted">
        보관한 게시물은 회원님만 볼 수 있습니다. 게시물의 ⋯ 메뉴에서 "프로필에 표시"를 누르면 원래 자리로 돌아갑니다.
      </p>
      <InfiniteGrid
        queryKey={['archive-posts']}
        url="/api/me/archive/posts"
        empty={
          <div className="flex flex-col items-center gap-3 py-16 text-center text-muted">
            <Archive size={44} strokeWidth={1.3} />
            <p className="text-sm">보관한 게시물이 없습니다.</p>
          </div>
        }
      />
    </div>
  )
}

/** 최근 삭제됨: 지운 게시물은 30일 동안 여기서 되살릴 수 있다 */
export function RecentlyDeletedPage() {
  const query = useGrid(['recently-deleted'], '/api/me/recently-deleted')
  const qc = useQueryClient()
  const [picked, setPicked] = useState<GridItem | null>(null)
  const [busy, setBusy] = useState(false)
  const items = query.data?.pages.flatMap((p) => p.items) ?? []

  const restore = async () => {
    if (!picked) return
    setBusy(true)
    try {
      await api.post<PostView>(`/api/posts/${picked.shortcode}/restore`)
      toast('게시물을 복원했습니다')
      setPicked(null)
      void qc.invalidateQueries({ queryKey: ['grid'] })
      void qc.invalidateQueries({ queryKey: ['profile'] })
      void qc.invalidateQueries({ queryKey: ['feed'] })
    } catch (e) {
      toast((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-[935px] pb-8">
      <Header title="최근 삭제됨" />
      <p className="px-4 pb-3 text-sm text-muted">
        삭제한 게시물은 30일 동안 여기에 있다가 영구 삭제됩니다. 그 전에는 눌러서 복원할 수 있습니다.
      </p>
      {query.isLoading && <p className="p-6 text-center text-sm text-muted">불러오는 중…</p>}
      {query.isSuccess && items.length === 0 && (
        <div className="flex flex-col items-center gap-3 py-16 text-center text-muted">
          <Trash2 size={44} strokeWidth={1.3} />
          <p className="text-sm">최근 삭제한 콘텐츠가 없습니다.</p>
        </div>
      )}
      <ul className="grid grid-cols-3 gap-0.5 md:gap-1">
        {items.map((it) => (
          <li key={it.shortcode}>
            <button
              onClick={() => setPicked(it)}
              aria-label="삭제한 게시물"
              className="relative block aspect-[3/4] w-full overflow-hidden bg-surface-2"
            >
              <img src={it.thumbUrl} alt="" loading="lazy" className="h-full w-full object-cover opacity-80" />
            </button>
          </li>
        ))}
      </ul>
      {query.hasNextPage && (
        <button className="mx-auto mt-4 block text-sm text-muted" onClick={() => query.fetchNextPage()}>
          더 보기
        </button>
      )}
      <Modal open={picked !== null} onClose={() => setPicked(null)} bare title="삭제한 게시물">
        {picked && <img src={picked.thumbUrl} alt="" className="mx-auto mt-4 h-40 rounded object-cover" />}
        <SheetButton onClick={restore} disabled={busy}>
          복원
        </SheetButton>
        <SheetButton onClick={() => setPicked(null)}>취소</SheetButton>
      </Modal>
    </div>
  )
}
