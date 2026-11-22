import { Link, useNavigate } from 'react-router'
import { useLives } from '../../lib/live'
import { Plus } from 'lucide-react'
import { useMe } from '../../lib/auth'
import { useStoryTray } from '../../lib/stories'
import { Avatar } from '../Avatar'
import { StoryRingAvatar } from './StoryRingAvatar'

/** 홈 위쪽 스토리 트레이: 내 스토리 → 안 본 스토리 → 다 본 스토리 */
export function StoryTray() {
  const { data: me } = useMe()
  const { data: tray, isLoading } = useStoryTray()
  const { data: lives } = useLives()
  const navigate = useNavigate()
  if (!me) return null

  const mine = tray?.find((t) => t.me)
  const others = tray?.filter((t) => !t.me) ?? []
  // 뷰어에서 다음 사람으로 넘어갈 순서
  const queue = (tray ?? []).map((t) => t.user.username)
  const open = (username: string) => navigate(`/stories/${username}`, { state: { queue } })

  return (
    <div className="no-scrollbar flex gap-3 overflow-x-auto border-b border-border px-3 py-3 md:mb-2 md:border-b-0 md:px-0" aria-label="스토리">
      <div className="flex w-[70px] shrink-0 flex-col items-center gap-1">
        <div className="relative">
          {mine ? (
            <button onClick={() => open(me.user.username)} aria-label="내 스토리 보기">
              <StoryRingAvatar user={me.user} ring="SEEN" size={66} />
            </button>
          ) : (
            <Link to="/create/story" aria-label="스토리 만들기">
              <Avatar user={me.user} size={66} />
            </Link>
          )}
          <Link
            to="/create/story"
            aria-label="스토리 추가"
            className="absolute right-0 bottom-0 flex h-5 w-5 items-center justify-center rounded-full border-2 border-bg bg-primary text-white"
          >
            <Plus size={12} strokeWidth={3} />
          </Link>
        </div>
        <span className="w-full truncate text-center text-xs text-muted">내 스토리</span>
      </div>

      {isLoading &&
        Array.from({ length: 6 }, (_, i) => (
          <div key={i} className="flex w-[70px] shrink-0 flex-col items-center gap-1">
            <div className="h-[66px] w-[66px] animate-pulse rounded-full bg-surface-2" />
            <div className="h-3 w-12 animate-pulse rounded bg-surface-2" />
          </div>
        ))}

      {lives?.map((l) => (
        <Link
          key={`live-${l.id}`}
          to={`/live/${l.id}`}
          className="flex w-[70px] shrink-0 flex-col items-center gap-1"
          aria-label={`${l.host.username}님의 라이브 방송`}
        >
          <span className="relative rounded-full bg-gradient-to-tr from-[#feda75] via-[#d62976] to-[#4f5bd5] p-[2.5px]">
            <span className="block rounded-full border-2 border-bg">
              <Avatar user={l.host} size={57} />
            </span>
            <span className="absolute -bottom-1 left-1/2 -translate-x-1/2 rounded border-2 border-bg bg-gradient-to-r from-[#d62976] to-[#fa7e1e] px-1 text-[9px] leading-[13px] font-bold text-white">
              LIVE
            </span>
          </span>
          <span className="w-full truncate text-center text-xs">{l.mine ? '내 라이브' : l.host.username}</span>
        </Link>
      ))}

      {others.map((t) => (
        <button
          key={t.user.id}
          onClick={() => open(t.user.username)}
          className="flex w-[70px] shrink-0 flex-col items-center gap-1"
          aria-label={`${t.user.username}님의 스토리${t.ring === 'UNSEEN' ? ' (새 스토리)' : ''}`}
        >
          <StoryRingAvatar user={t.user} ring={t.ring} size={66} />
          <span className={`w-full truncate text-center text-xs ${t.ring === 'UNSEEN' ? '' : 'text-muted'}`}>
            {t.user.username}
          </span>
        </button>
      ))}
    </div>
  )
}
