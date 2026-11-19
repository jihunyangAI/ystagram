import type { ReactNode } from 'react'
import { ResearchBanner } from '../../components/ResearchBanner'

/** 로그인·가입 화면 공통 틀: 가운데 카드 + 아래 보조 카드 */
export function AuthShell({ children, footer }: { children: ReactNode; footer: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <ResearchBanner />
      <div className="flex flex-1 flex-col items-center justify-center px-4 py-10">
        <div className="w-full max-w-[350px] space-y-3">
          <div className="rounded-lg border-border px-6 py-8 sm:border sm:px-10">{children}</div>
          <div className="rounded-lg border-border py-5 text-center text-sm sm:border">{footer}</div>
        </div>
      </div>
    </div>
  )
}
