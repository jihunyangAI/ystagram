import { Navigate, Outlet, useLocation } from 'react-router'
import { useMe } from '../../lib/auth'
import { ResearchBanner } from '../ResearchBanner'
import { BottomNav, MobileTopBar } from './MobileBars'
import { Sidebar } from './Sidebar'

/** 로그인한 사용자용 화면 틀. 로그인 안 했으면 로그인 화면으로 보낸다. */
export function AppLayout() {
  const { data: me, isLoading, isError } = useMe()
  const location = useLocation()

  if (isLoading) {
    return <div className="flex h-dvh items-center justify-center text-sm text-muted">불러오는 중…</div>
  }
  if (isError) {
    return (
      <div className="flex h-dvh items-center justify-center p-6 text-center text-sm text-danger">
        서버에 연결할 수 없습니다. 백엔드(8080)가 실행 중인지 확인하세요.
      </div>
    )
  }
  if (!me) {
    const next = location.pathname + location.search
    return <Navigate to={`/accounts/login${next !== '/' ? `?next=${encodeURIComponent(next)}` : ''}`} replace />
  }

  return (
    <div className="min-h-dvh">
      <Sidebar me={me} />
      <div className="md:pl-[72px] xl:pl-[244px]">
        <ResearchBanner />
        <MobileTopBar />
        <main className="pb-[60px] md:pb-0">
          <Outlet />
        </main>
      </div>
      <BottomNav me={me} />
    </div>
  )
}
