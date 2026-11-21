import { useEffect, useState } from 'react'
import { Bookmark, Check, Plus } from 'lucide-react'
import clsx from 'clsx'
import { useCollectionActions, usePostCollections, useSavedOverview, useToggleSave } from '../../lib/social'
import type { PostView } from '../../lib/types'
import { Modal } from '../Modal'
import { toast } from '../Toast'

/**
 * 저장(북마크). 누르면 바로 저장되고 잠깐 "컬렉션에 저장" 버튼이 뜬다.
 * 컬렉션에 넣으면 자동으로 저장, 저장을 취소하면 모든 컬렉션에서 빠진다.
 */
export function SaveButton({ post }: { post: PostView }) {
  const toggle = useToggleSave()
  const [hint, setHint] = useState(false)
  const [picker, setPicker] = useState(false)

  useEffect(() => {
    if (!hint) return
    const t = setTimeout(() => setHint(false), 4000)
    return () => clearTimeout(t)
  }, [hint])

  const click = () => {
    const save = !post.savedByMe
    toggle.mutate({ shortcode: post.shortcode, save }, { onError: (e) => toast(e.message) })
    setHint(save)
  }

  return (
    <div className="relative">
      <button
        aria-label={post.savedByMe ? '저장 취소' : '저장'}
        aria-pressed={post.savedByMe}
        className="p-0.5 hover:opacity-60"
        onClick={click}
      >
        <Bookmark size={24} className={clsx(post.savedByMe && 'fill-text')} />
      </button>
      {hint && (
        <div className="absolute right-0 bottom-9 z-20 flex w-max items-center gap-3 rounded-lg border border-border bg-bg px-3 py-2 text-sm shadow-lg">
          <span className="text-muted">저장됨</span>
          <button
            className="font-semibold text-primary"
            onClick={() => {
              setHint(false)
              setPicker(true)
            }}
          >
            컬렉션에 저장
          </button>
        </div>
      )}
      <CollectionPicker shortcode={post.shortcode} open={picker} onClose={() => setPicker(false)} />
    </div>
  )
}

function CollectionPicker({ shortcode, open, onClose }: { shortcode: string; open: boolean; onClose: () => void }) {
  const overview = useSavedOverview()
  const inside = usePostCollections(shortcode, open)
  const actions = useCollectionActions()
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)

  const run = (p: Promise<unknown>) => {
    setBusy(true)
    p.catch((e: Error) => toast(e.message)).finally(() => setBusy(false))
  }

  return (
    <Modal open={open} onClose={onClose} title="컬렉션에 저장">
      <ul className="max-h-[50vh] overflow-y-auto py-2">
        {overview.data?.collections.length === 0 && <li className="px-4 py-3 text-sm text-muted">아직 컬렉션이 없습니다.</li>}
        {overview.data?.collections.map((c) => {
          const on = inside.data?.includes(c.id!) ?? false
          return (
            <li key={c.id}>
              <button
                disabled={busy}
                onClick={() => run(on ? actions.remove(c.id!, shortcode) : actions.add(c.id!, shortcode))}
                className="flex w-full items-center gap-3 px-4 py-2 text-left hover:bg-surface-2"
              >
                {c.covers[0] ? (
                  <img src={c.covers[0]} alt="" className="h-11 w-11 rounded object-cover" />
                ) : (
                  <span className="h-11 w-11 rounded bg-surface-2" />
                )}
                <span className="min-w-0 flex-1 truncate text-sm">{c.name}</span>
                <span
                  className={clsx('flex h-6 w-6 items-center justify-center rounded-full border-2', on ? 'border-primary bg-primary text-white' : 'border-muted')}
                >
                  {on && <Check size={14} strokeWidth={3} />}
                </span>
              </button>
            </li>
          )
        })}
      </ul>
      <form
        className="flex items-center gap-2 border-t border-border px-4 py-3"
        onSubmit={(e) => {
          e.preventDefault()
          if (!name.trim()) return
          run(actions.create(name.trim(), shortcode).then(() => setName('')))
        }}
      >
        <Plus size={20} className="text-muted" />
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={100}
          placeholder="새 컬렉션 이름"
          aria-label="새 컬렉션 이름"
          className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-muted"
        />
        <button type="submit" disabled={!name.trim() || busy} className="text-sm font-semibold text-primary disabled:opacity-40">
          만들기
        </button>
      </form>
    </Modal>
  )
}
