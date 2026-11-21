import { useDmUnread } from '../../lib/dm'
import { useNotificationUnread } from '../../lib/social'

/** 메시지 아이콘의 빨간 숫자: 안 읽은 대화 수 */
export function DmBadge({ className = '' }: { className?: string }) {
  const { data } = useDmUnread()
  const n = data ? data.primary + data.general + data.requests : 0
  if (!n) return null
  return (
    <span
      className={`absolute flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-danger px-1 text-[11px] font-bold text-white ${className}`}
      aria-label={`안 읽은 대화 ${n}개`}
    >
      {n > 99 ? '99+' : n}
    </span>
  )
}

/** 알림(하트) 아이콘의 빨간 점: 안 읽은 알림이나 팔로우 요청이 있으면 */
export function NotificationDot({ className = '' }: { className?: string }) {
  const { data } = useNotificationUnread()
  if (!data || (data.count === 0 && data.requests === 0)) return null
  return <span className={`absolute h-2 w-2 rounded-full bg-danger ${className}`} aria-label="새 알림" />
}
