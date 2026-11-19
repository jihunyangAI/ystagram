import type { LucideIcon } from 'lucide-react'

/** 아직 만들지 않은 화면 자리 */
export function ComingSoonPage({ title, stage, icon: Icon }: { title: string; stage: string; icon: LucideIcon }) {
  return (
    <div className="flex min-h-[70dvh] flex-col items-center justify-center gap-3 px-6 text-center">
      <span className="flex h-20 w-20 items-center justify-center rounded-full border-2 border-text">
        <Icon size={36} strokeWidth={1.5} />
      </span>
      <h1 className="text-xl font-bold">{title}</h1>
      <p className="text-sm text-muted">{stage} 단계에서 추가될 예정입니다.</p>
    </div>
  )
}
