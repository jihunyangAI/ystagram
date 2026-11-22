import { useState } from 'react'
import { ConfirmModal, Modal, SheetButton } from '../Modal'
import { toast } from '../Toast'
import { Toggle } from '../post/CaptionField'
import { ReportModal } from '../report/ReportModal'
import { removeFollower, useRelationAction } from '../../lib/relations'
import type { Profile } from '../../lib/types'
import { useQueryClient } from '@tanstack/react-query'

/**
 * 다른 사람 프로필의 ⋯ 메뉴: 친한 친구·즐겨찾기·숨기기·제한·신고·차단·팔로워 삭제·링크 복사.
 * 차단·제한·숨기기는 상대에게 알리지 않는다.
 */
export function ProfileMenu({ profile, open, onClose }: { profile: Profile; open: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const action = useRelationAction()
  const [sheet, setSheet] = useState<'block' | 'mute' | 'remove' | 'report' | null>(null)
  const { user, flags } = profile
  const run = (a: Parameters<typeof action.mutate>[0]['action'], done: string) =>
    action.mutate(
      { username: user.username, action: a },
      {
        onSuccess: () => {
          toast(done)
          setSheet(null)
          onClose()
        },
        onError: (e) => toast(e.message),
      },
    )

  return (
    <>
      <Modal open={open && sheet === null} onClose={onClose} bare title={`${user.username} 옵션`}>
        {!flags.blocked && (
          <>
            <SheetButton
              onClick={() =>
                run({ op: 'close-friend', on: !flags.closeFriend }, flags.closeFriend ? '친한 친구에서 뺐습니다' : '친한 친구에 추가했습니다')
              }
            >
              {flags.closeFriend ? '친한 친구에서 삭제' : '친한 친구에 추가'}
            </SheetButton>
            {profile.relationship === 'FOLLOWING' && (
              <SheetButton
                onClick={() => run({ op: 'favorite', on: !flags.favorite }, flags.favorite ? '즐겨찾기에서 뺐습니다' : '즐겨찾기에 추가했습니다')}
              >
                {flags.favorite ? '즐겨찾기에서 삭제' : '즐겨찾기에 추가'}
              </SheetButton>
            )}
            <SheetButton onClick={() => setSheet('mute')}>숨기기</SheetButton>
            <SheetButton
              onClick={() =>
                run({ op: 'restrict', on: !flags.restricted }, flags.restricted ? '제한을 해제했습니다' : `${user.username}님을 제한했습니다`)
              }
            >
              {flags.restricted ? '제한 해제' : '제한'}
            </SheetButton>
            {profile.followsMe && (
              <SheetButton danger onClick={() => setSheet('remove')}>
                팔로워 삭제
              </SheetButton>
            )}
          </>
        )}
        <SheetButton danger onClick={() => setSheet('report')}>
          신고
        </SheetButton>
        <SheetButton danger onClick={() => (flags.blocked ? run({ op: 'block', on: false }, '차단을 해제했습니다') : setSheet('block'))}>
          {flags.blocked ? '차단 해제' : '차단'}
        </SheetButton>
        <SheetButton
          onClick={() => {
            const url = `${window.location.origin}/${user.username}`
            navigator.clipboard?.writeText(url).then(() => toast('프로필 링크를 복사했습니다'), () => toast(url))
            onClose()
          }}
        >
          프로필 링크 복사
        </SheetButton>
        <SheetButton onClick={onClose}>취소</SheetButton>
      </Modal>

      <ConfirmModal
        open={sheet === 'block'}
        onClose={() => setSheet(null)}
        title={`${user.username}님을 차단하시겠어요?`}
        message="상대는 회원님의 프로필·게시물·스토리를 찾을 수 없고, 서로 팔로우가 해제됩니다. 상대에게 알림은 가지 않습니다."
        confirmLabel="차단"
        pending={action.isPending}
        onConfirm={() => run({ op: 'block', on: true }, `${user.username}님을 차단했습니다`)}
      />
      <ConfirmModal
        open={sheet === 'remove'}
        onClose={() => setSheet(null)}
        title="팔로워를 삭제하시겠어요?"
        message={`${user.username}님에게 팔로워 목록에서 삭제되었다고 알리지 않습니다.`}
        confirmLabel="삭제"
        onConfirm={() =>
          removeFollower(user.username).then(
            () => {
              toast('팔로워에서 삭제했습니다')
              qc.invalidateQueries({ queryKey: ['profile'] })
              setSheet(null)
              onClose()
            },
            (e: Error) => toast(e.message),
          )
        }
      />
      {sheet === 'report' && (
        <ReportModal
          target={{ type: 'USER', target: user.username, label: '계정' }}
          onClose={() => {
            setSheet(null)
            onClose()
          }}
        />
      )}
      <MuteModal
        open={sheet === 'mute'}
        onClose={() => setSheet(null)}
        username={user.username}
        posts={flags.mutedPosts}
        stories={flags.mutedStories}
        onSave={(posts, stories) => run({ op: 'mute', posts, stories }, posts || stories ? '숨겼습니다' : '숨기기를 해제했습니다')}
      />
    </>
  )
}

function MuteModal({
  open,
  onClose,
  username,
  posts,
  stories,
  onSave,
}: {
  open: boolean
  onClose: () => void
  username: string
  posts: boolean
  stories: boolean
  onSave: (posts: boolean, stories: boolean) => void
}) {
  const [p, setP] = useState(posts)
  const [s, setS] = useState(stories)
  return (
    <Modal open={open} onClose={onClose} title="숨기기">
      <div className="space-y-4 p-5">
        <p className="text-sm text-muted">{username}님은 숨긴 사실을 알 수 없습니다. 팔로우는 그대로 유지됩니다.</p>
        <Toggle checked={p} onChange={setP} label="게시물" help="홈 피드에 이 사람의 게시물이 나오지 않습니다" />
        <Toggle checked={s} onChange={setS} label="스토리" help="스토리 트레이에 이 사람의 스토리가 나오지 않습니다" />
        <button className="btn btn-primary w-full" onClick={() => onSave(p, s)}>
          저장
        </button>
      </div>
    </Modal>
  )
}
