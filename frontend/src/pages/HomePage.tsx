import { Link } from 'react-router'
import { FlaskConical, Images } from 'lucide-react'
import { Avatar } from '../components/Avatar'
import { openLabSwitcher } from '../components/lab/LabSwitcher'
import { useMe } from '../lib/auth'

/** 홈 피드 자리. M1에서 실제 피드로 바뀐다. */
export function HomePage() {
  const { data: me } = useMe()
  if (!me) return null

  return (
    <div className="mx-auto w-full max-w-[470px] px-4 py-8">
      <div className="rounded-xl border border-border p-6 text-center">
        <Avatar user={me.user} size={72} className="mx-auto" />
        <h1 className="mt-3 text-lg font-bold">{me.user.fullName}님, 환영합니다</h1>
        <p className="text-sm text-muted">@{me.user.username}</p>

        <div className="mt-6 rounded-lg bg-surface-2 p-4 text-left text-sm">
          <p className="flex items-center gap-2 font-semibold">
            <Images size={16} className="text-primary" /> 홈 피드는 다음 단계(M1)에서 채워집니다
          </p>
          <p className="mt-1 text-muted">
            지금은 뼈대 단계라 로그인, 계정 전환, 프로필 화면까지 동작합니다. 팔로우한 사람들의 게시물이 여기에 표시될 예정입니다.
          </p>
        </div>

        <div className="mt-5 flex justify-center gap-2">
          <Link to={`/${me.user.username}`} className="btn btn-secondary">
            내 프로필
          </Link>
          <button className="btn btn-primary" onClick={openLabSwitcher}>
            <FlaskConical size={16} /> 다른 계정으로 전환
          </button>
        </div>
      </div>
    </div>
  )
}
