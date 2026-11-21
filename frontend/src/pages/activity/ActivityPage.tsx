import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import { ChevronRight, Heart } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { Avatar } from '../../components/Avatar'
import { notificationVerb } from '../../components/RealtimeBridge'
import { FollowButton } from '../../components/user/FollowButton'
import { nowMs } from '../../lib/clock'
import { timeAgo } from '../../lib/format'
import { useActivity, useMarkNotificationsRead } from '../../lib/social'
import type { ActivityItem } from '../../lib/types'

const DAY = 86_400_000

/** 인스타그램 알림 화면의 시간 묶음 */
function section(iso: string): string {
  const now = new Date(nowMs())
  const t = new Date(iso)
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  if (t.getTime() >= startOfToday) return '오늘'
  if (now.getTime() - t.getTime() < 7 * DAY) return '이번 주'
  if (now.getTime() - t.getTime() < 30 * DAY) return '이번 달'
  return '이전 활동'
}

/** 활동(알림) 화면: 팔로우 요청 줄 + 시간대별 알림. 같은 게시물 좋아요는 "a님, b님 외 N명"으로 묶인다 */
export function ActivityPage() {
  const { data, isLoading, isError, error } = useActivity()
  const markRead = useMarkNotificationsRead()
  const qc = useQueryClient()
  const marked = useRef(false)

  // 화면을 열면 모두 읽음으로 (목록의 파란 점은 이번에 본 것까지 유지)
  useEffect(() => {
    if (data && !marked.current) {
      marked.current = true
      markRead.mutate()
    }
  }, [data, markRead])
  useEffect(() => () => void qc.invalidateQueries({ queryKey: ['activity'] }), [qc])

  const groups: [string, ActivityItem[]][] = []
  for (const item of data?.items ?? []) {
    const name = section(item.createdAt)
    const last = groups[groups.length - 1]
    if (last && last[0] === name) last[1].push(item)
    else groups.push([name, [item]])
  }

  return (
    <div className="mx-auto max-w-[600px] pb-8">
      <h1 className="px-4 pt-6 pb-2 text-xl font-bold md:pt-8">알림</h1>

      {data && data.requests.count > 0 && (
        <Link to="/notifications/requests" className="flex items-center gap-3 px-4 py-3 hover:bg-surface-2">
          <span className="relative shrink-0">
            {data.requests.latest && <Avatar user={data.requests.latest} size={44} />}
            <span className="absolute -top-1 -right-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-danger px-1 text-[11px] font-bold text-white">
              {data.requests.count}
            </span>
          </span>
          <span className="min-w-0 flex-1 text-sm">
            <b className="block">팔로우 요청</b>
            <span className="text-muted">
              {data.requests.latest?.username}
              {data.requests.count > 1 ? `님 외 ${data.requests.count - 1}명` : '님'}
            </span>
          </span>
          <ChevronRight size={20} className="text-muted" />
        </Link>
      )}

      {isLoading && <p className="p-6 text-center text-sm text-muted">불러오는 중…</p>}
      {isError && <p className="p-6 text-center text-sm text-danger">{error.message}</p>}
      {data && data.items.length === 0 && (
        <div className="flex flex-col items-center gap-3 px-6 py-16 text-center">
          <span className="rounded-full border-2 border-text p-4">
            <Heart size={36} strokeWidth={1.5} />
          </span>
          <p className="font-semibold">회원님의 게시물 활동</p>
          <p className="text-sm text-muted">누군가 회원님의 게시물을 좋아하거나 댓글을 남기면 여기에 표시됩니다.</p>
        </div>
      )}

      {groups.map(([name, items]) => (
        <section key={name} className="border-t border-border pt-3 first-of-type:border-t-0">
          <h2 className="px-4 pb-1 font-bold">{name}</h2>
          <ul>
            {items.map((item) => (
              <ActivityRow key={item.id} item={item} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

function ActivityRow({ item }: { item: ActivityItem }) {
  const [first, second] = item.actors
  const others = item.actorCount - item.actors.length
  const quote = item.body && ['COMMENT', 'REPLY', 'COMMENT_LIKE', 'MENTION'].includes(item.type)
  const target = item.shortcode ? `/p/${item.shortcode}` : `/${first.username}`

  return (
    <li className="relative flex items-center gap-3 px-4 py-2">
      {item.unread && <span className="absolute top-1/2 left-1 h-1.5 w-1.5 -translate-y-1/2 rounded-full bg-primary" aria-label="새 알림" />}
      <Link to={`/${first.username}`} className="relative shrink-0" style={{ width: 44, height: 44 }}>
        {second ? (
          <>
            <Avatar user={second} size={32} className="absolute top-0 right-0" />
            <Avatar user={first} size={32} className="absolute bottom-0 left-0 ring-2 ring-bg" />
          </>
        ) : (
          <Avatar user={first} size={44} />
        )}
      </Link>
      <p className="min-w-0 flex-1 text-sm break-words break-keep">
        <Link to={`/${first.username}`} className="font-semibold">
          {first.username}
        </Link>
        {second && (
          <>
            님,{' '}
            <Link to={`/${second.username}`} className="font-semibold">
              {second.username}
            </Link>
          </>
        )}
        님{others > 0 && <> 외 {others.toLocaleString('ko-KR')}명</>}이 {notificationVerb(item.type)}
        {quote && <span className="text-text"> {item.body}</span>}{' '}
        <time className="text-muted" dateTime={item.createdAt}>
          {timeAgo(item.createdAt)}
        </time>
      </p>
      {item.type === 'FOLLOW' || item.type === 'FOLLOW_ACCEPTED' ? (
        item.relationship && item.relationship !== 'SELF' ? (
          <FollowButton user={first} relationship={item.relationship} followsMe className="px-4 py-1.5" />
        ) : null
      ) : item.thumbUrl ? (
        <Link to={target} className="shrink-0">
          <img src={item.thumbUrl} alt="" className="h-11 w-11 rounded object-cover" />
        </Link>
      ) : null}
    </li>
  )
}
