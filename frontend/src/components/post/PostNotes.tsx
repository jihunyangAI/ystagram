import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { patchPost } from '../../lib/posts'
import type { PostView } from '../../lib/types'
import { Avatar } from '../Avatar'
import { Modal } from '../Modal'
import { toast } from '../Toast'

/** 사진 위 왼쪽 위에 뜨는 노트 말풍선 (최대 3개) */
export function PostNoteBubbles({ post }: { post: PostView }) {
  const notes = post.extras.notes
  if (!notes.length) return null
  return (
    <div className="pointer-events-none absolute top-3 left-3 z-10 flex max-w-[70%] flex-col items-start gap-1.5" aria-label="게시물 노트">
      {notes.slice(0, 3).map((n) => (
        <div key={n.id} className="flex items-center gap-1.5">
          <Avatar user={n.user} size={22} />
          <span className="rounded-2xl rounded-tl-sm bg-white/90 px-2.5 py-1 text-xs text-black shadow">
            <b className="mr-1">{n.mine ? '내 노트' : n.user.username}</b>
            {n.body}
          </span>
        </div>
      ))}
    </div>
  )
}

/** 노트 남기기·바꾸기·지우기 (맞팔로우만) */
export function PostNoteModal({ post, open, onClose }: { post: PostView; open: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const mine = post.extras.notes.find((n) => n.mine)
  const [body, setBody] = useState(mine?.body ?? '')
  const [busy, setBusy] = useState(false)
  const save = async (remove: boolean) => {
    setBusy(true)
    try {
      const next = remove
        ? await api.delete<PostView>(`/api/posts/${post.shortcode}/note`)
        : await api.put<PostView>(`/api/posts/${post.shortcode}/note`, { body })
      patchPost(qc, post.shortcode, () => next)
      toast(remove ? '노트를 지웠습니다' : '노트를 남겼습니다. 3일 동안 서로 팔로우하는 사람에게 보입니다')
      onClose()
    } catch (e) {
      toast((e as Error).message)
    } finally {
      setBusy(false)
    }
  }
  const length = [...body].length
  return (
    <Modal open={open} onClose={onClose} title="게시물에 노트 남기기">
      <div className="space-y-3 p-4">
        <p className="text-xs text-muted">
          {post.author.username}님과 서로 팔로우하는 사람에게 3일 동안 사진 위에 보입니다. 게시물 주인은 모든 노트를 봅니다.
        </p>
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={2}
          aria-label="노트 내용"
          placeholder="짧은 생각을 남겨 보세요"
          className="w-full resize-none rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-muted"
        />
        <p className={length > 60 ? 'text-right text-xs text-danger' : 'text-right text-xs text-muted'}>{length}/60</p>
        <button className="btn btn-primary w-full" disabled={busy || !body.trim() || length > 60} onClick={() => save(false)}>
          {mine ? '노트 바꾸기' : '노트 남기기'}
        </button>
        {mine && (
          <button className="w-full py-1 text-sm text-danger" disabled={busy} onClick={() => save(true)}>
            노트 지우기
          </button>
        )}
      </div>
    </Modal>
  )
}
