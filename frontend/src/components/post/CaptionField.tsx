import clsx from 'clsx'

export const MAX_CAPTION = 2200
export const MAX_HASHTAGS = 5

/** 백엔드 Hashtags.extract와 같은 규칙: 대소문자 무시, 중복 제외 */
export function countHashtags(text: string): number {
  const tags = new Set<string>()
  for (const m of text.matchAll(/#([\p{L}\p{N}_]{1,100})/gu)) tags.add(m[1].toLowerCase())
  return tags.size
}

/** 캡션 입력: 글자 수(2,200)와 해시태그 수(5개)를 실시간으로 보여 주고 넘으면 경고 */
export function CaptionField({ value, onChange, autoFocus }: { value: string; onChange: (v: string) => void; autoFocus?: boolean }) {
  const tags = countHashtags(value)
  const tooManyTags = tags > MAX_HASHTAGS
  const tooLong = value.length > MAX_CAPTION
  return (
    <div>
      <textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoFocus={autoFocus}
        rows={6}
        placeholder="문구를 입력하세요…"
        aria-label="문구"
        aria-invalid={tooManyTags || tooLong}
        aria-describedby="caption-help"
        className="w-full resize-none bg-transparent text-sm outline-none placeholder:text-muted"
      />
      <div id="caption-help" className="flex items-center justify-between text-xs">
        <span className={clsx(tooManyTags ? 'font-semibold text-danger' : 'text-muted')}>
          해시태그 {tags}/{MAX_HASHTAGS}
          {tooManyTags && ' · 해시태그는 5개까지 쓸 수 있습니다'}
        </span>
        <span className={clsx(tooLong ? 'font-semibold text-danger' : 'text-muted')}>
          {value.length.toLocaleString('ko-KR')}/{MAX_CAPTION.toLocaleString('ko-KR')}
        </span>
      </div>
    </div>
  )
}

/** 켜기/끄기 스위치 */
export function Toggle({ checked, onChange, label, help }: { checked: boolean; onChange: (v: boolean) => void; label: string; help?: string }) {
  return (
    <label className="flex cursor-pointer items-start justify-between gap-4 text-sm">
      <span>
        {label}
        {help && <span className="mt-0.5 block text-xs text-muted">{help}</span>}
      </span>
      <span className="relative mt-0.5 inline-flex shrink-0">
        <input type="checkbox" role="switch" className="peer sr-only" checked={checked} onChange={(e) => onChange(e.target.checked)} />
        <span className="h-6 w-10 rounded-full bg-border transition-colors peer-checked:bg-text peer-focus-visible:outline-2 peer-focus-visible:outline-primary" />
        <span className="absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform peer-checked:translate-x-4" />
      </span>
    </label>
  )
}
