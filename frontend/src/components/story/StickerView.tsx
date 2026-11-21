import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Hourglass, Link2, MapPin, Send } from 'lucide-react'
import clsx from 'clsx'
import { nowMs } from '../../lib/clock'
import type { Sticker, StickerResult } from '../../lib/types'

interface Props {
  sticker: Sticker
  result?: StickerResult
  /** 내 스토리면 결과·답변을 보여 주고 응답은 못 한다 */
  mine?: boolean
  /** 응답 보내기 (미리보기에서는 없음) */
  onRespond?: (value: string) => void
  /** 입력 중에는 스토리 진행을 멈춘다 */
  onFocusChange?: (focused: boolean) => void
}

const card = 'w-[220px] rounded-2xl bg-white p-3 text-center text-black shadow-lg'

/** 스토리 위의 스티커 하나 (분석 문서 §8). 위치는 부모가 잡는다 */
export function StickerView({ sticker: s, result, mine, onRespond, onFocusChange }: Props) {
  const answered = result?.myValue != null
  const reveal = mine || answered
  const counts = result?.counts ?? null
  const total = counts ? counts.reduce((a, b) => a + b, 0) : 0
  const stop = (e: React.SyntheticEvent) => e.stopPropagation()

  switch (s.type) {
    case 'POLL':
      return (
        <div className={card} onPointerDown={stop} onPointerUp={stop}>
          {s.question && <p className="mb-2 text-sm font-bold">{s.question}</p>}
          <div className="flex flex-col gap-1.5">
            {(s.options ?? []).map((o, i) => {
              const pct = reveal && counts && total ? Math.round((counts[i] / total) * 100) : null
              return (
                <button
                  key={i}
                  disabled={mine || answered || !onRespond}
                  onClick={() => onRespond?.(String(i))}
                  className={clsx(
                    'relative overflow-hidden rounded-xl border border-black/10 px-3 py-2 text-sm font-semibold',
                    result?.myValue === String(i) && 'border-[#1f8a70]',
                  )}
                >
                  {pct !== null && <span className="absolute inset-y-0 left-0 bg-[#1f8a70]/20" style={{ width: `${pct}%` }} />}
                  <span className="relative flex justify-between gap-2">
                    <span>{o}</span>
                    {pct !== null && <span>{pct}%</span>}
                  </span>
                </button>
              )
            })}
          </div>
          {mine && <p className="mt-1.5 text-[11px] text-black/50">응답 {result?.total ?? 0}명</p>}
        </div>
      )
    case 'QUIZ':
      return (
        <div className={card} onPointerDown={stop} onPointerUp={stop}>
          <p className="mb-2 text-sm font-bold">{s.question ?? '퀴즈'}</p>
          <div className="flex flex-col gap-1.5">
            {(s.options ?? []).map((o, i) => {
              const correct = reveal && s.answer === i
              const wrongPick = answered && result?.myValue === String(i) && s.answer !== i
              return (
                <button
                  key={i}
                  disabled={mine || answered || !onRespond}
                  onClick={() => onRespond?.(String(i))}
                  className={clsx(
                    'flex justify-between rounded-xl border px-3 py-2 text-left text-sm font-semibold',
                    correct ? 'border-[#1fc25b] bg-[#1fc25b] text-white' : wrongPick ? 'border-[#d6455d] bg-[#d6455d] text-white' : 'border-black/10',
                  )}
                >
                  <span>{o}</span>
                  {reveal && counts && <span className="text-xs opacity-80">{counts[i]}</span>}
                </button>
              )
            })}
          </div>
        </div>
      )
    case 'SLIDER':
      return <SliderSticker s={s} result={result} mine={mine} onRespond={onRespond} />
    case 'QUESTION':
      return <QuestionSticker s={s} result={result} mine={mine} onRespond={onRespond} onFocusChange={onFocusChange} />
    case 'MENTION':
      return (
        <Link
          to={`/${s.username}`}
          onPointerDown={stop}
          onPointerUp={stop}
          className="rounded-lg bg-white px-3 py-1.5 text-lg font-extrabold text-[#d6455d] uppercase shadow"
        >
          @{s.username}
        </Link>
      )
    case 'HASHTAG':
      return (
        <Link
          to={`/explore/tags/${encodeURIComponent(s.tag ?? '')}`}
          onPointerDown={stop}
          onPointerUp={stop}
          className="rounded-lg bg-white px-3 py-1.5 text-lg font-extrabold text-[#962fbf] shadow"
        >
          #{s.tag}
        </Link>
      )
    case 'LOCATION':
      return (
        <span className="flex items-center gap-1 rounded-lg bg-white px-3 py-1.5 text-base font-extrabold text-[#4f5bd5] uppercase shadow">
          <MapPin size={18} /> {s.name}
        </span>
      )
    case 'LINK':
      return (
        <a
          href={s.url ?? '#'}
          target="_blank"
          rel="noopener noreferrer nofollow"
          onPointerDown={stop}
          onPointerUp={stop}
          className="flex max-w-[240px] items-center gap-1.5 rounded-lg bg-white px-3 py-1.5 text-sm font-bold text-[#3d7bff] shadow"
        >
          <Link2 size={16} className="shrink-0" />
          <span className="truncate">{s.label || (s.url ?? '').replace(/^https?:\/\//, '')}</span>
        </a>
      )
    case 'COUNTDOWN':
      return <Countdown s={s} />
  }
}

function SliderSticker({ s, result, mine, onRespond }: { s: Sticker; result?: StickerResult; mine?: boolean; onRespond?: (v: string) => void }) {
  const [value, setValue] = useState(result?.myValue ? Number(result.myValue) : 50)
  const done = result?.myValue != null
  const stop = (e: React.SyntheticEvent) => e.stopPropagation()
  return (
    <div className={card} onPointerDown={stop} onPointerUp={stop}>
      {s.question && <p className="mb-2 text-sm font-bold">{s.question}</p>}
      <div className="relative">
        <input
          type="range"
          min={0}
          max={100}
          value={value}
          disabled={mine || !onRespond}
          aria-label={s.question ?? '슬라이더'}
          onChange={(e) => setValue(Number(e.target.value))}
          onPointerUp={() => onRespond?.(String(value))}
          onKeyUp={() => onRespond?.(String(value))}
          className="w-full accent-[#d6455d]"
        />
        <span className="pointer-events-none absolute -top-5 text-xl" style={{ left: `calc(${value}% - 12px)` }}>
          {s.emoji ?? '😍'}
        </span>
      </div>
      {(done || mine) && result?.average != null && (
        <p className="mt-1 text-[11px] text-black/50">
          평균 {Math.round(result.average)}점 · 응답 {result.total}명
        </p>
      )}
    </div>
  )
}

function QuestionSticker({
  s,
  result,
  mine,
  onRespond,
  onFocusChange,
}: {
  s: Sticker
  result?: StickerResult
  mine?: boolean
  onRespond?: (v: string) => void
  onFocusChange?: (f: boolean) => void
}) {
  const [text, setText] = useState('')
  const [open, setOpen] = useState(false)
  const stop = (e: React.SyntheticEvent) => e.stopPropagation()
  return (
    <div className={clsx(card, 'p-0')} onPointerDown={stop} onPointerUp={stop}>
      <p className="rounded-t-2xl bg-[#1f8a70] px-3 py-2 text-sm font-bold text-white">{s.question}</p>
      {mine ? (
        <div className="p-2 text-left text-xs">
          <button className="w-full text-center font-semibold text-[#1f8a70]" onClick={() => setOpen((o) => !o)}>
            답변 {result?.total ?? 0}개 {open ? '숨기기' : '보기'}
          </button>
          {open && (
            <ul className="mt-1 max-h-32 space-y-1 overflow-y-auto">
              {(result?.answers ?? []).map((a, i) => (
                <li key={i}>
                  <b>{a.username}</b> {a.value}
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : (
        <form
          className="flex items-center gap-1 p-2"
          onSubmit={(e) => {
            e.preventDefault()
            if (text.trim()) {
              onRespond?.(text.trim())
              setText('')
              ;(document.activeElement as HTMLElement | null)?.blur()
            }
          }}
        >
          <input
            value={text}
            onChange={(e) => setText(e.target.value)}
            onFocus={() => onFocusChange?.(true)}
            onBlur={() => onFocusChange?.(false)}
            maxLength={500}
            disabled={!onRespond}
            placeholder={result?.myValue ? '답변을 보냈습니다' : '답변 입력…'}
            aria-label="질문에 답하기"
            className="min-w-0 flex-1 rounded-lg bg-black/5 px-2 py-1.5 text-sm outline-none"
          />
          <button type="submit" aria-label="보내기" disabled={!text.trim()} className="p-1 text-[#1f8a70] disabled:opacity-30">
            <Send size={16} />
          </button>
        </form>
      )}
    </div>
  )
}

function Countdown({ s }: { s: Sticker }) {
  const [, tick] = useState(0)
  useEffect(() => {
    const t = setInterval(() => tick((n) => n + 1), 1000)
    return () => clearInterval(t)
  }, [])
  const left = Math.max(0, new Date(s.endsAt ?? 0).getTime() - nowMs())
  const d = Math.floor(left / 86_400_000)
  const h = Math.floor((left % 86_400_000) / 3_600_000)
  const m = Math.floor((left % 3_600_000) / 60_000)
  const sec = Math.floor((left % 60_000) / 1000)
  return (
    <div className={card}>
      <p className="flex items-center justify-center gap-1 text-sm font-bold">
        <Hourglass size={14} /> {s.question ?? '카운트다운'}
      </p>
      <p className="mt-1 font-mono text-2xl font-extrabold tracking-wider">
        {left === 0 ? '종료' : `${d}일 ${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`}
      </p>
    </div>
  )
}

/** 스티커를 화면 좌표(0~1)에 놓는 틀 */
export function StickerLayer({
  stickers,
  results,
  mine,
  onRespond,
  onFocusChange,
}: {
  stickers: Sticker[]
  results: StickerResult[]
  mine: boolean
  onRespond?: (stickerId: string, value: string) => void
  onFocusChange?: (focused: boolean) => void
}) {
  return (
    <>
      {stickers.map((s) => (
        <div
          key={s.id}
          className="absolute z-10 -translate-x-1/2 -translate-y-1/2"
          style={{ left: `${s.x * 100}%`, top: `${s.y * 100}%` }}
        >
          <StickerView
            sticker={s}
            result={results.find((r) => r.stickerId === s.id)}
            mine={mine}
            onRespond={onRespond ? (v) => onRespond(s.id, v) : undefined}
            onFocusChange={onFocusChange}
          />
        </div>
      ))}
    </>
  )
}
