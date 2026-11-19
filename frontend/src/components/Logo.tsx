import clsx from 'clsx'

/** 양 모티프 심볼 (자체 제작) */
export function LogoMark({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
      <rect width="64" height="64" rx="16" fill="var(--primary)" />
      <g fill="#fff">
        <circle cx="22" cy="28" r="9" />
        <circle cx="32" cy="23" r="10" />
        <circle cx="42" cy="28" r="9" />
        <circle cx="27" cy="36" r="9" />
        <circle cx="37" cy="36" r="9" />
      </g>
      <ellipse cx="32" cy="38" rx="7" ry="8" fill="#16352d" />
      <circle cx="29.5" cy="37" r="1.4" fill="#fff" />
      <circle cx="34.5" cy="37" r="1.4" fill="#fff" />
    </svg>
  )
}

/** 워드마크 */
export function Logo({ className, withMark = false }: { className?: string; withMark?: boolean }) {
  return (
    <span className={clsx('inline-flex items-center gap-2 select-none', className)}>
      {withMark && <LogoMark />}
      <span className="text-[22px] font-extrabold tracking-[-0.04em]">
        Yang<span className="text-primary">stagram</span>
      </span>
    </span>
  )
}
