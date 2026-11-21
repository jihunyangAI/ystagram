import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { Bookmark, ChevronLeft, MoreHorizontal } from 'lucide-react'
import { ConfirmModal, Modal, SheetButton } from '../components/Modal'
import { toast } from '../components/Toast'
import { InfiniteGrid } from '../components/post/Grid'
import { useCollection, useCollectionActions } from '../lib/social'

/** 저장됨 → "모든 게시물" 또는 컬렉션 하나 (본인만) */
export function SavedCollectionPage() {
  const { username = '', id = 'all' } = useParams()
  const navigate = useNavigate()
  const isAll = id === 'all'
  const collection = useCollection(id)
  const actions = useCollectionActions()
  const [menu, setMenu] = useState<'menu' | 'rename' | 'delete' | null>(null)
  const [name, setName] = useState('')

  const title = isAll ? '모든 게시물' : (collection.data?.name ?? '')
  if (!isAll && collection.isError) return <p className="p-10 text-center text-sm text-muted">{collection.error.message}</p>

  return (
    <div className="mx-auto max-w-[935px] pb-8">
      <header className="flex items-center gap-2 px-2 pt-4 pb-3 md:pt-8">
        <button aria-label="뒤로" onClick={() => navigate(`/${username}`)} className="p-2">
          <ChevronLeft size={26} />
        </button>
        <h1 className="flex-1 truncate text-lg font-bold">{title}</h1>
        {!isAll && (
          <button aria-label="컬렉션 옵션" className="p-2" onClick={() => setMenu('menu')}>
            <MoreHorizontal size={22} />
          </button>
        )}
      </header>
      <InfiniteGrid
        queryKey={['saved', id]}
        url={isAll ? '/api/me/saved/posts' : `/api/me/collections/${id}/posts`}
        empty={
          <div className="flex flex-col items-center gap-3 py-16 text-center text-muted">
            <Bookmark size={40} strokeWidth={1.3} />
            <p className="text-sm">{isAll ? '저장한 게시물이 없습니다.' : '이 컬렉션에 게시물을 추가하세요. 게시물의 저장 버튼 → "컬렉션에 저장"'}</p>
          </div>
        }
      />

      <Modal open={menu === 'menu'} onClose={() => setMenu(null)} bare title="컬렉션 옵션">
        <SheetButton
          onClick={() => {
            setName(collection.data?.name ?? '')
            setMenu('rename')
          }}
        >
          이름 바꾸기
        </SheetButton>
        <SheetButton danger onClick={() => setMenu('delete')}>
          컬렉션 삭제
        </SheetButton>
        <SheetButton onClick={() => setMenu(null)}>취소</SheetButton>
      </Modal>
      <Modal open={menu === 'rename'} onClose={() => setMenu(null)} title="컬렉션 이름 바꾸기">
        <form
          className="flex flex-col gap-3 p-4"
          onSubmit={(e) => {
            e.preventDefault()
            if (!name.trim()) return
            actions.rename(Number(id), name.trim()).then(() => setMenu(null), (err: Error) => toast(err.message))
          }}
        >
          <input
            autoFocus
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={100}
            aria-label="컬렉션 이름"
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none"
          />
          <button type="submit" className="rounded-lg bg-primary py-2 text-sm font-semibold text-white">
            완료
          </button>
        </form>
      </Modal>
      <ConfirmModal
        open={menu === 'delete'}
        onClose={() => setMenu(null)}
        title="컬렉션을 삭제하시겠어요?"
        message="저장한 게시물은 '모든 게시물'에 그대로 남습니다."
        confirmLabel="삭제"
        onConfirm={() =>
          actions.delete(Number(id)).then(
            () => {
              toast('컬렉션을 삭제했습니다')
              navigate(`/${username}`)
            },
            (e: Error) => toast(e.message),
          )
        }
      />
    </div>
  )
}
