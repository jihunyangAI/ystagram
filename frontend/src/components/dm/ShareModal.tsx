import { useState } from 'react'
import { Link2 } from 'lucide-react'
import { useMe } from '../../lib/auth'
import { sharePost } from '../../lib/dm'
import type { UserSummary } from '../../lib/types'
import { Modal } from '../Modal'
import { toast } from '../Toast'
import { copyLink } from '../post/PostParts'
import { UserPicker } from './UserPicker'

/** 게시물 공유: DM으로 보내기(여러 명, 메모 선택) 또는 링크 복사 */
export function ShareModal({ shortcode, open, onClose }: { shortcode: string; open: boolean; onClose: () => void }) {
  const { data: me } = useMe()
  const [selected, setSelected] = useState<UserSummary[]>([])
  const [note, setNote] = useState('')
  const [sending, setSending] = useState(false)
  if (!me) return null

  const send = () => {
    if (!selected.length || sending) return
    setSending(true)
    sharePost(shortcode, selected.map((u) => u.username), note)
      .then(() => {
        toast(`${selected.length === 1 ? `${selected[0].username}님에게` : `${selected.length}명에게`} 보냈습니다`)
        setSelected([])
        setNote('')
        onClose()
      }, (e: Error) => toast(e.message))
      .finally(() => setSending(false))
  }

  return (
    <Modal open={open} onClose={onClose} title="공유" className="flex h-[70vh] flex-col">
      {open && <UserPicker selected={selected} onChange={setSelected} meId={me.user.id} />}
      <div className="border-t border-border p-3">
        {selected.length > 0 ? (
          <>
            <input
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="메시지 쓰기..."
              aria-label="함께 보낼 메시지"
              maxLength={1000}
              className="mb-2 w-full bg-transparent px-1 py-2 text-sm outline-none placeholder:text-muted"
            />
            <button
              onClick={send}
              disabled={sending}
              className="w-full rounded-lg bg-primary py-2.5 text-sm font-semibold text-white hover:bg-primary-hover disabled:opacity-50"
            >
              {sending ? '보내는 중…' : selected.length > 1 ? '각각 보내기' : '보내기'}
            </button>
          </>
        ) : (
          <button
            onClick={() => {
              copyLink(shortcode)
              onClose()
            }}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-surface-2 py-2.5 text-sm font-semibold hover:bg-border"
          >
            <Link2 size={16} /> 링크 복사
          </button>
        )}
      </div>
    </Modal>
  )
}
