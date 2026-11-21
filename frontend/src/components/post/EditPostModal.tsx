import { useState } from 'react'
import { useUpdatePost } from '../../lib/posts'
import type { PostView } from '../../lib/types'
import { Avatar } from '../Avatar'
import { Modal } from '../Modal'
import { toast } from '../Toast'
import { CaptionField, Toggle, countHashtags, MAX_CAPTION, MAX_HASHTAGS } from './CaptionField'

/** 게시 후 수정: 캡션 · 위치 · 좋아요 수 숨기기 · 댓글 기능 끄기 */
export function EditPostModal({ post, onClose }: { post: PostView; onClose: () => void }) {
  const update = useUpdatePost(post.shortcode)
  const [caption, setCaption] = useState(post.caption ?? '')
  const [location, setLocation] = useState(post.location?.name ?? '')
  const [hideLikeCount, setHide] = useState(post.hideLikeCount)
  const [commentsDisabled, setNoComments] = useState(post.commentsDisabled)
  const invalid = caption.length > MAX_CAPTION || countHashtags(caption) > MAX_HASHTAGS

  const save = () =>
    update.mutate(
      { caption, location, hideLikeCount, commentsDisabled },
      {
        onSuccess: () => {
          toast('게시물을 수정했습니다')
          onClose()
        },
        onError: (e) => toast(e.message),
      },
    )

  return (
    <Modal open onClose={onClose} title="정보 수정" className="max-w-[520px]">
      <div className="overflow-y-auto p-4">
        <div className="mb-3 flex items-center gap-2 text-sm font-semibold">
          <Avatar user={post.author} size={28} /> {post.author.username}
        </div>
        <CaptionField value={caption} onChange={setCaption} />
        <label className="mt-4 block text-sm">
          <span className="mb-1 block font-semibold">위치</span>
          <input className="input" value={location} maxLength={100} onChange={(e) => setLocation(e.target.value)} placeholder="위치 추가" />
        </label>
        <div className="mt-4 space-y-3">
          <Toggle checked={hideLikeCount} onChange={setHide} label="이 게시물의 좋아요 수 숨기기" />
          <Toggle checked={commentsDisabled} onChange={setNoComments} label="댓글 기능 해제" />
        </div>
      </div>
      <div className="flex justify-end gap-2 border-t border-border p-3">
        <button className="btn btn-secondary" onClick={onClose}>
          취소
        </button>
        <button className="btn btn-primary" disabled={invalid || update.isPending} onClick={save}>
          완료
        </button>
      </div>
    </Modal>
  )
}
