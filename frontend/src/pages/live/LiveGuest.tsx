import { useState } from 'react'
import { UserPlus, X } from 'lucide-react'
import { api } from '../../lib/api'
import { useRealtimeEvent } from '../../lib/realtime'
import type { UserSummary } from '../../lib/types'
import type { LiveComment, LiveView } from '../../lib/live'
import { Avatar } from '../../components/Avatar'
import { Modal, SheetButton } from '../../components/Modal'
import { toast } from '../../components/Toast'

/**
 * 라이브 게스트 (기획서 ⚪ 흉내): 진행자가 보고 있는 사람을 초대하고, 수락하면 화면 오른쪽 위에 게스트 자리가 생긴다.
 * 실제 게스트 영상은 연결하지 않고 프로필 사진으로 자리만 보여 준다.
 */
export function LiveGuestLayer({
  live,
  hosting,
  meId,
  comments,
  onChange,
}: {
  live: LiveView
  hosting: boolean
  meId: number | undefined
  comments: LiveComment[]
  onChange: (l: LiveView) => void
}) {
  const [picking, setPicking] = useState(false)
  const [invited, setInvited] = useState(false)

  const refresh = () => api.get<LiveView>(`/api/lives/${live.id}`).then(onChange, () => undefined)
  useRealtimeEvent((e) => {
    const d = e.data as { liveId?: number }
    if (d?.liveId !== live.id) return
    if (e.type === 'live:guest') void refresh()
    if (e.type === 'live:guest-invite') setInvited(true)
  })
  const act = (action: 'accept' | 'remove') =>
    api.post<LiveView>(`/api/lives/${live.id}/guest/${action}`).then(
      (l) => {
        onChange(l)
        setInvited(false)
        toast(action === 'accept' ? '게스트로 참여했습니다' : '게스트에서 나갔습니다')
      },
      (err: Error) => toast(err.message),
    )

  // 진행자가 초대할 수 있는 사람: 이 방송에 들어온 사람 (참여 댓글 기준)
  const watchers = new Map<number, UserSummary>()
  for (const c of comments) if (c.kind === 'JOIN' && c.user.id !== live.host.id) watchers.set(c.user.id, c.user)

  const isGuest = live.guest?.id === meId
  return (
    <>
      {live.guest && (
        <div className="absolute top-16 right-3 z-20 flex h-40 w-28 flex-col items-center justify-center gap-2 rounded-xl border border-white/30 bg-neutral-800/90 p-2 text-center">
          <Avatar user={live.guest} size={52} />
          <span className="w-full truncate text-xs font-semibold">{live.guest.username}</span>
          <span className="text-[10px] text-white/70">{live.guestStatus === 'JOINED' ? '게스트 (영상은 흉내)' : '초대 수락 기다리는 중'}</span>
          {(hosting || isGuest) && (
            <button aria-label="게스트 내보내기" className="absolute top-1 right-1 rounded-full bg-black/50 p-0.5" onClick={() => act('remove')}>
              <X size={14} />
            </button>
          )}
        </div>
      )}
      {hosting && !live.guest && (
        <button
          aria-label="게스트 초대"
          className="absolute top-16 right-3 z-20 flex items-center gap-1 rounded-full bg-black/50 px-3 py-1.5 text-xs font-semibold"
          onClick={() => setPicking(true)}
        >
          <UserPlus size={14} /> 게스트 초대
        </button>
      )}
      <Modal open={picking} onClose={() => setPicking(false)} title="게스트 초대">
        {watchers.size === 0 ? (
          <p className="p-6 text-center text-sm text-muted">아직 들어온 시청자가 없습니다.</p>
        ) : (
          <ul className="max-h-[50dvh] overflow-y-auto py-1">
            {[...watchers.values()].map((u) => (
              <li key={u.id}>
                <button
                  className="flex w-full items-center gap-3 px-4 py-2 text-left hover:bg-surface-2"
                  onClick={() =>
                    api.post<LiveView>(`/api/lives/${live.id}/guest`, { username: u.username }).then(
                      (l) => {
                        onChange(l)
                        setPicking(false)
                        toast(`${u.username}님을 게스트로 초대했습니다`)
                      },
                      (err: Error) => toast(err.message),
                    )
                  }
                >
                  <Avatar user={u} size={36} />
                  <span className="text-sm font-semibold">{u.username}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </Modal>
      <Modal open={invited || (isGuest && live.guestStatus === 'INVITED')} onClose={() => setInvited(false)} bare title="게스트 초대">
        <p className="p-5 text-center text-sm">
          <b>{live.host.username}</b>님이 라이브 게스트로 초대했습니다.
          <br />
          <span className="text-xs text-muted">연구용: 카메라는 연결하지 않고 프로필 사진으로 참여합니다.</span>
        </p>
        <SheetButton bold onClick={() => act('accept')}>
          참여하기
        </SheetButton>
        <SheetButton onClick={() => act('remove')}>거절</SheetButton>
      </Modal>
    </>
  )
}
