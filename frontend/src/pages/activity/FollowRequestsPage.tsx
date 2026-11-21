import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ChevronLeft } from 'lucide-react'
import { Avatar } from '../../components/Avatar'
import { toast } from '../../components/Toast'
import { FollowButton } from '../../components/user/FollowButton'
import { useAnswerRequest, useFollowRequests } from '../../lib/social'
import type { Relationship } from '../../lib/types'

/** 비공개 계정에 온 팔로우 요청: 확인(승인) / 삭제(거절). 승인하면 "맞팔로우" 버튼으로 바뀐다 */
export function FollowRequestsPage() {
  const navigate = useNavigate()
  const { data, isLoading } = useFollowRequests()
  const answer = useAnswerRequest()
  // 승인한 사람은 목록에서 빼지 않고 "맞팔로우" 버튼을 보여 준다
  const [accepted, setAccepted] = useState<Record<string, Relationship>>({})

  return (
    <div className="mx-auto max-w-[600px] pb-8">
      <header className="flex items-center gap-2 px-2 pt-4 pb-2 md:pt-8">
        <button aria-label="뒤로" onClick={() => navigate(-1)} className="p-2">
          <ChevronLeft size={26} />
        </button>
        <h1 className="text-lg font-bold">팔로우 요청</h1>
      </header>
      {isLoading && <p className="p-6 text-center text-sm text-muted">불러오는 중…</p>}
      {data && data.length === 0 && Object.keys(accepted).length === 0 && (
        <p className="p-10 text-center text-sm text-muted">대기 중인 팔로우 요청이 없습니다.</p>
      )}
      <ul>
        {data?.map(({ user }) => (
          <li key={user.id} className="flex items-center gap-3 px-4 py-2">
            <Link to={`/${user.username}`} className="shrink-0">
              <Avatar user={user} size={44} />
            </Link>
            <Link to={`/${user.username}`} className="min-w-0 flex-1 text-sm leading-tight">
              <b className="block truncate">{user.username}</b>
              <span className="block truncate text-muted">{user.fullName}</span>
            </Link>
            <button
              className="rounded-lg bg-primary px-4 py-1.5 text-sm font-semibold text-white hover:bg-primary-hover disabled:opacity-50"
              disabled={answer.isPending}
              onClick={() =>
                answer.mutate(
                  { username: user.username, accept: true },
                  {
                    onSuccess: (rel) => setAccepted((a) => ({ ...a, [user.username]: rel ?? 'NONE' })),
                    onError: (e) => toast(e.message),
                  },
                )
              }
            >
              확인
            </button>
            <button
              className="rounded-lg bg-surface-2 px-4 py-1.5 text-sm font-semibold hover:bg-border disabled:opacity-50"
              disabled={answer.isPending}
              onClick={() => answer.mutate({ username: user.username, accept: false }, { onError: (e) => toast(e.message) })}
            >
              삭제
            </button>
          </li>
        ))}
      </ul>
      {Object.keys(accepted).length > 0 && (
        <>
          <p className="px-4 pt-4 pb-1 text-sm font-semibold">승인함</p>
          <ul>
            {Object.entries(accepted).map(([username, rel]) => (
              <AcceptedRow key={username} username={username} relationship={rel} />
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

function AcceptedRow({ username, relationship }: { username: string; relationship: Relationship }) {
  return (
    <li className="flex items-center gap-3 px-4 py-2 text-sm">
      <Link to={`/${username}`} className="min-w-0 flex-1 truncate font-semibold">
        {username}
      </Link>
      <span className="text-muted">님이 이제 회원님을 팔로우합니다</span>
      <FollowButton
        user={{ id: 0, username, fullName: '', avatarUrl: null, avatarSeed: username, verified: false, isPrivate: false }}
        relationship={relationship}
        followsMe
        className="px-3 py-1"
      />
    </li>
  )
}
